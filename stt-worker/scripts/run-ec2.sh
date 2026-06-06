#!/usr/bin/env bash
# Run stt-worker on an EC2 instance.
# Pull the image from ECR and start the container with the correct runtime config.
#
# Prerequisites on the EC2 host:
#   - Docker installed (run install-docker.sh if needed)
#   - IAM role attached to the instance with:
#       ecr:GetAuthorizationToken, ecr:BatchGetImage, ecr:GetDownloadUrlForLayer
#       s3:GetObject (for audio files)
#       secretsmanager:GetSecretValue (if using Secrets Manager)
#
# For GPU instances additionally:
#   - NVIDIA driver installed (>= 525)
#   - nvidia-container-toolkit installed
#
# Usage:
#   ./run-ec2.sh [start|stop|restart|status|logs]
#
# Required env vars (export or set in /etc/stt-worker/env):
#   IMAGE_URI              Full ECR image URI, e.g.:
#                            653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:v1-cpu
#   KAFKA_BOOTSTRAP_SERVERS  e.g. b-1.mskcluster.xxx.kafka.ap-south-1.amazonaws.com:9092
#
# Optional env vars (sensible defaults provided):
#   COMPUTE                cpu | gpu            (default: cpu)
#   DEVICE                 cpu | cuda           (default: cpu)
#   HF_TOKEN               HuggingFace token    (default: empty — only for gated models)
#   MODELS_DIR             Host path for model cache  (default: /opt/stt-worker/models)
#   TMP_DIR                Host path for audio temp files (default: /tmp/stt-worker)
#   AWS_REGION             default: ap-south-1
#   STT_ENGINE             indicconformer | fasterwhisper  (default: indicconformer)
#   STT_ENABLE_FALLBACK    true | false         (default: true)
#   STT_DEFAULT_LANG       default: mr
#   MODEL_SIZE             small | medium | large (default: small — FasterWhisper model)
#   WORKER_CONCURRENCY     default: 1
#   LOG_LEVEL              default: INFO
#   CONTAINER_NAME         default: stt-worker
set -euo pipefail

# ── Load env file if present ──────────────────────────────────────────────────
ENV_FILE="${ENV_FILE:-/etc/stt-worker/env}"
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  set -a; source "$ENV_FILE"; set +a
fi

# ── Config with defaults ──────────────────────────────────────────────────────
IMAGE_URI="${IMAGE_URI:-}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-}"
COMPUTE="${COMPUTE:-cpu}"
DEVICE="${DEVICE:-cpu}"
COMPUTE_TYPE="${COMPUTE_TYPE:-}"          # blank → engine auto-selects
HF_TOKEN="${HF_TOKEN:-}"
MODELS_DIR="${MODELS_DIR:-/opt/stt-worker/models}"
TMP_DIR="${TMP_DIR:-/tmp/stt-worker}"
AWS_REGION="${AWS_REGION:-ap-south-1}"
STT_ENGINE="${STT_ENGINE:-indicconformer}"
STT_ENABLE_FALLBACK="${STT_ENABLE_FALLBACK:-true}"
STT_DEFAULT_LANG="${STT_DEFAULT_LANG:-mr}"
MODEL_SIZE="${MODEL_SIZE:-small}"
WORKER_CONCURRENCY="${WORKER_CONCURRENCY:-1}"
LOG_LEVEL="${LOG_LEVEL:-INFO}"
CONTAINER_NAME="${CONTAINER_NAME:-stt-worker}"

ECR_REGISTRY="${ECR_REGISTRY:-$(echo "$IMAGE_URI" | cut -d/ -f1)}"

# ── Helpers ───────────────────────────────────────────────────────────────────
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

require_var() {
  if [[ -z "${!1:-}" ]]; then
    echo "ERROR: required variable $1 is not set." >&2
    echo "       Set it as an env var or add it to ${ENV_FILE}" >&2
    exit 1
  fi
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: required command not found: $1" >&2
    exit 1
  fi
}

ecr_login() {
  log "Logging in to ECR (${ECR_REGISTRY})..."
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$ECR_REGISTRY"
}

pull_image() {
  log "Pulling image: ${IMAGE_URI}"
  docker pull "${IMAGE_URI}"
}

gpu_flags() {
  if [[ "$COMPUTE" == "gpu" ]]; then
    echo "--gpus all"
  else
    echo ""
  fi
}

do_start() {
  require_var IMAGE_URI
  require_var KAFKA_BOOTSTRAP_SERVERS
  require_cmd docker
  require_cmd aws

  # Stop any existing container with the same name
  if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log "Stopping existing container: ${CONTAINER_NAME}"
    docker stop "${CONTAINER_NAME}" 2>/dev/null || true
    docker rm   "${CONTAINER_NAME}" 2>/dev/null || true
  fi

  mkdir -p "${MODELS_DIR}" "${TMP_DIR}"
  # appuser inside the container runs as UID 10001; the bind-mount dirs must be
  # writable by that UID regardless of who created them on the host.
  chown -R 10001:10001 "${MODELS_DIR}" "${TMP_DIR}" 2>/dev/null \
    || sudo chown -R 10001:10001 "${MODELS_DIR}" "${TMP_DIR}"

  ecr_login
  pull_image

  log "Starting container: ${CONTAINER_NAME} (compute=${COMPUTE}, device=${DEVICE})"

  # Build the docker run command
  # shellcheck disable=SC2046
  docker run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    $(gpu_flags) \
    -p 9108:9108 \
    -p 8081:8081 \
    -v "${MODELS_DIR}:/models/hf" \
    -v "${TMP_DIR}:/app/tmp" \
    -e KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
    -e STT_JOBS_TOPIC="${STT_JOBS_TOPIC:-stt.jobs}" \
    -e STT_RESULTS_TOPIC="${STT_RESULTS_TOPIC:-stt.results}" \
    -e STT_DLQ_TOPIC="${STT_DLQ_TOPIC:-stt.jobs.dlq}" \
    -e LOG_LEVEL="${LOG_LEVEL}" \
    -e STT_ENGINE="${STT_ENGINE}" \
    -e STT_ENABLE_FALLBACK="${STT_ENABLE_FALLBACK}" \
    -e STT_DEFAULT_LANG="${STT_DEFAULT_LANG}" \
    -e ASR_MODEL_ID="${ASR_MODEL_ID:-ai4bharat/indic-conformer-600m-multilingual}" \
    -e ASR_MODEL_PATH="${ASR_MODEL_PATH:-}" \
    -e ASR_DECODING="${ASR_DECODING:-rnnt}" \
    -e STT_ALLOW_RUNTIME_MODEL_DOWNLOAD=true \
    -e HF_HOME=/models/hf \
    -e HF_TOKEN="${HF_TOKEN}" \
    -e MODEL_SIZE="${MODEL_SIZE}" \
    -e DEVICE="${DEVICE}" \
    -e COMPUTE_TYPE="${COMPUTE_TYPE}" \
    -e WORKER_CONCURRENCY="${WORKER_CONCURRENCY}" \
    -e AUDIO_DOWNLOAD_RETRIES=2 \
    -e AUDIO_DOWNLOAD_BACKOFF_SEC=0.5 \
    -e TMP_DIR=/app/tmp \
    "${IMAGE_URI}"

  log "Container started. Waiting for health check..."
  local retries=30 i=0
  until curl -fsS "http://localhost:8081/health" >/dev/null 2>&1; do
    i=$((i+1))
    if [[ $i -ge $retries ]]; then
      echo "ERROR: health check did not pass after ${retries} attempts." >&2
      echo "       Check logs: docker logs ${CONTAINER_NAME}" >&2
      exit 1
    fi
    echo -n "."
    sleep 5
  done
  echo ""
  log "Worker is healthy."
  log "  Metrics : http://localhost:9108/metrics"
  log "  Health  : http://localhost:8081/health"
  log "  Logs    : docker logs -f ${CONTAINER_NAME}"
}

do_stop() {
  log "Stopping container: ${CONTAINER_NAME}"
  docker stop "${CONTAINER_NAME}" 2>/dev/null && docker rm "${CONTAINER_NAME}" 2>/dev/null || true
  log "Stopped."
}

do_status() {
  if docker ps --format '{{.Names}}\t{{.Status}}' | grep -q "^${CONTAINER_NAME}"; then
    docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' \
      | grep -E "^NAMES|^${CONTAINER_NAME}"
    echo ""
    curl -fsS "http://localhost:8081/health" 2>/dev/null && echo " (health OK)" || echo "(health endpoint not reachable)"
  else
    echo "Container '${CONTAINER_NAME}' is not running."
  fi
}

do_logs() {
  docker logs -f "${CONTAINER_NAME}"
}

# ── Main ──────────────────────────────────────────────────────────────────────
ACTION="${1:-start}"

case "$ACTION" in
  start)   do_start   ;;
  stop)    do_stop    ;;
  restart) do_stop; do_start ;;
  status)  do_status  ;;
  logs)    do_logs    ;;
  *)
    echo "Usage: $0 [start|stop|restart|status|logs]" >&2
    exit 1
    ;;
esac

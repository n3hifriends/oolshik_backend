#!/usr/bin/env bash
# Run stt-worker on an EC2 instance.
# Pull the image from ECR and start the container with the correct runtime config.
#
# Prerequisites on the EC2 host:
#   - Docker installed (run install-docker.sh if needed)
#   - IAM role attached to the instance with:
#       ecr:GetAuthorizationToken, ecr:BatchGetImage, ecr:GetDownloadUrlForLayer
#       s3:GetObject (for audio files fetched from S3)
#
# Usage:
#   ./run-ec2.sh [start|stop|restart|status|logs]
#
# Required env vars (export or set in /etc/stt-worker/env):
#   KAFKA_BOOTSTRAP_SERVERS  e.g. 10.20.0.13:9092
#
# Optional env vars (sensible defaults provided):
#   IMAGE_URI              Full ECR image URI
#                          default: 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:latest-cpu
#   COMPUTE                cpu | gpu            (default: cpu)
#   DEVICE                 cpu | cuda           (default: cpu)
#   MODELS_DIR             Host path for model cache      (default: /opt/stt-worker/models)
#   TMP_DIR                Host path for temp audio files (default: /opt/stt-worker/tmp)
#   AWS_REGION             default: ap-south-1
#   STT_ENGINE             fasterwhisper        (default: fasterwhisper)
#   STT_DEFAULT_LANG       default: auto
#   MODEL_SIZE             medium | large-v3    (default: large-v3 — recommended for 8 GB RAM)
#   COMPUTE_TYPE           int8 | float16       (default: int8)
#   WORKER_CONCURRENCY     default: 1
#   MEMORY_LIMIT           Docker memory limit  (default: 6g — leaves ~2 GB for OS on t3.large)
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
IMAGE_URI="${IMAGE_URI:-653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-stt-worker:latest-cpu}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-}"
COMPUTE="${COMPUTE:-cpu}"
DEVICE="${DEVICE:-cpu}"
COMPUTE_TYPE="${COMPUTE_TYPE:-int8}"
MODELS_DIR="${MODELS_DIR:-/opt/stt-worker/models}"
TMP_DIR="${TMP_DIR:-/opt/stt-worker/tmp}"
AWS_REGION="${AWS_REGION:-ap-south-1}"
STT_ENGINE="${STT_ENGINE:-fasterwhisper}"
STT_DEFAULT_LANG="${STT_DEFAULT_LANG:-auto}"
MODEL_SIZE="${MODEL_SIZE:-large-v3}"
WORKER_CONCURRENCY="${WORKER_CONCURRENCY:-1}"
TRANSCRIBE_TIMEOUT="${TRANSCRIBE_TIMEOUT:-600}"
MEMORY_LIMIT="${MEMORY_LIMIT:-6g}"
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

ensure_models_dir() {
  mkdir -p "${MODELS_DIR}"
  if ! chown -R 10001:10001 "${MODELS_DIR}" 2>/dev/null; then
    if ! sudo chown -R 10001:10001 "${MODELS_DIR}" 2>/dev/null; then
      echo "ERROR: cannot chown ${MODELS_DIR} to 10001:10001 — model downloads will fail." >&2
      exit 1
    fi
  fi
}

ensure_tmp_dir() {
  mkdir -p "${TMP_DIR}"
  if ! chown -R 10001:10001 "${TMP_DIR}" 2>/dev/null; then
    if ! sudo chown -R 10001:10001 "${TMP_DIR}" 2>/dev/null; then
      echo "ERROR: cannot chown ${TMP_DIR} to 10001:10001 — temp audio processing will fail." >&2
      exit 1
    fi
  fi
}

do_start() {
  require_var KAFKA_BOOTSTRAP_SERVERS
  require_cmd docker
  require_cmd aws

  # Stop any existing container with the same name
  if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log "Stopping existing container: ${CONTAINER_NAME}"
    docker stop "${CONTAINER_NAME}" 2>/dev/null || true
    docker rm   "${CONTAINER_NAME}" 2>/dev/null || true
  fi

  ensure_models_dir
  ensure_tmp_dir

  ecr_login
  pull_image

  local mem_flag=""
  if [[ -n "${MEMORY_LIMIT}" ]]; then
    mem_flag="--memory ${MEMORY_LIMIT}"
  fi

  log "Starting container: ${CONTAINER_NAME} (compute=${COMPUTE}, device=${DEVICE}${MEMORY_LIMIT:+, memory=${MEMORY_LIMIT}})"

  # shellcheck disable=SC2046
  docker run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    ${mem_flag} \
    $(gpu_flags) \
    -p 9108:9108 \
    -p 8081:8081 \
    --log-opt max-size=100m \
    --log-opt max-file=3 \
    -v "${MODELS_DIR}:/models/hf" \
    -v "${TMP_DIR}:/app/tmp" \
    -e KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
    -e STT_JOBS_TOPIC="${STT_JOBS_TOPIC:-stt.jobs}" \
    -e STT_RESULTS_TOPIC="${STT_RESULTS_TOPIC:-stt.results}" \
    -e STT_DLQ_TOPIC="${STT_DLQ_TOPIC:-stt.jobs.dlq}" \
    -e LOG_LEVEL="${LOG_LEVEL}" \
    -e STT_ENGINE="${STT_ENGINE}" \
    -e STT_DEFAULT_LANG="${STT_DEFAULT_LANG}" \
    -e HF_HOME=/models/hf \
    -e MODEL_SIZE="${MODEL_SIZE}" \
    -e DEVICE="${DEVICE}" \
    -e COMPUTE_TYPE="${COMPUTE_TYPE}" \
    -e TMP_DIR=/app/tmp \
    -e WORKER_CONCURRENCY="${WORKER_CONCURRENCY}" \
    -e TRANSCRIBE_TIMEOUT="${TRANSCRIBE_TIMEOUT}" \
    -e AUDIO_DOWNLOAD_RETRIES=2 \
    -e AUDIO_DOWNLOAD_BACKOFF_SEC=0.5 \
    "${IMAGE_URI}"

  # 180 retries × 5s = 15 minutes — enough for first-run large-v3 model download
  log "Container started. Waiting for health check (up to 15 min)..."
  local retries=180 i=0
  until curl -fsS "http://localhost:8081/health" >/dev/null 2>&1; do
    i=$((i+1))
    if [[ $i -ge $retries ]]; then
      echo "ERROR: health check did not pass after $((retries * 5))s." >&2
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

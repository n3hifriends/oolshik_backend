#!/usr/bin/env bash
# Run oolshik-backend on an EC2 instance.
# Pull the image from ECR and start the container with the correct runtime config.
#
# Prerequisites on the EC2 host:
#   - Docker installed
#   - IAM role attached with: ecr:*, s3:GetObject, s3:PutObject, secretsmanager:GetSecretValue
#   - /etc/oolshik-backend/env populated (copy scripts/oolshik-backend.env and fill secrets)
#   - Firebase SA JSON at /etc/oolshik-backend/firebase-sa.json
#
# Usage:
#   ./run-ec2.sh [start|stop|restart|status|logs]
set -euo pipefail

# ── Load env file if present ──────────────────────────────────────────────────
ENV_FILE="${ENV_FILE:-/etc/oolshik-backend/env}"
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  set -a; source "$ENV_FILE"; set +a
fi

# ── Config with defaults ──────────────────────────────────────────────────────
IMAGE_URI="${IMAGE_URI:-653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-api:latest}"
AWS_REGION="${AWS_REGION:-ap-south-1}"
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-}"
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-}"
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"
JWT_SECRET="${JWT_SECRET:-}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-10.20.0.13:9092}"
APP_MESSAGING_KAFKA_ENABLED="${APP_MESSAGING_KAFKA_ENABLED:-true}"
KAFKA_CONSUMER_GROUP="${KAFKA_CONSUMER_GROUP:-oolshik-stt-backend}"
MEDIA_STORAGE="${MEDIA_STORAGE:-s3}"
MEDIA_S3_BUCKET="${MEDIA_S3_BUCKET:-oolshik-dev-ap-south-1-storage-6538}"
MEDIA_S3_REGION="${MEDIA_S3_REGION:-ap-south-1}"
FIREBASE_PROJECT_ID="${FIREBASE_PROJECT_ID:-oolshik}"
FIREBASE_CHECK_REVOKED="${FIREBASE_CHECK_REVOKED:-false}"
FIREBASE_SA_JSON_PATH="${FIREBASE_SA_JSON_PATH:-/etc/oolshik-backend/firebase-sa.json}"
APP_AUTH_GOOGLE_ENABLED="${APP_AUTH_GOOGLE_ENABLED:-true}"
APP_AUTH_GOOGLE_REQUIRE_PHONE="${APP_AUTH_GOOGLE_REQUIRE_PHONE:-true}"
APP_AUTH_GOOGLE_AUTO_LINK_BY_EMAIL="${APP_AUTH_GOOGLE_AUTO_LINK_BY_EMAIL:-false}"
APP_AUTH_GOOGLE_ALLOWED_CLIENT_IDS="${APP_AUTH_GOOGLE_ALLOWED_CLIENT_IDS:-263296903071-l80n6ccefcn05s5apnobtlpl1cc0fd5t.apps.googleusercontent.com,263296903071-v73slipdgnp9ffj4vlnav47usqpf4l3t.apps.googleusercontent.com,263296903071-e6t5p5s4on6naqbkpieo1enrudr2d6ch.apps.googleusercontent.com}"
APP_OTP_PROVIDER="${APP_OTP_PROVIDER:-msg91}"
APP_OTP_DEV_ENABLED="${APP_OTP_DEV_ENABLED:-false}"
APP_OTP_MSG91_API_KEY="${APP_OTP_MSG91_API_KEY:-}"
APP_OTP_MSG91_TEMPLATE_ID="${APP_OTP_MSG91_TEMPLATE_ID:-}"
APP_OTP_MSG91_SENDER_ID="${APP_OTP_MSG91_SENDER_ID:-}"
APP_OTP_MSG91_ENTITY_ID="${APP_OTP_MSG91_ENTITY_ID:-}"
APP_CORS_ALLOWED_ORIGINS="${APP_CORS_ALLOWED_ORIGINS:-https://www.oolshik.in,https://oolshik.in}"
APP_SECRETS_AWS_ENABLED="${APP_SECRETS_AWS_ENABLED:-false}"
APP_SECRETS_AWS_DB_SECRET_NAME="${APP_SECRETS_AWS_DB_SECRET_NAME:-oolshik/dev/db}"
APP_SECRETS_AWS_APP_SECRET_NAME="${APP_SECRETS_AWS_APP_SECRET_NAME:-oolshik/dev/app}"
STT_AUDIO_SOURCE_MODE="${STT_AUDIO_SOURCE_MODE:-S3_ONLY}"
JAVA_OPTS="${JAVA_OPTS:--Xmx512m}"
LOG_LEVEL="${LOG_LEVEL:-INFO}"
CONTAINER_NAME="${CONTAINER_NAME:-oolshik-api}"
HOST_PORT="${HOST_PORT:-8080}"

ECR_REGISTRY="${ECR_REGISTRY:-$(echo "$IMAGE_URI" | cut -d/ -f1)}"

# ── Helpers ───────────────────────────────────────────────────────────────────
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

require_var() {
  if [[ -z "${!1:-}" ]]; then
    echo "ERROR: required variable $1 is not set." >&2
    echo "       Add it to ${ENV_FILE}" >&2
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

do_start() {
  require_var SPRING_DATASOURCE_URL
  require_var SPRING_DATASOURCE_USERNAME
  require_var SPRING_DATASOURCE_PASSWORD
  require_var JWT_SECRET
  require_cmd docker
  require_cmd aws

  # Stop any existing container with the same name
  if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log "Stopping existing container: ${CONTAINER_NAME}"
    docker stop "${CONTAINER_NAME}" 2>/dev/null || true
    docker rm   "${CONTAINER_NAME}" 2>/dev/null || true
  fi

  ecr_login
  pull_image

  log "Starting container: ${CONTAINER_NAME}"

  # Mount Firebase SA JSON if it exists on the host
  firebase_mount_flag=""
  firebase_credentials_env="-e FIREBASE_PROJECT_ID=${FIREBASE_PROJECT_ID} -e FIREBASE_CHECK_REVOKED=${FIREBASE_CHECK_REVOKED}"
  if [[ -f "${FIREBASE_SA_JSON_PATH}" ]]; then
    firebase_mount_flag="-v ${FIREBASE_SA_JSON_PATH}:/secrets/firebase-sa.json:ro"
    firebase_credentials_env="${firebase_credentials_env} -e GOOGLE_APPLICATION_CREDENTIALS=/secrets/firebase-sa.json"
  fi

  # shellcheck disable=SC2086
  docker run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    -p "${HOST_PORT}:8080" \
    ${firebase_mount_flag} \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e APP_DB_MODE=rds \
    -e AWS_REGION="${AWS_REGION}" \
    -e SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}" \
    -e SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}" \
    -e SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}" \
    -e JWT_SECRET="${JWT_SECRET}" \
    -e KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
    -e APP_MESSAGING_KAFKA_ENABLED="${APP_MESSAGING_KAFKA_ENABLED}" \
    -e KAFKA_CONSUMER_GROUP="${KAFKA_CONSUMER_GROUP}" \
    -e KAFKA_TOPIC_STT_JOBS=stt.jobs \
    -e KAFKA_TOPIC_STT_RESULTS=stt.results \
    -e KAFKA_TOPIC_STT_DLQ=stt.jobs.dlq \
    -e KAFKA_TOPIC_NOTIFICATION_EVENTS=notification.events \
    -e MEDIA_STORAGE="${MEDIA_STORAGE}" \
    -e MEDIA_S3_BUCKET="${MEDIA_S3_BUCKET}" \
    -e MEDIA_S3_REGION="${MEDIA_S3_REGION}" \
    ${firebase_credentials_env} \
    -e APP_AUTH_GOOGLE_ENABLED="${APP_AUTH_GOOGLE_ENABLED}" \
    -e APP_AUTH_GOOGLE_REQUIRE_PHONE="${APP_AUTH_GOOGLE_REQUIRE_PHONE}" \
    -e APP_AUTH_GOOGLE_AUTO_LINK_BY_EMAIL="${APP_AUTH_GOOGLE_AUTO_LINK_BY_EMAIL}" \
    -e APP_AUTH_GOOGLE_ALLOWED_CLIENT_IDS="${APP_AUTH_GOOGLE_ALLOWED_CLIENT_IDS}" \
    -e APP_OTP_PROVIDER="${APP_OTP_PROVIDER}" \
    -e APP_OTP_DEV_ENABLED="${APP_OTP_DEV_ENABLED}" \
    -e APP_OTP_MSG91_API_KEY="${APP_OTP_MSG91_API_KEY}" \
    -e APP_OTP_MSG91_TEMPLATE_ID="${APP_OTP_MSG91_TEMPLATE_ID}" \
    -e APP_OTP_MSG91_SENDER_ID="${APP_OTP_MSG91_SENDER_ID}" \
    -e APP_OTP_MSG91_ENTITY_ID="${APP_OTP_MSG91_ENTITY_ID}" \
    -e APP_CORS_ALLOWED_ORIGINS="${APP_CORS_ALLOWED_ORIGINS}" \
    -e APP_SECRETS_AWS_ENABLED="${APP_SECRETS_AWS_ENABLED}" \
    -e APP_SECRETS_AWS_DB_SECRET_NAME="${APP_SECRETS_AWS_DB_SECRET_NAME}" \
    -e APP_SECRETS_AWS_APP_SECRET_NAME="${APP_SECRETS_AWS_APP_SECRET_NAME}" \
    -e STT_AUDIO_SOURCE_MODE="${STT_AUDIO_SOURCE_MODE}" \
    -e JAVA_OPTS="${JAVA_OPTS}" \
    -e LOG_LEVEL="${LOG_LEVEL}" \
    "${IMAGE_URI}"

  log "Container started. Waiting for health check (up to 3 min)..."
  local retries=36 i=0
  until curl -fsS "http://localhost:${HOST_PORT}/actuator/health" >/dev/null 2>&1; do
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
  log "Backend is healthy."
  log "  Health : http://localhost:${HOST_PORT}/actuator/health"
  log "  Logs   : docker logs -f ${CONTAINER_NAME}"
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
    curl -fsS "http://localhost:${HOST_PORT}/actuator/health" 2>/dev/null && echo " (health OK)" \
      || echo "(health endpoint not reachable)"
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

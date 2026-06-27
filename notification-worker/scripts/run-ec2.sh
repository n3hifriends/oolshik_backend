#!/usr/bin/env bash
# Run notification-worker on an EC2 instance.
# Pull the image from ECR and start the container with the correct runtime config.
#
# Prerequisites on the EC2 host:
#   - Docker installed
#   - IAM role attached with: ecr:GetAuthorizationToken, ecr:BatchGetImage, ecr:GetDownloadUrlForLayer
#   - /etc/notification-worker/env populated (copy scripts/notification-worker.env and fill secrets)
#
# Usage:
#   ./run-ec2.sh [start|stop|restart|status|logs]
#
# Required env vars (set in /etc/notification-worker/env):
#   SPRING_DATASOURCE_URL       RDS connection string
#   SPRING_DATASOURCE_USERNAME  RDS username
#   SPRING_DATASOURCE_PASSWORD  RDS password
#   KAFKA_BOOTSTRAP_SERVERS     e.g. 10.20.0.13:9092
#
# Optional env vars (sensible defaults provided):
#   IMAGE_URI              Full ECR image URI (default: latest)
#   AWS_REGION             default: ap-south-1
#   KAFKA_CONSUMER_GROUP   default: notification-worker-v1
#   FIREBASE_SA_JSON_PATH  default: /etc/oolshik-backend/firebase-sa.json
#   NOTIF_WORKER_FCM_ENABLED       default: true
#   NOTIF_WORKER_FCM_BATCH_SIZE    default: 500
#   EXPO_PUSH_ENDPOINT     default: https://exp.host/--/api/v2/push/send
#   NOTIF_WORKER_MAX_SEND_ATTEMPTS  default: 3
#   NOTIF_COALESCE_WINDOW_SECONDS   default: 10
#   NOTIF_EXPO_BATCH_SIZE           default: 100
#   MEMORY_LIMIT           Docker memory limit (default: no limit — set e.g. 1g)
#   JAVA_OPTS              default: -Xmx384m
#   LOG_LEVEL              default: INFO
#   SERVER_PORT            default: 8081
#   CONTAINER_NAME         default: notification-worker
set -euo pipefail

# ── Load env file if present ──────────────────────────────────────────────────
ENV_FILE="${ENV_FILE:-/etc/notification-worker/env}"
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  set -a; source "$ENV_FILE"; set +a
fi

# ── Config with defaults ──────────────────────────────────────────────────────
IMAGE_URI="${IMAGE_URI:-653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-notification-worker:latest}"
AWS_REGION="${AWS_REGION:-ap-south-1}"
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-}"
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-}"
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-}"
KAFKA_CONSUMER_GROUP="${KAFKA_CONSUMER_GROUP:-notification-worker-v1}"
KAFKA_TOPIC_NOTIFICATION_EVENTS="${KAFKA_TOPIC_NOTIFICATION_EVENTS:-notification.events}"
FIREBASE_SA_JSON_PATH="${FIREBASE_SA_JSON_PATH:-/etc/oolshik-backend/firebase-sa.json}"
NOTIF_WORKER_FCM_ENABLED="${NOTIF_WORKER_FCM_ENABLED:-true}"
NOTIF_WORKER_FCM_BATCH_SIZE="${NOTIF_WORKER_FCM_BATCH_SIZE:-500}"
EXPO_PUSH_ENDPOINT="${EXPO_PUSH_ENDPOINT:-https://exp.host/--/api/v2/push/send}"
NOTIF_WORKER_MAX_SEND_ATTEMPTS="${NOTIF_WORKER_MAX_SEND_ATTEMPTS:-3}"
NOTIF_COALESCE_WINDOW_SECONDS="${NOTIF_COALESCE_WINDOW_SECONDS:-10}"
NOTIF_EXPO_BATCH_SIZE="${NOTIF_EXPO_BATCH_SIZE:-100}"
MEMORY_LIMIT="${MEMORY_LIMIT:-}"
JAVA_OPTS="${JAVA_OPTS:--Xmx384m}"
LOG_LEVEL="${LOG_LEVEL:-INFO}"
SERVER_PORT="${SERVER_PORT:-8081}"
CONTAINER_NAME="${CONTAINER_NAME:-notification-worker}"
HOST_PORT="${HOST_PORT:-8081}"

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
  require_var KAFKA_BOOTSTRAP_SERVERS
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

  local mem_flag=""
  if [[ -n "${MEMORY_LIMIT}" ]]; then
    mem_flag="--memory ${MEMORY_LIMIT}"
  fi

  log "Starting container: ${CONTAINER_NAME}${MEMORY_LIMIT:+ (memory=${MEMORY_LIMIT})}"

  firebase_mount_flag=""
  firebase_credentials_env=""
  if [[ "${NOTIF_WORKER_FCM_ENABLED}" == "true" ]]; then
    if [[ ! -f "${FIREBASE_SA_JSON_PATH}" ]]; then
      echo "ERROR: NOTIF_WORKER_FCM_ENABLED=true requires Firebase service account JSON." >&2
      echo "       Expected file on EC2 host: ${FIREBASE_SA_JSON_PATH}" >&2
      echo "       Copy the Firebase Admin SDK JSON there or set FIREBASE_SA_JSON_PATH." >&2
      exit 1
    fi
    if [[ ! -r "${FIREBASE_SA_JSON_PATH}" ]]; then
      echo "ERROR: Firebase service account JSON exists but is not readable." >&2
      echo "       Host file: ${FIREBASE_SA_JSON_PATH}" >&2
      exit 1
    fi
    firebase_mount_flag="-v ${FIREBASE_SA_JSON_PATH}:/secrets/firebase-sa.json:ro"
    firebase_credentials_env="-e GOOGLE_APPLICATION_CREDENTIALS=/secrets/firebase-sa.json"
  fi

  # shellcheck disable=SC2086
  docker run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    ${mem_flag} \
    -p "${HOST_PORT}:8081" \
    --log-opt max-size=50m \
    --log-opt max-file=3 \
    ${firebase_mount_flag} \
    -e APP_DB_MODE=rds \
    -e SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}" \
    -e SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}" \
    -e SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}" \
    -e KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
    -e KAFKA_CONSUMER_GROUP="${KAFKA_CONSUMER_GROUP}" \
    -e KAFKA_TOPIC_NOTIFICATION_EVENTS="${KAFKA_TOPIC_NOTIFICATION_EVENTS}" \
    ${firebase_credentials_env} \
    -e SERVER_PORT="${SERVER_PORT}" \
    -e NOTIF_WORKER_FCM_ENABLED="${NOTIF_WORKER_FCM_ENABLED}" \
    -e NOTIF_WORKER_FCM_BATCH_SIZE="${NOTIF_WORKER_FCM_BATCH_SIZE}" \
    -e EXPO_PUSH_ENDPOINT="${EXPO_PUSH_ENDPOINT}" \
    -e NOTIF_WORKER_MAX_SEND_ATTEMPTS="${NOTIF_WORKER_MAX_SEND_ATTEMPTS}" \
    -e NOTIF_COALESCE_WINDOW_SECONDS="${NOTIF_COALESCE_WINDOW_SECONDS}" \
    -e NOTIF_EXPO_BATCH_SIZE="${NOTIF_EXPO_BATCH_SIZE}" \
    -e JAVA_OPTS="${JAVA_OPTS}" \
    -e LOG_LEVEL="${LOG_LEVEL}" \
    "${IMAGE_URI}"

  log "Container started. Waiting for health check (up to 2 min)..."
  local retries=24 i=0
  until curl -fsS "http://localhost:${HOST_PORT}/actuator/health" >/dev/null 2>&1; do
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
  log "Notification worker is healthy."
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

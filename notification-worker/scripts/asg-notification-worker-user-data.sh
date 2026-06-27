#!/bin/bash
# User-data template for the Oolshik notification-worker EC2 instance.
#
# It recreates /etc/notification-worker/env and the Firebase Admin SDK JSON on
# every launch, then starts the worker with FCM enabled and credentials mounted.
#
# Required instance role permissions:
#   - ecr:GetAuthorizationToken, ecr:BatchGetImage, ecr:GetDownloadUrlForLayer
#   - secretsmanager:GetSecretValue for NOTIFICATION_WORKER_ENV_SECRET_ID
#   - secretsmanager:GetSecretValue for FIREBASE_SA_SECRET_ID
#
# Required Secrets Manager values:
#   NOTIFICATION_WORKER_ENV_SECRET_ID: plaintext .env content in KEY=value format
#   FIREBASE_SA_SECRET_ID: plaintext Firebase Admin SDK service-account JSON

set -euo pipefail

REGION="${AWS_REGION:-ap-south-1}"
ACCOUNT_ID="${ACCOUNT_ID:-653895707563}"
REPOSITORY_NAME="${REPOSITORY_NAME:-oolshik-notification-worker}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
CONTAINER_NAME="${CONTAINER_NAME:-notification-worker}"
HOST_PORT="${HOST_PORT:-8081}"
SERVER_PORT="${SERVER_PORT:-8081}"

NOTIFICATION_WORKER_ENV_SECRET_ID="${NOTIFICATION_WORKER_ENV_SECRET_ID:-oolshik/dev/notification-worker/env}"
FIREBASE_SA_SECRET_ID="${FIREBASE_SA_SECRET_ID:-oolshik/dev/firebase-admin-sdk-json}"

ENV_DIR="/etc/notification-worker"
ENV_FILE="${ENV_DIR}/env"
FIREBASE_DIR="/etc/oolshik-backend"
FIREBASE_SA_JSON_PATH="${FIREBASE_DIR}/firebase-sa.json"
CONTAINER_FIREBASE_PATH="/secrets/firebase-sa.json"

ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
IMAGE_URI="${ECR_REGISTRY}/${REPOSITORY_NAME}:${IMAGE_TAG}"

dnf update -y
dnf install -y docker awscli

systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user || true

mkdir -p "${ENV_DIR}" "${FIREBASE_DIR}"

aws secretsmanager get-secret-value \
  --region "${REGION}" \
  --secret-id "${NOTIFICATION_WORKER_ENV_SECRET_ID}" \
  --query SecretString \
  --output text > "${ENV_FILE}"
chmod 0600 "${ENV_FILE}"

aws secretsmanager get-secret-value \
  --region "${REGION}" \
  --secret-id "${FIREBASE_SA_SECRET_ID}" \
  --query SecretString \
  --output text > "${FIREBASE_SA_JSON_PATH}"
chmod 0400 "${FIREBASE_SA_JSON_PATH}"

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

: "${SPRING_DATASOURCE_URL:?missing SPRING_DATASOURCE_URL}"
: "${SPRING_DATASOURCE_USERNAME:?missing SPRING_DATASOURCE_USERNAME}"
: "${SPRING_DATASOURCE_PASSWORD:?missing SPRING_DATASOURCE_PASSWORD}"
: "${KAFKA_BOOTSTRAP_SERVERS:?missing KAFKA_BOOTSTRAP_SERVERS}"

NOTIF_WORKER_FCM_ENABLED="${NOTIF_WORKER_FCM_ENABLED:-true}"
if [[ "${NOTIF_WORKER_FCM_ENABLED}" == "true" && ! -f "${FIREBASE_SA_JSON_PATH}" ]]; then
  echo "ERROR: NOTIF_WORKER_FCM_ENABLED=true requires ${FIREBASE_SA_JSON_PATH}" >&2
  exit 1
fi

aws ecr get-login-password --region "${REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

docker pull "${IMAGE_URI}"
docker rm -f "${CONTAINER_NAME}" || true

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${HOST_PORT}:${SERVER_PORT}" \
  --log-opt max-size=50m \
  --log-opt max-file=3 \
  -v "${FIREBASE_SA_JSON_PATH}:${CONTAINER_FIREBASE_PATH}:ro" \
  --env-file "${ENV_FILE}" \
  -e APP_DB_MODE=rds \
  -e SERVER_PORT="${SERVER_PORT}" \
  -e GOOGLE_APPLICATION_CREDENTIALS="${CONTAINER_FIREBASE_PATH}" \
  -e NOTIF_WORKER_FCM_ENABLED="${NOTIF_WORKER_FCM_ENABLED}" \
  -e NOTIF_WORKER_FCM_BATCH_SIZE="${NOTIF_WORKER_FCM_BATCH_SIZE:-500}" \
  -e KAFKA_CONSUMER_GROUP="${KAFKA_CONSUMER_GROUP:-notification-worker-v1}" \
  -e KAFKA_TOPIC_NOTIFICATION_EVENTS="${KAFKA_TOPIC_NOTIFICATION_EVENTS:-notification.events}" \
  -e EXPO_PUSH_ENDPOINT="${EXPO_PUSH_ENDPOINT:-https://exp.host/--/api/v2/push/send}" \
  -e NOTIF_WORKER_MAX_SEND_ATTEMPTS="${NOTIF_WORKER_MAX_SEND_ATTEMPTS:-3}" \
  -e NOTIF_COALESCE_WINDOW_SECONDS="${NOTIF_COALESCE_WINDOW_SECONDS:-10}" \
  -e NOTIF_EXPO_BATCH_SIZE="${NOTIF_EXPO_BATCH_SIZE:-100}" \
  -e JAVA_OPTS="${JAVA_OPTS:--Xmx384m}" \
  -e LOG_LEVEL="${LOG_LEVEL:-INFO}" \
  "${IMAGE_URI}"

for i in $(seq 1 24); do
  if curl -fsS "http://localhost:${HOST_PORT}/actuator/health" >/dev/null 2>&1; then
    echo "Notification worker is healthy"
    exit 0
  fi
  sleep 5
done

echo "ERROR: notification worker health check did not pass" >&2
docker logs --tail 200 "${CONTAINER_NAME}" >&2 || true
exit 1

#!/bin/bash
# User-data template for the Oolshik API Auto Scaling Group.
#
# This script is intended for EC2 launch template user-data. It recreates the
# runtime env file and Firebase Admin SDK JSON on every new instance, then
# starts the API container with the same production settings as scripts/run-ec2.sh.
#
# Required instance role permissions:
#   - ecr:GetAuthorizationToken, ecr:BatchGetImage, ecr:GetDownloadUrlForLayer
#   - secretsmanager:GetSecretValue for BACKEND_ENV_SECRET_ID and FIREBASE_SA_SECRET_ID
#
# Required Secrets Manager values:
#   BACKEND_ENV_SECRET_ID: plaintext .env content in KEY=value format
#   FIREBASE_SA_SECRET_ID: plaintext Firebase Admin SDK service-account JSON

set -euo pipefail

REGION="${AWS_REGION:-ap-south-1}"
ACCOUNT_ID="${ACCOUNT_ID:-653895707563}"
REPOSITORY_NAME="${REPOSITORY_NAME:-oolshik-api}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
CONTAINER_NAME="${CONTAINER_NAME:-oolshik-api}"
HOST_PORT="${HOST_PORT:-8080}"

BACKEND_ENV_SECRET_ID="${BACKEND_ENV_SECRET_ID:-oolshik/dev/backend/env}"
FIREBASE_SA_SECRET_ID="${FIREBASE_SA_SECRET_ID:-oolshik/dev/firebase-admin-sdk-json}"

ENV_DIR="/etc/oolshik-backend"
ENV_FILE="${ENV_DIR}/env"
FIREBASE_SA_JSON_PATH="${ENV_DIR}/firebase-sa.json"
CONTAINER_FIREBASE_PATH="/secrets/firebase-sa.json"

ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
IMAGE_URI="${ECR_REGISTRY}/${REPOSITORY_NAME}:${IMAGE_TAG}"

dnf update -y
dnf install -y docker awscli

systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user || true

mkdir -p "${ENV_DIR}"

aws secretsmanager get-secret-value \
  --region "${REGION}" \
  --secret-id "${BACKEND_ENV_SECRET_ID}" \
  --query SecretString \
  --output text > "${ENV_FILE}"
chmod 0600 "${ENV_FILE}"

aws secretsmanager get-secret-value \
  --region "${REGION}" \
  --secret-id "${FIREBASE_SA_SECRET_ID}" \
  --query SecretString \
  --output text > "${FIREBASE_SA_JSON_PATH}"
chown 10001:10001 "${FIREBASE_SA_JSON_PATH}"
chmod 0400 "${FIREBASE_SA_JSON_PATH}"

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

: "${SPRING_DATASOURCE_URL:?missing SPRING_DATASOURCE_URL}"
: "${SPRING_DATASOURCE_USERNAME:?missing SPRING_DATASOURCE_USERNAME}"
: "${SPRING_DATASOURCE_PASSWORD:?missing SPRING_DATASOURCE_PASSWORD}"
: "${JWT_SECRET:?missing JWT_SECRET}"
: "${KAFKA_BOOTSTRAP_SERVERS:?missing KAFKA_BOOTSTRAP_SERVERS}"

ADMIN_NOTIF_PUSH_PROVIDER="${ADMIN_NOTIF_PUSH_PROVIDER:-FCM}"
if [[ "${ADMIN_NOTIF_PUSH_PROVIDER}" == "FCM" && ! -f "${FIREBASE_SA_JSON_PATH}" ]]; then
  echo "ERROR: ADMIN_NOTIF_PUSH_PROVIDER=FCM requires ${FIREBASE_SA_JSON_PATH}" >&2
  exit 1
fi

aws ecr get-login-password --region "${REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

docker pull "${IMAGE_URI}"
docker rm -f "${CONTAINER_NAME}" || true

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${HOST_PORT}:8080" \
  -v "${FIREBASE_SA_JSON_PATH}:${CONTAINER_FIREBASE_PATH}:ro" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e APP_DB_MODE=rds \
  -e AWS_REGION="${REGION}" \
  -e SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}" \
  -e SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}" \
  -e SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}" \
  -e JWT_SECRET="${JWT_SECRET}" \
  -e KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-10.20.0.13:9092}" \
  -e APP_MESSAGING_KAFKA_ENABLED="${APP_MESSAGING_KAFKA_ENABLED:-true}" \
  -e KAFKA_CONSUMER_GROUP="${KAFKA_CONSUMER_GROUP:-oolshik-stt-backend}" \
  -e KAFKA_TOPIC_STT_JOBS=stt.jobs \
  -e KAFKA_TOPIC_STT_RESULTS=stt.results \
  -e KAFKA_TOPIC_STT_DLQ=stt.jobs.dlq \
  -e KAFKA_TOPIC_NOTIFICATION_EVENTS=notification.events \
  -e MEDIA_STORAGE="${MEDIA_STORAGE:-s3}" \
  -e MEDIA_S3_BUCKET="${MEDIA_S3_BUCKET:-oolshik-dev-ap-south-1-storage-6538}" \
  -e MEDIA_S3_REGION="${MEDIA_S3_REGION:-ap-south-1}" \
  -e FIREBASE_PROJECT_ID="${FIREBASE_PROJECT_ID:-oolshik}" \
  -e FIREBASE_CHECK_REVOKED="${FIREBASE_CHECK_REVOKED:-false}" \
  -e GOOGLE_APPLICATION_CREDENTIALS="${CONTAINER_FIREBASE_PATH}" \
  -e APP_AUTH_GOOGLE_ENABLED="${APP_AUTH_GOOGLE_ENABLED:-true}" \
  -e APP_AUTH_GOOGLE_REQUIRE_PHONE="${APP_AUTH_GOOGLE_REQUIRE_PHONE:-true}" \
  -e APP_AUTH_GOOGLE_AUTO_LINK_BY_EMAIL="${APP_AUTH_GOOGLE_AUTO_LINK_BY_EMAIL:-false}" \
  -e APP_AUTH_GOOGLE_ALLOWED_CLIENT_IDS="${APP_AUTH_GOOGLE_ALLOWED_CLIENT_IDS:-}" \
  -e APP_OTP_PROVIDER="${APP_OTP_PROVIDER:-dev}" \
  -e APP_OTP_DEV_ENABLED="${APP_OTP_DEV_ENABLED:-false}" \
  -e APP_OTP_MSG91_API_KEY="${APP_OTP_MSG91_API_KEY:-}" \
  -e APP_OTP_MSG91_TEMPLATE_ID="${APP_OTP_MSG91_TEMPLATE_ID:-}" \
  -e APP_OTP_MSG91_SENDER_ID="${APP_OTP_MSG91_SENDER_ID:-}" \
  -e APP_OTP_MSG91_ENTITY_ID="${APP_OTP_MSG91_ENTITY_ID:-}" \
  -e APP_CORS_ALLOWED_ORIGINS="${APP_CORS_ALLOWED_ORIGINS:-https://www.oolshik.in,https://oolshik.in}" \
  -e ADMIN_NOTIF_PUSH_PROVIDER="${ADMIN_NOTIF_PUSH_PROVIDER}" \
  -e APP_SECRETS_AWS_ENABLED="${APP_SECRETS_AWS_ENABLED:-false}" \
  -e APP_SECRETS_AWS_DB_SECRET_NAME="${APP_SECRETS_AWS_DB_SECRET_NAME:-oolshik/dev/db}" \
  -e APP_SECRETS_AWS_APP_SECRET_NAME="${APP_SECRETS_AWS_APP_SECRET_NAME:-oolshik/dev/app}" \
  -e STT_AUDIO_SOURCE_MODE="${STT_AUDIO_SOURCE_MODE:-S3_ONLY}" \
  -e JAVA_OPTS="${JAVA_OPTS:--Xmx512m}" \
  -e LOG_LEVEL="${LOG_LEVEL:-INFO}" \
  "${IMAGE_URI}"

for i in $(seq 1 36); do
  if curl -fsS "http://localhost:${HOST_PORT}/actuator/health" >/dev/null 2>&1; then
    echo "Backend is healthy"
    exit 0
  fi
  sleep 5
done

echo "ERROR: backend health check did not pass" >&2
docker logs --tail 200 "${CONTAINER_NAME}" >&2 || true
exit 1

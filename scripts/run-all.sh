#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_MOBILE_DIR="/Users/nitinkalokhe/Ni3/Oolshik"

TARGET=""
ACTION=""
PLATFORM=""
API_URL=""
API_URL_EXPLICIT=false
MOBILE_DIR_VALUE="${MOBILE_DIR:-}"
GPU=false
SERVICES=()
DOCKER_CMD=()

usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-all.sh <target> <action> <service...> [options]

Targets:
  local   Run services on this machine using Docker Compose and Expo.
  ec2     Run or deploy services on an EC2 host using Docker/ECR.
  aws     AWS helper actions such as pushing images to ECR.

Actions:
  up       Local alias for start.
  deploy   EC2 deploy/start from ECR image.
  build    Local build helper for supported services.
  start    Start service.
  stop     Stop service.
  restart  Restart service.
  status   Show service status and health.
  logs     Follow service logs.
  push     Build and push image to ECR.

Services:
  api
  db
  kafka
  stt-worker
  notification-worker
  mobile
  all

Options:
  --platform ios|android        Mobile local default API URL selector.
  --api-url URL                 API URL passed to mobile as EXPO_PUBLIC_API_URL.
  --mobile-dir PATH             Mobile repo path. Overrides MOBILE_DIR env var.
  --gpu                         Use GPU variant for supported STT operations.
  -h, --help                    Show this help.

Examples:
  ./scripts/run-all.sh local up all
  ./scripts/run-all.sh local up api
  ./scripts/run-all.sh local up kafka notification-worker
  ./scripts/run-all.sh local build stt-worker
  ./scripts/run-all.sh local build stt-worker --gpu
  ./scripts/run-all.sh local logs api
  ./scripts/run-all.sh local up mobile --platform ios
  ./scripts/run-all.sh local up mobile --platform android
  ./scripts/run-all.sh local up mobile --api-url http://192.168.29.209:8080
  MOBILE_DIR=/Users/me/projects/Oolshik ./scripts/run-all.sh local up mobile --platform ios
  ./scripts/run-all.sh local up mobile --mobile-dir /Users/me/projects/Oolshik --platform ios

  ./scripts/run-all.sh ec2 deploy api
  ./scripts/run-all.sh ec2 deploy notification-worker
  ./scripts/run-all.sh ec2 deploy stt-worker
  ./scripts/run-all.sh ec2 restart api
  ./scripts/run-all.sh ec2 status all
  ./scripts/run-all.sh ec2 logs notification-worker

  ./scripts/run-all.sh aws push api
  ./scripts/run-all.sh aws push notification-worker
  ./scripts/run-all.sh aws push stt-worker
  ./scripts/run-all.sh aws push stt-worker --gpu

Notes:
  EC2 commands are host-local. Run them on the EC2 host unless a future SSH mode is added.
  EC2 deploy pulls from ECR and assumes images have already been pushed.
  EC2 Kafka management is intentionally unsupported until Kafka hosting is decided.
EOF
}

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

die() {
  echo "ERROR: $*" >&2
  echo "" >&2
  usage >&2
  exit 1
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: required command not found: $1" >&2
    exit 1
  fi
}

parse_args() {
  if [[ $# -eq 0 ]]; then
    usage
    exit 0
  fi

  case "${1:-}" in
    -h|--help|help)
      usage
      exit 0
      ;;
  esac

  TARGET="${1:-}"
  ACTION="${2:-}"
  if [[ -z "$TARGET" || -z "$ACTION" ]]; then
    die "target and action are required"
  fi
  shift 2

  while [[ $# -gt 0 ]]; do
    case "$1" in
      -h|--help|help)
        usage
        exit 0
        ;;
      --platform)
        [[ $# -ge 2 ]] || die "--platform requires ios or android"
        PLATFORM="$2"
        shift 2
        ;;
      --api-url)
        [[ $# -ge 2 ]] || die "--api-url requires a URL"
        API_URL="$2"
        API_URL_EXPLICIT=true
        shift 2
        ;;
      --mobile-dir)
        [[ $# -ge 2 ]] || die "--mobile-dir requires a path"
        MOBILE_DIR_VALUE="$2"
        shift 2
        ;;
      --gpu)
        GPU=true
        shift
        ;;
      --*)
        die "unknown option: $1"
        ;;
      *)
        SERVICES+=("$1")
        shift
        ;;
    esac
  done

  [[ ${#SERVICES[@]} -gt 0 ]] || die "at least one service is required"
}

validate_target_action() {
  case "$TARGET" in
    local|ec2|aws) ;;
    *) die "unknown target: $TARGET" ;;
  esac

  case "$ACTION" in
    up|deploy|build|start|stop|restart|status|logs|push) ;;
    *) die "unknown action: $ACTION" ;;
  esac
}

validate_service_name() {
  case "$1" in
    api|db|kafka|stt-worker|notification-worker|mobile|all) ;;
    *) die "unknown service: $1" ;;
  esac
}

compose() {
  require_cmd docker
  (
    cd "$ROOT_DIR"
    APP_DB_MODE=local \
    SPRING_PROFILES_ACTIVE=docker \
    SPRING_DATASOURCE_URL= \
    SPRING_DATASOURCE_USERNAME= \
    SPRING_DATASOURCE_PASSWORD= \
    docker compose "$@"
  )
}

local_start_service() {
  case "$1" in
    all)
      log "Starting local full stack with Docker Compose profile 'full'."
      compose --profile full up -d --build
      ;;
    db)
      log "Starting local db."
      compose up -d db
      ;;
    api)
      log "Starting local api with db dependency."
      compose up -d --build db api
      ;;
    kafka)
      log "Starting local kafka."
      compose --profile full up -d kafka
      ;;
    stt-worker)
      log "Starting local stt-worker with kafka dependency."
      log "First STT worker start may take several minutes while model files are downloaded or initialized."
      compose --profile full up -d --build kafka stt-worker
      ;;
    notification-worker)
      log "Starting local notification-worker with db and kafka dependencies."
      compose --profile full up -d --build db kafka notification-worker
      ;;
    mobile)
      start_mobile
      ;;
  esac
}

local_build_service() {
  case "$1" in
    stt-worker)
      local compute="cpu"
      if [[ "$GPU" == "true" ]]; then
        compute="gpu"
      fi
      log "Building local stt-worker (${compute})."
      (cd "$ROOT_DIR/stt-worker" && ./scripts/build.sh "$compute")
      ;;
    all)
      log "local build all builds only stt-worker. API and worker images are built by Docker Compose on start."
      local_build_service stt-worker
      ;;
    *)
      die "local build is only supported for stt-worker"
      ;;
  esac
}

local_status_service() {
  case "$1" in
    all) compose --profile full ps ;;
    mobile) die "local status mobile is not supported by this runner" ;;
    *) compose ps "$1" ;;
  esac
}

local_logs_service() {
  case "$1" in
    all) compose --profile full logs -f ;;
    mobile) die "local logs mobile is not supported by this runner" ;;
    *) compose logs -f "$1" ;;
  esac
}

local_stop_service() {
  case "$1" in
    all) compose --profile full stop ;;
    mobile) die "local stop mobile is not supported by this runner" ;;
    *) compose stop "$1" ;;
  esac
}

local_restart_service() {
  case "$1" in
    all) compose --profile full restart ;;
    mobile) die "local restart mobile is not supported by this runner" ;;
    *) compose restart "$1" ;;
  esac
}

resolve_mobile_dir() {
  if [[ -z "$MOBILE_DIR_VALUE" ]]; then
    MOBILE_DIR_VALUE="$DEFAULT_MOBILE_DIR"
  fi
  if [[ ! -d "$MOBILE_DIR_VALUE" ]]; then
    echo "ERROR: mobile directory not found: $MOBILE_DIR_VALUE" >&2
    echo "       Set MOBILE_DIR or pass --mobile-dir /path/to/Oolshik." >&2
    exit 1
  fi
  if [[ ! -f "$MOBILE_DIR_VALUE/package.json" ]]; then
    echo "ERROR: mobile directory does not contain package.json: $MOBILE_DIR_VALUE" >&2
    echo "       Set MOBILE_DIR or pass --mobile-dir /path/to/Oolshik." >&2
    exit 1
  fi
}

resolve_mobile_api_url() {
  if [[ -n "$API_URL" ]]; then
    if [[ "$API_URL" =~ :8081(/|$) ]]; then
      echo "ERROR: --api-url points to port 8081: $API_URL" >&2
      echo "       Local backend API runs on port 8080. Port 8081 is used by Metro/worker services." >&2
      echo "       Use: --api-url http://<your-mac-lan-ip>:8080" >&2
      exit 1
    fi
    return
  fi

  case "$PLATFORM" in
    ios)
      API_URL="http://localhost:8080"
      ;;
    android)
      API_URL="http://10.0.2.2:8080"
      ;;
    "")
      echo "ERROR: mobile needs --platform ios|android or --api-url URL." >&2
      exit 1
      ;;
    *)
      echo "ERROR: unsupported mobile platform: $PLATFORM" >&2
      echo "       Use --platform ios or --platform android." >&2
      exit 1
      ;;
  esac
}

start_mobile() {
  require_cmd npm
  require_cmd node
  resolve_mobile_dir
  resolve_mobile_api_url

  log "Starting mobile app from ${MOBILE_DIR_VALUE}."
  log "EXPO_PUBLIC_API_URL=${API_URL}"
  (
    cd "$MOBILE_DIR_VALUE"
    if ! node -e "const p=require('./package.json'); process.exit(p.scripts && p.scripts['start:local'] ? 0 : 1)" >/dev/null 2>&1; then
      echo "ERROR: mobile package.json does not define a 'start:local' script." >&2
      exit 1
    fi
    if [[ "$API_URL_EXPLICIT" == "true" ]]; then
      EXPO_PUBLIC_API_URL="$API_URL" \
      EXPO_PUBLIC_AUTH_PHONE_OTP_ENABLED="${EXPO_PUBLIC_AUTH_PHONE_OTP_ENABLED:-true}" \
      npx expo start --dev-client --lan --clear
      exit
    fi
    EXPO_PUBLIC_API_URL="$API_URL" \
    EXPO_PUBLIC_AUTH_PHONE_OTP_ENABLED="${EXPO_PUBLIC_AUTH_PHONE_OTP_ENABLED:-true}" \
    npm run start:local
  )
}

run_local() {
  case "$ACTION" in
    up|start)
      for service in "${SERVICES[@]}"; do
        validate_service_name "$service"
        local_start_service "$service"
      done
      ;;
    build)
      for service in "${SERVICES[@]}"; do
        validate_service_name "$service"
        local_build_service "$service"
      done
      ;;
    stop)
      for service in "${SERVICES[@]}"; do
        validate_service_name "$service"
        local_stop_service "$service"
      done
      ;;
    restart)
      for service in "${SERVICES[@]}"; do
        validate_service_name "$service"
        local_restart_service "$service"
      done
      ;;
    status)
      for service in "${SERVICES[@]}"; do
        validate_service_name "$service"
        local_status_service "$service"
      done
      ;;
    logs)
      for service in "${SERVICES[@]}"; do
        validate_service_name "$service"
        local_logs_service "$service"
      done
      ;;
    deploy|push)
      die "action '$ACTION' is not supported for target local"
      ;;
  esac
}

ec2_script_for_service() {
  case "$1" in
    api) echo "$ROOT_DIR/scripts/run-ec2.sh" ;;
    notification-worker) echo "$ROOT_DIR/notification-worker/scripts/run-ec2.sh" ;;
    stt-worker) echo "$ROOT_DIR/stt-worker/scripts/run-ec2.sh" ;;
    kafka)
      echo "ERROR: EC2 Kafka management is not implemented yet. Decide Kafka hosting first: same-host Docker, dedicated EC2, or MSK." >&2
      exit 1
      ;;
    db|mobile)
      echo "ERROR: service '$1' is not supported for EC2 target." >&2
      exit 1
      ;;
    all)
      echo "ERROR: internal error: all should be expanded before script lookup." >&2
      exit 1
      ;;
  esac
}

ec2_action_for_delegate() {
  case "$1" in
    deploy|up|start) echo "start" ;;
    stop|restart|status|logs) echo "$1" ;;
    *)
      echo "ERROR: action '$1' is not supported for target ec2." >&2
      exit 1
      ;;
  esac
}

init_docker_cmd() {
  if [[ ${#DOCKER_CMD[@]} -gt 0 ]]; then
    return
  fi

  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    DOCKER_CMD=(docker)
    return
  fi

  if command -v sudo >/dev/null 2>&1 && sudo -n docker info >/dev/null 2>&1; then
    DOCKER_CMD=(sudo docker)
    return
  fi

  echo "ERROR: current user cannot access the Docker daemon." >&2
  echo "       Run with sudo, or add the user to the docker group and start a new login session." >&2
  exit 1
}

docker_cmd() {
  init_docker_cmd
  "${DOCKER_CMD[@]}" "$@"
}

known_ec2_container_names() {
  printf '%s\n' "oolshik-api" "notification-worker" "stt-worker"
}

service_for_ec2_container() {
  case "$1" in
    oolshik-api) echo "api" ;;
    notification-worker) echo "notification-worker" ;;
    stt-worker) echo "stt-worker" ;;
    *)
      echo "ERROR: unknown EC2 container: $1" >&2
      exit 1
      ;;
  esac
}

present_ec2_container_names() {
  local name
  while IFS= read -r name; do
    if docker_cmd ps -a --format '{{.Names}}' | grep -qx "$name"; then
      printf '%s\n' "$name"
    fi
  done < <(known_ec2_container_names)
}

run_ec2_all() {
  case "$ACTION" in
    deploy|up|start)
      echo "ERROR: ec2 deploy all is intentionally unsupported." >&2
      echo "       Name the service explicitly: api, notification-worker, or stt-worker." >&2
      exit 1
      ;;
    status)
      local found=false
      local container
      while IFS= read -r container; do
        found=true
        log "Status for ${container}"
        docker_cmd ps -a --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' \
          | grep -E "^NAMES|^${container}[[:space:]]"
        case "$container" in
          oolshik-api) curl -fsS "http://localhost:8080/actuator/health" 2>/dev/null && echo " (health OK)" || echo "(health endpoint not reachable)" ;;
          notification-worker) curl -fsS "http://localhost:8081/actuator/health" 2>/dev/null && echo " (health OK)" || echo "(health endpoint not reachable)" ;;
          stt-worker) curl -fsS "http://localhost:8081/health" 2>/dev/null && echo " (health OK)" || echo "(health endpoint not reachable)" ;;
        esac
        echo ""
      done < <(present_ec2_container_names)
      if [[ "$found" == "false" ]]; then
        echo "No known Oolshik containers found on this host."
      fi
      ;;
    stop|restart)
      local found=false
      local container
      while IFS= read -r container; do
        found=true
        local service
        service="$(service_for_ec2_container "$container")"
        local script
        script="$(ec2_script_for_service "$service")"
        log "${ACTION} ${service}"
        "$script" "$ACTION"
      done < <(present_ec2_container_names)
      if [[ "$found" == "false" ]]; then
        echo "No known Oolshik containers found on this host."
      fi
      ;;
    logs)
      local found=false
      local container
      while IFS= read -r container; do
        found=true
        log "Recent logs for ${container}"
        docker_cmd logs --tail 200 "$container"
        echo ""
      done < <(present_ec2_container_names)
      if [[ "$found" == "false" ]]; then
        echo "No known Oolshik containers found on this host."
      else
        echo "Note: ec2 logs all prints recent logs for each known container; use a single service to follow logs."
      fi
      ;;
    *)
      die "action '$ACTION' is not supported for target ec2"
      ;;
  esac
}

run_ec2_service() {
  local service="$1"
  validate_service_name "$service"

  if [[ "$service" == "all" ]]; then
    run_ec2_all
    return
  fi

  local script
  script="$(ec2_script_for_service "$service")"
  local delegate_action
  delegate_action="$(ec2_action_for_delegate "$ACTION")"

  if [[ ! -x "$script" ]]; then
    echo "ERROR: service script is not executable: $script" >&2
    exit 1
  fi

  "$script" "$delegate_action"
}

run_ec2() {
  if [[ "$ACTION" == "build" || "$ACTION" == "push" ]]; then
    die "action '$ACTION' is not supported for target ec2"
  fi

  for service in "${SERVICES[@]}"; do
    run_ec2_service "$service"
  done
}

run_aws_push_service() {
  local service="$1"
  validate_service_name "$service"

  case "$service" in
    api)
      (cd "$ROOT_DIR" && ./push-image.sh oolshik-api)
      ;;
    notification-worker)
      (cd "$ROOT_DIR/notification-worker" && ./push-image.sh oolshik-notification-worker)
      ;;
    stt-worker)
      local compute="cpu"
      if [[ "$GPU" == "true" ]]; then
        compute="gpu"
      fi
      (cd "$ROOT_DIR/stt-worker" && ./push-image.sh oolshik-stt-worker "$compute")
      ;;
    all)
      run_aws_push_service api
      run_aws_push_service notification-worker
      run_aws_push_service stt-worker
      ;;
    *)
      die "aws push is not supported for service '$service'"
      ;;
  esac
}

run_aws() {
  case "$ACTION" in
    push)
      for service in "${SERVICES[@]}"; do
        run_aws_push_service "$service"
      done
      ;;
    *)
      die "action '$ACTION' is not supported for target aws"
      ;;
  esac
}

main() {
  parse_args "$@"
  validate_target_action

  case "$TARGET" in
    local) run_local ;;
    ec2) run_ec2 ;;
    aws) run_aws ;;
  esac
}

main "$@"

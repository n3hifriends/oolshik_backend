#!/usr/bin/env bash
# Connect to the AWS RDS (ap-south-1) via SSM port forwarding — no bastion needed.
# Requires: aws cli v2, session-manager-plugin, psql

set -euo pipefail

# ── RDS / tunnel config ───────────────────────────────────────────────────────
DB_HOST="127.0.0.1"
DB_PORT="5433"
DB_NAME="oolshik"
DB_USER="oolshik_admin"
export PGPASSWORD="${PGPASSWORD:-Ndroid11!}"

AWS_REGION="ap-south-1"
SSM_TARGET="${SSM_TARGET:-i-0fa45b589d5e37630}" # STT worker EC2 (has RDS network access)
SSM_TARGET_NAME_PATTERN="${SSM_TARGET_NAME_PATTERN:-*stt-worker*}"
RDS_HOST="oolshik-dev-ap-south-1-rds.c1acsg0uu5qk.ap-south-1.rds.amazonaws.com"
RDS_PORT="5432"

TUNNEL_PID_FILE="/tmp/db-rds-tunnel.pid"

# ── tunnel lifecycle ──────────────────────────────────────────────────────────
port_in_use() {
  nc -z 127.0.0.1 "$DB_PORT" 2>/dev/null
}

ssm_target_online() {
  local target="$1"
  local status
  if ! status=$(aws ssm describe-instance-information \
    --region "$AWS_REGION" \
    --filters "Key=InstanceIds,Values=${target}" \
    --query 'InstanceInformationList[0].PingStatus' \
    --output text)
  then
    return 1
  fi
  [[ "$status" == "Online" ]]
}

discover_ssm_target() {
  local candidates id
  candidates=$(aws ec2 describe-instances \
    --region "$AWS_REGION" \
    --filters "Name=tag:Name,Values=${SSM_TARGET_NAME_PATTERN}" "Name=instance-state-name,Values=running" \
    --query 'Reservations[].Instances[].InstanceId' \
    --output text)

  for id in $candidates; do
    if ssm_target_online "$id"; then
      printf '%s\n' "$id"
      return 0
    fi
  done

  return 1
}

resolve_ssm_target() {
  if ssm_target_online "$SSM_TARGET"; then
    printf '%s\n' "$SSM_TARGET"
    return 0
  fi

  local discovered
  if discovered=$(discover_ssm_target); then
    echo "[db-rds] Configured SSM target ${SSM_TARGET} is not online. Using discovered target ${discovered}." >&2
    printf '%s\n' "$discovered"
    return 0
  fi

  echo "[db-rds] ERROR: configured SSM target ${SSM_TARGET} is not connected to SSM, and no online '${SSM_TARGET_NAME_PATTERN}' instance was found." >&2
  echo "[db-rds] Set SSM_TARGET=<instance-id> or start/fix SSM on the worker instance, then retry." >&2
  return 1
}

start_ssm_session() {
  local target
  target=$(resolve_ssm_target)
  aws ssm start-session \
    --region "$AWS_REGION" \
    --target "$target" \
    --document-name AWS-StartPortForwardingSessionToRemoteHost \
    --parameters "{\"host\":[\"${RDS_HOST}\"],\"portNumber\":[\"${RDS_PORT}\"],\"localPortNumber\":[\"${DB_PORT}\"]}" \
    >/dev/null &
}

tunnel_start() {
  if port_in_use; then
    echo "[db-rds] Tunnel already running on port ${DB_PORT}."
    return
  fi

  echo "[db-rds] Starting persistent SSM tunnel -> ${RDS_HOST}:${RDS_PORT} ..."
  start_ssm_session
  echo $! > "$TUNNEL_PID_FILE"

  local retries=20
  while ! port_in_use; do
    if (( retries-- == 0 )); then
      echo "[db-rds] ERROR: tunnel did not open on port ${DB_PORT} within 10 s." >&2
      tunnel_stop
      exit 1
    fi
    sleep 0.5
  done
  echo "[db-rds] Tunnel ready on 127.0.0.1:${DB_PORT} (PID $(cat "$TUNNEL_PID_FILE")). Run './db-rds.sh tunnel-stop' when done."
}

tunnel_stop() {
  if [[ -f "$TUNNEL_PID_FILE" ]]; then
    local pid
    pid=$(cat "$TUNNEL_PID_FILE")
    kill "$pid" 2>/dev/null && echo "[db-rds] Tunnel (PID ${pid}) stopped." || echo "[db-rds] Tunnel was already gone."
    rm -f "$TUNNEL_PID_FILE"
  else
    echo "[db-rds] No tunnel PID file found — nothing to stop."
  fi
}

ensure_tunnel() {
  if port_in_use; then
    return  # reuse whatever is holding the port (persistent or manual)
  fi
  echo "[db-rds] No tunnel on port ${DB_PORT}. Starting one-shot tunnel for this query..."
  start_ssm_session
  local ssm_pid=$!
  trap "kill $ssm_pid 2>/dev/null; exit" EXIT

  local retries=20
  while ! port_in_use; do
    if (( retries-- == 0 )); then
      echo "[db-rds] ERROR: tunnel did not open on port ${DB_PORT} within 10 s." >&2
      kill "$ssm_pid" 2>/dev/null || true
      exit 1
    fi
    sleep 0.5
  done
  echo "[db-rds] Tip: run './db-rds.sh tunnel-start' once to avoid per-query tunnel overhead."
}

# ── psql wrapper ──────────────────────────────────────────────────────────────
psql_cmd() {
  psql "host=${DB_HOST} port=${DB_PORT} dbname=${DB_NAME} user=${DB_USER} sslmode=require" "$@"
}

usage() {
  cat <<'EOF'
Usage:
  ./db-rds.sh <command>

The script opens an SSM tunnel automatically — no separate terminal needed.

Tunnel commands (run once — shared across all queries):
  tunnel-start          Start a persistent background SSM tunnel
  tunnel-stop           Stop the persistent tunnel

Query commands (auto-start a one-shot tunnel if none is running):
  connect               Open an interactive psql shell
  users                 List all app users
  user <uuid|phone>     Look up a single user by UUID or phone number
  help-requests         Recent 20 help requests
  help-request <uuid>   Full detail for a single help request + events
  audio-files           Recent 20 audio files
  transcription-jobs    Recent 20 transcription jobs
  otp-log <phone>       Last 10 OTP audit entries for a masked phone
  payment-requests      Recent 20 payment requests
  flyway                Flyway migration history
  run <sql>             Run an arbitrary SQL statement
  <sql>                 Run an arbitrary SQL statement directly

Recommended workflow:
  ./db-rds.sh tunnel-start   # once
  ./db-rds.sh users
  ./db-rds.sh help-requests
  ./db-rds.sh tunnel-stop    # when done

Override password at runtime:
  PGPASSWORD=secret ./db-rds.sh users

Override SSM target at runtime:
  SSM_TARGET=i-0123456789abcdef0 ./db-rds.sh users
EOF
}

for cmd in psql aws nc; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: '${cmd}' not found. Install it before running this script." >&2
    exit 1
  fi
done

CMD="${1:-}"

if [[ -z "$CMD" || "$CMD" == "help" || "$CMD" == "--help" || "$CMD" == "-h" ]]; then
  usage
  exit 0
fi

case "$CMD" in
  tunnel-start)
    tunnel_start
    exit 0
    ;;
  tunnel-stop)
    tunnel_stop
    exit 0
    ;;
esac

# All query commands go through ensure_tunnel (reuses persistent tunnel if up)
ensure_tunnel

run_sql() {
  local sql="$1"
  psql_cmd -c "${sql}"
}

case "$CMD" in
  connect)
    echo "Connecting to ${DB_HOST}/${DB_NAME} as ${DB_USER} ..."
    psql_cmd
    ;;

  users)
    psql_cmd -c "
      SELECT id, phone_number, email, display_name, preferred_language, created_at
      FROM public.app_user
      ORDER BY created_at DESC
      LIMIT 30;
    "
    ;;

  user)
    ID="${2:-}"
    if [[ -z "$ID" ]]; then echo "Usage: $0 user <uuid|phone_number>"; exit 1; fi
    psql_cmd -c "
      SELECT id, phone_number, email, display_name, roles, preferred_language, created_at, updated_at
      FROM public.app_user
      WHERE id::text = '${ID}' OR phone_number = '${ID}'
      LIMIT 1;
    "
    ;;

  help-requests)
    psql_cmd -c "
      SELECT id, requester_id, helper_id, title, status, created_at, updated_at
      FROM public.help_request
      ORDER BY created_at DESC
      LIMIT 20;
    "
    ;;

  help-request)
    ID="${2:-}"
    if [[ -z "$ID" ]]; then echo "Usage: $0 help-request <uuid>"; exit 1; fi
    psql_cmd -c "
      SELECT hr.id, hr.title, hr.status, hr.created_at, hr.updated_at,
             hr.requester_id, hr.helper_id,
             hr.offer_amount, hr.offer_currency,
             hr.cancel_reason_code, hr.reassigned_count,
             u.phone_number AS requester_phone,
             u.display_name AS requester_name
      FROM public.help_request hr
      JOIN public.app_user u ON u.id = hr.requester_id
      WHERE hr.id::text = '${ID}';
    "
    psql_cmd -c "
      SELECT id, event_type, actor_role, reason_code, reason_text, created_at
      FROM public.help_request_event
      WHERE request_id::text = '${ID}'
      ORDER BY created_at;
    "
    ;;

  audio-files)
    psql_cmd -c "
      SELECT id, request_id, owner_user_id, storage_key, storage_provider,
             mime_type, duration_ms, size_bytes, created_at
      FROM public.audio_files
      ORDER BY created_at DESC
      LIMIT 20;
    "
    ;;

  transcription-jobs)
    psql_cmd -c "
      SELECT job_id, task_id, status, engine, language_hint, detected_language,
             attempt_count, last_error_code, created_at, updated_at
      FROM transcription_job
      ORDER BY created_at DESC
      LIMIT 20;
    "
    ;;

  otp-log)
    PHONE="${2:-}"
    if [[ -z "$PHONE" ]]; then echo "Usage: $0 otp-log <masked-phone>"; exit 1; fi
    psql_cmd -c "
      SELECT masked_phone, provider, action, status, detail, created_at
      FROM otp_audit_log
      WHERE masked_phone = '${PHONE}'
      ORDER BY created_at DESC
      LIMIT 10;
    "
    ;;

  payment-requests)
    psql_cmd -c "
      SELECT id, task_id, requester_user, helper_user, payer_user, payer_role,
             amount_requested, currency, payment_mode, status, created_at, updated_at
      FROM payment_requests
      ORDER BY created_at DESC
      LIMIT 20;
    "
    ;;

  flyway)
    psql_cmd -c "
      SELECT version, description, type, state, installed_on
      FROM public.flyway_schema_history
      ORDER BY installed_rank;
    "
    ;;

  run)
    SQL="${2:-}"
    if [[ -z "$SQL" ]]; then echo "Usage: $0 run '<sql>'"; exit 1; fi
    run_sql "$SQL"
    ;;

  SELECT*|select*|INSERT*|insert*|UPDATE*|update*|DELETE*|delete*|WITH*|with*|ALTER*|alter*|CREATE*|create*|DROP*|drop*|TRUNCATE*|truncate*|DO*|do*)
    run_sql "$CMD"
    ;;

  *)
    echo "Unknown command: ${CMD}" >&2
    usage
    exit 1
    ;;
esac

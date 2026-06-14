#!/bin/sh
set -eu

db_mode="${APP_DB_MODE:-local}"

case "$db_mode" in
  local)
    unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
    export DB_HOST="${DB_HOST:-db}"
    export DB_PORT="${DB_PORT:-5432}"
    export DB_NAME="${DB_NAME:-oolshik}"
    export DB_USER="${DB_USER:-oolshik}"
    export DB_PASSWORD="${DB_PASSWORD:-oolshik}"
    ;;
  external|rds|neon)
    : "${SPRING_DATASOURCE_URL:?Set SPRING_DATASOURCE_URL when APP_DB_MODE is external or rds}"
    : "${SPRING_DATASOURCE_USERNAME:?Set SPRING_DATASOURCE_USERNAME when APP_DB_MODE is external or rds}"
    : "${SPRING_DATASOURCE_PASSWORD:?Set SPRING_DATASOURCE_PASSWORD when APP_DB_MODE is external or rds}"

    ;;
  *)
    echo "Unsupported APP_DB_MODE: $db_mode. Use 'local', 'external', 'rds', or 'neon'." >&2
    exit 1
    ;;
esac

exec sh -c "java $JAVA_OPTS -jar /app/app.jar"

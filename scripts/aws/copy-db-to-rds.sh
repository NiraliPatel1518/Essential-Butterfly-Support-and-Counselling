#!/usr/bin/env bash
# =====================================================================
# One-time copy of the PostgreSQL database from the "db" container on the
# EC2 server into an EMPTY Amazon RDS database.  Run ON THE SERVER, from
# ~/app:
#
#     bash scripts/aws/copy-db-to-rds.sh <rds-endpoint>
#
# What it does:
#   1. Checks RDS is reachable, uses the same major version, and is EMPTY
#      (it refuses to write into a database that already has tables).
#   2. Stops ONLY the backend, so no data changes during the copy
#      (visitors see the "We will be right back" page for a few minutes).
#   3. pg_dump of the local database (read only - the local database and
#      its volume are never changed; they stay as the way back).
#   4. pg_restore into RDS in ONE transaction: it either all works or
#      nothing is written.
#   5. Compares the exact row count of every table on both sides,
#      including Flyway's history table.
#
# If any step fails, the backend is started again on the LOCAL database,
# exactly as before.  On success the backend is left stopped, so the
# switch to RDS (docker-compose.aws.yml) can be made with no lost writes.
#
# Password: by default the RDS master password is read from
# secrets/postgres_password.txt (create RDS with the same password).
# Otherwise:  RDS_PASSWORD_FILE=secrets/rds_password.txt bash ... <endpoint>
# =====================================================================
set -Eeuo pipefail

RDS_HOST="${1:?usage: copy-db-to-rds.sh <rds-endpoint>}"
RDS_USER="${RDS_USER:-postgres}"
DB_NAME="essential_butterfly"
APP_DIR="${APP_DIR:-$HOME/app}"
PASSWORD_FILE="${RDS_PASSWORD_FILE:-secrets/postgres_password.txt}"
DB_CONTAINER="essential-butterfly-db"
WORK_DIR="${WORK_DIR:-$HOME/migration}"
COMPOSE=(sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml)

fail() { printf '\nSTOPPED: %s\n' "$*" >&2; exit 1; }
log()  { printf '\n==> %s\n' "$*"; }

cd "$APP_DIR" || fail "$APP_DIR not found."
[[ "$RDS_HOST" =~ ^[a-z0-9.-]+\.rds\.amazonaws\.com$ ]] \
    || fail "'$RDS_HOST' does not look like an RDS endpoint (…rds.amazonaws.com)."
[[ -s "$PASSWORD_FILE" ]] || fail "Password file '$PASSWORD_FILE' is missing or empty."
PASSWORD_PATH="$(realpath "$PASSWORD_FILE")"

# psql / pg_restore against RDS, in a throw-away postgres:17 container.
# The password is read inside the container, never put on a command line.
rds() {
    sudo docker run --rm -i \
        -v "$PASSWORD_PATH:/run/secrets/pw:ro" \
        -v "$WORK_DIR:/work:ro" \
        -e PGHOST="$RDS_HOST" -e PGUSER="$RDS_USER" -e PGDATABASE="$DB_NAME" \
        -e PGSSLMODE=require -e PGCONNECT_TIMEOUT=15 \
        postgres:17 \
        sh -c 'PGPASSWORD="$(tr -d "\r\n" < /run/secrets/pw)"; export PGPASSWORD; exec "$@"' sh "$@"
}
local_psql() { sudo docker exec -i "$DB_CONTAINER" psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 -At "$@"; }

# Exact row count of every table in the public schema, one "table|count" per line.
COUNT_SQL="SELECT format('SELECT %L || ''|'' || count(*) FROM public.%I', tablename, tablename)
           FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename \\gexec"

# ---------------------------------------------------------------------
log "1. Checking RDS"
mkdir -p "$WORK_DIR"; chmod 700 "$WORK_DIR"

[[ "$(sudo docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null)" == "true" ]] \
    || fail "The local database container is not running."

rds_version="$(rds psql -At -c 'SHOW server_version_num')" \
    || fail "Cannot connect to RDS. Check the endpoint, the password, that the database '$DB_NAME' was created, and that the RDS security group allows this EC2 instance on port 5432."
local_version="$(local_psql -c 'SHOW server_version_num')" \
    || fail "Cannot read the local database. Nothing was changed."
echo "Local PostgreSQL: $local_version   RDS PostgreSQL: $rds_version"
[[ "${rds_version:0:2}" == "${local_version:0:2}" ]] \
    || fail "RDS major version (${rds_version:0:2}) differs from the local one (${local_version:0:2}). Create RDS with PostgreSQL ${local_version:0:2}."

rds_tables="$(rds psql -At -c "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'")" \
    || fail "Could not check whether the RDS database is empty. Nothing was changed."
[[ "$rds_tables" == "0" ]] \
    || fail "The RDS database already has $rds_tables table(s). This script only copies into an EMPTY database, so nothing was changed."

# ---------------------------------------------------------------------
log "2. Stopping the backend (the site shows 'We will be right back')"
"${COMPOSE[@]}" stop backend

backend_restarted_on_local=false
restart_on_local() {
    if [[ "$backend_restarted_on_local" == false ]]; then
        backend_restarted_on_local=true
        echo "Starting the backend again on the LOCAL database..." >&2
        "${COMPOSE[@]}" start backend >&2 || true
    fi
}
trap 'restart_on_local' ERR
trap 'restart_on_local; exit 130' INT TERM

# ---------------------------------------------------------------------
log "3. Dumping the local database (read only)"
DUMP_NAME="$DB_NAME-$(date -u +%Y%m%dT%H%M%SZ).dump"
sudo docker exec "$DB_CONTAINER" pg_dump -U postgres -d "$DB_NAME" -Fc > "$WORK_DIR/$DUMP_NAME"
chmod 600 "$WORK_DIR/$DUMP_NAME"
dump_tables="$(sudo docker exec -i "$DB_CONTAINER" pg_restore -l < "$WORK_DIR/$DUMP_NAME" | grep -c ' TABLE DATA ' || true)"
[[ "$dump_tables" -gt 0 ]] || { restart_on_local; fail "The dump contains no table data."; }
echo "Dump: $WORK_DIR/$DUMP_NAME ($(du -h "$WORK_DIR/$DUMP_NAME" | cut -f1), $dump_tables tables with data)"

# ---------------------------------------------------------------------
log "4. Restoring into RDS (one transaction: all or nothing)"
rds pg_restore --no-owner --no-acl --single-transaction --exit-on-error \
    -d "$DB_NAME" "/work/$DUMP_NAME" \
    || { restart_on_local; fail "pg_restore failed; RDS was left unchanged (the transaction was rolled back)."; }

# ---------------------------------------------------------------------
log "5. Comparing every table"
local_counts="$(printf '%s\n' "$COUNT_SQL" | local_psql | sort)"
rds_counts="$(printf '%s\n' "$COUNT_SQL" | rds psql -At -v ON_ERROR_STOP=1 | sort)"

if [[ "$local_counts" != "$rds_counts" ]]; then
    diff <(printf '%s\n' "$local_counts") <(printf '%s\n' "$rds_counts") >&2 || true
    restart_on_local
    fail "Row counts differ (local < >  RDS). The site is back on the local database."
fi
printf '%s\n' "$local_counts" | tr '|' '\t'
flyway="$(rds psql -At -c "SELECT max(installed_rank) || ' migrations, latest V' || (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1) FROM flyway_schema_history" 2>/dev/null || echo "unknown")"

trap - ERR INT TERM
cat <<EOF

OK: all $(printf '%s\n' "$local_counts" | grep -c .) tables match exactly. Flyway on RDS: $flyway.
The local database was not changed and is still running (it is the way back).

The backend is STOPPED on purpose. Next (guide step 6):
  1. Add SPRING_DATASOURCE_URL for RDS to docker-compose.aws.yml
  2. ${COMPOSE[*]} up -d --no-deps backend
     ${COMPOSE[*]} restart gateway

To cancel instead and go back to the local database:
  ${COMPOSE[*]} start backend
EOF

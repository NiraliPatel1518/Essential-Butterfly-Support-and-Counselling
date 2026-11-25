#!/usr/bin/env bash
# =====================================================================
# Deploy one commit to the EC2 server.
#
# GitHub Actions (.github/workflows/deploy.yml) pipes this script over
# SSH and runs it on the server as:   bash -s -- <commit-sha>
#
# What it does, in order:
#   0. Safety checks - stops before changing anything if the server is
#      not in the expected state.
#   1. Backs up the database the backend really uses (pg_dump) - BEFORE
#      the new backend starts, because Flyway applies any new migration
#      when the backend starts. That is the local "db" container, or Amazon
#      RDS once SPRING_DATASOURCE_URL in docker-compose.aws.yml points there.
#   2. Checks out the exact commit that passed CI.
#   3. Builds the new images. If the build fails, the old containers keep
#      running and the site stays up.
#   4. Replaces ONLY backend and frontend, then restarts the gateway.
#   5. Checks the site works. If not, it rolls back to the previous commit.
#
# What it never touches:
#   - the db container, the postgres-data and memory-wall-data volumes
#   - caddy (HTTPS) and its certificates
#   - the untracked server files: secrets/, .env, Caddyfile,
#     docker-compose.aws.yml
#   It never runs "docker compose down" and never uses "-v".
# =====================================================================
set -Eeuo pipefail

SHA="${1:?usage: ec2-deploy.sh <commit-sha>}"
APP_DIR="${APP_DIR:-$HOME/app}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_BACKUPS="${KEEP_BACKUPS:-14}"
DB_CONTAINER="essential-butterfly-db"
HEALTH_TRIES="${HEALTH_TRIES:-36}"   # 36 x 5 s = 3 minutes

COMPOSE=(sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml)

log()  { printf '\n==> %s\n' "$*"; }
fail() { printf '\nDEPLOY STOPPED: %s\n' "$*" >&2; exit 1; }

cd "$APP_DIR" || fail "$APP_DIR does not exist on the server."

# ---------------------------------------------------------------------
# 0. Safety checks (nothing has been changed yet)
# ---------------------------------------------------------------------
log "Safety checks"

[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || fail "'$SHA' is not a full commit id."

for f in docker-compose.yml docker-compose.aws.yml .env Caddyfile \
         secrets/postgres_password.txt secrets/jwt_secret.txt; do
    [[ -e "$f" ]] || fail "Required server file '$f' is missing."
done

# Someone edited a tracked file directly on the server: do not overwrite it.
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    git status --short --untracked-files=no >&2
    fail "Tracked files were edited on the server (listed above). Commit them to GitHub or undo them, then deploy again."
fi

# Which database does the backend use? Read it from the merged Compose
# configuration (docker-compose.yml + docker-compose.aws.yml).
compose_config="$("${COMPOSE[@]}" config 2>/dev/null)" \
    || fail "docker compose config failed; check docker-compose.aws.yml. Nothing was changed."
url_pattern='SPRING_DATASOURCE_URL:[[:space:]]*["'"'"']?jdbc:postgresql://([^:/"'"'"'[:space:]]+)(:([0-9]+))?/([A-Za-z0-9_]+)'
[[ "$compose_config" =~ $url_pattern ]] \
    || fail "Could not read SPRING_DATASOURCE_URL from the Compose files. Nothing was changed."
DB_HOST="${BASH_REMATCH[1]}"
DB_PORT="${BASH_REMATCH[3]:-5432}"
DB_NAME="${BASH_REMATCH[4]}"
user_pattern='SPRING_DATASOURCE_USERNAME:[[:space:]]*["'"'"']?([A-Za-z0-9_]+)'
DB_USER="postgres"
[[ "$compose_config" =~ $user_pattern ]] && DB_USER="${BASH_REMATCH[1]}"

if [[ "$DB_HOST" == "db" ]]; then
    echo "Database: local container ($DB_CONTAINER)"
    db_running="$(sudo docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null || echo false)"
    [[ "$db_running" == "true" ]] || fail "The database container is not running. Fix that first; nothing was changed."
else
    echo "Database: $DB_HOST (Amazon RDS)"
fi

PREV="$(git rev-parse HEAD)"
echo "Currently deployed: $PREV"
echo "Deploying:          $SHA"

# ---------------------------------------------------------------------
# 1. Database backup
# ---------------------------------------------------------------------
log "Backing up the database"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/db-$(date -u +%Y%m%dT%H%M%SZ)-${PREV:0:7}.sql.gz"

dump_database() {
    if [[ "$DB_HOST" == "db" ]]; then
        sudo docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME"
    else
        # A throw-away postgres:17 container (the same image as the local
        # database, so no new download) runs pg_dump against RDS. The
        # password is read from the secret file inside the container, so it
        # never appears in a command line or a log.
        sudo docker run --rm \
            -v "$APP_DIR/secrets/postgres_password.txt:/run/secrets/pw:ro" \
            -e PGHOST="$DB_HOST" -e PGPORT="$DB_PORT" -e PGUSER="$DB_USER" \
            -e PGDATABASE="$DB_NAME" -e PGSSLMODE=require -e PGCONNECT_TIMEOUT=15 \
            postgres:17 \
            sh -c 'PGPASSWORD="$(tr -d "\r\n" < /run/secrets/pw)" exec pg_dump'
    fi
}
dump_database | gzip > "$BACKUP_FILE" \
    || { rm -f "$BACKUP_FILE"; fail "pg_dump failed; nothing was changed."; }

# The backup is only accepted when the gzip file is intact AND pg_dump's
# last line "PostgreSQL database dump complete" is in it, which proves the
# whole dump was written. "tail" reads the full file, so this check cannot
# be broken by a "broken pipe" (a pipe that stops reading early fails
# under "set -o pipefail" on a large file).
backup_ok() {
    local footer
    gzip -t "$BACKUP_FILE" 2>/dev/null || return 1
    footer="$(zcat "$BACKUP_FILE" | tail -n 5)" || return 1
    [[ "$footer" == *"PostgreSQL database dump complete"* ]]
}
backup_ok \
    || { rm -f "$BACKUP_FILE"; fail "The database backup is incomplete or damaged; nothing was changed."; }
chmod 600 "$BACKUP_FILE"
echo "Backup saved: $BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"

# keep only the newest $KEEP_BACKUPS backups
ls -1t "$BACKUP_DIR"/db-*.sql.gz 2>/dev/null | tail -n +"$((KEEP_BACKUPS + 1))" | xargs -r rm -f --

# ---------------------------------------------------------------------
# helpers used for the deploy and for a roll back
# ---------------------------------------------------------------------
# Every step is chained with && on purpose: inside an "if" bash ignores
# "set -e", so without && a failed build would be hidden.
build_and_replace() {
    "${COMPOSE[@]}" build backend frontend \
    && "${COMPOSE[@]}" up -d --no-deps backend frontend \
    && "${COMPOSE[@]}" restart gateway
    # --no-deps: never recreate the database (or anything else).
    # The gateway restart is needed because nginx looks up the backend's
    # address only when it starts, and the recreated backend may have a
    # new address (it also loads any change to gateway/nginx.conf).
}

site_is_healthy() {
    local i
    for ((i = 1; i <= HEALTH_TRIES; i++)); do
        if curl -fsS --max-time 5 -o /dev/null http://localhost:5173/ \
           && curl -fsS --max-time 5 -o /dev/null http://localhost:5173/api/features; then
            echo "Healthy after $((i * 5)) s."
            return 0
        fi
        echo "Waiting for the site... ($i/$HEALTH_TRIES)"
        sleep 5
    done
    return 1
}

# ---------------------------------------------------------------------
# 2. Get the commit
# ---------------------------------------------------------------------
log "Fetching code"
git fetch --quiet origin
git cat-file -e "${SHA}^{commit}" 2>/dev/null || fail "Commit $SHA was not found on GitHub."
git checkout --quiet --detach "$SHA"

# ---------------------------------------------------------------------
# 3 + 4. Build and replace backend/frontend
# ---------------------------------------------------------------------
log "Building and starting the new version"
if ! build_and_replace; then
    echo "Build or start failed - going back to $PREV" >&2
    git checkout --quiet --detach "$PREV"
    # rebuild the previous code so the images match it again (Docker's
    # cache makes this quick); the old containers may still be running
    build_and_replace || true
    site_is_healthy \
        || fail "The new version could not be built, and the previous version is not healthy either. Check the server now. Database backup: $BACKUP_FILE"
    fail "The new version could not be built or started. The previous version is running."
fi

# ---------------------------------------------------------------------
# 5. Health check, roll back if needed
# ---------------------------------------------------------------------
log "Checking the site"
if site_is_healthy; then
    log "Deployed $SHA"
    sudo docker image prune -f >/dev/null || true   # removes only unused, untagged images
    "${COMPOSE[@]}" ps
    exit 0
fi

echo "The new version did not become healthy. Last backend log lines:" >&2
"${COMPOSE[@]}" logs --no-color --tail 80 backend >&2 || true

log "Rolling back to $PREV"
git checkout --quiet --detach "$PREV"
build_and_replace || true
if site_is_healthy; then
    fail "The new version failed its health check. Rolled back to $PREV - the site is up. Database backup: $BACKUP_FILE"
fi
fail "Roll back did not become healthy either. Check the server now. Database backup: $BACKUP_FILE"

#!/usr/bin/env bash
# =====================================================================
# Deploy one commit to Amazon ECS.
#
# GitHub Actions (.github/workflows/deploy.yml, when the repository
# variable DEPLOY_TARGET is "ecs") pipes this script over SSH to the EC2
# server, which is now only the BUILD machine:   bash -s -- <commit-sha>
# scripts/aws/ecs-setup.sh also runs it once for the first deploy.
#
#   0. Safety checks (nothing is changed if one fails)
#   1. Backs up the RDS database (pg_dump) into ~/backups, BEFORE the new
#      backend starts (Flyway runs new migrations when it starts)
#   2. Checks out the exact commit that passed CI
#   3. Builds the backend and web images and pushes them to ECR
#   4. Registers a new task definition and updates the ECS service.
#      ECS starts the new task next to the old one, waits until the load
#      balancer health check passes, then stops the old one: no downtime.
#      If the new task never becomes healthy, ECS's deployment circuit
#      breaker rolls back to the previous task definition by itself.
#   5. Checks the public address
#
# Settings come from ~/ecs-config.env (written by ecs-setup.sh).
# Self-contained on purpose: it is piped over SSH, so it cannot load
# other files before the code is checked out.
# =====================================================================
set -Eeuo pipefail

SHA="${1:?usage: ecs-deploy.sh <commit-sha>}"
APP_DIR="${APP_DIR:-$HOME/app}"
ECS_CONFIG="${ECS_CONFIG:-$HOME/ecs-config.env}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_BACKUPS="${KEEP_BACKUPS:-14}"
AWS_CLI_IMAGE="${AWS_CLI_IMAGE:-amazon/aws-cli:latest}"
WORK_DIR="${WORK_DIR:-$HOME/.ecs-work}"

log()  { printf '\n==> %s\n' "$*"; }
fail() { printf '\nDEPLOY STOPPED: %s\n' "$*" >&2; exit 1; }

mkdir -p "$WORK_DIR"; chmod 700 "$WORK_DIR"
aws() {
    sudo docker run --rm -i -e AWS_DEFAULT_REGION="$AWS_REGION" -e AWS_PAGER="" \
        -v "$WORK_DIR:/work" -w /work "$AWS_CLI_IMAGE" "$@"
}
awst() {
    local out
    out="$(aws "$@" --output text)" || return 1
    [[ "$out" == "None" ]] && out=""
    printf '%s' "$out"
}

# ---------------------------------------------------------------------
log "Safety checks"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || fail "'$SHA' is not a full commit id."
[[ -f "$ECS_CONFIG" ]] || fail "$ECS_CONFIG not found. Run scripts/aws/ecs-setup.sh first."
# shellcheck disable=SC1090
source "$ECS_CONFIG"
for key in AWS_REGION ACCOUNT_ID CLUSTER SERVICE FAMILY ECR_BACKEND ECR_WEB TG_ARN SUBNETS TASK_SG DB_CLIENT_SG DB_HOST DB_USER SITE_URL SECRET_MAP; do
    [[ -n "${!key:-}" ]] || fail "$key is missing in $ECS_CONFIG. Run scripts/aws/ecs-setup.sh again."
done
cd "$APP_DIR" || fail "$APP_DIR does not exist."
[[ -s secrets/postgres_password.txt ]] || fail "secrets/postgres_password.txt is missing (needed for the backup)."
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    git status --short --untracked-files=no >&2
    fail "Tracked files were edited on the server (listed above). Undo them, then deploy again."
fi
PREV="$(git rev-parse HEAD)"
echo "Target:    ECS service $SERVICE in cluster $CLUSTER"
echo "Database:  $DB_HOST (Amazon RDS)"
echo "Deploying: $SHA"

# ---------------------------------------------------------------------
log "Backing up the database"
mkdir -p "$BACKUP_DIR"; chmod 700 "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/db-$(date -u +%Y%m%dT%H%M%SZ)-${SHA:0:7}.sql.gz"
sudo docker run --rm \
    -v "$APP_DIR/secrets/postgres_password.txt:/run/secrets/pw:ro" \
    -e PGHOST="$DB_HOST" -e PGUSER="$DB_USER" -e PGDATABASE=essential_butterfly \
    -e PGSSLMODE=require -e PGCONNECT_TIMEOUT=15 \
    postgres:17 sh -c 'PGPASSWORD="$(tr -d "\r\n" < /run/secrets/pw)" exec pg_dump' \
    | gzip > "$BACKUP_FILE" \
    || { rm -f "$BACKUP_FILE"; fail "pg_dump failed; nothing was changed."; }
backup_ok() {
    local footer
    gzip -t "$BACKUP_FILE" 2>/dev/null || return 1
    footer="$(zcat "$BACKUP_FILE" | tail -n 5)" || return 1
    [[ "$footer" == *"PostgreSQL database dump complete"* ]]
}
backup_ok || { rm -f "$BACKUP_FILE"; fail "The database backup is incomplete; nothing was changed."; }
chmod 600 "$BACKUP_FILE"
echo "Backup saved: $BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"
ls -1t "$BACKUP_DIR"/db-*.sql.gz 2>/dev/null | tail -n +"$((KEEP_BACKUPS + 1))" | xargs -r rm -f --

# ---------------------------------------------------------------------
log "Fetching code"
git fetch --quiet origin
git cat-file -e "${SHA}^{commit}" 2>/dev/null || fail "Commit $SHA was not found on GitHub."
git checkout --quiet --detach "$SHA"

# From here on, if the deploy stops for any reason, put the server's
# checkout back to the previous commit (ECS itself is never left half-done:
# it keeps the old task until the new one is healthy).
deploy_done=false
trap '[[ "$deploy_done" == true ]] || git checkout --quiet --detach "$PREV" 2>/dev/null || true' EXIT

# ---------------------------------------------------------------------
log "Building and pushing the images (3-6 minutes)"
REGISTRY="${ECR_BACKEND%%/*}"
aws ecr get-login-password | sudo docker login --username AWS --password-stdin "$REGISTRY" > /dev/null \
    || fail "Could not log in to ECR."
sudo docker build -q -t "$ECR_BACKEND:$SHA" essential-butterfly-backend > /dev/null \
    || fail "The backend image could not be built. Nothing was changed on ECS."
sudo docker build -q -t "$ECR_WEB:$SHA" -f deploy/ecs/web.Dockerfile . > /dev/null \
    || fail "The web image could not be built. Nothing was changed on ECS."
sudo docker push -q "$ECR_BACKEND:$SHA" > /dev/null || fail "Pushing the backend image failed."
sudo docker push -q "$ECR_WEB:$SHA" > /dev/null || fail "Pushing the web image failed."
echo "Pushed $ECR_BACKEND:$SHA"
echo "Pushed $ECR_WEB:$SHA"

# ---------------------------------------------------------------------
log "Updating the ECS service"
IMAGE_TAG="$SHA" \
FAMILY="$FAMILY" CPU_ARCH="$CPU_ARCH" LAB_ROLE_ARN="$LAB_ROLE_ARN" \
ECR_BACKEND="$ECR_BACKEND" ECR_WEB="$ECR_WEB" DB_URL="$DB_URL" DB_USER="$DB_USER" \
SITE_URL="$SITE_URL" S3_BUCKET="$S3_BUCKET" S3_REGION="$S3_REGION" \
LOG_GROUP="$LOG_GROUP" AWS_REGION="$AWS_REGION" SECRET_MAP="$SECRET_MAP" \
    python3 deploy/ecs/render-task-definition.py > "$WORK_DIR/taskdef.json" \
    || fail "Could not prepare the task definition. Nothing was changed on ECS."
TASK_DEF_ARN="$(awst ecs register-task-definition --cli-input-json file://taskdef.json \
    --query 'taskDefinition.taskDefinitionArn')" || fail "Registering the task definition failed."
echo "Task definition: $TASK_DEF_ARN"

service_status="$(awst ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
    --query 'services[0].status')"
if [[ "$service_status" != "ACTIVE" ]]; then
    echo "Creating the service (1 task)"
    aws ecs create-service --cluster "$CLUSTER" --service-name "$SERVICE" \
        --task-definition "$TASK_DEF_ARN" --desired-count 1 \
        --launch-type FARGATE --platform-version LATEST \
        --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$TASK_SG,$DB_CLIENT_SG],assignPublicIp=ENABLED}" \
        --load-balancers "targetGroupArn=$TG_ARN,containerName=web,containerPort=80" \
        --health-check-grace-period-seconds 300 \
        --deployment-configuration "deploymentCircuitBreaker={enable=true,rollback=true},maximumPercent=200,minimumHealthyPercent=100" \
        > /dev/null || fail "Creating the ECS service failed."
else
    aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" \
        --task-definition "$TASK_DEF_ARN" > /dev/null || fail "Updating the ECS service failed."
fi

desired="$(awst ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
    --query 'services[0].desiredCount')"
if [[ "$desired" == "0" ]]; then
    deploy_done=true
    log "Deployed $SHA"
    echo "The service is turned OFF (0 tasks); the new version starts with: bash scripts/aws/ecs-scale.sh on"
    exit 0
fi

echo "Waiting for the new task to pass the health check (usually 3-6 minutes)..."
if ! aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICE"; then
    echo "Still not stable after 10 minutes." >&2
fi

primary="$(awst ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
    --query "services[0].deployments[?status=='PRIMARY'] | [0].[taskDefinition,rolloutState]")"
primary_td="$(printf '%s' "$primary" | cut -f1)"
rollout="$(printf '%s' "$primary" | cut -f2)"
if [[ "$primary_td" != "$TASK_DEF_ARN" || "$rollout" != "COMPLETED" ]]; then
    echo "Last ECS events:" >&2
    aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
        --query 'services[0].events[0:8].[createdAt,message]' --output text >&2 || true
    fail "The new version did not become healthy (rollout: ${rollout:-unknown}). ECS keeps the previous task running until a new one is healthy, and its circuit breaker rolls back a failed release, so the site stays up. Backend logs: CloudWatch > Log groups > $LOG_GROUP. Database backup: $BACKUP_FILE"
fi
deploy_done=true

# ---------------------------------------------------------------------
if [[ "${EDGE:-cloudfront}" == "ec2" ]]; then
    # Caddy on this server sends the traffic to the load balancer; check the
    # load balancer itself (only this server is allowed to reach it).
    check_url="http://$ALB_DNS"
else
    check_url="$SITE_URL"
fi
log "Checking $check_url"
if curl -fsS --max-time 15 -o /dev/null "$check_url/api/features"; then
    echo "The new version answers through the load balancer."
else
    echo "WARNING: $check_url does not answer yet. ECS itself reports the new version healthy."
fi

# Keep the build machine's disk small: drop older local copies of the images.
for repo in "$ECR_BACKEND" "$ECR_WEB"; do
    sudo docker images "$repo" --format '{{.Repository}}:{{.Tag}}' | grep -v ":$SHA\$" | xargs -r sudo docker rmi > /dev/null 2>&1 || true
done
sudo docker image prune -f > /dev/null 2>&1 || true

log "Deployed $SHA to ECS"

#!/usr/bin/env bash
# =====================================================================
# Switch which copy of the application serves the public address, when
# HTTPS stays on this EC2 server (Caddy; ecs-setup.sh with PUBLIC_URL).
# Run ON THE EC2 SERVER, from ~/app:
#
#     bash scripts/aws/switch-site.sh ecs   # Caddy -> load balancer -> ECS
#     bash scripts/aws/switch-site.sh ec2   # back to the containers on this server
#
# The address does not change. Both copies use the same RDS database and
# S3 bucket, so no data moves and nothing is lost either way.
#
# "ecs": checks ECS answers, saves Caddyfile as Caddyfile.before-ecs,
#        points Caddy at the load balancer, checks the public address
#        (and puts the old Caddyfile back by itself if that fails), then
#        stops the backend/frontend/gateway containers here.
# "ec2": starts those containers again, waits until they answer, then
#        puts the saved Caddyfile back.
# =====================================================================
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-aws.sh
source "$SCRIPT_DIR/lib-aws.sh"
load_config
COMPOSE=(sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml)

cd "$APP_DIR" || fail "$APP_DIR not found."
[[ "${EDGE:-}" == "ec2" ]] || fail "This is only for the Caddy-on-EC2 setup (EDGE=ec2 in $ECS_CONFIG)."
[[ -f Caddyfile ]] || fail "Caddyfile not found in $APP_DIR."

answers() {   # url [tries]
    local i
    for ((i = 1; i <= ${2:-1}; i++)); do
        curl -fsS --max-time 10 -o /dev/null "$1/api/features" && return 0
        (( i < ${2:-1} )) && sleep 5
    done
    return 1
}

case "${1:-}" in
ecs)
    log "1. Is ECS answering?"
    answers "http://$ALB_DNS" 3 \
        || fail "The load balancer does not answer. Is ECS on? (bash scripts/aws/ecs-scale.sh status). Nothing was changed."
    echo "ECS answers through the load balancer."

    log "2. Pointing Caddy at the load balancer"
    [[ -f Caddyfile.before-ecs ]] || cp Caddyfile Caddyfile.before-ecs
    cat > Caddyfile <<EOF
{\$SITE_ADDRESS} {
	reverse_proxy http://$ALB_DNS
}
EOF
    "${COMPOSE[@]}" restart caddy

    log "3. Checking $SITE_URL"
    if ! answers "$SITE_URL" 6; then
        echo "The public address does not answer through ECS. Putting the old Caddyfile back..." >&2
        cp Caddyfile.before-ecs Caddyfile
        "${COMPOSE[@]}" restart caddy
        fail "Switch cancelled; the site is served by this server as before."
    fi
    echo "The public address is now served by ECS."

    log "4. Stopping the app containers on this server"
    "${COMPOSE[@]}" stop backend frontend gateway
    cat <<EOM

Done: $SITE_URL -> Caddy (this server) -> load balancer -> ECS.
Set the GitHub variable DEPLOY_TARGET to "ecs" so CD deploys to ECS.
To go back:  bash scripts/aws/switch-site.sh ec2
EOM
    ;;
ec2)
    [[ -f Caddyfile.before-ecs ]] || fail "Caddyfile.before-ecs not found; the site was never switched to ECS."
    log "1. Starting the app containers on this server"
    "${COMPOSE[@]}" up -d --no-deps backend frontend
    "${COMPOSE[@]}" up -d --no-deps gateway
    "${COMPOSE[@]}" restart gateway
    answers "http://localhost:5173" 36 \
        || fail "The containers on this server do not answer yet; the public address still goes to ECS. Check: ${COMPOSE[*]} logs backend"

    log "2. Putting the old Caddyfile back"
    cp Caddyfile.before-ecs Caddyfile
    "${COMPOSE[@]}" restart caddy
    answers "$SITE_URL" 6 || echo "WARNING: $SITE_URL does not answer yet; check: ${COMPOSE[*]} logs caddy" >&2
    cat <<EOM

Done: $SITE_URL is served by this server again.
Set the GitHub variable DEPLOY_TARGET to "ec2", and turn ECS off to save
credit:  bash scripts/aws/ecs-scale.sh off
EOM
    ;;
*)
    fail "usage: switch-site.sh ecs|ec2" ;;
esac

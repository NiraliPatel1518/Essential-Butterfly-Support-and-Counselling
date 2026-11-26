#!/usr/bin/env bash
# =====================================================================
# Turn the ECS site on or off, to save AWS Academy credit.
# Run ON THE EC2 SERVER, from ~/app:
#
#     bash scripts/aws/ecs-scale.sh off      # 0 tasks: site shows an error page, no task cost
#     bash scripts/aws/ecs-scale.sh on       # 1 task: site back in ~3-5 minutes
#     bash scripts/aws/ecs-scale.sh status
#
# "off" only stops the containers. The database (RDS), the photos (S3) and
# every setting stay as they are. The load balancer still costs a little
# while the site is off.
# =====================================================================
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-aws.sh
source "$SCRIPT_DIR/lib-aws.sh"
load_config

show_status() {
    local counts
    counts="$(awst ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
        --query 'services[0].[desiredCount,runningCount]')"
    echo "Tasks wanted: ${counts%%$'\t'*}, running: ${counts##*$'\t'}"
    echo "Address: $SITE_URL"
}

case "${1:-status}" in
    on)
        aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count 1 > /dev/null
        echo "Starting 1 task; waiting until the load balancer reports it healthy (3-6 minutes)..."
        healthy=""
        for ((i = 1; i <= 40; i++)); do
            healthy="$(awst elbv2 describe-target-health --target-group-arn "$TG_ARN" \
                --query "length(TargetHealthDescriptions[?TargetHealth.State=='healthy'])")" || healthy=""
            [[ "${healthy:-0}" -ge 1 ]] && break
            sleep 15
        done
        if [[ "${healthy:-0}" -ge 1 ]]; then echo "Healthy."; else echo "Not healthy after 10 minutes; check the ECS console (service Events) and CloudWatch logs." >&2; fi
        show_status ;;
    off)
        aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count 0 > /dev/null
        echo "Stopping the task. The data in RDS and S3 is not affected."
        show_status ;;
    status)
        show_status ;;
    *)
        fail "usage: ecs-scale.sh on|off|status" ;;
esac

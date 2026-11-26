#!/usr/bin/env bash
# =====================================================================
# One-time creation of the AWS pieces for running the site on Amazon ECS.
# Run ON THE EC2 SERVER, from ~/app, AFTER the RDS and S3 move:
#
#     bash scripts/aws/ecs-setup.sh
#
# Creates (each step skips what already exists, so it is safe to re-run):
#   1. ECR repositories for the two images (backend, web)
#   2. CloudWatch log group (7 days)
#   3. SSM Parameter Store SecureStrings from secrets/*.txt
#      (the values are never printed)
#   4. Security groups: load balancer <- CloudFront only, tasks <- load balancer only
#   5. Application Load Balancer + target group + listener
#   6. CloudFront distribution (free HTTPS address)
#   7. ECS cluster (Fargate)
# Then it runs the first deploy (scripts/deploy/ecs-deploy.sh), which builds
# the images, pushes them and creates the ECS service with 1 task.
#
# It does NOT touch: RDS, the S3 bucket, the running EC2 site, the secrets
# files. The EC2 site keeps working until you switch (see the guide).
#
# All names and IDs are saved to ~/ecs-config.env (outside the repo).
# =====================================================================
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-aws.sh
source "$SCRIPT_DIR/lib-aws.sh"

NAME="essential-butterfly"
SSM_PREFIX="/$NAME"
COMPOSE=(sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml)

cd "$APP_DIR" || fail "$APP_DIR not found."

# The public address chosen by an earlier run (see step 7).
saved_edge=""; saved_site_url=""
if [[ -f "$ECS_CONFIG" ]]; then
    saved_edge="$(sed -n 's/^EDGE="\(.*\)"$/\1/p' "$ECS_CONFIG")"
    saved_site_url="$(sed -n 's/^SITE_URL="\(.*\)"$/\1/p' "$ECS_CONFIG")"
fi

# ---------------------------------------------------------------------
log "0. Reading the current setup"
compose_config="$("${COMPOSE[@]}" config 2>/dev/null)" || fail "docker compose config failed."

url_pattern='SPRING_DATASOURCE_URL:[[:space:]]*["'"'"']?(jdbc:postgresql://([^:/"'"'"'[:space:]]+)[^"'"'"'[:space:]]*)'
[[ "$compose_config" =~ $url_pattern ]] || fail "SPRING_DATASOURCE_URL not found in the Compose files."
DB_URL="${BASH_REMATCH[1]}"; DB_HOST="${BASH_REMATCH[2]}"
[[ "$DB_HOST" == *.rds.amazonaws.com ]] || fail "The backend does not use RDS yet ($DB_HOST). Finish docs/operations/aws-s3-rds-migration.md first."
DB_USER="postgres"
user_pattern='SPRING_DATASOURCE_USERNAME:[[:space:]]*["'"'"']?([A-Za-z0-9_]+)'
[[ "$compose_config" =~ $user_pattern ]] && DB_USER="${BASH_REMATCH[1]}"

bucket_pattern='MEMORY_WALL_S3_BUCKET:[[:space:]]*["'"'"']?([a-z0-9.-]+)'
[[ "$compose_config" =~ $bucket_pattern ]] || fail "MEMORY_WALL_S3_BUCKET not found; finish the S3 move first."
S3_BUCKET="${BASH_REMATCH[1]}"
region_pattern='MEMORY_WALL_S3_REGION:[[:space:]]*["'"'"']?([a-z0-9-]+)'
S3_REGION="$AWS_REGION"
[[ "$compose_config" =~ $region_pattern ]] && S3_REGION="${BASH_REMATCH[1]}"

case "$(uname -m)" in
    x86_64)  CPU_ARCH="X86_64" ;;
    aarch64) CPU_ARCH="ARM64" ;;
    *) fail "Unknown CPU type $(uname -m)." ;;
esac

ACCOUNT_ID="$(awst sts get-caller-identity --query Account)" \
    || fail "The AWS CLI cannot get credentials (is LabInstanceProfile attached?)."
LAB_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/LabRole"

echo "Account:   $ACCOUNT_ID   CPU: $CPU_ARCH"
echo "Database:  $DB_HOST"
echo "Bucket:    $S3_BUCKET ($S3_REGION)"

for key in NAME SSM_PREFIX DB_URL DB_HOST DB_USER S3_BUCKET S3_REGION CPU_ARCH ACCOUNT_ID LAB_ROLE_ARN AWS_REGION; do
    save_config "$key" "${!key}"
done
save_config CLUSTER "$NAME"
save_config SERVICE "$NAME"
save_config FAMILY "$NAME"
save_config LOG_GROUP "/ecs/$NAME"

# ---------------------------------------------------------------------
log "1. Network"
VPC_ID="$(awst ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId')"
[[ -n "$VPC_ID" ]] || fail "No default VPC found."
# Default subnets, without the one Availability Zone where Fargate does not run (use1-az3).
subnet_list="$(awst ec2 describe-subnets \
    --filters Name=vpc-id,Values="$VPC_ID" Name=default-for-az,Values=true \
    --query "Subnets[?AvailabilityZoneId!='use1-az3'].SubnetId")"
read -r -a SUBNET_ARRAY <<< "$(echo "$subnet_list" | tr '\t' ' ')"
(( ${#SUBNET_ARRAY[@]} >= 2 )) || fail "Need at least 2 default subnets, found ${#SUBNET_ARRAY[@]}."
SUBNETS="$(IFS=,; echo "${SUBNET_ARRAY[*]}")"
echo "VPC $VPC_ID, subnets $SUBNETS"

# The security group RDS lets in (created by "Connect to an EC2 compute
# resource"). Giving it to the tasks lets them reach the database.
rds_sg="$(awst rds describe-db-instances \
    --query "DBInstances[?Endpoint.Address=='$DB_HOST'].VpcSecurityGroups[0].VpcSecurityGroupId | [0]")"
[[ -n "$rds_sg" ]] || fail "RDS instance for $DB_HOST not found."
DB_CLIENT_SG="$(awst ec2 describe-security-groups --group-ids "$rds_sg" \
    --query 'SecurityGroups[0].IpPermissions[?ToPort==`5432`].UserIdGroupPairs[0].GroupId | [0]')"
[[ -n "$DB_CLIENT_SG" ]] || fail "Could not find the security group that RDS allows on port 5432."
echo "Database client security group: $DB_CLIENT_SG"

for key in VPC_ID SUBNETS DB_CLIENT_SG; do save_config "$key" "${!key}"; done

# ---------------------------------------------------------------------
log "2. Container registries (ECR)"
cat > "$WORK_DIR/ecr-lifecycle.json" <<'EOF'
{"rules":[{"rulePriority":1,"description":"Keep the last 10 images","selection":{"tagStatus":"any","countType":"imageCountMoreThan","countNumber":10},"action":{"type":"expire"}}]}
EOF
for part in backend web; do
    repo="$NAME-$part"
    uri="$(awst ecr describe-repositories --repository-names "$repo" \
        --query 'repositories[0].repositoryUri' 2>/dev/null || true)"
    if [[ -z "$uri" ]]; then
        uri="$(awst ecr create-repository --repository-name "$repo" \
            --image-scanning-configuration scanOnPush=true \
            --query 'repository.repositoryUri')"
        echo "Created $uri"
    else
        echo "Exists  $uri"
    fi
    aws ecr put-lifecycle-policy --repository-name "$repo" \
        --lifecycle-policy-text file://ecr-lifecycle.json > /dev/null
    if [[ "$part" == backend ]]; then save_config ECR_BACKEND "$uri"; else save_config ECR_WEB "$uri"; fi
done

# ---------------------------------------------------------------------
log "3. Log group"
existing="$(awst logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" \
    --query "logGroups[?logGroupName=='$LOG_GROUP'].logGroupName | [0]")"
[[ -n "$existing" ]] || aws logs create-log-group --log-group-name "$LOG_GROUP"
aws logs put-retention-policy --log-group-name "$LOG_GROUP" --retention-in-days 7
echo "$LOG_GROUP (logs kept 7 days)"

# ---------------------------------------------------------------------
log "4. Secrets -> SSM Parameter Store (values are not shown)"
# secrets/<file>.txt  ->  environment variable of the backend
SECRET_FILES=(
    postgres_password:SPRING_DATASOURCE_PASSWORD
    gmail_username:GMAIL_USERNAME
    gmail_app_password:GMAIL_APP_PASSWORD
    jwt_secret:JWT_SECRET
    admin_email:ADMIN_EMAIL
    admin_password:ADMIN_PASSWORD
    twilio_account_sid:TWILIO_ACCOUNT_SID
    twilio_auth_token:TWILIO_AUTH_TOKEN
    twilio_verify_service_sid:TWILIO_VERIFY_SERVICE_SID
    google_calendar_client_id:GOOGLE_CALENDAR_CLIENT_ID
    google_calendar_client_secret:GOOGLE_CALENDAR_CLIENT_SECRET
    google_calendar_refresh_token:GOOGLE_CALENDAR_REFRESH_TOKEN
)
secret_map=()
value_file="$WORK_DIR/secret-value.txt"
trap 'rm -f "$value_file"' EXIT
for entry in "${SECRET_FILES[@]}"; do
    file="${entry%%:*}"; env_name="${entry##*:}"
    param="$SSM_PREFIX/$file"
    value=""
    [[ -f "secrets/$file.txt" ]] && value="$(tr -d '\r\n' < "secrets/$file.txt")"
    if [[ -z "$value" ]]; then
        # Empty means "feature off" (as on EC2); SSM cannot store an empty value.
        aws ssm delete-parameter --name "$param" > /dev/null 2>&1 || true
        echo "  $env_name: empty, not set (same as on EC2)"
        continue
    fi
    (umask 077; printf '%s' "$value" > "$value_file")
    aws ssm put-parameter --name "$param" --type SecureString --overwrite \
        --value "file:///work/secret-value.txt" > /dev/null
    rm -f "$value_file"
    secret_map+=("$env_name=arn:aws:ssm:$AWS_REGION:$ACCOUNT_ID:parameter$param")
    echo "  $env_name: saved as $param"
done
unset value
[[ " ${secret_map[*]} " == *" SPRING_DATASOURCE_PASSWORD="* ]] || fail "secrets/postgres_password.txt is empty."
save_config SECRET_MAP "$(IFS=,; echo "${secret_map[*]}")"

# ---------------------------------------------------------------------
log "5. Security groups"
ensure_sg() {   # name description -> id
    local id
    id="$(awst ec2 describe-security-groups \
        --filters Name=vpc-id,Values="$VPC_ID" Name=group-name,Values="$1" \
        --query 'SecurityGroups[0].GroupId')"
    [[ -n "$id" ]] || id="$(awst ec2 create-security-group --vpc-id "$VPC_ID" \
        --group-name "$1" --description "$2" --query GroupId)"
    printf '%s' "$id"
}
allow_in() {    # group-id ip-permissions (ignores "already exists")
    local err
    if ! err="$(aws ec2 authorize-security-group-ingress --group-id "$1" --ip-permissions "$2" 2>&1 >/dev/null)"; then
        [[ "$err" == *Duplicate* ]] || fail "Could not add the rule to $1: $err"
    fi
}
ALB_SG="$(ensure_sg "$NAME-alb" "Load balancer: HTTP from CloudFront only")"  # description is fixed once created
TASK_SG="$(ensure_sg "$NAME-tasks" "ECS tasks: HTTP from the load balancer only")"
CF_PREFIX_LIST="$(awst ec2 describe-managed-prefix-lists \
    --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
    --query 'PrefixLists[0].PrefixListId')"
[[ -n "$CF_PREFIX_LIST" ]] || fail "CloudFront prefix list not found."
allow_in "$TASK_SG" "IpProtocol=tcp,FromPort=80,ToPort=80,UserIdGroupPairs=[{GroupId=$ALB_SG,Description=Load-balancer}]"
echo "Load balancer $ALB_SG, tasks $TASK_SG"
save_config ALB_SG "$ALB_SG"; save_config TASK_SG "$TASK_SG"

# ---------------------------------------------------------------------
log "6. Load balancer"
ALB_ARN="$(awst elbv2 describe-load-balancers --names "$NAME-alb" \
    --query 'LoadBalancers[0].LoadBalancerArn' 2>/dev/null || true)"
if [[ -z "$ALB_ARN" ]]; then
    ALB_ARN="$(awst elbv2 create-load-balancer --name "$NAME-alb" --type application \
        --scheme internet-facing --subnets "${SUBNET_ARRAY[@]}" --security-groups "$ALB_SG" \
        --query 'LoadBalancers[0].LoadBalancerArn')"
fi
ALB_DNS="$(awst elbv2 describe-load-balancers --load-balancer-arns "$ALB_ARN" \
    --query 'LoadBalancers[0].DNSName')"

TG_ARN="$(awst elbv2 describe-target-groups --names "$NAME-tg" \
    --query 'TargetGroups[0].TargetGroupArn' 2>/dev/null || true)"
if [[ -z "$TG_ARN" ]]; then
    # Health check through nginx to the backend and the database, so a
    # broken release is detected (and rolled back) by ECS.
    TG_ARN="$(awst elbv2 create-target-group --name "$NAME-tg" --protocol HTTP --port 80 \
        --vpc-id "$VPC_ID" --target-type ip \
        --health-check-path /api/features --matcher HttpCode=200 \
        --health-check-interval-seconds 30 --healthy-threshold-count 2 --unhealthy-threshold-count 3 \
        --query 'TargetGroups[0].TargetGroupArn')"
fi
aws elbv2 modify-target-group-attributes --target-group-arn "$TG_ARN" \
    --attributes Key=deregistration_delay.timeout_seconds,Value=30 > /dev/null

listener="$(awst elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" \
    --query 'Listeners[?Port==`80`].ListenerArn | [0]')"
[[ -n "$listener" ]] || aws elbv2 create-listener --load-balancer-arn "$ALB_ARN" \
    --protocol HTTP --port 80 --default-actions Type=forward,TargetGroupArn="$TG_ARN" > /dev/null
echo "Load balancer $ALB_DNS"
save_config ALB_ARN "$ALB_ARN"; save_config ALB_DNS "$ALB_DNS"; save_config TG_ARN "$TG_ARN"

# ---------------------------------------------------------------------
log "7. Public HTTPS address"
# Two ways, chosen once and remembered in ~/ecs-config.env:
#
#  a) Caddy on this EC2 server keeps the current HTTPS address and passes
#     the traffic to the load balancer (used in AWS Academy, where CloudFront
#     is not allowed):
#         PUBLIC_URL=https://184-193-202-252.sslip.io bash scripts/aws/ecs-setup.sh
#  b) CloudFront in front of the load balancer:
#         CLOUDFRONT_DOMAIN=dxxxx.cloudfront.net bash scripts/aws/ecs-setup.sh
#     or, if LabRole may use CloudFront, no variable at all.
if [[ -n "${PUBLIC_URL:-}" ]]; then
    PUBLIC_URL="${PUBLIC_URL%/}"
    [[ "$PUBLIC_URL" =~ ^https://[a-z0-9.-]+$ ]] \
        || fail "PUBLIC_URL must look like https://184-193-202-252.sslip.io (got '$PUBLIC_URL')."
    EDGE="ec2"; SITE_URL="$PUBLIC_URL"
    # The load balancer accepts traffic only from this server. Caddy reaches
    # the internet-facing load balancer through its public address, so the
    # traffic arrives from this server's public (Elastic) IP.
    imds_token="$(curl -fsS -X PUT http://169.254.169.254/latest/api/token \
        -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')" || fail "Could not read the instance metadata."
    EC2_PUBLIC_IP="$(curl -fsS -H "X-aws-ec2-metadata-token: $imds_token" \
        http://169.254.169.254/latest/meta-data/public-ipv4)" || fail "This server has no public IP."
    [[ "$EC2_PUBLIC_IP" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Unexpected public IP '$EC2_PUBLIC_IP'."
    allow_in "$ALB_SG" "IpProtocol=tcp,FromPort=80,ToPort=80,IpRanges=[{CidrIp=$EC2_PUBLIC_IP/32,Description=Caddy-on-EC2}]"
    # An earlier run may have opened the load balancer to CloudFront; close it.
    aws ec2 revoke-security-group-ingress --group-id "$ALB_SG" \
        --ip-permissions "IpProtocol=tcp,FromPort=80,ToPort=80,PrefixListIds=[{PrefixListId=$CF_PREFIX_LIST}]" \
        > /dev/null 2>&1 || true
    save_config EC2_PUBLIC_IP "$EC2_PUBLIC_IP"
    echo "HTTPS stays on this server (Caddy): $SITE_URL -> load balancer (allowed only from $EC2_PUBLIC_IP)"
elif [[ -n "${CLOUDFRONT_DOMAIN:-}" ]]; then
    CLOUDFRONT_DOMAIN="${CLOUDFRONT_DOMAIN#https://}"; CLOUDFRONT_DOMAIN="${CLOUDFRONT_DOMAIN%%/*}"
    [[ "$CLOUDFRONT_DOMAIN" =~ ^d[a-z0-9]+\.cloudfront\.net$ ]] \
        || fail "CLOUDFRONT_DOMAIN must look like dxxxxxxxxxxxx.cloudfront.net (got '$CLOUDFRONT_DOMAIN')."
    allow_in "$ALB_SG" "IpProtocol=tcp,FromPort=80,ToPort=80,PrefixListIds=[{PrefixListId=$CF_PREFIX_LIST,Description=CloudFront}]"
    EDGE="cloudfront"; SITE_URL="https://$CLOUDFRONT_DOMAIN"
    echo "Using the CloudFront distribution created in the console."
elif [[ -n "$saved_edge" && -n "$saved_site_url" ]]; then
    EDGE="$saved_edge"; SITE_URL="$saved_site_url"
    echo "Using the address chosen earlier ($EDGE)."
else
    cf_error_file="$WORK_DIR/cloudfront-error.txt"
    if ! CF_ID="$(awst cloudfront list-distributions \
            --query "DistributionList.Items[?Comment=='$NAME'].Id | [0]" 2>"$cf_error_file")"; then
        if grep -q "AccessDenied" "$cf_error_file"; then
            cat >&2 <<EOM

LabRole is not allowed to use CloudFront in this AWS Academy lab.
Keep HTTPS on this server instead (Caddy passes traffic to the load
balancer). Run this script again with the site's current address:

    PUBLIC_URL=https://184-193-202-252.sslip.io bash scripts/aws/ecs-setup.sh

Everything created so far is kept; the running site is not affected.
EOM
            fail "Choose how the site is reached (see above)."
        fi
        cat "$cf_error_file" >&2
        fail "Could not check CloudFront (error above)."
    fi
    if [[ -z "$CF_ID" ]]; then
        cat > "$WORK_DIR/cloudfront.json" <<EOF
{
  "CallerReference": "$NAME-$(date +%s)",
  "Comment": "$NAME",
  "Enabled": true,
  "PriceClass": "PriceClass_100",
  "HttpVersion": "http2",
  "IsIPV6Enabled": true,
  "Origins": {
    "Quantity": 1,
    "Items": [{
      "Id": "alb",
      "DomainName": "$ALB_DNS",
      "CustomOriginConfig": {
        "HTTPPort": 80,
        "HTTPSPort": 443,
        "OriginProtocolPolicy": "http-only",
        "OriginReadTimeout": 60,
        "OriginKeepaliveTimeout": 5,
        "OriginSslProtocols": { "Quantity": 1, "Items": ["TLSv1.2"] }
      }
    }]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "alb",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 7,
      "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
      "CachedMethods": { "Quantity": 2, "Items": ["GET", "HEAD"] }
    },
    "Compress": true,
    "CachePolicyId": "4135ea2d-6df8-44a3-9df3-4b5a84be39ad",
    "OriginRequestPolicyId": "33f36d7e-f396-46d9-90e0-52428a34d9dc"
  }
}
EOF
        CF_ID="$(awst cloudfront create-distribution --distribution-config file://cloudfront.json \
            --query 'Distribution.Id')" \
            || fail "CloudFront could not be created (see the error above). Nothing else is affected."
    fi
    CF_DOMAIN="$(awst cloudfront get-distribution --id "$CF_ID" --query 'Distribution.DomainName')"
    save_config CF_ID "$CF_ID"
    allow_in "$ALB_SG" "IpProtocol=tcp,FromPort=80,ToPort=80,PrefixListIds=[{PrefixListId=$CF_PREFIX_LIST,Description=CloudFront}]"
    EDGE="cloudfront"; SITE_URL="https://$CF_DOMAIN"
fi
echo "Public address: $SITE_URL"
save_config EDGE "$EDGE"; save_config SITE_URL "$SITE_URL"

# ---------------------------------------------------------------------
log "8. ECS cluster"
aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com > /dev/null 2>&1 || true
status="$(awst ecs describe-clusters --clusters "$CLUSTER" --query 'clusters[0].status')"
if [[ "$status" != "ACTIVE" ]]; then
    aws ecs create-cluster --cluster-name "$CLUSTER" --capacity-providers FARGATE \
        --settings name=containerInsights,value=disabled > /dev/null \
        || fail "The ECS cluster could not be created. If the message mentions the service linked role, run this script again."
fi
echo "Cluster $CLUSTER"

# ---------------------------------------------------------------------
log "9. First deploy (builds the images and starts 1 task)"
bash "$APP_DIR/scripts/deploy/ecs-deploy.sh" "$(git rev-parse HEAD)"

cat <<EOF

=====================================================================
ECS is set up and running ($EDGE). Public address: $SITE_URL

The site is still served by the EC2 containers until you switch:
follow "D. Switch to ECS" in docs/operations/aws-ecs.md.

Turn the site off/on to save credit:  bash scripts/aws/ecs-scale.sh off|on
=====================================================================
EOF

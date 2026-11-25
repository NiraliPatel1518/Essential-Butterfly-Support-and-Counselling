#!/usr/bin/env bash
# =====================================================================
# One-time copy of the Memory Wall photos from the Docker volume on the
# EC2 server to the S3 bucket.        Run ON THE SERVER, from ~/app:
#
#     bash scripts/aws/copy-photos-to-s3.sh <bucket-name>
#
# Safe to run more than once. It only COPIES:
#   - the Docker volume is mounted read-only, nothing is removed from it
#   - "aws s3 sync" without --delete never removes anything from S3
#   - the database is only read
# It does not switch the application to S3; that is a separate step
# (docs/operations/aws-s3-rds-migration.md).
#
# Needs: the instance has the IAM role LabInstanceProfile and the
# metadata hop limit is 2 (so containers can use the role).
# =====================================================================
set -Eeuo pipefail

BUCKET="${1:?usage: copy-photos-to-s3.sh <bucket-name>}"
REGION="${AWS_REGION:-us-east-1}"
VOLUME="${VOLUME:-app_memory-wall-data}"
DB_CONTAINER="essential-butterfly-db"
AWS_IMAGE="amazon/aws-cli:latest"

fail() { printf '\nSTOPPED: %s\n' "$*" >&2; exit 1; }
aws_cli() { sudo docker run --rm -e AWS_DEFAULT_REGION="$REGION" "$@"; }

echo "==> Checking AWS access from containers"
aws_cli "$AWS_IMAGE" sts get-caller-identity --query Arn --output text \
    || fail "Containers cannot get AWS credentials. Attach LabInstanceProfile to the instance and set the metadata hop limit to 2 (see the migration guide)."

aws_cli "$AWS_IMAGE" s3api head-bucket --bucket "$BUCKET" \
    || fail "Bucket '$BUCKET' does not exist or is not reachable in $REGION."

echo "==> Finding the photo volume"
MOUNT="$(sudo docker volume inspect -f '{{.Mountpoint}}' "$VOLUME" 2>/dev/null)" \
    || fail "Docker volume '$VOLUME' not found."
local_list="$(sudo find "$MOUNT" -type f -printf '%P\n' | sort)"
local_count="$(printf '%s' "$local_list" | grep -c . || true)"
echo "Files in the volume: $local_count"

echo "==> Copying to s3://$BUCKET (nothing is deleted anywhere)"
aws_cli -v "$MOUNT:/photos:ro" "$AWS_IMAGE" \
    s3 sync /photos "s3://$BUCKET" --only-show-errors

echo "==> Checking the copy"
s3_list="$(aws_cli "$AWS_IMAGE" s3api list-objects-v2 --bucket "$BUCKET" \
    --query 'Contents[].Key' --output text | tr '\t' '\n' | grep -v '^None$' | sort || true)"

missing_files="$(comm -23 <(printf '%s\n' "$local_list" | grep .) <(printf '%s\n' "$s3_list" | grep .) || true)"
[[ -z "$missing_files" ]] || { printf '%s\n' "$missing_files" >&2; fail "These files are not in S3 yet (listed above). Run the script again."; }

# Every photo the database points to must be in the bucket.
db_keys="$(sudo docker exec "$DB_CONTAINER" psql -U postgres -d essential_butterfly -At \
    -c "SELECT storage_key FROM memory_wall_photos WHERE storage_key IS NOT NULL ORDER BY 1" | sort)"
db_count="$(printf '%s' "$db_keys" | grep -c . || true)"
missing_db="$(comm -23 <(printf '%s\n' "$db_keys" | grep .) <(printf '%s\n' "$s3_list" | grep .) || true)"
if [[ -n "$missing_db" ]]; then
    printf '%s\n' "$missing_db" >&2
    echo "WARNING: the database points to the files above, but they were not in the volume either (they were already missing before this copy)." >&2
fi

echo
echo "OK: $local_count file(s) in the volume, all present in s3://$BUCKET."
echo "    The database refers to $db_count photo(s)."
echo "    The volume was not changed. You can now switch the backend to S3."

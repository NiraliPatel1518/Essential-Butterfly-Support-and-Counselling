#!/usr/bin/env bash
# =====================================================================
# Shared helpers for the scripts in scripts/aws/ (sourced, not run).
#
# The AWS CLI runs from the official amazon/aws-cli Docker image, so
# nothing has to be installed on the server. It gets its permissions from
# the instance profile (LabInstanceProfile -> LabRole), so no AWS keys are
# stored anywhere.
# =====================================================================

AWS_REGION="${AWS_REGION:-us-east-1}"
APP_DIR="${APP_DIR:-$HOME/app}"
ECS_CONFIG="${ECS_CONFIG:-$HOME/ecs-config.env}"
AWS_CLI_IMAGE="${AWS_CLI_IMAGE:-amazon/aws-cli:latest}"
# Files the AWS CLI must read (JSON documents, secret values) are written
# here; the folder is mounted into the CLI container as /work.
WORK_DIR="${WORK_DIR:-$HOME/.ecs-work}"
mkdir -p "$WORK_DIR"
chmod 700 "$WORK_DIR"

log()  { printf '\n==> %s\n' "$*"; }
fail() { printf '\nSTOPPED: %s\n' "$*" >&2; exit 1; }

aws() {
    sudo docker run --rm -i \
        -e AWS_DEFAULT_REGION="$AWS_REGION" -e AWS_PAGER="" \
        -v "$WORK_DIR:/work" -w /work \
        "$AWS_CLI_IMAGE" "$@"
}

# Same, with plain-text output; "None" (nothing found) becomes "".
awst() {
    local out
    out="$(aws "$@" --output text)" || return 1
    [[ "$out" == "None" ]] && out=""
    printf '%s' "$out"
}

# Remembers a value in $ECS_CONFIG (KEY="value" lines, read with "source").
save_config() {
    local key="$1" value="$2" tmp
    touch "$ECS_CONFIG"; chmod 600 "$ECS_CONFIG"
    tmp="$(mktemp)"
    grep -v "^${key}=" "$ECS_CONFIG" > "$tmp" || true
    printf '%s="%s"\n' "$key" "$value" >> "$tmp"
    cat "$tmp" > "$ECS_CONFIG"; rm -f "$tmp"
    printf -v "$key" '%s' "$value"
}

load_config() {
    [[ -f "$ECS_CONFIG" ]] || fail "$ECS_CONFIG not found. Run scripts/aws/ecs-setup.sh first."
    # shellcheck disable=SC1090
    source "$ECS_CONFIG"
}

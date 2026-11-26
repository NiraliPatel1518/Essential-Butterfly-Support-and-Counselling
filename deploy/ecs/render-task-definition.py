#!/usr/bin/env python3
"""Fill in deploy/ecs/task-definition.template.json and print the result.

Every ${NAME} in the template is replaced by the environment variable NAME
(the script stops if one is missing). The backend's secrets are added from
SECRET_MAP, a comma-separated list of ENV_NAME=ssm-parameter-arn pairs, so
the task reads them from SSM Parameter Store when it starts; no secret
value is ever written into the task definition.

Usage:  python3 deploy/ecs/render-task-definition.py > taskdef.json
"""
import json
import os
import re
import sys
from pathlib import Path

TEMPLATE = Path(__file__).with_name("task-definition.template.json")
PLACEHOLDER = re.compile(r"\$\{([A-Z0-9_]+)\}")


def substitute(value):
    if isinstance(value, str):
        def repl(match):
            name = match.group(1)
            if not os.environ.get(name):
                sys.exit(f"render-task-definition: {name} is not set")
            return os.environ[name]
        return PLACEHOLDER.sub(repl, value)
    if isinstance(value, list):
        return [substitute(item) for item in value]
    if isinstance(value, dict):
        return {key: substitute(item) for key, item in value.items()}
    return value


def main():
    definition = substitute(json.loads(TEMPLATE.read_text()))

    secrets = []
    for pair in filter(None, os.environ.get("SECRET_MAP", "").split(",")):
        name, _, arn = pair.partition("=")
        if not name or not arn.startswith("arn:aws:ssm:"):
            sys.exit(f"render-task-definition: bad SECRET_MAP entry {pair!r}")
        secrets.append({"name": name, "valueFrom": arn})

    backend = next(c for c in definition["containerDefinitions"] if c["name"] == "backend")
    backend["secrets"] = secrets

    if not any(s["name"] == "SPRING_DATASOURCE_PASSWORD" for s in secrets):
        sys.exit("render-task-definition: the database password secret is missing")

    json.dump(definition, sys.stdout, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()

#!/usr/bin/env sh
set -eu

# Saves the local homelab MinIO/S3 credentials for product image imports.
# Source file is intentionally ignored by git:
#   .secrets/homelab-minio.env

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
env_file="${ENV_FILE:-$repo_root/.secrets/homelab-minio.env}"
item_name="${BW_ITEM_NAME:-grocery-automate/homelab/minio-product-images}"

if ! command -v bw >/dev/null 2>&1; then
  echo "Bitwarden CLI is not installed." >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required." >&2
  exit 1
fi

status="$(bw status 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])' 2>/dev/null || true)"
if [ "$status" != "unlocked" ]; then
  echo "Bitwarden CLI is not unlocked." >&2
  echo "Run one of these first:" >&2
  echo "  bw login" >&2
  echo "  export BW_SESSION=\$(bw unlock --raw)" >&2
  exit 1
fi

if [ ! -f "$env_file" ]; then
  echo "Missing env file: $env_file" >&2
  exit 1
fi

item_json="$(ENV_FILE="$env_file" BW_ITEM_NAME="$item_name" python3 - <<'PY'
from pathlib import Path
import json
import os

env_file = Path(os.environ["ENV_FILE"])
item_name = os.environ["BW_ITEM_NAME"]

values = {}
for line in env_file.read_text().splitlines():
    stripped = line.strip()
    if not stripped or stripped.startswith("#") or "=" not in stripped:
        continue
    key, value = stripped.split("=", 1)
    value = value.strip()
    if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):
        value = value[1:-1]
    values[key.strip()] = value

required = [
    "S3_ENDPOINT",
    "S3_BUCKET",
    "ASSET_BASE_URL",
    "S3_ACCESS_KEY",
    "S3_SECRET_KEY",
]
for key in required:
    value = values.get(key, "")
    if not value or value.startswith("TODO_"):
        raise SystemExit(f"{key} is missing or still TODO in {env_file}")

region = values.get("S3_REGION", "homelab")

item = {
    "type": 1,
    "name": item_name,
    "notes": (
        "Grocery Automate homelab MinIO credentials for product image imports.\n"
        f"S3 API: {values['S3_ENDPOINT']}\n"
        f"Assets: {values['ASSET_BASE_URL']}\n"
        f"Bucket: {values['S3_BUCKET']}\n"
        "Source file: .secrets/homelab-minio.env"
    ),
    "login": {
        "username": values["S3_ACCESS_KEY"],
        "password": values["S3_SECRET_KEY"],
        "uris": [
            {"uri": values["S3_ENDPOINT"]},
            {"uri": values["ASSET_BASE_URL"]},
        ],
    },
    "fields": [
        {"name": "S3_ENDPOINT", "value": values["S3_ENDPOINT"], "type": 0},
        {"name": "S3_BUCKET", "value": values["S3_BUCKET"], "type": 0},
        {"name": "ASSET_BASE_URL", "value": values["ASSET_BASE_URL"], "type": 0},
        {"name": "S3_REGION", "value": region, "type": 0},
        {"name": "S3_ACCESS_KEY", "value": values["S3_ACCESS_KEY"], "type": 1},
        {"name": "S3_SECRET_KEY", "value": values["S3_SECRET_KEY"], "type": 1},
    ],
}

print(json.dumps(item))
PY
)"

item_id="$(bw list items --search "$item_name" \
  | python3 -c 'import json,sys; items=json.load(sys.stdin); print(next((i["id"] for i in items if i.get("name") == "'"$item_name"'"), ""))')"

encoded="$(printf '%s' "$item_json" | bw encode)"

if [ -n "$item_id" ]; then
  bw edit item "$item_id" "$encoded" >/dev/null
  echo "Updated Bitwarden item: $item_name"
else
  bw create item "$encoded" >/dev/null
  echo "Created Bitwarden item: $item_name"
fi

bw sync >/dev/null
echo "Bitwarden sync complete."

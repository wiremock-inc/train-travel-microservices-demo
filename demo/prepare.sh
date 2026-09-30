#!/usr/bin/env bash
#
# Makes a sibling copy of this repo containing only what an agent would have in a real project: the
# code, the API contracts and plain READMEs. Git history, stub mappings, design docs and demo notes
# are left out, and READMEs that describe the mock implementation are swapped for the clean
# versions in demo/overlay/.
#
# Usage: demo/prepare.sh [target-dir] [--force]
#   target-dir  defaults to ../<repo-dir-name>-demo
#   --force     replace the target if it already exists

set -euo pipefail

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$(dirname "$DEMO_DIR")"
PARENT_DIR="$(dirname "$SOURCE_DIR")"

TARGET_DIR=""
FORCE=false
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    -h|--help) sed -n '3,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) TARGET_DIR="$arg" ;;
  esac
done
TARGET_DIR="${TARGET_DIR:-$PARENT_DIR/$(basename "$SOURCE_DIR")-demo}"

if [[ -e "$TARGET_DIR" ]]; then
  if [[ "$FORCE" != true ]]; then
    echo "Target already exists: $TARGET_DIR (re-run with --force to replace it)" >&2
    exit 1
  fi
  rm -rf "$TARGET_DIR"
fi

# node_modules is kept so the UI and E2E don't need a reinstall before the demo.
rsync -a \
  --exclude='.git' \
  --exclude='/stubs/' \
  --exclude='/docs/' \
  --exclude='/demo/' \
  --exclude='/demo-flows.md' \
  --exclude='/build/' \
  --exclude='/.gradle/' \
  --exclude='/.kotlin/' \
  --exclude='/.idea/' \
  --exclude='.DS_Store' \
  "$SOURCE_DIR/" "$TARGET_DIR/"

rsync -a "$DEMO_DIR/overlay/" "$TARGET_DIR/"

# Point the config and contracts at placeholder hosts, so the agent can't just call (or record) the
# existing mocks. The Google IdP (5e5y3) stays: template mocks can only be created via the WireMock Cloud UI.
URL_PLACEHOLDERS=(
  'wm-train-travel-stations.wiremockapi.cloud' 'stations.api.example.com'
  'y06rz.wiremockapi.cloud'                    'stations.api.example.com'
  'wm-train-travel-trips.wiremockapi.cloud'    'trips.api.example.com'
  'kvmly.wiremockapi.cloud'                    'trips.api.example.com'
  'wm-train-travel-bookings.wiremockapi.cloud' 'bookings.api.example.com'
  'vkoeq.wiremockapi.cloud'                    'bookings.api.example.com'
  'wm-train-travel-payments.wiremockapi.cloud' 'payments.api.example.com'
  'wm-train-travel-webhook.wiremockapi.cloud'  'webhooks.example.com'
)
files_with_urls="$(grep -rIl --exclude-dir=node_modules --exclude=pnpm-lock.yaml 'wiremockapi\.cloud' "$TARGET_DIR" || true)"
for ((i = 0; i < ${#URL_PLACEHOLDERS[@]}; i += 2)); do
  from="${URL_PLACEHOLDERS[i]}" to="${URL_PLACEHOLDERS[i + 1]}"
  [[ -n "$files_with_urls" ]] && FROM="$from" TO="$to" perl -pi -e 's/\Q$ENV{FROM}\E/$ENV{TO}/g' $files_with_urls
done

leftovers="$(find "$TARGET_DIR" -path '*/node_modules' -prune -o \
  \( -name '.git' -o -iname '*stub*.json' -o -path "$TARGET_DIR/docs" \) -print)"
mentions="$(grep -rIl --exclude-dir=node_modules -e 'stubs/' -e 'docs/train-travel' "$TARGET_DIR" || true)"
mentions+="$(grep -rIo --exclude-dir=node_modules --exclude=pnpm-lock.yaml '[a-z0-9-]*\.wiremockapi\.cloud' "$TARGET_DIR" \
  | grep -v ':5e5y3\.wiremockapi\.cloud$' || true)"
if [[ -n "$leftovers$mentions" ]]; then
  echo "Copy still contains, or points to, material that should have been removed:" >&2
  printf '%s\n' $leftovers $mentions >&2
  exit 1
fi

echo "Demo copy ready at $TARGET_DIR"

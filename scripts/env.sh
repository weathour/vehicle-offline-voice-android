#!/usr/bin/env bash
# Repository-local Android/Codex environment bootstrap.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/.codex/android-env.sh"

#!/usr/bin/env bash
# Legado Desktop backend - cross-platform start script (Linux/macOS/Git-Bash/WSL)
# Windows: use tools/start_backend.ps1 instead.
#
# Usage:
#   tools/start_backend.sh [--build] [backend args...]
#     --build            force re-run installDist (default: only if not installed)
#     backend args...    passed through, e.g. --port 2323 --host 127.0.0.1
#                        --set-js-source-token <token>, --api-smoke-test, ...
#
# Env:
#   LEGADO_DESKTOP_HOME         data dir (default ~/.legado-desktop)
#   LEGADO_DESKTOP_ENABLE_JCEF  set to 1 to enable the JCEF webview engine
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND="$ROOT/backend"
BIN_DIR="$BACKEND/build/install/legado-desktop-backend/bin"
BIN="$BIN_DIR/legado-desktop-backend"

if [[ "$*" == *--help* ]] || [[ "$*" == *-h ]]; then
  sed -n '2,16p' "${BASH_SOURCE[0]}"
  exit 0
fi

# filter our own --build flag, keep the rest for the backend
BUILD=0
ARGS=()
for a in "$@"; do
  if [ "$a" = "--build" ]; then
    BUILD=1
  else
    ARGS+=("$a")
  fi
done

# data dir default
if [ -z "${LEGADO_DESKTOP_HOME:-}" ]; then
  export LEGADO_DESKTOP_HOME="$HOME/.legado-desktop"
  echo "[start-backend] LEGADO_DESKTOP_HOME=$LEGADO_DESKTOP_HOME"
fi

if [ ! -x "$BIN" ] || [ "$BUILD" -eq 1 ]; then
  echo "[start-backend] running installDist ..."
  (cd "$BACKEND" && ./gradlew installDist --console=plain)
fi

echo "[start-backend] starting backend: $BIN ${ARGS[*]:-}"
exec "$BIN" "${ARGS[@]}"

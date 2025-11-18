#!/usr/bin/env bash
set -euo pipefail

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
BUILD_DIR="$ROOT_DIR/build"
JAVA_DIR="$ROOT_DIR/java"
OUT_DIR="$ROOT_DIR/out"

# ── Help / Usage ──────────────────────────────────────────────────────────────
if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <JavaMainClass>"
  echo
  echo "Example:"
  echo "  $0 com.demos.DemoIETI"
  echo "  $0 com.demos.DemoAnim"
  echo "  $0 com.project.client.Main"
  echo "  $0 com.project.server.Main"
  exit 1
fi

MAIN_CLASS="$1"

# ── 1) Build lib JNI ─────────────────────────────────────────────────────────
if [[ ! -f "$BUILD_DIR/libpiomatterjni.so" ]]; then
  echo "ℹ️  No JNI library found; building first…"
  "$ROOT_DIR/build_lib.sh"
fi

# ── 2) Compile Java ───────────────────────────────────────────────────────────
echo "▶️  Compiling Java → $OUT_DIR"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

if ! javac -d "$OUT_DIR" $(find "$JAVA_DIR" -name "*.java"); then
  echo "✖ Java compilation failed."
  exit 1
fi

# ── 3) Run selected class ─────────────────────────────────────────────────────
echo "▶️  Running Demo ($MAIN_CLASS)…"
export LD_LIBRARY_PATH="$BUILD_DIR${LD_LIBRARY_PATH+:$LD_LIBRARY_PATH}"
java -cp "$OUT_DIR" "$MAIN_CLASS"

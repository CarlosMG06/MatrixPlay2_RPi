#!/usr/bin/env bash
set -euo pipefail

rm -rf build

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
BUILD_DIR="$ROOT_DIR/build"

# ── Checks ────────────────────────────────────────────────────────────────────
need() { command -v "$1" >/dev/null 2>&1 || { echo "✖ Falta '$1'. Instal·la'l i torna-ho a provar."; exit 1; }; }
need cmake
need make
need gcc
need javac

# Informatiu: mostra versió Java
echo "ℹ️  Java: $(javac -version 2>&1 || true)"

# ── Build .so ─────────────────────────────────────────────────────────────────
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

echo "▶️  CMake configure…"
cmake -DCMAKE_BUILD_TYPE=Release ..

echo "▶️  Make…"
make -j"$(nproc)"

SO_PATH="$BUILD_DIR/libpiomatterjni.so"
if [[ ! -f "$SO_PATH" ]]; then
  echo "✖ No s'ha generat $SO_PATH"
  exit 1
fi

echo "✅ Fet! S'ha generat: $SO_PATH"

#!/usr/bin/env bash
set -euo pipefail

ZIP_FILE="src_backup.zip"
SRC_DIR="src"

# ── Comprova si el fitxer ZIP existeix ─────────────────────────────────────────
if [[ ! -f "$ZIP_FILE" ]]; then
  echo "✖ No s'ha trobat $ZIP_FILE. Crea'l primer amb: zip -r src_backup.zip src"
  exit 1
fi

# ── Esborra la carpeta src actual ─────────────────────────────────────────────
if [[ -d "$SRC_DIR" ]]; then
  echo "🗑  Esborrant la carpeta '$SRC_DIR' actual..."
  rm -rf "$SRC_DIR"
fi

# ── Restaura la carpeta src des del ZIP ───────────────────────────────────────
echo "📦 Extraient carpeta '$SRC_DIR' des de $ZIP_FILE..."
unzip -q "$ZIP_FILE"

echo "✅ Restauració completada. Carpeta '$SRC_DIR' restaurada amb èxit."

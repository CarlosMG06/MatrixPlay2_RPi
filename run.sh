#!/usr/bin/env bash
set -euo pipefail

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
BUILD_DIR="$ROOT_DIR/build"        # on deixes libpiomatterjni.so
JAVA_DIR="$ROOT_DIR/java"

# ── Args ──────────────────────────────────────────────────────────────────────
if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <JavaMainClass> [build]"
  echo 
  echo "Optional param 'build' to clean package."
  echo
  echo "Examples:"
  echo "  $0 com.demos.DemoIETI"
  echo "  $0 com.demos.DemoAnim"
  echo "  $0 com.project.client.Client"
  echo "  $0 com.project.server.Main"
  exit 1
fi

MAIN_CLASS="$1"
ACTION="${2:-run}"   # "build" opcional

# ── 1) Build lib JNI si no existeix ───────────────────────────────────────────
if [[ ! -f "$BUILD_DIR/libpiomatterjni.so" ]]; then
  echo "ℹ️  No JNI library found; building first…"
  "$ROOT_DIR/build_lib.sh"
fi

# ── 2) (Opcional) Config JavaFX via Maven local ───────────────────────────────
get_latest_version() {
  local module_name=$1
  find "$HOME/.m2/repository/org/openjfx" -name "${module_name}-*.jar" \
    | grep -vE "javadoc|sources" | sort -Vr | head -n1
}

case "$OSTYPE" in
  darwin*)  javafx_platform="mac" ;;
  linux*)   javafx_platform="linux" ;;
  *)        javafx_platform="linux" ;;
esac

FX_BASE_PATH="$(get_latest_version "javafx-base" || true)"
FX_CONTROLS_PATH="$(get_latest_version "javafx-controls" || true)"
FX_FXML_PATH="$(get_latest_version "javafx-fxml" || true)"
FX_GRAPHICS_PATH="$(get_latest_version "javafx-graphics" || true)"

FX_PATH=""
if [[ -n "${FX_BASE_PATH:-}" && -n "${FX_CONTROLS_PATH:-}" && -n "${FX_FXML_PATH:-}" && -n "${FX_GRAPHICS_PATH:-}" ]]; then
  FX_PATH="${FX_BASE_PATH}:${FX_CONTROLS_PATH}:${FX_FXML_PATH}:${FX_GRAPHICS_PATH}"
fi

# ── 3) MAVEN_OPTS (obertures + JavaFX si disponible) ──────────────────────────
export MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED"

if [[ -n "$FX_PATH" ]]; then
  export MAVEN_OPTS="$MAVEN_OPTS --module-path $FX_PATH --add-modules javafx.controls,javafx.fxml,javafx.graphics"
fi

if [[ "$OSTYPE" == "darwin"* ]]; then
  export MAVEN_OPTS="$MAVEN_OPTS -Xdock:icon=./target/classes/icons/iconOSX.png"
fi

# ── 4) Vars natives per a la lib JNI ──────────────────────────────────────────
export LD_LIBRARY_PATH="$BUILD_DIR${LD_LIBRARY_PATH+:$LD_LIBRARY_PATH}"
# També ho passem a la JVM per si algun entorn no respecta l'export
JAVA_NATIVE_PROP="-Djava.library.path=$BUILD_DIR"

echo "▶️  MAIN: $MAIN_CLASS"
echo "▶️  MAVEN_OPTS: $MAVEN_OPTS"
[[ -n "$FX_PATH" ]] && echo "▶️  JavaFX platform: $javafx_platform"

# ── 5) Accions Maven ──────────────────────────────────────────────────────────
if [[ "$ACTION" == "build" ]]; then
  mvn -q -e -DskipTests clean package
  echo "✅ Build OK → target/"
  exit 0
fi

# Compila i executa el main
exec mvn -e -DskipTests \
  -Dexec.cleanDaemonThreads=false \
  -Dexec.mainClass="$MAIN_CLASS" \
  -Dexec.args="" \
  -Djavafx.platform="$javafx_platform" \
  -Dexec.jvmArgs="$JAVA_NATIVE_PROP $MAVEN_OPTS" \
  compile exec:java


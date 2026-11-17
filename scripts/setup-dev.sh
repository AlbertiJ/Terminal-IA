#!/usr/bin/env bash
set -euo pipefail

# Script de preparación local para Terminal-IA.
# Verifica JDK 17+, Android SDK y Gradle, y genera debug.keystore si falta.
# No descarga paquetes ni herramientas de Internet.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> Directorio del proyecto: $ROOT_DIR"

# 1. Verificar JDK 17+ y keytool
if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: No se encontró 'java' en el PATH. Instala JDK 17 o superior." >&2
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "ERROR: No se encontró 'keytool' en el PATH. Instala un JDK completo (17+)." >&2
  exit 1
fi

JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1)"
JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION_LINE" | sed -n 's/.*"\([0-9][0-9]*\)\..*/\1/p')"
if [ -z "$JAVA_MAJOR" ]; then
  JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION_LINE" | sed -n 's/.*"\([0-9][0-9]*\)".*/\1/p')"
fi

if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 17 ]; then
  echo "ERROR: Se requiere JDK 17 o superior. Detectado: $JAVA_VERSION_LINE" >&2
  exit 1
fi
echo "OK: JDK detectado ($JAVA_VERSION_LINE)"

# 2. Verificar Android SDK (sin descargar nada)
SDK_PATH="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK_PATH" ]; then
  echo "ERROR: Define ANDROID_SDK_ROOT o ANDROID_HOME apuntando a tu Android SDK local." >&2
  exit 1
fi

if [ ! -d "$SDK_PATH" ]; then
  echo "ERROR: La ruta del Android SDK no existe: $SDK_PATH" >&2
  exit 1
fi

if [ ! -d "$SDK_PATH/platforms" ]; then
  echo "ADVERTENCIA: No se encontró '$SDK_PATH/platforms'. Asegúrate de tener instalada la plataforma Android API 36 desde Android Studio." >&2
else
  echo "OK: Android SDK encontrado en $SDK_PATH"
fi

# 3. Verificar Gradle (opcional informativo si compilas por CLI)
if command -v gradle >/dev/null 2>&1; then
  GRADLE_VER="$(gradle --version 2>/dev/null | grep -E '^Gradle ' | head -n 1 || true)"
  echo "OK: Gradle disponible (${GRADLE_VER:-detectado})"
else
  echo "AVISO: 'gradle' no está en el PATH. Puedes abrir el proyecto en Android Studio o instalar Gradle 9.3.1."
fi

# 4. Generar debug.keystore si falta (coincide con debugConfig en app/build.gradle.kts)
KEYSTORE_FILE="$ROOT_DIR/debug.keystore"
if [ -f "$KEYSTORE_FILE" ]; then
  echo "OK: Ya existe debug.keystore en $KEYSTORE_FILE (no se modifica)."
else
  echo "==> Generando debug.keystore en $KEYSTORE_FILE ..."
  keytool -genkeypair \
    -keystore "$KEYSTORE_FILE" \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
  echo "OK: debug.keystore creado correctamente."
fi

echo
echo "Entorno verificado. Comandos útiles:"
echo "  gradle :app:testDebugUnitTest"
echo "  gradle :app:assembleDebug"

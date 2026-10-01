#!/usr/bin/env bash
set -euo pipefail

# --- Paths ---
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_ROOT/src"
BUILD_DIR="${BUILD_DIR:-$PROJECT_ROOT/build}"
CLASSES_DIR="$BUILD_DIR/classes"
JAR_OUT="$BUILD_DIR/viewpointthirdperson.jar"
VERSION_DIR="42.21"

# Per-machine overrides — create build.local (gitignored) to set PZ_DIR etc.
if [ -f "$PROJECT_ROOT/build.local" ]; then
    # shellcheck disable=SC1091
    source "$PROJECT_ROOT/build.local"
fi

if [ -z "${PZ_DIR:-}" ] || [ ! -f "$PZ_DIR/projectzomboid.jar" ]; then
    echo "[build] ERROR: Project Zomboid install not found." >&2
    echo "        Set PZ_DIR in build.local (see build.local.example)." >&2
    exit 1
fi

if [ -z "${VIEWPOINT_JAR:-}" ] || [ ! -f "$VIEWPOINT_JAR" ]; then
    echo "[build] ERROR: Viewpoint.jar not found." >&2
    echo "        Set VIEWPOINT_JAR in build.local (see build.local.example)." >&2
    exit 1
fi

# If PZ is running, the JVM holds a Windows file lock on the jar.
# The install step does `rm -rf` + `cp -r` — rm fails on the locked JAR
# mid-tree and set -e leaves the mod folder half-deployed.
if [ -z "${SKIP_PZ_CHECK:-}" ] && command -v tasklist >/dev/null 2>&1; then
    if tasklist //FI "IMAGENAME eq ProjectZomboid64.exe" //FO CSV //NH 2>/dev/null | grep -qi ProjectZomboid64; then
        echo "[build] ERROR: Project Zomboid is running. Close it before building." >&2
        exit 1
    fi
fi

: "${MOD_INSTALL_ROOT:=$USERPROFILE/Zomboid/mods/ViewpointThirdPerson}"

PZ_JAR="$PZ_DIR/projectzomboid.jar"
ZB_JAR="$PZ_DIR/ZombieBuddy.jar"

: "${JDK_DIR:=C:/Program Files/Zulu/zulu-25}"
JAVAC="$JDK_DIR/bin/javac.exe"
JAR="$JDK_DIR/bin/jar.exe"
if [ ! -f "$JAVAC" ]; then
    echo "[build] ERROR: javac not found at $JAVAC — set JDK_DIR in build.local." >&2
    exit 1
fi

# --- Clean ---
rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES_DIR"

# --- Compile ---
echo "[build] Compiling..."
mapfile -t SOURCES < <(find "$SRC_DIR" -name '*.java')

"$JAVAC" \
    --release 17 \
    -classpath "$PZ_JAR;$ZB_JAR;$VIEWPOINT_JAR" \
    -d "$CLASSES_DIR" \
    "${SOURCES[@]}"

# --- Package jar ---
echo "[build] Packaging jar..."
"$JAR" --create --file "$JAR_OUT" -C "$CLASSES_DIR" .

# --- Stage mod directory ---
echo "[build] Staging mod directory..."
STAGE="$BUILD_DIR/stage/ViewpointThirdPerson"
rm -rf "$STAGE"
mkdir -p "$STAGE/$VERSION_DIR"
cp "$PROJECT_ROOT/mod_files/mod.info" "$STAGE/mod.info"
cp "$PROJECT_ROOT/mod_files/$VERSION_DIR/mod.info" "$STAGE/$VERSION_DIR/mod.info"
cp -r "$PROJECT_ROOT/mod_files/$VERSION_DIR/media" "$STAGE/$VERSION_DIR/media"
cp "$PROJECT_ROOT/poster.png" "$STAGE/poster.png"
cp "$PROJECT_ROOT/poster.png" "$STAGE/$VERSION_DIR/poster.png"
cp "$PROJECT_ROOT/icon.png" "$STAGE/icon.png"
cp "$PROJECT_ROOT/icon.png" "$STAGE/$VERSION_DIR/icon.png"
mkdir -p "$STAGE/$VERSION_DIR/media/java/client"
cp "$JAR_OUT" "$STAGE/$VERSION_DIR/media/java/client/viewpointthirdperson.jar"

# --- Install to Zomboid mods dir ---
echo "[build] Installing to $MOD_INSTALL_ROOT"
rm -rf "$MOD_INSTALL_ROOT"
mkdir -p "$(dirname "$MOD_INSTALL_ROOT")"
cp -r "$STAGE" "$MOD_INSTALL_ROOT"

echo "[build] Done."
echo "       Jar:     $JAR_OUT"
echo "       Install: $MOD_INSTALL_ROOT"

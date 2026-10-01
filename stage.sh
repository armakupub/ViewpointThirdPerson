#!/usr/bin/env bash
# Workshop stage for a release, the only way the stage gets filled.
#   bash stage.sh          before the upload: checks the release, builds, fills and verifies the stage
#   bash stage.sh finish   after upload and subscription: reports uploader edits, checks the Steam copy,
#                          removes the local copies
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"
if [ -f build.local ]; then
    # shellcheck disable=SC1091
    source build.local
fi

fail() { echo "[stage] FAIL: $*" >&2; exit 1; }
ok() { echo "[stage] ok: $*"; }
field() { sed -n "s/^$1=//p" "$2" | tr -d '\r' | head -n 1; }
# Jar content per entry (a rebuild changes the timestamps, not the classes).
jar_sums() {
    local e
    unzip -Z1 "$1" | grep -v -e '/$' -e '^META-INF/MANIFEST.MF$' | sort | while IFS= read -r e; do
        printf '%s %s\n' "$(unzip -p "$1" "$e" | sha256sum | cut -c1-64)" "$e"
    done
}
same_tree() {
    diff -rq -x "$JAR_NAME" "$1" "$2" >/dev/null || return 1
    local a b
    a="$(find "$1" -name "$JAR_NAME")"
    b="$(find "$2" -name "$JAR_NAME")"
    [ -n "$a" ] && [ -n "$b" ] && diff <(jar_sums "$a") <(jar_sums "$b") >/dev/null
}
# Text without CR and trailing blank lines.
norm() { tr -d '\r' < "$1" | awk '{a[NR]=$0} END {n=NR; while (n > 0 && a[n] ~ /^[ \t]*$/) n--; for (i = 1; i <= n; i++) print a[i]}'; }

MOD="$(field id mod_files/mod.info)"
JAR_NAME="$(basename "$(field javaJarFile "$(find mod_files -mindepth 2 -name mod.info | head -n 1)")")"
[ -n "$MOD" ] && [ -n "$JAR_NAME" ] || fail "mod id or jar name missing in mod_files"
STAGE_ROOT="${WORKSHOP_ROOT:-$USERPROFILE/Zomboid/Workshop}/$MOD"
BUILT="$PROJECT_ROOT/build/stage/$MOD"
WORKSHOP_ID="$(field id workshop.txt)"
STEAM_COPY="${PZ_DIR:?PZ_DIR not set in build.local}/../../workshop/content/108600/$WORKSHOP_ID/mods/$MOD"
LOCAL_MODS="${MOD_INSTALL_ROOT:-$USERPROFILE/Zomboid/mods/$MOD}"

check_release() {
    [ -z "$(git status --porcelain --untracked-files=no)" ] || fail "uncommitted changes"
    VERSION="$(field modversion mod_files/mod.info)"
    while IFS= read -r f; do
        [ "$(field modversion "$f")" = "$VERSION" ] || fail "$f: modversion != $VERSION"
    done < <(find mod_files -name mod.info)
    [ "$(grep -m 1 -oE '^[0-9]+\.[0-9]+\.[0-9]+' CHANGELOG.txt)" = "$VERSION" ] \
        || fail "CHANGELOG.txt top entry != $VERSION"
    TAG="$(git describe --exact-match --tags HEAD 2>/dev/null)" || fail "HEAD is not tagged"
    [ "$TAG" = "v$VERSION" ] || fail "tag $TAG != v$VERSION"
    git fetch -q origin
    [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || fail "HEAD is not origin/main (push first)"
    git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null || fail "tag $TAG is not pushed"
    ok "release $TAG clean, tagged, pushed; mod.info and CHANGELOG at $VERSION"
}

check_workshop_txt() {
    [[ "$WORKSHOP_ID" =~ ^[0-9]+$ ]] || fail "workshop.txt: no numeric id= (upload would publish a new item)"
    [ -n "$(field tags workshop.txt)" ] || fail "workshop.txt: no tags="
    diff <(sed -n 's/^description=//p' workshop.txt | tr -d '\r') <(norm workshop_description.txt) >/dev/null \
        || fail "workshop.txt description differs from workshop_description.txt"
    ok "workshop.txt id=$WORKSHOP_ID, tags, description in sync"
}

prepare() {
    check_release
    check_workshop_txt
    bash build.sh
    rm -rf "$STAGE_ROOT/Contents"
    mkdir -p "$STAGE_ROOT/Contents/mods"
    cp -r "$BUILT" "$STAGE_ROOT/Contents/mods/$MOD"
    cp workshop.txt "$STAGE_ROOT/workshop.txt"
    cp poster.png "$STAGE_ROOT/preview.png"
    diff -rq "$STAGE_ROOT/Contents/mods/$MOD" "$BUILT" >/dev/null || fail "stage Contents != build"
    cmp -s workshop.txt "$STAGE_ROOT/workshop.txt" || fail "stage workshop.txt != repo"
    cmp -s poster.png "$STAGE_ROOT/preview.png" || fail "stage preview.png != poster.png"
    ok "stage filled: $STAGE_ROOT"
    echo "[stage] READY FOR UPLOAD: $MOD $TAG"
}

finish() {
    if ! diff <(norm workshop.txt) <(norm "$STAGE_ROOT/workshop.txt"); then
        fail "the uploader changed workshop.txt (above: < repo, > stage); take it into the repo or re-upload"
    fi
    ok "stage workshop.txt == repo"
    [ -d "$BUILT" ] || fail "no build to compare against; run bash build.sh on the release commit"
    [ -d "$STEAM_COPY" ] || fail "no Steam copy at $STEAM_COPY (subscribe and let Steam download)"
    same_tree "$STEAM_COPY" "$BUILT" || fail "Steam copy != build (files or jar content)"
    ok "Steam copy == build"
    rm -rf "$LOCAL_MODS" "$STAGE_ROOT/Contents"
    local n
    n="$(find "$USERPROFILE/Zomboid/mods" "${WORKSHOP_ROOT:-$USERPROFILE/Zomboid/Workshop}" \
        "$PZ_DIR/../../workshop/content/108600" -iname "$JAR_NAME" 2>/dev/null | wc -l)"
    [ "$n" -eq 1 ] || fail "$n copies of $JAR_NAME in the scan paths, expected 1"
    ok "local copies removed, one $JAR_NAME left (Steam)"
    echo "[stage] DONE: $MOD runs from the subscription"
}

case "${1:-}" in
    "") prepare ;;
    finish) finish ;;
    *) fail "usage: bash stage.sh [finish]" ;;
esac

#!/usr/bin/env bash
# Builds a signed release APK, publishes it to BUILD/, and writes the manifest
# the in-app updater reads.
#
#   client/scripts/release.sh            # build from the current commit
#   client/scripts/release.sh -n "notes" # with release notes
#
# The manifest is served from raw.githubusercontent.com, so the release is live
# as soon as BUILD/ is committed and pushed — no GitHub Release needed.
set -euo pipefail

NOTES=""
while getopts ":n:h" opt; do
    case $opt in
        n) NOTES="$OPTARG" ;;
        *) echo "usage: release.sh [-n \"release notes\"]" >&2; exit 2 ;;
    esac
done

CLIENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$CLIENT_DIR/.." && pwd)"
BUILD_DIR="$REPO_DIR/BUILD"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
GRADLE="${GRADLE:-/opt/gradle-8.11.1/bin/gradle}"

[ -f "$CLIENT_DIR/keystore/release.properties" ] || {
    echo "error: keystore/release.properties missing — a debug-signed build" >&2
    echo "       cannot update an existing install." >&2
    exit 1
}

# A release must be reproducible from a commit, so refuse a dirty tree.
if [ -n "$(git -C "$REPO_DIR" status --porcelain)" ]; then
    echo "error: working tree is dirty; commit before releasing" >&2
    exit 1
fi

echo "==> building signed release"
( cd "$CLIENT_DIR" && "$GRADLE" :app:assembleRelease --console=plain -q )

APK="$CLIENT_DIR/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "error: no APK produced" >&2; exit 1; }

AAPT=$(ls "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | head -1)
read -r VERSION_CODE VERSION_NAME <<<"$(
    "$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" manifest print "$APK" 2>/dev/null \
    | grep -oE 'versionCode="[0-9]+"|versionName="[^"]+"' \
    | sed -E 's/.*="([^"]+)"/\1/' | paste -sd' '
)"

SHA=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c%s "$APK")
COMMIT=$(git -C "$REPO_DIR" rev-parse --short HEAD)
NAME="filmix-ng-$VERSION_NAME.apk"

mkdir -p "$BUILD_DIR"
# Keep only the current release: the repo is not an archive, and each APK is
# ~2.6MB of binary that stays in git history forever.
rm -f "$BUILD_DIR"/filmix-client-*.apk "$BUILD_DIR"/filmix-ng-*.apk
cp "$APK" "$BUILD_DIR/$NAME"

RAW_BASE="https://raw.githubusercontent.com/davidturchak/filmix-rebuild/main/BUILD"
cat > "$BUILD_DIR/latest.json" <<JSON
{
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "commit": "$COMMIT",
  "apkUrl": "$RAW_BASE/$NAME",
  "sizeBytes": $SIZE,
  "sha256": "$SHA",
  "notes": $(python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$NOTES")
}
JSON

echo
echo "published to BUILD/"
echo "  $NAME  ($(numfmt --to=iec "$SIZE"))"
echo "  versionCode=$VERSION_CODE versionName=$VERSION_NAME commit=$COMMIT"
echo
echo "commit and push BUILD/ to make the update visible to clients."

#!/usr/bin/env bash
# Builds a signed release APK, publishes it to BUILD/, and writes the manifest
# the in-app updater reads.
#
#   client/scripts/release.sh            # build from the current commit
#
# Release notes come from CHANGELOG.md, not the command line: write them under
# "## Unreleased" and commit before releasing. The version and build number are
# stamped in by changelog.py once the build reports them.
#
# The manifest is served from raw.githubusercontent.com, so the release is live
# as soon as BUILD/ is committed and pushed — no GitHub Release needed.
set -euo pipefail

while getopts ":h" opt; do
    case $opt in
        *) echo "usage: release.sh   # notes come from CHANGELOG.md" >&2; exit 2 ;;
    esac
done

CLIENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$CLIENT_DIR/.." && pwd)"
BUILD_DIR="$REPO_DIR/BUILD"
CHANGELOG="$REPO_DIR/CHANGELOG.md"

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

python3 "$CLIENT_DIR/scripts/changelog.py" check "$CHANGELOG" || exit 1

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
# Stamps the Unreleased heading with this build's version and returns the
# notes and the full changelog; the tree is dirty afterwards, which is why the
# publish commit carries CHANGELOG.md alongside BUILD/.
NOTES_JSON=$(python3 "$CLIENT_DIR/scripts/changelog.py" release "$CHANGELOG" "$VERSION_NAME" "$VERSION_CODE") || exit 1

python3 - "$BUILD_DIR/latest.json" "$VERSION_CODE" "$VERSION_NAME" "$COMMIT" \
    "$RAW_BASE/$NAME" "$SIZE" "$SHA" "$NOTES_JSON" <<'PY'
import json, sys
target, code, name, commit, url, size, sha, notes_json = sys.argv[1:]
notes = json.loads(notes_json)
manifest = {
    "versionCode": int(code),
    "versionName": name,
    "commit": commit,
    "apkUrl": url,
    "sizeBytes": int(size),
    "sha256": sha,
    # A string, and it must stay one: every client released before the
    # changelog existed parses this key and would fail on an array.
    "notes": notes["notes"],
    "changelog": notes["changelog"],
}
with open(target, "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY

echo
echo "published to BUILD/"
echo "  $NAME  ($(numfmt --to=iec "$SIZE"))"
echo "  versionCode=$VERSION_CODE versionName=$VERSION_NAME commit=$COMMIT"
echo
echo "commit and push BUILD/ to make the update visible to clients."

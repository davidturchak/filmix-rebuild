#!/usr/bin/env bash
# Rebuild a decoded APK and produce an installable, signed artifact.
# apktool b -> zipalign -> apksigner sign (v1+v2+v3) -> apksigner verify
set -euo pipefail

usage() {
    cat >&2 <<'EOF'
usage: rebuild.sh [-d DECODED_DIR] [-o OUTPUT.apk] [-k KEYSTORE] [-p PASS]

  -d  decoded apktool directory   (default: $APKTOOL_OUT or ./apktool_out)
  -o  output apk path             (default: build/<decoded>-signed.apk)
  -k  keystore                    (default: $APK_KEYSTORE or ./test.keystore)
  -p  keystore+key password       (default: $APK_KEYSTORE_PASS or "android")
  -a  key alias                   (default: $APK_KEY_ALIAS or "test")

Generates the keystore on first use if it does not exist.
EOF
    exit 2
}

DECODED="${APKTOOL_OUT:-apktool_out}"
OUT=""
KEYSTORE="${APK_KEYSTORE:-test.keystore}"
STOREPASS="${APK_KEYSTORE_PASS:-android}"
ALIAS="${APK_KEY_ALIAS:-test}"

while getopts ":d:o:k:p:a:h" opt; do
    case $opt in
        d) DECODED="$OPTARG" ;;
        o) OUT="$OPTARG" ;;
        k) KEYSTORE="$OPTARG" ;;
        p) STOREPASS="$OPTARG" ;;
        a) ALIAS="$OPTARG" ;;
        *) usage ;;
    esac
done

[ -d "$DECODED" ] || { echo "error: decoded dir '$DECODED' not found" >&2; exit 1; }

# apktool.jar: explicit env, then a local tools/ copy, then PATH.
if [ -n "${APKTOOL_JAR:-}" ]; then
    APKTOOL=(java -jar "$APKTOOL_JAR")
elif [ -f tools/apktool.jar ]; then
    APKTOOL=(java -jar tools/apktool.jar)
elif command -v apktool >/dev/null 2>&1; then
    APKTOOL=(apktool)
else
    echo "error: apktool not found (set APKTOOL_JAR or place tools/apktool.jar)" >&2
    exit 1
fi

for t in zipalign apksigner; do
    command -v "$t" >/dev/null 2>&1 || { echo "error: '$t' not on PATH" >&2; exit 1; }
done

BASE=$(basename "$DECODED")
BUILD_DIR=build
mkdir -p "$BUILD_DIR"
[ -n "$OUT" ] || OUT="$BUILD_DIR/${BASE}-signed.apk"
RAW="$BUILD_DIR/${BASE}-raw.apk"
ALIGNED="$BUILD_DIR/${BASE}-aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    echo "==> generating keystore $KEYSTORE (debug-quality, not for distribution)"
    keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$STOREPASS" -keypass "$STOREPASS" \
        -dname "CN=APK Mod,O=Local,C=US" >/dev/null
fi

echo "==> apktool build: $DECODED"
"${APKTOOL[@]}" b "$DECODED" -o "$RAW"

echo "==> zipalign"
zipalign -p -f 4 "$RAW" "$ALIGNED"

echo "==> apksigner sign"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$STOREPASS" --key-pass "pass:$STOREPASS" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT" "$ALIGNED"

echo "==> apksigner verify"
# Per-entry "not protected by signature" warnings on META-INF/*.version are
# normal for AndroidX apps and are not failures.
apksigner verify -v "$OUT" 2>/dev/null | grep -E '^(Verifies|Verified using)' || {
    echo "error: verification failed" >&2
    exit 1
}

rm -f "$RAW" "$ALIGNED" "$OUT.idsig"
echo
echo "built: $OUT  ($(du -h "$OUT" | cut -f1))"
echo "install: adb install -r $OUT"
echo "note: signed with your key — uninstall the original first, signatures differ."

#!/usr/bin/env bash
# Resolve Android resource IDs against a decoded APK's public.xml, both ways.
#   resid.sh 0x7f09006a     -> id/app_name
#   resid.sh app_name       -> id/app_name = 0x7f09006a
#   resid.sh -u app_name    -> also list smali sites using that constant
set -uo pipefail

usage() {
    cat >&2 <<'EOF'
usage: resid.sh [-u] [-d DECODED_DIR] <0xHEXID | resource_name>

  -u  also grep smali for uses of the resolved hex constant
  -d  decoded apktool directory (default: $APKTOOL_OUT or ./apktool_out)

  resid.sh 0x7f09006a      hex   -> type/name
  resid.sh app_name        name  -> type/name = hex  (all matching types)
EOF
    exit 2
}

DECODED="${APKTOOL_OUT:-apktool_out}"
SHOW_USES=0
while getopts ":ud:h" opt; do
    case $opt in
        u) SHOW_USES=1 ;;
        d) DECODED="$OPTARG" ;;
        *) usage ;;
    esac
done
shift $((OPTIND - 1))
[ $# -eq 1 ] || usage

QUERY="$1"
PUBLIC="$DECODED/res/values/public.xml"

if [ ! -f "$PUBLIC" ]; then
    echo "error: $PUBLIC not found (decode the APK first, or pass -d)" >&2
    exit 1
fi

# Normalize a hex query to the 0x%08x form public.xml uses.
if [[ "$QUERY" =~ ^(0[xX])?[0-9a-fA-F]{8}$ ]]; then
    hex=$(printf '0x%08x' $((16#${QUERY#0[xX]})))
    match=$(grep -oE "type=\"[a-z]+\" name=\"[^\"]+\" id=\"$hex\"" "$PUBLIC" | head -1)
    if [ -z "$match" ]; then
        echo "no resource with id $hex" >&2
        exit 1
    fi
    type=$(sed -n 's/.*type="\([a-z]*\)".*/\1/p' <<<"$match")
    name=$(sed -n 's/.*name="\([^"]*\)".*/\1/p' <<<"$match")
    echo "$type/$name = $hex"
    resolved_hex="$hex"
else
    # Name lookup: the same name can exist under several types (id, string, ...).
    mapfile -t matches < <(grep -oE "type=\"[a-z]+\" name=\"$QUERY\" id=\"0x[0-9a-f]{8}\"" "$PUBLIC")
    if [ ${#matches[@]} -eq 0 ]; then
        echo "no resource named '$QUERY'" >&2
        exit 1
    fi
    for m in "${matches[@]}"; do
        type=$(sed -n 's/.*type="\([a-z]*\)".*/\1/p' <<<"$m")
        hex=$(sed -n 's/.*id="\([^"]*\)".*/\1/p' <<<"$m")
        echo "$type/$QUERY = $hex"
    done
    resolved_hex=$(sed -n 's/.*id="\([^"]*\)".*/\1/p' <<<"${matches[0]}")
fi

if [ "$SHOW_USES" -eq 1 ]; then
    echo
    echo "smali references to $resolved_hex:"
    # Smali writes the constant with no leading zeros, e.g. 0x7f09006a -> 0x7f09006a,
    # but small ids lose padding; match the unpadded form too.
    short="0x$(printf '%x' $((16#${resolved_hex#0x})))"
    grep -rn --include='*.smali' -E "(^|[ ,])($resolved_hex|$short)$" "$DECODED"/smali* 2>/dev/null \
        | sed "s|^$DECODED/||" | head -50
    echo
    echo "xml references to @$QUERY:"
    grep -rln --include='*.xml' "@\(id\|string\|color\|drawable\|style\|dimen\|layout\)/$QUERY" \
        "$DECODED/res" 2>/dev/null | sed "s|^$DECODED/||" | head -20
fi

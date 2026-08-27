#!/usr/bin/env bash
# Map a piece of UI back to the files that define it.
#   find-ui.sh -t "Настройки"        by visible text
#   find-ui.sh -a SettingsActivity   by activity (-> layout, ids, strings)
#   find-ui.sh -l activity_main      by layout name (-> ids, who inflates it)
set -uo pipefail

usage() {
    cat >&2 <<'EOF'
usage: find-ui.sh [-d DECODED_DIR] (-t TEXT | -a ACTIVITY | -l LAYOUT)

  -t TEXT      find the string resource for visible text, and its users
  -a ACTIVITY  find an activity's layout, resource ids, and theme
  -l LAYOUT    find a layout's ids and the code that inflates it
  -d DIR       decoded apktool directory (default: $APKTOOL_OUT or ./apktool_out)
EOF
    exit 2
}

DECODED="${APKTOOL_OUT:-apktool_out}"
MODE="" ARG=""
while getopts ":t:a:l:d:h" opt; do
    case $opt in
        t) MODE=text;     ARG="$OPTARG" ;;
        a) MODE=activity; ARG="$OPTARG" ;;
        l) MODE=layout;   ARG="$OPTARG" ;;
        d) DECODED="$OPTARG" ;;
        *) usage ;;
    esac
done
[ -n "$MODE" ] || usage
[ -d "$DECODED" ] || { echo "error: '$DECODED' not found" >&2; exit 1; }

SELF_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
resid() { "$SELF_DIR/resid.sh" -d "$DECODED" "$@" 2>/dev/null; }

case $MODE in
text)
    echo "### string resources matching: $ARG"
    grep -rn --include='strings.xml' -F "$ARG" "$DECODED"/res/values* \
        | sed "s|^$DECODED/||" | head -20
    echo
    names=$(grep -rh --include='strings.xml' -F "$ARG" "$DECODED"/res/values* \
        | grep -oE 'name="[^"]+"' | sed 's/name="//;s/"//' | sort -u)
    [ -z "$names" ] && { echo "(no string resource — text may be built in smali)"; \
        echo; echo "### smali literals:"; \
        grep -rn --include='*.smali' -F "\"$ARG\"" "$DECODED"/smali* | sed "s|^$DECODED/||" | head -10; exit 0; }
    for n in $names; do
        echo "### @string/$n"
        resid "$n"
        echo "-- referenced in res/ (layout, menu, xml, ...):"
        grep -rln --include='*.xml' "@string/$n" "$DECODED/res" 2>/dev/null \
            | grep -v '/values' | sed "s|^$DECODED/||" | head -10
        h=$(resid "$n" | grep '^string/' | awk '{print $3}')
        if [ -n "$h" ]; then
            echo "-- referenced in smali:"
            grep -rln --include='*.smali' "$h" "$DECODED"/smali* 2>/dev/null \
                | sed "s|^$DECODED/||" | head -10
        fi
        echo
    done
    ;;

activity)
    smali=$(find "$DECODED"/smali* -name "${ARG}.smali" | head -1)
    [ -n "$smali" ] || { echo "activity '$ARG' not found" >&2; exit 1; }
    echo "### $smali" | sed "s|$DECODED/||"
    echo
    echo "-- theme / manifest entry:"
    grep -oE "<activity[^>]*${ARG}\"[^>]*>" "$DECODED/AndroidManifest.xml" | head -3
    echo
    echo "-- setContentView layout:"
    # apktool interleaves .line markers, so the const can sit well above the
    # invoke: track the most recent constant and emit it when setContentView hits.
    ctx=$(awk '
        match($0, /0x7f[0-9a-f]{6}/) { last = substr($0, RSTART, RLENGTH) }
        /setContentView/ && last { print last }
    ' "$smali" | tail -1)
    if [ -n "$ctx" ]; then resid "$ctx"; else echo "(none found — may inflate a fragment)"; fi
    echo
    echo "-- resource ids referenced (resolved):"
    grep -oE '0x7f[0-9a-f]{6}' "$smali" | sort -u | while read -r h; do
        r=$(resid "$h"); [ -n "$r" ] && echo "   $r"
    done | head -40
    ;;

layout)
    f="$DECODED/res/layout/${ARG}.xml"
    [ -f "$f" ] || { echo "layout '$ARG' not found at $f" >&2; exit 1; }
    echo "### $f" | sed "s|$DECODED/||"
    echo
    echo "-- qualifier variants:"
    find "$DECODED/res" -path '*/layout*' -name "${ARG}.xml" | sed "s|^$DECODED/||"
    echo
    echo "-- ids declared:"
    grep -oE 'android:id="@\+?id/[^"]+"' "$f" | sed 's/.*id\///;s/"//' | sort -u | while read -r n; do
        printf '   %-34s %s\n' "$n" "$(resid "$n" | grep '^id/' | awk '{print $3}')"
    done
    echo
    echo "-- inflated by:"
    lh=$(resid "$ARG" | grep '^layout/' | awk '{print $3}')
    [ -n "$lh" ] && grep -rln --include='*.smali' "$lh" "$DECODED"/smali* | sed "s|^$DECODED/||" | head -10
    ;;
esac

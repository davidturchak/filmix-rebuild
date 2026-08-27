#!/usr/bin/env bash
# Resolves a usable adb target for the tablet, reconnecting if needed.
#
# Wireless-debugging ports rotate whenever the tablet reboots or the debugging
# service restarts, and the connection also drops when the device sleeps. This
# rediscovers the current port over mDNS and reconnects, so callers can run
#   D=$(scripts/adb-target.sh) || exit 1
# before any adb use rather than discovering the drop through a 5-minute hang.
set -uo pipefail

HOST="${ADB_HOST:-192.168.1.162}"

is_ready() {
    [ -n "${1:-}" ] && adb devices 2>/dev/null | grep -q "^$1[[:space:]]*device$"
}

# Already connected and healthy?
for target in $(adb devices 2>/dev/null | awk -v h="$HOST" '$2=="device" && $1 ~ h {print $1}'); do
    echo "$target"
    exit 0
done

# Drop any stale/offline entries before rediscovering.
adb disconnect "$HOST" >/dev/null 2>&1
for stale in $(adb devices 2>/dev/null | awk -v h="$HOST" '$1 ~ h {print $1}'); do
    adb disconnect "$stale" >/dev/null 2>&1
done

PORT=$(timeout 20 avahi-browse -artp 2>/dev/null \
    | grep '_adb-tls-connect' | grep ";$HOST;" | head -1 | awk -F';' '{print $9}')

if [ -z "$PORT" ]; then
    echo "no _adb-tls-connect advertised for $HOST — is wireless debugging on?" >&2
    exit 1
fi

timeout 30 adb connect "$HOST:$PORT" >/dev/null 2>&1
if is_ready "$HOST:$PORT"; then
    echo "$HOST:$PORT"
    exit 0
fi

echo "discovered $HOST:$PORT but it did not come up as 'device'" >&2
exit 1

#!/usr/bin/env bash
# Glass Home on a Fire TV: build the launcher, install it over the running one, and switch the Home watcher
# (HomeKeyService with stock_home_redirect) back on, since Android turns accessibility services off on reinstall.
# It also grants the Now Playing panel its media access (NowPlayingListener), which Fire OS has no screen for, and
# "draw over other apps" for sleep mode's dimming layer and the alarm screen.
# Amazon's launcher stays enabled and stays the system Home; nothing is disabled.
#
# Usage: TV_HOST=<tv ip> launcher/glass-install.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew --no-daemon assembleRelease -q --console=plain
PY=../tools/venv/bin/python
APK=app/build/outputs/apk/release/app-release.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/tvhome.apk
OUT=$("$PY" -I ../tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac
"$PY" -I ../tools/tv.py 'pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null
settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
settings put secure accessibility_enabled 1
cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener
appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow
cmd package compile -m speed -f com.gathin.tvhome >/dev/null
input keyevent WAKEUP
am start -W -n com.gathin.tvhome/.MainActivity | grep -E "Status|Error"'
echo "Installed. Press Home on the remote to see it."

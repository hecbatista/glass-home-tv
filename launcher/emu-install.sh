#!/usr/bin/env bash
# Glass Home on the Android TV emulator: the preview before a change goes to the TV with glass-install.sh.
# Starts the hisense-40 AVD if it is not running (Android TV 12, 1920x1080 at density 320, like the Hisense), installs
# the debug build (no R8, so it builds faster), makes it Home, gives it the same grants as glass-install.sh and opens it.
# Then look at it with: tools/tv.py --target emu --screenshot shots/emu.png [KEY ...]
#
# Usage: launcher/emu-install.sh            (EMU_AVD=<name> for another AVD)
#        EMU_PORT=5556 launcher/emu-install.sh
#                                           a second emulator beside the first, for a session of its own or a
#                                           comparison; it runs read-only (nothing it changes is kept). Pass the
#                                           same EMU_PORT to tools/tv.py --target emu.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
AVD="${EMU_AVD:-hisense-40}"
PORT="${EMU_PORT:-5554}"
export ANDROID_SERIAL="emulator-$PORT"
ADB="$ANDROID_HOME/platform-tools/adb"
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "Starting the $AVD emulator on port $PORT..."
  EXTRA=()
  [ "$PORT" = 5554 ] || EXTRA=(-read-only)   # one AVD can run twice only when the extra copies are read-only
  nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -port "$PORT" "${EXTRA[@]}" -no-boot-anim -no-snapshot-save \
    >/dev/null 2>&1 &
  "$ADB" wait-for-device
fi
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 1; done
./gradlew installDebug -q --console=plain
# Android TV's own launcher outranks set-home-activity on this image, so it is switched off: on the emulator only, which
# is thrown away, never on the TV (there Amazon's launcher stays Home and HomeKeyService redirects).
"$ADB" shell 'pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null
pm disable-user --user 0 com.google.android.tvlauncher >/dev/null 2>&1
cmd package set-home-activity com.gathin.tvhome/.MainActivity >/dev/null
settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
settings put secure accessibility_enabled 1
cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener
appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow
input keyevent WAKEUP
am start -W -n com.gathin.tvhome/.MainActivity | grep -E "Status|Error"'
echo "Installed on $ANDROID_SERIAL."

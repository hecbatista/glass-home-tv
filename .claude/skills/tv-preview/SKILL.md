---
name: tv-preview
description: Preview and check any Glass Home launcher change on the Android TV emulator on this Mac before it goes to the Hisense Fire TV. Use for every change under launcher/ that shows on screen (layout, colours, focus and D-pad, Now Playing, lyrics, alarm, sleep mode, screensaver, a new screen), whenever the user wants to "see", "try", "preview", "mock up" or "screenshot" something, and before any glass-install.sh run. Replaces HTML mockups for this app.
---

# Preview on the emulator first, the TV last

The loop for this fork (branch `glass-home`): change the Java, install the debug build on the emulator,
put the app in the state you need, screenshot, look, fix, and repeat until it is right. Only then go to the TV.
Don't build HTML or artifact mockups of launcher screens: the preview is the real code.

## 1. Install and open it

```bash
launcher/emu-install.sh
```

Starts the `hisense-40` AVD if it isn't running (a visible window the user can use with the Mac's arrow
keys), installs the debug build (no R8, so it's quicker), makes Glass Home the Home app, applies the same
grants as `glass-install.sh` and opens it. It takes about 20 s, or less when Gradle is warm.

## 2. Look and drive it

`tools/tv.py --target emu` takes every `tv.py` command and sends it to the emulator instead of the TV:

```bash
tools/venv/bin/python -I tools/tv.py --target emu --screenshot shots/emu-home.png HOME DPAD_DOWN
tools/venv/bin/python -I tools/tv.py --target emu 'dumpsys activity activities | grep mFocusedApp'
```

Read each screenshot before you say a change works. Check focus from every edge (Up, Down, Left, Right),
as AGENTS.md asks for the TV. Keep screenshots in `shots/`, which git ignores.

## 3. Reach a state

| State | How |
|---|---|
| Home | `HOME` key |
| Now Playing panel + lyrics card | `am broadcast -n com.gathin.tvhome/.DemoMedia --es title "Blinding Lights" --es artist "The Weeknd" --el duration 200040 --el pos 60000`, then `HOME`. Use a real song and its real length: lyrics come from LRCLIB. Extras: `--ez playing false`, `--es art /data/local/tmp/cover.jpg` (push it first), `--ez stop true` to end it. |
| Panel focused | From Home: `DPAD_RIGHT DPAD_RIGHT DPAD_UP` lands on play/pause |
| Full-screen lyrics | Panel focused, then `DPAD_LEFT DPAD_LEFT DPAD_CENTER` (OK on the cover); `BACK` closes it |
| Alarm ringing | `am broadcast -n com.gathin.tvhome/.AlarmTestReceiver --ei in 5 --ei sunrise 0`, wait about 8 s. `--ei sunrise 60` shows the sunrise first. |
| Sleep mode | `am broadcast -n com.gathin.tvhome/.AlarmTestReceiver --ez sleep true` |
| Screensaver (ClockDream) | `settings put secure screensaver_components com.gathin.tvhome/.ClockDream; am start -n com.android.systemui/.Somnambulator` |

`DemoMedia` lives in `launcher/app/src/debug/`, so release APKs never contain it. A new feature that needs
a state the emulator can't produce gets its own debug-only hook there, in the same style: a receiver that
only the shell may call (`WRITE_SECURE_SETTINGS`), registered in `src/debug/AndroidManifest.xml`.

## 4. Then the TV

When the emulator looks right, `TV_HOST=<tv ip> launcher/glass-install.sh` (the R8 release build), then
`tools/venv/bin/python -I tools/tv.py --screenshot shots/tv-after.png ...` and compare. The TV stays the
final word for:

- **Performance:** the emulator runs on an M1 Max, the TV on a slow ARM chip. Judge animation smoothness,
  blur cost and start time only on the TV (`tools/bench.py`).
- **Fire OS:** Amazon's launcher, the Home key redirect through HomeKeyService, Fire OS settings screens.
- **Real Spotify and other players**, and R8-only problems in the release build.
- **Buttons handled by an accessibility helper**, which need a press on the real remote.

## How the emulator differs from the Hisense

Checked side by side on 2026-10-26, with the same commit on both (Home, focus in the top bar, focus in the dock).
Rendering matches: Inter, the clock, the date, the glass pills, the big title, the focus ring and the glow behind
the focused app are at the same pixel positions and look the same. What differs is content, not drawing:

- **The dock.** The emulator has 3 apps (Play Store, Settings, YouTube); the TV has 6 (Netflix, HBO Max,
  YouTube, Prime Video, Stremio, Spotify). The dock sizes tiles by app count, so on the emulator they are larger
  and the dock is taller and narrower. Judge dock spacing and tile size on the TV, or with 6 apps on the emulator.
- **"Last opened" under the title** shows only on the TV, where apps have been used; on the emulator the title
  sits a little lower without it.
- **The Wi-Fi pill** reads `AndroidWifi` and "VPN off" on the emulator; the TV has its real network and
  Tailscale on ("VPN"), which makes the pill a little narrower.
- **The alarm icon** after the date shows only where an alarm is set.

Also: Android TV 12 (API 31) rather than 11, because there is no arm64 Android TV 11 image; 1920x1080 at density
320 like the TV. Android TV's own launcher is disabled on the emulator (it outranks `set-home-activity`), never
on the TV. `EMU_PORT=5556 launcher/emu-install.sh` starts a second, read-only copy for a parallel session (not
yet tried); pass the same `EMU_PORT` to `tools/tv.py --target emu`.

## Setting it up again on a new Mac

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
sdkmanager --install "emulator" "system-images;android-31;android-tv;arm64-v8a"
avdmanager create avd -n hisense-40 -k "system-images;android-31;android-tv;arm64-v8a" -d tv_1080p
```

Then in `~/.android/avd/hisense-40.avd/config.ini` set `hw.keyboard=yes`, `hw.gpu.enabled=yes` and
`hw.gpu.mode=host` (the tv_1080p profile already gives 1920x1080, density 320 and a D-pad).

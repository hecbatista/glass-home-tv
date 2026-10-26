# Alarm, sunrise and sleep mode: design decisions

2026-10-25. Mode: lite (the main session codes). Design agent: apple-design.

Feature, from the user:
- **Alarm** in the style of iPhone StandBy. The TV is fully off overnight (real standby); a Hatch-style sunrise wakes
  it, then gentle generated tones (no Spotify, no account).
- **Sleep mode:** hold ≡ Menu in any app to dim the screen heavily (backlight down, near-black layer) while the
  show or music keeps playing. It stays dim until a button is pressed.

Proven on the TV (2026-10-25):
- An app alarm fires in standby and can turn the screen on.
- A window's screenBrightness drives the LCD backlight, which physically dims, including from an overlay over
  another app.

## Brief

Design agent, turn 1. TV px at 1920×1080. Everything not stated follows `design-system.md`. HIG pages read:
`widgets.md`, `live-activities.md`, `pickers.md`, `playing-audio.md`, `accessibility.md`, `motion.md`,
`feedback.md`, `focus-and-selection.md`, `settings.md`, `toggles.md`. No tvOS pages: TV calls are **judgment**.

**Thesis.** One warm screen that turns the TV into a bedside sunrise: a huge clock that starts as StandBy's dim night
red and ends warm white over a rising glow (the design system's glow, now as a sun). Quiet everywhere else.

### 1. Where it's set: a new screen, "Alarm and Sleep"

Not a section in Customize Home: six controls would double that long scroll, and the alarm changes nightly while
Customize Home holds rarely-changed looks (`settings.md › Best practices`: "Put general, infrequently changed settings
in your custom settings area"). One entry row leads there.

- **Customize Home:** new chevron row pill "Alarm and Sleep" at y 316–412, right under Reorder Apps (24 px gap);
  everything below moves down 120. Its value sits right-aligned 24 px left of the chevron, 34 px Inter SemiBold at 62%
  ink: "7:00 AM", or "Off".
- **The screen** (`GlassSettings` throughout, title 76 px baseline y 150, pills 96 tall, 24 px apart, sections 36 px):

| Order | Element | Words / behaviour |
|---|---|---|
| 1 | Row pill, y 196–292, no chevron | "Alarm: On" / "Alarm: Off" (the Animations toggle pattern). |
| 2 | Row pill, y 316–412 | "Time" at left; value right-aligned at x 1768: "7:00 AM" (100% ink). OK edits it in place, below. |
| 3 | Hint (hint style, 14 px under 2) | On: "Rings in 8 hours 20 minutes. The screen starts to brighten at 6:40 AM." (under an hour: "Rings in 25 minutes"; next ring more than 24 h away: "Rings Monday at 7:00 AM."). Sunrise Off drops the second sentence. Alarm off: "Off. Saving a new time turns it on." |
| 4 | Section "Repeat" + 4 pills | "Every Day", "Weekdays", "Weekends", "Never" (Apple's Clock words). Never turns the alarm off after it rings. |
| 5 | Section "Sunrise" + 4 pills | "Off", "10 min", "20 min", "30 min". Default 20 min. |
| 6 | Section "Sound" + 3 pills | "Chimes", "Harp", "Light Only". Choosing Chimes or Harp plays one phrase as a preview (−10 dB, no loop). |
| 7 | Row pill, chevron, 36 px under 6 | "Test Alarm": StandBy with a 30 s sunrise, then ringing at the real volume policy. Stop returns here. The caption reads "TEST". |
| 8 | Row pill, 36 px under 7 | "Sleep Mode: On" / "Sleep Mode: Off". |
| 9 | Hint | "Hold [menu glyph] for a second in any app to dim the screen. The show keeps playing. Press any button to bring the picture back." Missing helper or overlay permission: "Needs Glass Home's remote helper and the draw-over-apps permission, set once over ADB." |

Inter has no ≡ (U+2261): use Material Symbols Rounded `menu` inline at the hint's cap height. Defaults: Alarm Off,
7:00 AM, Every Day, 20 min, Chimes, Sleep Mode Off. Saving a time turns the alarm On.

**Time picker (D-pad), edited in place** (`pickers.md › Best practices`: "Avoid switching views to show a picker";
"Consider providing less granularity when specifying minutes"):
- OK on the focused Time pill enters edit mode. The pill stays solid white. Its value becomes three segments
  `[7] : [00] [AM]`, each a 64 px-tall capsule (radius 32, 16 px side padding), 34 px Inter SemiBold, tnum. Active
  segment: fill #111216, text #FFFFFF. Inactive: text #111216. Colon #111216 at 62%. Edit starts on the hour.
- Up/Down: hour ±1 (12→1 wraps); minutes ±5 (55→00 wraps, no carry); AM/PM toggles. A held key steps every 120 ms
  after 400 ms. Left/Right move between segments and stop at the ends (no wrap, no leaving the pill).
- OK saves, exits edit mode and turns the alarm On. Back cancels. The hint reads "Up and Down change it. OK saves,
  Back cancels." while editing.
- 24-hour system format: `[07] : [05]`, two segments, hours 00–23.

**Home (recommended):** when the alarm rings within the next 24 h, Material `alarm` at 30 px and 62% white, 24 px after
the date on its cap-height midline. Glyph only: "7:00 AM" would collide with the Wi-Fi module at x 760.

### 2. The StandBy screen

`widgets.md › StandBy and CarPlay`: "make use of the additional space by scaling up and rearranging text so people can
glance at the widget content from a greater distance", and in low light "a monochromatic look with a red tint".

**Layout (fixed across phases, nothing jumps):**
| Element | Spec |
|---|---|
| Clock | Inter Display SemiBold **600 px**, tracking −0.03 em, **proportional digits** (tnum would make "10:00" about 1600 px wide and gappy around "1"; it changes once a minute, centred, so width changes don't jitter). 12-hour without AM/PM, no leading zero ("7:00"); 24-hour as "07:00". Centred on x 960; cap top y 268, baseline y 704 (cap height 436). Widest 12-hour value ("10:00") is about 1430 px. |
| Date caption | Left at x 112, baseline y 160. 40 px Inter SemiBold, caps, +0.08 em: "SATURDAY, OCTOBER 25". |
| Status caption | Right edge x 1808, baseline y 160, same style. Material `alarm` 40 px + 12 px + "7:00 AM". Snoozed: `snooze` + "SNOOZED UNTIL 7:09 AM". Test: `alarm` + "TEST". |
| Pills (ringing, snoozed) | 360×96, radius 48, 34 px Inter SemiBold, top y 836. Ringing: "Snooze" x 588–948, "Stop" x 972–1332. Snoozed: "Stop" alone, x 780–1140. Rest fill **#66000000** (40% black) with #FFFFFF text; focused #F5FFFFFF with #111216. |
| Clock and captions colour | One colour per phase (below), captions at **100%** of it: at 62% the night caption falls to 1.8:1. Size carries the hierarchy (40 against 600). |

The design system's rest pill (12% white) gives white text only 2.6:1 over the full sunrise glow; 40% black gives 7.1:1.

**Background**, two layers on #000000:
1. **Sky:** a vertical linear gradient, top colour at y 0, horizon colour at y 1080.
2. **Sun:** its own View (hardware layer). Radial gradient centred at (960, 1260), radius 1000, three stops: the sun
   colour at 100% alpha at 0, 45% at 0.5, 0% at 1.

**Sunrise keyframes** (p = elapsed / sunrise length; interpolate sRGB between keys with `ArgbEvaluator`):
| p | Sky top | Horizon | Sun | Clock & captions | Clock contrast (mid / baseline) |
|---|---|---|---|---|---|
| 0.00 | #000000 | #0A0100 | #3D0600 | #B81C0C | 3.1 / 3.0 |
| 0.25 | #000000 | #1E0400 | #7A1404 | #D23A1C | 4.0 / 3.5 |
| 0.50 | #050102 | #3A0E04 | #C2400E | #F07A48 | 5.8 / 4.3 |
| 0.75 | #0E0608 | #5A2410 | #F08A30 | #FFC79A | 7.8 / 4.8 |
| 1.00 | #1A1012 | #6E3A1E | #FFC780 | #FFF3E4 | 7.9 / 4.3 |

**Backlight** (window `screenBrightness`): `b(p) = FLOOR + (0.80 − FLOOR) · p^2.2`, FLOOR = 0.0. Check first on the
TV that 0.0 is the panel's dimmest and not off (`BRIGHTNESS_OVERRIDE_OFF` is 0.0f); if the panel blanks, FLOOR = 0.02.
Tick every 2000 ms for both colour and backlight: a 20-minute sunrise moves under 1 sRGB level and under 0.004
backlight per tick, so no step shows. No per-frame animator.

```java
static float backlight(float p) { p = clamp01(p); return FLOOR + (0.80f - FLOOR) * (float) Math.pow(p, 2.2); }
static int key(float p, int[] col) {           // col[i] for P = {0, .25f, .5f, .75f, 1}
    p = clamp01(p); int i = Math.min(3, (int) (p / 0.25f));
    return (int) EVAL.evaluate((p - P[i]) / 0.25f, col[i], col[i + 1]);
}
```

**Phases.**
| Phase | What happens |
|---|---|
| **Sunrise** (T − L → T) | The alarm wakes the TV at T − L. Window black (`windowBackground` #000000) and brightness set **before** `setContentView`, so the panel never flashes. Content fades in from black over 4000 ms, then p runs 0 → 1. No focusable element. Any key except volume reveals the "Stop" pill (focused, x 780–1140). OK on it ends the sunrise and skips **today's** ring only (Repeat continues). Back, or 10 s with no key, hides it again. |
| Sunrise set to Off | At T the screen turns on at p = 1 colours, backlight 0.80, content fading in from black over 2000 ms. |
| Less than L to go when the alarm is saved | The sunrise runs from now to T with the same keyframes. Under 60 s left: no sunrise. |
| **Ringing** (T) | Tones start (section 3). Pills fade in over 240 ms, **focus on Snooze**. Backlight 0.80 → 1.00 over 60 s. The sun breathes with the tone: alpha 0.85 ↔ 1.00, 4000 ms cosine, on the sun View only (`accessibility.md`: "Augment audio cues with visual cues"; it also shows a muted TV is ringing). Reduce Motion: no breathing. |
| Keys while ringing | OK = the focused pill. Left/Right move between pills. **Back, Home, media keys and Up/Down = Snooze**, like iPhone's side button: an accidental press costs 9 minutes, never a missed morning. Stop is always deliberate: Right, then OK. Volume keys pass to the TV. |
| **Snoozed** | 9 minutes. Tones fade out over 600 ms. Snooze pill removed, the Stop pill centres and takes focus. Status caption "SNOOZED UNTIL 7:09 AM". The screen stays at p = 1, backlight 1.00. Back or Home leaves to Home; the alarm still returns at 7:09. At 7:09 it rings again from −15 dB (section 3). |
| **After Stop** | Tones fade out over 600 ms. Crossfade to Home over 400 ms; brightness ramps back to the system value (`BRIGHTNESS_OVERRIDE_NONE`) over 600 ms. Home's Keep Home on rules apply from then. Repeat Never turns the alarm off. Audio focus is abandoned so a paused app can resume (`playing-audio.md`: "Let other apps know when your app finishes playing temporary audio"). |
| **No response** | Ring 10 min, then auto-snooze 9 min; three rings in all (about 48 min). Then tones fade over 4000 ms, the screen holds the clock 60 s, then puts the TV to standby through the helper's lock action if Fire OS allows it (device test). Otherwise it drops keep-screen-on and the TV's own timers take it (5 min to the screensaver, 14 more to standby). Brightness reset either way. |
| TV already on at T − L | No sunrise: something is playing or someone is awake. At T, StandBy rings over the app with `AUDIOFOCUS_GAIN_TRANSIENT`, so the show pauses. |
| Sleep mode on at T − L | Remove the dim overlay and run the sunrise normally. The screen is already dark, so it starts as intended. |

### 3. The tones

48 kHz mono PCM16, one phrase rendered once into an `AudioTrack` (`MODE_STATIC`, looped), `USAGE_ALARM` +
`CONTENT_TYPE_SONIFICATION`. Generated in code: no bundled audio (the APK gate). A fixed noise seed for Harp, so every
morning sounds the same.

- **Chimes:** a rising G-major-pentatonic bell phrase, 4.0 s loop. G4 392.00, C5 523.25, E5 659.26, G5 783.99 at 0,
  0.45, 0.90 and 1.35 s, levels 0.80 / 0.85 / 0.90 / 1.00. Each note sums partials (ratio, level) (1, 1.0), (2, 0.30),
  (3, 0.12), (4.2, 0.06), each decaying `exp(−t·ratio/0.8 s)`. 30 ms linear attack, 2.6 s long, 20 ms release.
  The last tail ends at 3.95 s, so the loop is seamless.
- **Harp:** Karplus–Strong plucks C4 261.63, E4 329.63, G4 392.00, C5 523.25, E5 659.26, G5 783.99, C6 1046.50,
  140 ms apart, 5.0 s loop. Delay length sr/f, seeded with noise averaged over 2 samples (softer), loop filter
  `0.996 · (x[n] + x[n−1]) / 2`, 3.5 s each.
- Both: normalise the phrase to a −3 dBFS peak. No partial above about 4.5 kHz; the fundamentals clear the TV
  speakers' roll-off.
- **Loudness ramp** (track gain, `setVolume` every 250 ms): `dB(t) = −30 + 30 · min(1, t / 120 s)`, so it takes 2 minutes
  to reach full level. Re-ring after snooze: from −15 dB to 0 over 60 s. Stop and Snooze: down to silence over 600 ms,
  then `stop()`.
- **Volume policy.** An alarm must sound even if the TV was muted last night. `playing-audio.md › Silence`: a silenced
  device still plays "alarms", but "Adjust levels automatically when necessary — don't adjust the overall volume". So
  only a floor, never a cut, and only for the ring:
  - at ring start, save STREAM_ALARM's (and STREAM_MUSIC's, if Fire OS aliases them) index and mute state;
  - `ADJUST_UNMUTE`, and raise the index to `ceil(0.30 · max)` if it's lower;
  - never lower a louder setting;
  - restore everything after Stop or give-up (not on snooze). Flags 0: no volume panel on screen.
  The 30% floor is judgment; the user listens.

### 4. Sleep mode

| Item | Spec |
|---|---|
| Trigger | ≡ held **800 ms**. A shorter press still reaches the app. |
| Dim level | Backlight FLOOR (0.0, same device check as the sunrise) **plus** a black layer at **#CC000000** (80%). The show stays faintly visible (about 1–2% of normal), so the TV doesn't look off or crashed. |
| In | Layer 0 → 80% and backlight from the current system value to FLOOR, together over **2000 ms**, decelerate. Backlight ticks every 50 ms. |
| Confirmation | Material `bedtime` 96 px over "Sleep Mode" (40 px Inter SemiBold), 24 px apart, both #CCFFFFFF, centred on (960, 540). Behind them, a radial scrim: #99000000 at the centre to 0 at radius 360, so they read over a bright frame. Fades in 200 ms from t = 0, holds, fades out 1600–2000 ms. That's the only text (`feedback.md`: "confirm that a significant action or task has completed"). Reduce Motion keeps it: it's a fade. |
| Out | The first non-volume key press is **consumed** (down and up) and only wakes the screen, as a dark iPhone's first tap does. In the dark you can't see what OK or Left would do in Netflix. Layer 80% → 0 and backlight → `BRIGHTNESS_OVERRIDE_NONE` over **300 ms**. No label. |
| Volume / mute | Pass through and **don't** wake: turning the sound down at night shouldn't light the room. |
| Keep-screen-on | The overlay holds the screen on only while a media session is PLAYING (Glass Home's `NowPlayingListener`). Netflix and HBO hold it themselves. |
| Show ends / Netflix "Are you still watching?" | Playback stops, so the overlay releases the screen. Recommended: after **5 min** with nothing playing and no key, put the TV to standby through the helper (as with the alarm's give-up), before Fire OS's screensaver can draw a bright slideshow in the dark. Fallback: the TV's own timers. |
| TV sleep timer / screen off | On `ACTION_SCREEN_OFF`, remove the overlay and clear the brightness override, so the TV always wakes normal. |
| Screensaver | If `DREAMING_STARTED` fires while dimmed, check on the TV whether the screensaver draws above the overlay. If it does, the 5-minute standby above is required, not optional. |
| Hold ≡ while dimmed | The press wakes it (first press rule). |

### 5. States
| # | State | How |
|---|---|---|
| 1 | Customize Home with the "Alarm and Sleep" row, focused | on device |
| 2 | Alarm and Sleep screen, alarm On, Chimes chosen, Sleep Mode On | on device |
| 3 | Time pill in edit mode, minutes segment active | on device |
| 4 | StandBy sunrise p = 0 (night red) | on device (hook, compressed sunrise) |
| 5 | StandBy sunrise p = 0.5 | on device |
| 6 | Sunrise with the Stop pill revealed by a key | on device |
| 7 | Ringing, Snooze focused | on device; tones: the user listens |
| 8 | Snoozed, Stop focused, "SNOOZED UNTIL" caption | on device |
| 9 | After Stop → Home with the alarm glyph by the date | on device |
| 10 | Sleep mode confirmation over Home or an app | on device (screenshot shows the overlay; DRM video is black) |
| 11 | Sleep mode steady dim, and the first press only waking | on device: the user judges the backlight by eye and presses the **real** remote |
| 12 | Volume policy on a muted TV | on device, the user listens; the floor and restore maths in a unit test |
| 13 | Keyframes, backlight curve, the 2 s tick, next-trigger maths (DST, Weekdays, Never, a short sunrise) | unit test only |
| 14 | No response: three rings, then standby | unit test for the timeline; on device with compressed timings if the hook allows |
| 15 | Screen off while dimmed: overlay gone, brightness reset | on device (TV off and on); unit test for the receiver |
| 16 | TV already on at T − L: no sunrise, rings over an app, the show pauses | on device |

Eleven are on device and five are unit tests; the tones are on device with the user listening.

## Decisions

## Review

**Round 1 (design agent): fix 1 critical, then ship.** The screens match the brief. Measured on the full-size shots:
the night clock is #BD220F on #140300–#230400 (about 3:1). At ringing the Stop pill's rest fill is #6E5032 under white,
7.4:1. The clock's cap top is y 264 (spec 268), and the badge group is centred on y 540. Snooze has focus on ring,
Stop takes focus when snoozed, and the captions and strings are word for word.

- **Critical — sleep mode can turn the TV off mid-show.** `SleepMode.playing()` reads only media sessions. If
  Netflix or HBO Max publishes no PLAYING session on Fire OS, the TV goes to standby 5 min into the show it was meant
  to keep playing. Fix: `return anySessionPlaying || audioManager.isMusicActive();`. On the TV, also log which
  sessions Netflix and HBO Max publish. Proof: the user's Netflix check, dimmed for at least 7 min.
- **Medium — the time value jumps on entering edit.** At rest "AM" ends at x 1768; in edit it ends at x 1752. The colon
  sits 22 px from "7" but 6 px from the minute capsule. Fix: lay out by the glyphs' ink. "AM" ink right edge at
  x 1768 in both states, 20 px from the minute capsule. The colon is centred between the hour's ink right edge and the
  minute capsule's left edge (equal gaps, about 15 px).
- **Low — the exit after Stop snaps.** `leave()` sets `BRIGHTNESS_OVERRIDE_NONE` at once and uses the system fade.
  Fix: ramp `screenBrightness` from its current value to `SCREEN_BRIGHTNESS / 255f` over 600 ms. Then clear the
  override and fade to Home over 400 ms.
- **Low (code) — fade-out runs twice.** `stop()` starts a second fade-out over the one `endRinging()` already started.
  Pass the volume restore and `leave()` as `endRinging`'s fade callback instead.
- **Missing state 2b.** No shot shows the Test Alarm and Sleep Mode rows, or the hint's inline `menu` glyph. For
  round 2, send one crop of the screen scrolled to the bottom with "Sleep Mode: On" focused.

**Round 2 (design agent): ship.** The critical fix is in. `SleepMode.playing()` returns
`AudioManager.isMusicActive()` after the session loop and `true` without media access, so a show with no session can no
longer trigger standby. The proof is still the user's 7-minute Netflix check. State 2b (`al-13-sleep-row.png`) matches
the brief: Test Alarm has its chevron, "Sleep Mode: On" is focused, the hint is word for word with the `menu` glyph at
cap height, and focusing the row scrolls the hint fully into view. Low #4 is fixed. Medium #2 (segment ink positions)
and low #3 (the 600 ms exit ramp) are deferred: polish, not blockers.

Still open, all on the user's side: wake on first press and holding ≡ (real remote), DRM video under the dim, whether
the screensaver draws above the dim, `GLOBAL_ACTION_LOCK_SCREEN` standby, and the tones and volume (alarm index 3 of 7).

## For the user

- **OK snoozes, Stop needs Right then OK.** Like iPhone, where the side button snoozes. Back, Home and stray keys also
  snooze. Say if you'd rather OK stop it.
- **The alarm forces the volume to at least 30%** and unmutes, only while it rings, then restores yours. Is 30% right
  on this TV?
- **Repeat:** Every Day, Weekdays, Weekends or Never. Default Every Day.
- **After Stop it goes to Home.** The other option keeps the StandBy clock up as a morning clock until a key is
  pressed.
- **Sleep mode:** volume keys change the volume without waking the screen. Every other key wakes it, and that
  first press does nothing else.
- **When the show ends in sleep mode, the TV turns itself off after 5 minutes** (if Fire OS lets the helper do it).
  Music keeps it on for as long as it plays.
- **A small alarm glyph by Home's date** when an alarm is set for the next 24 hours.
- **Deferred polish:** while you edit the time, the value shifts 16 px left and the colon sits closer to the minutes
  than to the hour. After Stop, the brightness drops at once instead of easing down over 600 ms. Say if either
  bothers you on the TV.

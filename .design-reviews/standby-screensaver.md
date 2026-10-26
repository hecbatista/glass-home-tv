# StandBy screensaver: design decisions

2026-10-25. Mode: lite (the main session codes). Design agent: apple-design.

Feature, from the user: Glass Home's screensaver (the Fire OS screensaver after 5 min idle is already Glass Home's
`ClockDream`) becomes a clock in the style of iPhone's StandBy, with the cover of whatever is playing in a corner.
The user wants a faithful preview before anything reaches the TV.

## Brief

Design agent, turn 1. TV px at 1920×1080. Everything not stated follows `design-system.md` and the alarm brief
(`alarm-and-sleep.md` §2). HIG pages read: `widgets.md`, `live-activities.md`, `motion.md`, `accessibility.md`,
`typography.md`, `color.md`, `dark-mode.md`. No tvOS pages: TV calls are **judgment**.

**Thesis.** The bedside clock that knows what's playing: StandBy's huge clock on black, with the song's cover holding
the bottom-left corner. One face, two layouts (with and without music) and two modes (day, night). It moves only
when something changes; burn-in protection is a 1 px orbit nobody sees.

### 0. Same family, never the alarm

| | Alarm (StandBy, `AlarmActivity`) | Screensaver (`ClockScene`) |
|---|---|---|
| Clock | 600 px, cap top 268 | **520 px**, cap top 263 (music) or 351 (clock only) |
| Background | sky gradient + sun | **pure #000000**; day only, a faint cover glow from the corner |
| Top right | status caption (`alarm` 7:00 AM, SNOOZED, TEST) | **empty**: the alarm's tell, never shown here |
| Bottom | Snooze / Stop pills | the Now Playing row, or nothing |
| Day colour | warm #FFF3E4 | neutral #E6E6EA |

Shared, word for word: Inter Display SemiBold, −0.03 em, proportional digits, "h:mm" without AM/PM ("HH:mm" in
24-hour), the date caption's style and place, night red #B81C0C with captions at 100% of it.

### 1. Layout (offset 0; the whole face moves together, see §3)

| Element | Spec |
|---|---|
| Date caption | Left glyph edge x 112, baseline y 160. 40 px Inter SemiBold, caps, +0.08 em: "SATURDAY, OCTOBER 25". Day 62% white (`0x9EFFFFFF`, 7.8:1). |
| Clock | Inter Display SemiBold **520 px**, −0.03 em, proportional digits, centred on x 960. Cap height 378 (Inter 0.7275 em). **With music:** cap top y 263, baseline y 641: 103 px under the date's baseline (160) and 103 px over the cover (744), so it sits exactly between them (rev. at review: 248 left 88 / 118). **Clock only:** cap box centred on y 540: cap top 351, baseline 729. "10:00" is about 1240 px wide (x 340–1580). Day #E6E6EA (16.9:1). Position by cap top and baseline, never by the text box. |
| Cover | **240×240 at x 112–352, y 744–984** (bottom matches the dock band's 985). Radius **20**. No shadow: invisible on black. Day: a 2 px inside stroke at 12% white (`0x1FFFFFFF`) so a black cover keeps its edge (`dark-mode.md › Icons and images`: "an icon that represents a drop of oil might need a slight border to make the edge visible against a dark background"). Non-square art (video apps): centre-crop to square. Decode Spotify's 300 px image, not the 640 (1 GB TV). |
| Title | Left x 384 (cover + 32), baseline y 852. 40 px Inter SemiBold, #E6E6EA. One line, max width 1040 (ends x 1424), ellipsis at the end. **No marquee:** motion all night is wrong for a screensaver; the marquee rule is Home's. |
| Second line | Left x 384, baseline y 900. 32 px Inter Medium, 62% white. Playing: the artist. Paused: the word "Paused". Same 1040 px limit and ellipsis. The title+artist block is centred on the cover's centre (y 864). |
| Source glyph | Only when the song plays on **another device** (Spotify Web API): Material Symbols Rounded, the panel's `sourceIcon()` (`smartphone`, `speaker`, `laptop_mac`…), 32 px, 55% white (`0x8C`), at x 384, centred on the second line's cap-height midline (y 888); the second line's text then starts at x 428 (glyph + 12). **Hidden for a session on this TV**: you are looking at the TV. |
| Title missing | Use the app's label ("YouTube"); hide the second line; title baseline y 878 (cap centre on 864). |
| No cover art | Tile 240×240, radius 20, fill `0x24FFFFFF`, the panel's `ic_np_music` glyph 112 px centred at `0x99FFFFFF` (Home's no-cover tile, scaled). |
| Cover glow (day only) | One radial gradient behind everything, centre (232, 864), radius 900, colour `c = CoverColours.of(cover)[0]`: stops `0x38·c` at 0, `0x14·c` at 0.5, 0 at 1 (the current `ClockScene.setGlow` alphas). No cover: `CoverColours.NONE[0]`. Glow is the design system's signature, here as light spilling from the album. Worst case (a pale cover) the artist line keeps 6.7:1. |
| Top right | Nothing. Not even the alarm glyph: Home already shows it. |

**With no music the clock re-centres** (cap top 263 → 351, 88 px) rather than leaving a hole or putting a
placeholder in the corner. `widgets.md › StandBy and CarPlay`: "make use of the additional space by scaling up and
rearranging text". The move is animated (§3) and happens only when music starts or ends.

### 2. Night mode

No light sensor, so night is a rule. `widgets.md`: in low light StandBy renders "a monochromatic look with a red
tint"; `live-activities.md › StandBy presentation`: "Verify your design in Night Mode".

**When:** sleep mode is on, **or** the time is from 22:00 until the night ends. The night ends when the alarm's
sunrise starts (T − L; T when Sunrise is Off) if the alarm is On and that falls within the next 14 h, else at 07:00.
So a 9:00 weekend alarm keeps the face red until 8:40, and a 5:00 alarm hands the TV to the sunrise at 4:40. An
alarm doesn't start the night; it ends it. Keys `standby_night_start` 1320 and `standby_night_end` 420 (minutes) in
`config.xml`, no UI.

```java
static final int NIGHT_START = 22 * 60, NIGHT_END = 7 * 60;
/** wake: the next sunrise start as minute of day if the alarm is On and it falls within 14 h, else -1. */
static boolean night(int minuteOfDay, boolean sleepMode, int wake) {
    if (sleepMode) return true;
    int end = wake >= 0 && wake < 12 * 60 ? wake : NIGHT_END;
    return minuteOfDay >= NIGHT_START || minuteOfDay < end;
}
```

**Look:** background #000000. Clock, date, title, second line, source glyph, all **#B81C0C at 100%** (3.2:1, the
alarm's p = 0; the design system's monochrome rule: size carries the hierarchy). No glow, no cover stroke. Cover
**tinted, not greyed**: its luma mapped onto black → **60% of #B81C0C** (#6E1107 at most), so even a white cover, 240 px of solid light, stays under the text (rev. at review)
(`widgets.md`: vibrant mode "desaturates text, images"). No-cover tile: fill `0x24B81C0C`, glyph #B81C0C. Paused:
cover at 40% as by day. Fire OS "High contrast text" on: use #D23A1C (4.0:1) for every night element.

```java
/** StandBy's night tint: luma onto black → tint. Android's ColorMatrix works on sRGB values (as SVG with
 *  color-interpolation-filters="sRGB"; the preview must set that, the default is linearRGB). */
static ColorMatrix nightTint(int tint) {
    final float k = 0.60f;   // the cover sits below the text
    float r = k * Color.red(tint) / 255f, g = k * Color.green(tint) / 255f, b = k * Color.blue(tint) / 255f;
    float yr = .2126f, yg = .7152f, yb = .0722f;
    return new ColorMatrix(new float[]{r*yr, r*yg, r*yb, 0, 0,  g*yr, g*yg, g*yb, 0, 0,  b*yr, b*yg, b*yb, 0, 0,
            0, 0, 0, 1, 0});
}   // #B81C0C, k .60: rows .0920 .3097 .0313 / .0140 .0471 .0047 / .0060 .0202 .0020
```

**Backlight** (window `screenBrightness`, set before `setContentView`, `setScreenBright(true)` so Fire OS doesn't
dim on top of it): day **0.50** (judgment; the user tunes it by eye), night **`Sunrise.FLOOR`** (0.0). Device check:
that a dream window's `screenBrightness` drives the backlight as the alarm's overlay did.

**Changes while running:**
- Day → night (22:00, or sleep mode): colours cross over **3000 ms** ease-in-out; the cover's matrix lerps identity →
  tint; glow fades out; backlight 0.50 → FLOOR over the same 3000 ms, 50 ms ticks.
- Night → day (07:00 without an alarm): over **10 min**, the alarm's 2000 ms tick, `p` from 0 to 1, colours via
  `Sunrise.blend(#B81C0C, #E6E6EA, p)`, backlight `FLOOR + (0.50 − FLOOR)·p^2.2`, so a sleeper isn't lit up.

**Sleep mode on:** the night face from the first frame, whatever the hour, at FLOOR. It is about as bright as
sleep mode's dimmed show (the red clock covers about 15% of the screen at L 0.11).

### 3. Motion and burn-in

`motion.md › Best practices`: "Add motion purposefully". The face never jumps around the screen as today's does: a
random jump every minute is exactly the glitch the user dislikes.

- **Drift:** the whole face (date, clock, Now Playing row; not the glow) is offset by integers on a Lissajous orbit
  inside **±24 × ±16 px**, updated **every 10 s**. Peak speed is 0.07 px/s, so each update moves at most 1 px
  (0.5 arcminute at 10 ft, under acuity), and text stays on whole pixels. Random phase per start; the orbit covers
  the box in about 14 h. Margins stay safe: x ≥ 88, bottom ≤ 1000.

```java
static int[] drift(long elapsedMs, double phase) {
    double t = elapsedMs / 1000.0;
    return new int[]{(int) Math.round(24 * Math.sin(2 * Math.PI * t / (37 * 60) + phase)),
                     (int) Math.round(16 * Math.sin(2 * Math.PI * t / (23 * 60) + 1.7 * phase))};
}   // content.setTranslationX(o[0]); content.setTranslationY(o[1]);
```

- **Start:** window black and backlight set first, content fades in **1200 ms**, decelerate.
- **Song change:** hold the new title until its cover arrives, at most **3000 ms** (Spotify sends the title first).
  Then the row fades out **300 ms** (accelerate), swaps, fades in **500 ms** (decelerate). The glow crossfades
  **600 ms** with the fade-in (two layers, never a per-frame gradient redraw). Text is replaced only when it changes.
- **Music starts:** the clock slides up 88 px over **800 ms**, cubic-bezier(.2, .8, .2, 1); the row fades in over
  500 ms from t = 300 ms. **Music ends:** the row fades out 400 ms, then the clock slides down 800 ms.
  (`live-activities.md › Animate layout changes`: "animating existing elements to their new positions".)
- **"Nothing playing"** means no session and no remote playback for **15 s** (rides out a failed poll), or paused for
  **10 min**. Until then the row stays.
- **Pause / resume:** the cover's alpha 1 ↔ 0.40 over 400 ms; the second line swaps artist ↔ "Paused".
- **Reduce Motion** (`reduced_motion` or the animations switch): the clock's slide becomes a crossfade (out 200 ms,
  move, in 300 ms). Fades stay. The drift stays: it is invisible and it is protection, not animation.

### 4. Interaction

Any key ends it, consumed, as Fire OS does today (`setInteractive(false)`), with two exceptions in
`ClockDream.dispatchKeyEvent` and `ClockActivity`:
- **Volume up/down/mute:** not consumed and the screensaver stays (return false). Turning music down at night
  shouldn't light the room. Device check: that Fire OS changes the volume from a dream window.
- **Play/pause, next, previous:** act on the shown song and the screensaver stays: the session's transport controls
  for a TV session, the panel's Spotify calls for another device. Show the new state at once (optimistic), and
  correct it on the next poll.
- **Sleep mode on:** the waking key also calls `SleepMode.clear()`, so one press brings the picture back (sleep
  mode's "first press only wakes" rule, applied once, not twice).

No custom fade on exit from the dream: API 30 has no `onWakeUp()`; Fire OS's own wake is used. `ClockActivity` keeps
its fade.

### 5. Edge cases

| Case | Behaviour |
|---|---|
| Long title or artist | One line, ellipsis at x 1424. Never marquee, never a smaller size. |
| No cover art | The tile (§1); night-tinted at night. Glow from `CoverColours.NONE[0]`. |
| Song changes | §3: hold ≤ 3 s for the cover, row out 300 / in 500, glow 600. |
| Alarm rings while dreaming | The dream listens for the alarm's ring and ends itself first; `AlarmActivity` then fades in from black as specified. |
| Night face at T − L | **Recommended:** count a night-face dream as "TV off", so the sunrise runs (the alarm brief otherwise skips it when the TV is on). Night red → black 800 ms → the sunrise at p = 0. Needs the user's yes (below). |
| Sleep mode on | Night face at FLOOR from the first frame; one key wakes both. |
| Spotify on a phone | Source glyph `smartphone`; Web API data on the 5 s poll; up to 5 s late is fine. |
| Spotify on this TV | A local session: no glyph. If both report, `NowPlayingChoice` picks, as on Home. |
| A paused show (Netflix art) | Shown like a song, centre-cropped; gone 10 min after pausing. |
| No Now Playing access | Clock only. |
| 24-hour clock | "07:00"; same positions. |

### 6. States

| # | State | How |
|---|---|---|
| 1 | Day, song on this TV, playing | previewable in HTML |
| 2 | Day, Spotify on a phone (`smartphone` glyph) | previewable in HTML |
| 3 | Day, paused ("Paused", cover 40%) | previewable in HTML |
| 4 | Day, no music: clock re-centred | previewable in HTML |
| 5 | Day, no cover art (tile) | previewable in HTML |
| 6 | Day, long title and artist (ellipsis) | previewable in HTML |
| 7 | Night, playing, tinted cover | previewable in HTML |
| 8 | Night, no music | previewable in HTML |
| 9 | Song change (row out and in, glow crossfade) | previewable in HTML, then on device |
| 10 | Music starts and ends (clock slide), and the Reduce Motion crossfade | previewable in HTML, then on device |
| 11 | Day → night at 22:00 (3 s) | previewable in HTML (colours); backlight on device |
| 12 | Backlight day 0.50, night FLOOR, from the dream window | on device (the user's eye) |
| 13 | Sleep mode on: night face at FLOOR, one key wakes both | on device |
| 14 | Volume passes; play/pause works without waking | on device, real remote |
| 15 | Alarm while dreaming; night face at T − L → sunrise | on device (Test Alarm with a compressed hook) |
| 16 | `ClockActivity` (Home idle) shows the same face | on device |
| 17 | `night()` (DST, Weekdays, a 5:00 and a 9:00 alarm, Sunrise Off), `drift()` (max step ≤ 1 px over 24 h), the 15 s and 10 min holds | unit test only |

### 7. What the preview must show

- A 1920×1080 stage on #000000, scaled to fit, with a toggle for guides: x 112 / 384 / 1424 / 1808, y 160, 263, 641,
  744, 984 (and 351 / 729 for clock only).
- Inter from Google Fonts with `font-variation-settings: "opsz" 32` for Inter Display; Material Symbols Rounded.
- Controls: Day / Night; Playing / Paused / No music; source This TV / Phone; Cover / No cover / Long title;
  "Next song" (runs state 9); "Start / stop music" (state 10); Reduce Motion; "Go to 22:00" (state 11, 3 s); drift at
  ×600 speed to show the orbit; 12 / 24-hour.
- **Compare:** the alarm's night (p = 0) and ringing (p = 1, pills) frames, from the alarm's keyframes, so the user
  judges "same family, not the alarm" side by side.
- Made-up covers (gradients or simple SVG, one near-white, one near-black) and made-up names: no real album art.
- The night tint as an SVG `feColorMatrix` with the rows above and `color-interpolation-filters="sRGB"`.
- One 1920×1080 shot per previewable state (1–8, plus a mid-frame of 9, 10 and 11).

### Calls the user should confirm
- The night face at the alarm's sunrise start hands over to the sunrise (recommended), rather than skipping it.
- Day backlight 0.50.
- Night hours 22:00–07:00, ended early or late by the alarm.
- Volume and play/pause work without ending the screensaver; every other key ends it.

## Decisions

## Review

**Preview, round 1 (design agent): ship after fixes 1–2 in the Java. Fixes 3–5 are for the preview only. No re-review
needed.** Measured on the full-size shots, after removing each frame's drift (worked out from the date's and the
clock's shared offset):
- Positions match: date glyph edge 112 and cap top 131, title cap top 823, artist baseline 900, clock cap top 248 or 351.
- Colours match: clock #E6E6EA, night #B81C0C, artist about #9D9693 over the glow.
- The ellipses, the paused cover at 40%, the phone glyph and the tile all follow the brief. It reads as StandBy, and
  next to the alarm frames it isn't the alarm.

1. **Medium (my arithmetic, brief corrected): the clock sits too close to the date.** With music the gaps were 88 px
   (date baseline 160 → cap 248) and 118 px (baseline 626 → cover 744). New values: cap top **263**, baseline
   **641**, which gives 103 / 103. The re-centre slide becomes **88 px**.
2. **Medium (brief changed): at night a white cover is a 240 px solid #B81C0C block,** the largest light on screen.
   Scale the tint by **0.60**, so the cover is at most #6E1107 (matrix rows in §2). With the TV build, send a night
   shot with the near-white "Paper Moon" cover.
3. **Medium (preview): Reduce Motion jumps.** `render()` moves the clock at t = 0 while it is still visible. Move it
   at 200 ms, when opacity is 0, then fade in over 300 ms. On stop, do this after the row's 400 ms fade-out. Don't
   copy this order into the Java.
4. **Low (preview): the glow's colour snaps on a song change.** Crossfade `glowA` and `glowB` over 600 ms with the
   row's fade-in. `glowB` is unused.
5. **Low (shots): every frame is shifted 8 px.** An 8 px #0E0E11 page border shows at the top and left, and the
   stage's right and bottom 8 px are cut off. Clip the screenshot to the stage, and set drift to 0 for review shots.
- **Low, check on the TV:** the long artist ends "and …" with a space before the ellipsis. Android should end it
  "and…"; if it doesn't, trim trailing spaces before ellipsizing.

**Java build, round 1 (design agent, emulator shots): ship after fix 1. Fixes 2–5 are polish. No second round
needed.** Measured on the full-size shots, after removing drift (day +21/+16, night +24/+5):
- Clock: cap top 263 and baseline 641 (overshoot ±5).
- Date: cap top 131. Title: cap top 823. Second line: baseline 900. Cover: x 112, y 744.
- Colours: clock #E6E6EA, night #B81C0C, the night cover's sun at most #5F0F06 (k 0.60 works).
- Code: the feed only publishes on a change (`sameAs`), so the 10-min paused hide can't flap. The 3 s cover wait,
  the 15 s ride-out, the alarm hooks and the keys match the brief.

1. **Medium: the glow switches on and off with no fade.** `showMusic()` → `paint()` sets `glowA` alpha from 0 to 1 in
   one frame while the row fades in over 500 ms, and back to 0 while it fades out over 400 ms. Fix: an animated
   `glowLevel` that follows the row (0 → 1 over 500 ms from t = 300 ms; 1 → 0 over 400 ms). Then
   `paint()`: `glowA.setAlpha((1 − night) · glowLevel · (playing ? 1 : 0.5))`.
2. **Low: paused glow at 50%.** With the near-white cover at 40%, the full glow (#2A2929 beside it) outshines the
   cover it comes from. Use the 0.5 factor in fix 1.
3. **Low: pause and resume snap.** `fill()` sets the cover's alpha at once. The brief says 400 ms:
   `cover.animate().alpha(t.playing ? 1f : .40f).setDuration(400)`.
4. **Low: the date sits 3–4 px right of x 112.** The text box is at 112, but the design-system rule measures the
   glyph's ink, so "M" lands at 115–116. Fix: `leftMargin = px(112) − bounds.left` of the first letter
   (`getTextBounds`), redone when the date changes. Do the same for the title.
5. **Low (code): `paint()` creates a new stroke drawable every call,** about 180 times in the 3 s change. Make one
   in the constructor and only change its colour.
- **On the TV:**
  - In an interactive dream, `finish()` must wake to the app underneath, not put the TV to sleep (Fire OS 8 is not
    the Android 12 emulator).
  - Volume passes through.
  - Backlight: 0.50 by day, FLOOR at night.
  - The 3 s day-to-night change.
  - The white "Paper Moon" cover at night.
  - The no-music shot, centred: cap top 351.

## For the user

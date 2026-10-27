# Night Clock (StandBy on demand): design decisions

2026-10-27. Mode: lite (the orchestrator builds). Design agent: apple-design.

Feature, from the user: a control on Glass Home that starts the StandBy clock now, before bed: the red night face
whatever the time, with the playing song's cover if music plays. It does not turn the TV off.

## Brief

Design agent, turn 1. TV px at 1920×1080 (1 dp = 2 px). Everything not stated follows `design-system.md`, the StandBy
brief (`standby-screensaver.md`) and sleep mode (`alarm-and-sleep.md` §4). HIG pages read: `designing-for-tvos.md`,
`remotes.md`, `focus-and-selection.md`, `buttons.md`, `feedback.md`, `accessibility.md`, `motion.md`. Fire OS calls are
**judgment**.

**Thesis.** The clock is the button. Select Home's clock and it becomes the night clock: no new glass and no new glyph.
The small clock turns into the big one.

### 1. Placement: Home's clock, not a fourth round button

Measured on `shots/tv-after-playing.png`: the date's alarm glyph ends at x 697 and the Wi-Fi module starts at x 810, so
the gap is 113 px. A two-digit hour ("12:58") widens the clock by about 60 px, which leaves about 53 px. A round button
needs 152 px (128 plus a 24 px gap). Next to Customize Home it would push Wi-Fi into the date. It also breaks "Add no
more glass surfaces". `focus-and-selection.md › TV`: "make sure the larger item doesn't crowd the surrounding
interface." So the clock group (time and AM/PM, id `leftBlock`'s time part) becomes focusable, and only that part.

- **Reach:** the clock is the top bar's first stop in reading order. Left from the first top-bar button (Wi-Fi, or
  Inputs when shown) focuses it. On the clock, Right goes back to that button, and Left and Up do nothing (consumed).
  Down does what Down does on the top bar: the Now Playing panel if shown, else the dock tile Up came from. Up from the
  dock still lands on the first button, not the clock. From the dock that is 2 presses (Up, Left). RTL mirrors the
  order, as `rtl_menu_bar` already does. (`remotes.md › Best practices`: "always moving focus in the same direction
  as the gesture".)
- **Offered when:** Glass Home's Control Center bar is up (`glassHome && !topMenu && !festive`) **and**
  `standby_clock` is on. Otherwise the clock stays unfocusable, as today.
- **Always, day and night** (judgment). A focus stop that appears only after 21:00 would make the D-pad behave
  differently by the hour. People nap, and the user asked for "whatever the time". `accessibility.md › Cognitive`:
  "navigate your interface using easy-to-remember and consistent interactions".

### 2. Focused look (at rest the clock is unchanged: bare type, no surface)

| Element | Spec |
|---|---|
| Pill | Behind the time only. x from the clock's first ink edge − 32 (**x 80**) to the AM/PM ink right edge + 32 (the last digit + 32 in 24-hour). y **86–214** (128 px, the modules' line), radius **64**. Fill **#F5FFFFFF**; on Light **#111216**. It is a focus fill, not glass, and it only shows while focused. |
| Time and AM/PM | **#111216** on the pill (white on Light). Positions unchanged: ink at x 112, baseline 185. |
| Divider (the hairline at about x 382) | Alpha → 0 while focused. The pill's edge now separates the time from the date. At "1:03 AM" the pill's right edge (about 380) would touch it. |
| Date and alarm glyph | Unchanged: outside the pill, at rest colours. They move with the clock's width, as they do today. |
| Caption | **"Night Clock"**, 26 px Inter SemiBold (13 sp, `includeFontPadding` false), ink at 100%. Text-box top at **y 240** (pill bottom + 26). Left ink edge at **x 112**. This matches Customize Home's label (`addAppearanceLabel`), left-aligned because the clock sits at the left end. Not read by accessibility (`IMPORTANT_FOR_ACCESSIBILITY_NO`). |
| Scale | **None** (no ×1.04, even with `advancedEffects`). Rescaling 96 px type wobbles its rasterising, and the pill is enough. Judgment, and `focus-and-selection.md › TV` on crowding. |
| Accessibility | Role button. Content description "Night Clock, " plus the shown time ("Night Clock, 1:03 AM"), set when focus arrives. |

Contrast: #111216 on #F5FFFFFF is about 17:1. The caption, white on #0B0B0F, is about 19:1. Both are measured from the
values, not from a JPEG.

**No glyph.** The time is the button and the caption names it. `ic_bedtime` stays sleep mode's mark.
**Words:** `night_clock` = "Night Clock", in `values/strings.xml` and every `values-*` folder. Not "StandBy", because
on a TV "standby" means off, and the user said this does not turn the TV off. A noun, not a verb, because it names what
appears, and the white pill already says "press". This is judgment against `buttons.md › Content` ("consider starting
the label with a verb").

### 3. Motion

- Focus in or out: the pill's alpha 0 ↔ 1, the text colour (ArgbEvaluator), the divider's alpha and the caption's
  alpha all change together over **160 ms**, decelerate. Reduce Motion or animations off: snap, as Customize Home's
  label does.
- **OK:** no press state beyond the transition, which starts at once. Then:
  1. Set the forced night (§4).
  2. `startActivity(ClockActivity)` with `overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)`,
     the pair its `finish()` already uses. ClockActivity's black window fades in over Home.
  3. The backlight goes to `Sunrise.FLOOR` only when the screen is black (`onEnterAnimationComplete`). A step in
     brightness during Home's fade would read as a jump.
  4. The face fades in over 1200 ms (`StandbyScene.start`, unchanged), already red.
- **No confirmation.** The red face is the confirmation. `feedback.md › Best practices`: "people typically expect
  their action or task to succeed, they only need to know when it doesn't". Sleep mode needed its badge because a dim
  over a show can look like a fault; a full-screen red clock can't.

### 4. How long the night lasts

**Until the next 07:00 (`standby_night_end`) after the press, then the normal rule.** So a forced night ends exactly
where an ordinary night would, never earlier:
- 9:00 alarm: red until its sunrise at 8:40.
- 5:00 alarm: `showingNight()` is true at 4:40, so the sunrise runs and ends StandBy.
- No alarm: at 07:00 the existing 10-minute morning rise.
- A press at 15:00 stays red until 07:00 tomorrow. A press at 06:30 rises at 07:00.

It is held in memory only (fails safe to the normal rule) and **cleared** by:
- a key that ends StandBy, from ClockActivity or ClockDream;
- `ACTION_SCREEN_OFF`;
- its own end time.

**It survives the Fire OS screensaver.** ClockActivity holds no screen-on flag, so after the TV's idle time the system
starts `ClockDream`, and ClockActivity finishes in `onPause`. The dream must show the same red face. If a StandBy face
was showing within the last 2 s, the dream also shows it at once, with no 1200 ms fade and the same drift phase and
start time. Otherwise the clock blinks off and jumps up to 24 px in a dark bedroom.

**Suggested implementation** (`StandbyFace`, pure and tested; `StandbyScene` reads it):

```java
/** Night Clock: the night face until this time (epoch ms), 0 when off. Memory only. */
static long nightUntil;
/** The next standby_night_end after now: 06:30 → 07:00 today, 07:00 or later → 07:00 tomorrow. */
static long nightEndAfter(long now, java.util.TimeZone zone, int endMinute) {
    java.util.Calendar c = java.util.Calendar.getInstance(zone);
    c.setTimeInMillis(now);
    c.set(java.util.Calendar.HOUR_OF_DAY, endMinute / 60); c.set(java.util.Calendar.MINUTE, endMinute % 60);
    c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
    if (c.getTimeInMillis() <= now) c.add(java.util.Calendar.DAY_OF_MONTH, 1);
    return c.getTimeInMillis();
}
// StandbyScene.isNight(): if (System.currentTimeMillis() < StandbyFace.nightUntil) return true; then today's rule.
```

### 5. Keys on the Night Clock

The same as the idle StandBy (`StandbyKeys`):
- Volume passes through.
- Media keys act on the song.
- Any other key ends it, back to Home with the existing fade, and **focus returns to the clock**. Back opens the parent
  screen (`remotes.md › Best practices`), and Home is the parent.

One guard: end only on an `ACTION_UP` whose `ACTION_DOWN` this window received, so a stray up from the OK that started
it can never close it.

### 6. Edge cases

| Case | Behaviour |
|---|---|
| Sleep mode on at Home | The first press only wakes (sleep mode's rule), so the OK that reaches the clock comes with sleep mode off. |
| Hold ≡ on the Night Clock | It ends, like any key, and sleep mode dims Home as it would from Home. |
| Alarm rings or snooze ends | `StandbyScene.endCurrent()` first, then AlarmActivity, as today. |
| TV's sleep timer | It turns the TV off as it does today. Screen off clears the forced night. |
| Music starts or stops while it shows | The existing slide and row fade, in red. |
| 24-hour clock | The pill ends 32 px after the last digit. |

### 7. States

| # | State | How |
|---|---|---|
| 1 | Home at rest: clock unchanged, no pill | on device (emulator) |
| 2 | Clock focused, Dark: pill, ink time, divider gone, "Night Clock" caption | on device |
| 3 | Clock focused, Light background | on device |
| 4 | Clock focused at "12:58 PM" with the alarm glyph shown | on device (emulator time set) |
| 5 | D-pad: Left from Wi-Fi to the clock, Right back, Down to Now Playing or the dock, Up and Left stay | on device |
| 6 | OK at 15:00 with music (DemoMedia): red face, tinted cover | on device |
| 7 | OK at 15:00, no music: red face, clock centred | on device |
| 8 | Any key returns to Home with focus on the clock | on device |
| 9 | 24-hour clock focused | on device |
| 10 | Volume passes; play/pause and next act without ending it | real TV, real remote |
| 11 | Backlight at FLOOR, with no step while Home fades | real TV (the user's eye) |
| 12 | Fire OS screensaver after the idle time keeps the red face, with no blink or jump | real TV |
| 13 | `nightEndAfter` (06:30, 07:00 exactly, 07:30, 23:00, both DST changes), forced night combined with the rule (5:00 and 9:00 alarms, no alarm), cleared by a key and by screen off, the key-up guard | unit test only |
| 14 | Alarm sunrise during a forced night: `showingNight()` true, so the sunrise runs | unit test; on device with Test Alarm |

Nine on the emulator, three on the real TV only, one unit test only, and one both.

## Decisions

## Review

**Round 1 (design agent, emulator shots): ship. No fixes needed.** I measured the 1000×300 crops assuming their
origin is (40, 40), which I worked out from the Wi-Fi module's top and bottom at y 86–214. Treat the positions as
estimates within ±2 px.

| What | Brief | Shots |
|---|---|---|
| Pill, dark | x 80–AM + 32, y 86–214, radius 64 | x 79–368 (AM ink ends at 337), y 86–214, fully round. Left gap 31, right gap 31. |
| Caption | ink at x 112, box top y 240 | ink at x 112, box top about 240 |
| Divider while focused | gone | gone |
| Date | unchanged | unchanged at x 406, 38 px clear of the pill |
| Light and 24-hour | inverted; pill ends at last digit + 32 | inverted; 24-hour pill ends at last digit + 30 |

- **D-pad and return:** Left and Up stay on the clock, Right goes back to Wi-Fi, and Down goes to Now Playing as the
  top bar's Down does. After a key, focus is back on the clock.
- **Night face:** red at 14:59 with and without music. The tinted cover sits well under the text.
- **Screensaver handoff:** ClockActivity and ClockDream give identical frames, with no fade and no drift jump.
- **The Home key going to the first tile** is right: Home means "start over".

Low, no re-review:
1. In 24-hour the content description ends in a space ("Night Clock, 15:01 "). Trim it, or add AM/PM only when it is
   shown.
2. `night_clock` exists only in `values/`. That matches the other Glass Home strings ("Customize Home" is untranslated
   too), so English is the fallback. An upstream PR would need every locale.

Still open, all on the TV:
- state 4 (12:58 with the alarm glyph);
- states 10 and 11 (volume and media keys; the backlight with no step during Home's fade);
- state 12 with Fire OS's real idle timer;
- state 14 (Test Alarm during a forced night). The combined alarm rule has no separate test. It goes through the
  unchanged `night()` path, so that is acceptable.

## For the user

- **The clock is the control, not a moon button in the top bar.** The bar has no room: about 53 px free at
  "12:58" against the 152 px a fourth round button needs. A visible moon button would fit only if Settings lost
  "Display, sound, apps", which you chose. Not recommended.
- **How long it stays on:** red until 07:00 (or your alarm's sunrise), but the TV's own timers still turn it off as they
  do today (about 19 minutes after the last press, by the alarm brief's numbers). If you want the night clock on all
  night, ClockActivity would hold the screen on until the night ends. That is one line, but your call.
- **Not from this feature:** a long Wi-Fi name can widen the Wi-Fi module up to 640 px, into the date. The pill wraps
  only the time, so this feature adds no collision.

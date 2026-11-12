# StandBy lyrics: design decisions

2026-10-27. Mode: lite (the orchestrator builds). Design agent: apple-design.

Feature, from the user: "add a adjustable setting to have one line of a lyric appear at a time while a song is
playing in standby mode in the space in the bottom between the right edge and the song".

## Brief

Design agent, turn 1. TV px at 1920×1080 (1 dp = 2 px). Everything not stated follows `design-system.md` and the
StandBy brief (`standby-screensaver.md` §1–4). It applies to every StandBy face: the screensaver, Home's idle clock
and the Night Clock. HIG pages read: `motion.md`, `accessibility.md`, `settings.md`. TV
calls are **judgment**.

**Thesis.** The line being sung, quietly, in the row's empty right half. It is the only thing on the face that changes
every few seconds, so it changes as softly as anything here: a fade in place, with no slide and no marquee.

### 1. Layout: two columns in the bottom row

The text beside the cover becomes two columns, but only for a song with synced lyrics while the switch is on (§4).

| Element | Spec |
|---|---|
| Song column | Title and second line keep x 384 and baselines 852 and 900. Their max width drops from 1040 to **560** (ending x 944), with an ellipsis at the end as now. |
| Lyric column | Left at **x 1008** (64 px after the song column), right at **x 1808** (the edge): 800 px. With drift the edge reaches at most x 1832, inside the 60 px margin. |
| Lyric type | **40 px Inter SemiBold**, the title's style. Tracking −0.01 em, proportional digits. Up to **2 lines**, wrapped at word breaks, with an ellipsis at the end of line 2. Never smaller, never a marquee. |
| Lyric lines | They share the row's baselines: line 1 at **y 852** (the title's), line 2 at **y 900** (the second line's), so line spacing is 48 px. The block hangs from line 1 and does not re-centre, so a one-line lyric sits level with the title. |
| Alignment | Start-aligned in its column (x 1008). `TEXT_DIRECTION_FIRST_STRONG`, so a Hebrew or Arabic line starts from the column's right edge. |
| Colour | Day **#E6E6EA** (the title's, 16.9:1). Night **#B81C0C** at 100% (about 3.2:1, the night rule's value; 40 px qualifies as large text). Both follow `paint()`'s day-to-night blend. Fire OS "High contrast text": #D23A1C. |
| Accessibility | Not announced (`IMPORTANT_FOR_ACCESSIBILITY_NO`), like the panel's lyrics card. |

The row view widens from 1312 to 1696 px (x 112–1808). The lyric is part of the face, so it drifts with it.

40 px at full ink puts the lyric level with the title. That is deliberate: while a line is sung it *is* the live
content, and the column keeps it from being mistaken for the title. A 60-character line is about 1300 px at 40 px, so
2 lines in 800 px fit nearly every LRCLIB line.

### 2. When a line shows, and when nothing does

| Moment | The lyric column |
|---|---|
| A line plays | It shows, from **150 ms before** its stamp (`indexAt(position + 150)`), so its fade-in starts on the beat. |
| A line has played for **10 s** and the next is further off | It fades out (an instrumental break with no empty line in the LRC). |
| An empty LRC line (a marked break) | Empty. |
| Before the first line (intro, `indexAt` −1) | **Empty.** No music note: a glyph that comes and goes would be motion with no information. The song column already says what is playing. |
| Paused | The line fades out over 400 ms, with the cover's dim. It comes back at the current line on resume. A frozen line is stale, and at night 40% red would be unreadable. |
| No synced lyrics, plain lyrics only, not found, or no lookup | **No column:** the title and second line keep their full 1040 px. |
| Music ends | It goes with the row (the existing 400 ms fade). |

**Deciding the columns without a jump.** The row's swap already waits up to 3 s for the cover (§3 of the StandBy
brief). It now waits for the lyrics lookup's answer in the same window. The panel's cache usually answers at once.
- Synced lyrics known at the swap: two columns.
- No lyrics, or no answer within 3 s: one column.
- Lyrics that arrive later are used only if the title's and second line's ink already ends by x 944. Otherwise that
  song shows no lyrics. A title must never re-ellipsize in front of the user.

**Remote Spotify (5 s poll, interpolated):** step back a line only for a jump of more than 2 s (a seek). A smaller
correction keeps the shown line, so nothing steps backwards (design system, Motion).

### 3. Motion

- **Line change:** out over **150 ms** (accelerate), then the new line in over **250 ms** (decelerate), in place.
  They run one after the other, never as a crossfade, so two lines never overlap. No slide.
  `motion.md › Providing feedback`: "generally avoid adding motion to UI interactions that occur frequently". That
  sentence is about interactions, but the principle holds here: this changes every few seconds, so it gets the least
  motion that still reads as a change (judgment).
- **Line appears after silence, or disappears:** the same 250 ms in and 150 ms out.
- **Reduce Motion:** unchanged; these are fades. (`accessibility.md › Cognitive` asks for fewer zooms and slides, and
  replaces them with fades.)
- Text is replaced only when the line index changes, never on a position update.

### 4. The setting

- **Where:** the **Alarm and Sleep** screen, as its last row, after Sleep Mode's hint (after Test Alarm when Sleep Mode
  is hidden). That screen is where StandBy's night and bedtime things live. Customize Home holds Home's look and is
  already long. This is judgment. `settings.md`: "Put general, infrequently changed settings in your custom settings
  area".
- **Row:** a standard row pill, **"Screensaver Lyrics: Off"** / **"Screensaver Lyrics: On"**. OK toggles it. It uses
  the `alarm_state` and `sleep_mode_state` pattern: `standby_lyrics_state` = "Screensaver Lyrics: %1$s" with
  `R.string.on`/`off`. Row gap 18 px, as Sleep Mode's.
- **Hint** (the screen's hint style, 28 px): **"Shows the line being sung beside the song, on the screensaver and the
  Night Clock."**
- **Words:** "Screensaver", not "StandBy". On a TV "standby" means off (the Night Clock brief), and Fire OS calls this
  face the screensaver.
- **Default: Off.** It is a new behaviour, and a line changing every few seconds by a bed should be chosen, not
  found.
- **Shown only when** `lyrics` and `standby_clock` are on. Pref key `standby_lyrics`, with a `config.xml` key of the
  same name defaulting to false that a theme can set (the off-by-default switch recipe).
- **"Adjustable" means On and Off, nothing more.** Size or position choices would let the user break the row's grid.
  Night is already handled: red, at the dimmest backlight. If lines at night prove distracting, the one extra choice
  worth adding is **Off / Day Only / Always**. Not now (For the user).

### 5. States

| # | State | How |
|---|---|---|
| 1 | Day, playing, a one-line lyric ("Blinding Lights"), song column at 560 | on device (emulator, DemoMedia) |
| 2 | Day, a two-line lyric, and one long enough to ellipsize on line 2 | on device (a long line, or a test hook) |
| 3 | Night (Night Clock), playing, lyric in red | on device |
| 4 | A line change, mid-fade (out, then in) | on device (one frame); timing as unit test |
| 5 | Intro before the first line: empty column, title at 560 | on device |
| 6 | Paused: lyric gone, "Paused", cover at 40% | on device |
| 7 | No synced lyrics: one column, title at 1040 | on device (a song LRCLIB lacks, or offline with no cache) |
| 8 | Long title with lyrics: title ends at x 944 with an ellipsis | on device |
| 9 | Alarm and Sleep: "Screensaver Lyrics: Off" focused, then On, with the hint | on device |
| 10 | Switch off: StandBy exactly as today | on device |
| 11 | Night Clock handed to the screensaver with a line showing: no blink, the same line | on device |
| 12 | `indexAt(p + 150)`, the 10 s cap, the 2 s backward rule, the swap wait, the late-lyrics rule (fits by x 944 or skipped) | unit test only |
| 13 | Spotify Connect on a phone: lines on time within the poll's interpolation | real TV only |

Eleven are on the emulator, one is unit test only and one is real TV only.

## Decisions

### D1. Empty LRC lines: approve
- **Asked:** `Lyrics.parseLrc` already drops empty lines (the panel card's previous and next lines need that). So a
  marked break is handled by the 10 s hold, not by an empty column.
- **Verdict:** approve. Keep the parser unchanged. A break shorter than 10 s keeps the last line up, as the panel's
  card does, and a longer one empties the column.
- **Basis:** judgment. One parser for both surfaces is worth more than an exact break.
- **Status:** built

### D2. Fire OS high contrast (#D23A1C) not built: approve
- **Verdict:** approve. StandbyScene has no high-contrast handling for any of its text, and adding it to the lyric
  alone would be inconsistent. It is removed from this brief. If it is ever done, it covers the whole face.
- **Status:** agreed

## Review

**Round 1 (design agent, emulator shots): ship after one medium fix. No re-review needed.** I measured the crops
×1.5, plus y 720, after removing drift (worked out from the cover's 112 / 744). Positions are ±2 px.

| What | Brief | Shots |
|---|---|---|
| Lyric column | starts at x 1008 | x 1009 |
| Title | x 384 | x 385 |
| Baselines | line 1 at 852 (the title's), line 2 at 900 | 851–854 and 899 (sl-3) |
| Long title with lyrics | ends by x 944 | "Somebody That I Used To…" ends about x 914 (sl-8c) |
| Night | everything #B81C0C | all red, cover tinted under the text |

- Intro, paused and no-lyrics states match the brief: empty column, or "Paused", or one 1040 px column.
- The handoff frame is identical. Switch off: StandBy as before.
- Alarm and Sleep shows the row and hint word for word, and the hint stays in view when the row is focused.
- The Night Clock focus fix (Up from the first tile lands on Wi-Fi, sl-up0) is correct.

1. **Medium: two-line lyrics leave a one-word last line.** "…the one I / trust" and "…always the / end": a widow at
   40 px reads as a separate lyric. Fix, on the lyric TextView:
   `setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)`, which gives "Oh, when I'm like this, / you're the one I
   trust". Also `setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)`, because lyrics must never hyphenate.
   With the TV build, send one crop of the sl-3 line.
- **Not caught, accepted:** a mid-fade frame (state 4; the timing is unit-tested) and an ellipsis on line 2 (no LRCLIB
  line was long enough). Check by eye on the TV.
- **On the TV:** Spotify Connect timing (state 13), and whether the line changes are calm enough at night.

## For the user

- **The setting is in Alarm and Sleep**, named "Screensaver Lyrics", and starts Off. Say if you'd rather it sat in
  Customize Home.
- **On and Off only.** If the lines are too lively at night, the next step is a third choice, "Day Only".
- **With lyrics on, long song titles are cut at about 25 characters**, so the line has room. Songs without synced
  lyrics keep the full width.

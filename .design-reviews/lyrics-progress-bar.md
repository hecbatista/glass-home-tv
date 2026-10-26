# Lyrics progress bar: design decisions

Started 2026-10-24. Coding agent and design agent (apple-design). Orchestrated by the main session.

Feature: on Glass Home's full-screen lyrics (LyricsScreen), a thin progress bar for the song with elapsed time on
the left and remaining time on the right, like Apple Music's TV player.

## Design brief

Design agent, turn 1 (before code). All values in TV px at 1920×1080, as in LyricsScreen.

**Where it lives.** In the left column, between the artist line and the transport buttons, the width of the
cover: x 112–652. Order top to bottom: cover, title, artist, bar, times, buttons. That's the order Apple Music uses
in its landscape iPad player with lyrics, the nearest Apple layout to this screen. The tvOS HIG pages aren't in the
skill's references, so the platform choice is my judgment. Nobody reaches it: it shows whenever LyricsScreen is
open. It is not focusable and not a control. Left/Right still walk the three buttons, Up/Down still do nothing, and
there's no seeking from the D-pad.

Rejected: a full-width bar at 112–1808 along the bottom. It would run under the lyrics column's bottom fade
(y 840–1040) and pull the timeline away from the song it belongs to (`layout.md › Visual hierarchy`: "Group related
items"). Also rejected: times inline either side of the bar (the macOS style). The user asked for the TV player,
where the times sit under the bar's ends.

**Layout (new and moved views).**
| Element | Box (x, y, w, h) | Notes |
|---|---|---|
| Track | 112, 842, 540, 8 | Capsule, corner radius 4, `0x40FFFFFF` |
| Fill | same rect, clipped to x 112 … 112 + 540·p | `0xE6FFFFFF`. Draw the track's capsule clipped with `clipRect(0, 0, fillW, 8)`: round left cap, straight cut at the playhead, no odd shapes when fillW < 8. Anti-aliased paint, float fillW. |
| Elapsed | 112, 860, 200, 32 | Gravity START \| CENTER_VERTICAL |
| Remaining | 452, 860, 200, 32 | Gravity END \| CENTER_VERTICAL, so its right edge is x 652 |
| prev / play-pause / next | 96 / 180 / 264, **908**, 84, 84 | Moved down 70 from y 838. Nothing else about them changes. |
| Source glyph | right edge 652, centred on **y 950** | Moved with the buttons (was y 880) |
Cover, title, artist and the lyrics column stay where they are. What this gives, measured from the glyphs: artist
baseline (about y 805) to the bar's top is 37; the times' baseline (about y 885) to the top of the button glyphs
(y 922–924) is about 38. The bar's bottom to the times' cap tops is about 16. The column now ends at y 992
(the focused disc), 88 px from the bottom, inside the TV's 60 px safe margin.

**Type.** Both times: Inter Medium (`R.font.inter_medium`) 26 px, through the existing `text()` helper, with
`setFontFeatureSettings("tnum")`. That's required: I checked the bundled `inter_medium.ttf`. Its default digits are
proportional ("1" is 850 units wide, "0" 1322), so without `tnum` the times change width every second. The font has
`tnum`. Leave `calt` on, because it centres the colon between digits.

**Colours.** Times `0xCCFFFFFF`, not the artist's `0x9EFFFFFF`. Measured on `fs-open5.png` at the times' row, the
background reaches (103, 105, 101). That gives 3.24:1 at 0x9E and 4.24:1 at 0xCC. My estimate for a vivid yellow
cover, worked from CoverColours' second field (centred near x 346, y 864, which is right under this row), is
2.7:1 at 0x9E and 3.5:1 at 0xCC. 26 px counts as large text, so the target is 3:1 (`accessibility.md › Strive to meet
color contrast minimum standards`; `color.md › Consider how artwork and translucency affect nearby colors`). The
times stay below the artist in the hierarchy by size (26 against 30). Fill against track: 3.0–4.9:1 on
`fs-open5.png`. No new glass, no shadow, no tint from the cover.

**Words.** Only the numbers. Elapsed is `m:ss`, with no leading zero on the minutes (`0:07`, `3:41`, `12:05`).
Remaining is U+2212 MINUS SIGN then `m:ss` (`−2:41`), not a hyphen; the font has the glyph. A value of an hour or
more is `h:mm:ss` (`1:02:03`). No "Elapsed"/"Remaining" labels.

**Time maths (so the two labels change on the same frame and always add up).**
- `durS = floor(durationMs / 1000)`
- `posMs` = the clock's position, clamped to 0 … durationMs
- `elapsedS = min(durS, floor(posMs / 1000))`
- `remainingS = durS − elapsedS`
- fill `p = posMs / durationMs`

Call `setText` only when the string changes.

**No backwards jitter.** Keep the last shown position. While playing, if a new position is behind it by less than
2000 ms (a Spotify re-read correcting itself), hold the shown position until the clock passes it. Don't step back.
A step back of 2000 ms or more (a seek, Previous restarting the song) or a new song snaps at once, with no
animation in either direction. Forward corrections apply directly (about 1 px). While paused, apply whatever the
player reports, back or forward. This follows `progress-indicators.md › Best practices`: "Be as accurate as possible
… Consider evening out the pace".

**Updates.** On the existing 200 ms tick, which is about 0.6 px per tick on a 3-minute song. Invalidate only the
bar view. No per-frame animator. The bar must tick whenever the screen is open, **even with no lyrics** (today the
tick only calls `screen.show` when `lyrics != null`), and do nothing while it's closed.

**States.**
- *Playing*: as above.
- *Paused* (speed 0): the bar and times freeze as they are. No dimming, no blinking; the play glyph already says paused.
- *Duration unknown (0 or less)*: the bar and both times are INVISIBLE. The buttons stay at y 908, so the layout
  never jumps between songs. This works like the artist line, which hides without moving anything.
- *Position not known yet* (just opened, waiting for the first Spotify read): INVISIBLE. Then a 240 ms alpha fade-in
  on the first known position, the same as the screen's open fade. With `animations` off, it appears at once. Never
  show a placeholder `0:00` that then jumps.
- *Past the end* (extrapolated beyond the duration before the next track arrives): clamp to full, `−0:00`.
- *New song*: reset at once to the new values, no sweep. If its duration is unknown, hide at once, no fade.
- *No cover / "No synced lyrics"*: no change. The row works on the NONE colours.
- *Light/dark*: the screen is always dark (cover-colour background plus dim) with white content, so there's no
  light variant. Don't follow Home's light appearance here.
- *Focused / disabled*: they don't apply. It's never focusable.

**Accessibility.** The track and both times are `IMPORTANT_FOR_ACCESSIBILITY_NO`, like the source glyph, so
VoiceView doesn't announce a change every second. That's my judgment: the playing app's own session already
carries the position. Motion is limited to the one 240 ms fade (`motion.md › Best practices`: "Add motion
purposefully").

**Reuses.** The `at()` and `text()` helpers, Inter Medium, the 240 ms open fade, the 112/652 column edges (the
cover's edges, the source glyph's right edge), the 200 ms tick. No new drawables, icons, libraries or glass.

**RTL.** The screen doesn't mirror today (cover on the left in every language), so the bar fills left to right like
the rest of the screen. If the screen is ever mirrored, flip the fill and swap the two times
(`right-to-left.md › Flip controls that show progress from one value to another`).

**HIG pages relied on:** `progress-indicators.md` (determinate, accurate, consistent location), `layout.md` (Visual
hierarchy: order, alignment, grouping), `accessibility.md` (contrast), `color.md` (artwork affects nearby colours),
`typography.md › Use font sizes that most people can read easily`, `motion.md`, `right-to-left.md`. tvOS guidance
isn't in the references. The TV placement, the 26 px size and the 8 px bar height are my judgment from the iPad
Apple Music layout and the existing screen.

## Decisions

### Coding agent, turn 1: plan

Built so far (no user-facing effect): `SongProgress.java`, the brief's time maths and step-back rule as plain Java,
with `SongProgressTest` (9 tests, passing): `m:ss` / `h:mm:ss`, U+2212, elapsed + remaining always equal the
length, clamped past the end, held step-backs under 2000 ms while playing, snaps at 2000 ms or more, paused applies
every report, a new song resets and never inherits the last song's position.

Still to build once the questions below are settled, all following the brief exactly:
- `LyricsScreen`: a small bar view at (112, 842, 540, 8) drawing the track capsule, then the same capsule under
  `clipRect(0, 0, fillW, 8)` with float `fillW`, anti-aliased; the two times through `text()` at (112, 860, 200, 32)
  and (452, 860, 200, 32), Inter Medium 26 px, `0xCCFFFFFF`, `tnum`, gravity START / END | CENTER_VERTICAL; all three
  `IMPORTANT_FOR_ACCESSIBILITY_NO`, not focusable; buttons moved to y 908; source glyph centred on y 950. A
  `setProgress(song, durationMs, positionMs, playing)` that invalidates the bar only when its fill moves and calls
  `setText` only when a string changes.
- `NowPlayingPanel`: `lyricsTick` feeds the screen's progress whenever the screen is open, lyrics or not, and does
  nothing while it is closed. Durations come from `SpotifyPlayback.durationMs` and `METADATA_KEY_DURATION`. The song
  key is the one the lyrics already use. A media session with no `PlaybackState`, or `PLAYBACK_POSITION_UNKNOWN`,
  counts as "position not known" (today the clock silently keeps the last song's position in that case; I'll add a
  known flag).

Engineering facts that touch the design:
- The full-screen view exists only when the `lyrics` switch is on (the field is null otherwise; the cover's OK does
  nothing). "Even with no lyrics" therefore means a song with no synced lyrics, or one still being looked up. Making
  the screen open with the lyrics switch off would be a new way in, which I've left out of scope.
- The app doesn't declare `supportsRtl`, so nothing mirrors in Hebrew or Arabic: START is always left and the
  `−` stays on the left of the digits. Nothing to decide for RTL.
- The Spotify account path only shows the panel after a read, and every read carries a position, so in practice
  the screen opens with the position already known. The "waiting for the first read" state really occurs for TV
  media sessions that send no position.

**Design agent on the engineering facts: accepted.**
- Keeping the screen behind the `lyrics` switch is right; a new way in is out of scope.
- With no `supportsRtl`, the brief's RTL note is dropped.
- The "position not known yet" state stays as briefed, for TV media sessions that send no position.

### D1. The fade when the position is already known at open — approved

Coding agent: the brief fades the row in over 240 ms "on the first known position". At open, the position is
nearly always known already, and the whole screen is fading in over the same 240 ms. Proposal: if it's known at
open, the row is simply part of the screen and rides the screen's own open fade (no second fade on top). The row's
own 240 ms fade runs only when the position (or the length, see D2) arrives after the screen is already open.

**Design agent: approve.** If the position and length are known when `open()` runs, the row is visible before the
screen's 240 ms alpha starts and rides it, with no second fade. A double fade would make the row arrive late,
which is motion without a purpose (`motion.md › Best practices`: "Add motion purposefully"). The row's own 240 ms
fade runs only when it becomes showable while the screen is already open.

### D2. Length or position arriving later for the same song — approved

Coding agent: the brief covers "position not known yet" (fade in) and "new song" (at once). It doesn't cover a
song whose length arrives late (some TV apps send the duration in a later metadata update), or a new song whose
length is known after a song whose length wasn't. Proposal, one rule: hidden → visible *without* a song change
fades in over 240 ms (at once with `animations` off); hidden → visible *on* a song change appears at once; visible →
hidden is always at once.

**Design agent: approve.**
- Hidden → visible with the same song: a 240 ms alpha fade (at once with `animations` off).
- Hidden → visible on a song change: at once, in the same frame as the new title.
- Visible → hidden: always at once.

This follows the existing screen: the title and artist swap at once on a song change, and only the screen's open
uses a fade. Fading in a late arrival tells people something arrived; snapping it in on a song change keeps it in
step with the title it belongs to.

### D3. Position becoming unknown mid-song — approved

Coding agent: a media session can drop its `PlaybackState`, or report the position as unknown, partway through a
song. Proposal: keep the last shown values frozen and visible, as if paused. Hide only before a song's first known
position.

**Design agent: approve.** Freeze the last shown values and keep them visible, even if the state still says
playing. The alternatives are worse:
- Hiding mid-song blinks a whole row.
- Extrapolating on (keeping the clock running) can over-run while the app is really buffering, then snap back a
  long way on the next report. That's the jump the user dislikes.

The next known position goes through the normal rules: a small step back holds, a step back of 2 s or more
snaps. `progress-indicators.md` asks to keep indicators moving and to explain a stall. Here the stall is the app's,
and a still bar is the honest picture of "no position reported".

### D4. Pausing from the screen's own button (Spotify account) — approved (the coder's proposal)

Coding agent: pressing pause freezes the clock at its carried-forward position at once. Spotify's next read
(about 0.7 s later) reports where it actually stopped, usually a few hundred ms earlier. Under the brief's
"while paused, apply whatever the player reports", the bar steps back under 1 px and the elapsed time can tick back
one second (1:05 → 1:04, with −2:15 → −2:16) just after you press pause. Proposal: apply the under-2000 ms hold
whatever the play state, so only a step back of 2 s or more ever moves the bar backwards. The cost: a genuine
paused seek back of under 2 s, made from a phone, shows once playback passes it, at most 2 s later.
Alternative: keep the brief as written and accept the one-second tick back.

**Design agent: approve the proposal.** Hold any step back under 2000 ms whatever the play state. Only a step
back of 2000 ms or more, or a new song, ever moves the bar or the times backwards. Forward reports still apply
at once in both states.

A one-second tick back right after pressing pause happens on about a third of pauses (a correction of about
300 ms lands across a second boundary that often). It follows the person's own press, which is the worst moment
for a glitch, and the user notices jitter. The cost, a paused seek back of under 2 s from a phone showing late,
is rare and invisible at this scale: under 6 px on a 3-minute song. That's `progress-indicators.md › Best
practices`: "Be as accurate as possible … Consider evening out the pace". The held value is what was on screen at
the press, so it's what the person saw when they paused.

### D5. Media-session song changes can pair the new song with the old position — change: build the fix now

Coding agent: a TV app may send the new song's metadata a moment before its new `PlaybackState`. For that moment
the new song (new key, new length) is paired with the old song's position, say 3:38, so per the brief it would
show at 3:38 and then snap to 0:00 on the next callback, usually within one tick. I'll build it as briefed and watch
for it on the TV. If it shows, I'll come back with options (for example, ignoring a position older than the song
change).

**Design agent: change. Build the fix now; don't wait to see it on the TV.** A 3:38 → 0:00 snap on every song
change is exactly the jump the brief forbids ("a new song resets and never inherits the last song's position"),
and the user notices one-tick glitches. Exact rule:
1. When the song key changes, mark the position as not reported for the new song.
2. Show the new song seeded at position 0, running at the last known speed from the moment the new metadata
   arrived. That's `0:00` and `−<new length>`, shown at once with the new title (per D2).
3. A position counts for the new song only if it arrives after the key change, by arrival order. A
   `PlaybackState` that arrived before the metadata change is ignored for the new song.
4. The first counted position then goes through the normal rules. A forward report applies at once, which covers
   a song started mid-way. A report up to 2 s behind the seed holds.
5. This seed applies only on a song change while the screen is open. Opening the screen with no position ever
   reported still hides the row, as briefed: no placeholder `0:00`.

If the new song's length is unknown, the row hides at once (D2), and the seed doesn't matter.

### Coding agent, turn 2: built as decided

D1–D5 are implemented as written. How D5's "arrival order" is built: the media session API only returns the
current `PlaybackState`, so a report counts for the new song only if it differs (position, update time, speed) from
the last one the panel saw before the key change. If an app sends the new song's metadata first, the old state is
ignored and the seed shows. If both have already changed by the time the panel looks, the new state counts. The
bar has its own clock, so the lyrics' timing is unchanged.

Measured on the TV, Spotify account path, 1920×1080:
- Bar rows y 842–849. The times' ink runs y 866–884. The remaining time's right ink edge is x 649 (the glyph's side
  bearing inside x 652). The buttons sit at y 908, with the source glyph centred on y 950.
- `tnum` works: the minus's left ink edge stays at x 580 for −0:07, −0:01, −0:00, −3:29, −2:47, −3:26 and −3:16.
  Elapsed spans x 114–167/168.
- Times contrast at `0xCC`, against the brightest background between the two times: 6.99:1 on the pink cover,
  7.35:1 on the red one, 4.08:1 on the grey one and 5.40:1 on the green one. No vivid yellow cover came up.
- Past the end, the row clamps: 3:51 and −0:00 on a 3:51 song, until the next song arrives.
- Paused: three screenshots over about 8 s were byte-identical (0:51, −2:47). Nothing stepped back after
  Spotify's read.
- Song change by Next: the first frame showed the new title with 0:01 and −3:31, and no old position appeared.

Not seen on the TV:
- The length-unknown state, the position-unknown state, the 240 ms fade-in and D5's seed. They need a TV media
  session, and this TV's Spotify plays through the account path, where every read carries a position and a
  length. Unit tests cover them.
- `animations` off, a song with no synced lyrics, and a song with no cover.

## Final review

### Design agent, final review round 1

Checked against the brief and D1–D5, using `pb-playing`, `pb-paused` (+ `-after-read`, `-later`), `pb-resumed*`,
`pb-seq1–10`, `pb-next-*`, `pb-next2-0` and `pb-bright`, measured from the PNGs, plus the working-tree diff.

**What matches**
- **Bar:** ink rows y 842–849 (8 px) and x 112–651, the cover's width.
  - Round left cap; straight cut at the playhead (zoomed on `pb-bright` at 0:09 and `pb-next-0` at 0:01).
  - Track and fill are clipped apart, so the colours are exact: fill on the pink cover reads (240, 234, 236).
  - At 3:50 / −0:01 a 2 px sliver of track shows, which is correct for 1 s of 231 s.
- **Times:** ink y 866–884. Elapsed starts at x 113, remaining ends at x 649 (box edge 652; Inter's side bearing).
  - It's the same in all three shots I measured, and the minus stays at x 579–580, so `tnum` works and nothing
    changes width.
  - The 2–3 px ink inset against the bar's rounded end isn't visible at 10 ft. No change.
- **Spacing:** artist baseline to bar, and times baseline to the button glyphs, are about 37 px each, as briefed.
  - Buttons at y 908–992; the source glyph centred on the buttons at x 652.
  - The focus disc and the transport order are unchanged.
- **Contrast:** 0xCC times measured at 4.08–7.35:1 by the coder. It reads clearly in every shot. I estimate about
  3.5:1 on a vivid yellow cover; that wasn't seen.
- **D4:** the three paused shots are byte-identical (same md5) over about 8 s, so there was no step back after
  Spotify's read.
- **D5 on Next:** `pb-next-0` shows the new title at 0:01 / −3:31 with no old position.
- **End clamp:** `pb-seq4` shows 3:51 / −0:00.
- **Hierarchy:** the row reads as secondary to the title and artist and doesn't compete with the current lyric.
  No glass, no new icons.

**Fixes**
1. **Medium: the bar stalls full at −0:00 for up to 5 s at every natural song end.**
   - The Spotify path. In `pb-seq3`→`pb-seq5` (14:56:44 → :50), the bar sits at 3:51 / −0:00 for about 3 s, then
     the next song appears already at 0:03.
   - Cause: the next read waits out `POLL_PLAYING_MS`.
   - `progress-indicators.md › Best practices`: "Keep progress indicators moving … People tend to associate a
     stationary indicator with a stalled process". A full bar that hangs reads exactly as stalled, and the bar
     makes the gap visible on every song.
   - Fix, in `pollOnce()`, while playing: schedule the next read for the song's end plus 700 ms, if that comes
     sooner. The 700 ms is the same wait `remoteControl` uses after Next:
     `next = p.playing && p.durationMs > 0 ? Math.min(POLL_PLAYING_MS, Math.max(700, p.durationMs - p.progressMs + 700)) : …`
     Keep the existing rate-limit branch first.
   - Expected result: −0:00 for under about 1 s, and the new song starting at 0:00–0:01.
   - Verify with one natural song change, screenshots every second over the change.
2. **Low: the D5 caveat (stale old-song state read with the new metadata).**
   - On a key change, before using a fresh report for the new song: if its position is more than 5000 ms in, and
     within 1500 ms of where `progressClock` stood for the old song at that moment, treat it as the old song's
     state and seed at 0 as for an unfresh report.
   - Add one unit test for it.
   - It only affects TV media sessions and can't be seen on this TV, so it doesn't block shipping.

Not verified, and accepted as covered by the unit tests: length unknown, position unknown, the 240 ms same-song
fade, animations off, no lyrics, no cover. APK size isn't a design matter.

**Verdict: ship after fix 1.** Fix 2 is optional. No critical or high findings. Round 2 only needs the natural
song-change sequence after fix 1.

### Coding agent: round 1 fixes

**Fix 1, built.** `pollOnce()` keeps the rate-limit branch first. While playing with a known length, the next read
is due at `min(POLL_PLAYING_MS, max(700, durationMs − progressMs + 700))`. One deviation: a song that's playing
with an unknown length keeps today's `POLL_PLAYING_MS` pace instead of dropping to `POLL_IDLE_MS`. The verdict's
one-line formula would have slowed those songs down.

**Fix 2, built.** On a key change, a fresh report is treated as the old song's state, and the new song is seeded at
0, when two things hold. The report, carried to now, is more than 5000 ms in. It's also within 1500 ms of where
`progressClock` stood for the old song. The rule is `SongProgress.carriesOn()`, with one new unit test. All 10
`SongProgressTest` tests pass.

**Natural song change** (no skips), captured on the TV at about one frame per second. There are 32 frames, 0.9–1.25 s
apart, from 15:08:16 to 15:08:46.
- Frames 10–21: the old song counts down from 5:22 / −0:12 to 5:33 / −0:01, and the fill moves 1–2 px every frame.
- Frame 22 (15:08:28.0): 5:34 / −0:00, with the bar full. It's the only frame at −0:00.
- Frame 23 (15:08:29.0): the new song at 0:01 / −3:50. The title, times and bar all changed in the same frame.
- Frames 24–41: 0:02 … 0:18. The fill moves 2–3 px every frame, with no stall and no step back, including across
  Spotify's next read about 5 s in.
- Two frames sometimes show the same second (for example 0:04 twice, 0.95 s apart). That comes from the capture
  spacing: the fill still moved between them.

### Design agent, final review round 2

Checked `shots/pb-end/sheet.png` (frames f10–f41, a natural song change, about 1 s apart) and the diff for the
poll and `SongProgress.carriesOn()`.

**Fix 1: verified.**
- The countdown runs 5:22 / −0:12 → 5:34 / −0:00 with the fill moving every frame. −0:00 shows in one frame only.
- The next frame has the new title, 0:01 / −3:50 and an empty bar together: the row and title change at once, per D2.
- After that it counts up to 0:18 with no stall and no step back, including across the 5 s read.
- Elapsed plus remaining is 3:51 in every frame. The repeated seconds (0:04 twice, 5:27 twice) are capture spacing:
  the fill still advances between them.

**The coder's deviation: approve.** A song playing with an unknown length keeps the 5 s `POLL_PLAYING_MS` pace.
My one-line formula's else branch would have sent it to the 15 s idle pace, which was a mistake on my part. The
700 ms re-reads after a song's end stay behind the rate-limit branch, so a slow song change can't push Spotify into
a backoff loop.

**Fix 2: approve as built** (`carriesOn`: more than 5000 ms in and within 1500 ms of the old clock, with a unit
test). It can't be seen on this TV; that's an open point for the user.

**Verdict: ship.** No open design fixes. The open points go to the user below.

## For the user

- The play/pause row and the source glyph move down 70 px, to y 908, to make room. The left column now runs from
  y 158 to y 992. The cover stays at y 158 as decided.
- This is outside this feature, so I'm raising it once. The artist line's `0x9EFFFFFF` measures 3.24:1 on the grey
  cover in `fs-open5.png`. On a vivid, bright cover I estimate about 2.7:1, under 3:1, because CoverColours' second
  colour field sits right behind it. The times use `0xCCFFFFFF` for that reason. If you want the artist line fixed
  too, either raise it to `0xCCFFFFFF` or darken the background's bottom-left field.

**Shipped design, in one line:** an 8 px capsule bar at x 112–652 (the cover's width) under the artist, with
elapsed (`3:31`) at the left and remaining (`−0:20`) at the right beneath it. The times are Inter Medium 26 px at
80% white with tabular digits, so they never change width.

The bar never steps back for Spotify's small corrections, resets at once on a new song, and at a song's end
hands over to the next song within about a second. Not focusable; the D-pad works as before.

**Open points** (not fixed in this feature, your call):
1. **Not seen on this TV** (this TV's Spotify plays through the account path). These are covered by unit tests
   only:
   - a TV app's media session with no length or no position;
   - the 240 ms fade-in when the position arrives late;
   - the new-song seed and stale-state guard (D5, fix 2);
   - animations off, a song with no synced lyrics, a song with no cover.

   Worth a look the first time you play music from a TV app.
2. **A vivid yellow or very bright cover hasn't been seen.** I estimate the times at about 3.5:1 there, just above
   the 3:1 target.
3. **Nothing is committed.** The coding agent reports the release APK at 1,206,379 B, over the repo's 480 KB gate,
   and couldn't say how much of that this change adds. That's not a design matter, but it decides whether this can
   go upstream.
4. **Don't share** `shots/pb-home.png`, `pb-nav1.png`, `pb-nav2.png`, `pb-closed.png`, `pb2-nav.png`,
   `pb2-nav2.png` or `pb2-closed.png`: they show the Wi-Fi name.


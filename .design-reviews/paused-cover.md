# Paused cover: design decisions

2026-10-26. Mode: lite. Design agent: apple-design.

The user's request: when the song is paused, the Now Playing cover shrinks slightly and dims, Apple Music style, and
springs back when it plays again. Checked on the emulator in both states and in the full-screen lyrics.

A first implementation was written before the pairing started (the user asked for the design skill mid-way); its
values are the proposal the brief should confirm or replace:

- `PausedCover.java`, shared by the panel cover (183 px) and the full-screen cover (540 px).
- Paused: scale 0.88 about the cover's centre; a black layer in the cover's foreground at 30% opacity, clipped to the
  cover's corners, under the panel's white focus ring (the ring stays bright but shrinks with the cover).
- Pause: spring stiffness 260, damping 1 (no overshoot). Play: stiffness 380, damping 0.55 (about 1.5% overshoot).
- Animations off or Reduce Motion: snaps. A panel or screen that first appears paused starts small, without motion.
- The full-screen cover's drop shadow (elevation 20 dp) shrinks with it.

## Brief

Design agent, turn 1. Verdict: **approve the proposal, with one change (D1: the dim's colour on Light).** Read against
`PausedCover.java`, the `pausedArt`/`pausedCover` wiring, `motion.md` and `accessibility.md`. No tvOS page exists in
the references; values marked *judgment* are mine.

**Paused look, both covers, one value (approved).** Scale 0.88 about the centre (default pivot; never set one), dim
30%. Panel 183 → 161 px (11 px in on each side); lyrics 540 → 475 px (32 px in: left edge x 144, bottom y 666, so
60 px above the title instead of 28). One value for both sizes, because it is one meaning (*judgment*). The paused
state never moves a sibling: the cover's layout box stays 183 / 540 px.
**Dim layer.** Black (#000000) at alpha 0.30 on Dark and on the lyrics screen; on Light, **#ECEEF3 at 0.30 (D1)**.
Drawn in the foreground, clipped to the cover's radius (28 px panel, 36 px lyrics). The glyph tile (no cover) is
the cover: it shrinks and dims the same way.
**Focus ring (approved).** Above the dim, so it stays #F5FFFFFF (ink on Light) at full strength; it scales with the
cover (6 px stroke → 5.3 px), which is fine at 10 ft. Focus does not undo the paused look: ring and size are separate.
**Shadow (approved).** The 40 px elevation stays; its footprint shrinks with the scale. No elevation animation: the
scale already reads as "settling", and z-depth animation is what `accessibility.md › Motion` asks to avoid.
**Springs (approved), on `amount` 0 playing … 1 paused:**
- Pause: stiffness 260, damping ratio 1.0. No overshoot; 90% in about 0.24 s, settled by about 0.4 s.
- Play: stiffness 380, damping ratio 0.55. Peak at about 0.19 s, 12.6% past target: scale 1.015 (lyrics +4 px a
  side, panel +1.4 px). Settled by about 0.55 s. The dim stays at 0 through the overshoot (the clamp in `dimAt`).
- A change mid-spring retargets from the current value and velocity (no restart, no jump): `motion.md › Let people
  cancel motion`.
**Reduce Motion / animations off (approved).** Snap to the end state, scale included; the design system's rule.
The paused look is a state, not motion, so it still shows. The state is never carried by motion alone: the
play/pause glyph and its spoken label change too (`accessibility.md`, `motion.md › Make motion optional`).
**First appearance while paused (approved).** The panel fading in, or lyrics opening, already paused: small and dim
at once, no spring. Buffering counts as playing (already so), so a skip or seek never dips the cover.
**Nothing else reacts.** Not the panel, title, lyrics lines or progress bar; the bar just stops. Not StandBy.

**States.** On device (emulator, one cropped still each):
- S1 Panel, Dark, paused, cover not focused.
- S2 Panel, Dark, paused, cover focused (ring bright over the dim).
- S3 Panel, Light, paused, with a cover (D1).
- S4 Panel, Light, paused, no cover: the glyph tile (D1). Skip if DemoMedia cannot send a song without art.
- S5 Lyrics, paused.
- S6 Lyrics, playing again after a pause: must match `shots/pb2-open.png` exactly (scale back to 1.0, no dim).

On the TV, by eye (motion can't be judged from stills): pause settle, play bounce, OK pressed fast on play/pause,
next track while playing (no dip), panel or lyrics shown while paused (no motion), `reduced_motion` on (snaps).
Unit test only: `scaleAt`/`dimAt` at 0 and 1; overshoot never brightens; `dimColour(light)` (D1).

## Decisions

- **D1. The dim fades toward the surface, not always to black.** Dark panel and lyrics: #000000 at 0.30 (as
  proposed). Light panel: #ECEEF3 (the Light background) at 0.30. Why: on Light, a black layer turns the glyph
  tile (0x14000000 on light glass) into a grey smudge and makes covers look shadowed rather than set back; washing
  toward the surface reads as "resting", as the dark version does on dark. *Judgment*; S3 and S4 confirm it.
  Suggested implementation:
  ```java
  /** The dim layer's colour: toward the surface it sits on. */
  static int dimColour(boolean light) { return light ? 0xFFECEEF3 : 0xFF000000; }
  PausedCover(View cover, GradientDrawable dim, boolean animations, boolean light) { ... dim.setColor(dimColour(light)); ... }
  ```
  `NowPlayingPanel` passes its `light`; `LyricsScreen` passes `false` (its background is always dark).

## Review

Design agent, final review. Verdict: **ship.** Measured on the emulator crops (sips → pixel reads), not estimated.
- **Sizes exact.** Panel cover 183 px playing (x 29–211 in the crop), 161 px paused (40–200), so 11 px in on each
  side about the centre. Lyrics 540 px at (112, 158) playing, which matches `pb2-open.png`. Paused it is 476 px from x 144:
  the title doesn't move, and the gap to it opens from 28 to 60 px as briefed. S6 is back at 1.0 with no residual dim.
- **Dim exact.** Dark: (162, 45, 130) → (116, 32, 93), that is ×0.70 (black at 0.30). Light (D1): → (184, 103, 163),
  which is 0.70·cover + 0.30·#ECEEF3 to within a level. On Light the cover recedes toward the glass and stays
  saturated, without the grey smudge. D1 confirmed.
- **S2 focus ring:** 5 px of (250, 247, 250) above the dim, unaffected by it, scaled as specified (6 → 5.3 px). Clear
  from 10 ft.
- **S1 / S5:** they read as paused at a glance, and the play glyph and its label agree. The cover is still easy to
  recognise, and the lyrics, progress bar and title don't react, as specified. The shadow shrinks with the cover.
- **Foreground fix:** setting the dim once (alone, or inside the LayerDrawable with the ring) is right. Replacing a
  foreground drops the old drawable's callback. Verified in `NowPlayingPanel` and `LyricsScreen`.
- **Not verified, left for the TV, not blocking:** S4, the glyph tile on Light (DemoMedia always sends a cover), and
  every motion check in the brief: the pause settle, the play bounce, fast OK presses, next track with no dip, shown
  while paused, `reduced_motion` snaps.

## For the user
- Launcher-wide, not this feature: Reduce Motion is only the launcher's own `reduced_motion` switch, and the springs
  (dynamicanimation 1.0.0) ignore Fire OS's "Remove animations" setting. Apple asks apps to follow the system's
  setting (`accessibility.md › Motion`). Worth a small follow-up that also treats `ValueAnimator.areAnimatorsEnabled()`
  false as Reduce Motion.

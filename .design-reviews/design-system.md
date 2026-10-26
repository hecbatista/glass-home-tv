# Glass Home: design system

Updated 2026-10-26. The design agent's source of truth for Glass Home (the `glass-home` branch of cleanyourtv). The
code wins if they disagree; then fix this file. All positions are TV px on the 1920×1080 screen.

## Screen and input
- 1920×1080 at density 2: 1 dp = 2 px. Briefs use TV px; code uses dp.
- A 40-inch Hisense LCD (Fire OS 8, Android 11), viewed from about 10 ft. 1 px is about 0.5 arcminute.
- D-pad remote: Up, Down, Left, Right, OK, Back, Home and media keys. No pointer, no touch. Every control is
  reachable by D-pad, and focus must always be visible.
- Safe margin: keep content at least 60 px from the screen's edges.

## Layout
- **Edges:** every left glyph edge at x 112 (measured on the glyph, not the text box); every right edge at x 1808.
- **Top bar:** Wi-Fi and Settings modules plus the round Customize Home button, all 128 px tall, centred on y 150
  (86–214), 24 px apart.
- **Clock:** 96 px Inter Display SemiBold, cap height 70, baseline y 185. AM/PM (34 px Inter SemiBold) and the
  short date (30 px Inter SemiBold, +0.08 em tracking, caps) sit on the same baseline.
- **Shelf** (the focused dock app): the name in 108 px Inter Display Bold, cap top y 500. Its "Last opened" line in
  32 px Inter Medium, baseline y 675. No page dots.
- **Dock:** band from x 112 to 1808 and y 736 to 985. Tiles 286×161 (radius about 25), 44 px around and between
  them, so the band's corners are concentric with the tiles'. The rows under the dock share its columns.
- **Now Playing panel:** right edge x 1808, bottom 703, 33 px above the band. Corner radius 52. Album cover 183 px
  square at x 1069, y 500.
- **Full-screen lyrics:**
  - Left column: cover 540 px square at (112, 158), radius 36. Title 40 px Inter SemiBold at top 726; artist 30 px
    Inter Medium at 62% white, top 774.
  - Progress bar: x 112–652 at y 842, 8 px tall. Times below it at y 860.
  - Buttons: 84 px discs at x 96, 180 and 264, top y 908. Source glyph's right edge at x 652, centred on y 950.
  - Lyrics column x 764–1808: lines in 60 px Inter Display Bold, 72 px line height, 34 px apart. The current line's
    box top is at y 338.
- **Launcher settings ("Customize Home"):**
  - Title 76 px Inter Display Bold, baseline y 150.
  - Pills 96 px tall, radius 48, between x 112 and 1808, 24 px apart.
  - Section labels 30 px Inter Medium at 62% ink, 36 px between sections.

## Type
- **Fonts:** Inter only (OFL; `res/font/inter_*`), in Regular, Medium, SemiBold, Bold, Display SemiBold and
  Display Bold. SF Pro may not be used: Apple licenses it for Apple platforms only.
- **Tracking:** display sizes are tightened (−0.025 to −0.035 em); captions in caps are opened (+0.08 em).
- **Tabular digits:** any number that changes while you watch (timers, progress times) uses tabular digits
  (`setFontFeatureSettings("tnum")`), because Inter's default digits vary in width.

## Colour
- **Backgrounds:**
  - Light #ECEEF3, Grey #3A3B40, Dark #0B0B0F (the default), Navy #1D3557, Plum #2E1F47, Pine #16302B.
  - On Light the ink is #111216; on all others it is white.
- **Text alphas:** primary 100%, secondary 62% (`0x9E`), tertiary or dim 36% (`0x5C`), source glyph 55%
  (`0x8C`). Progress times are 80% (`0xCC`), for contrast on bright covers.
- **Accents:** blue #0A84FF means *chosen* (settings checkmarks, the Control Center disc when focused). Reorder mode
  tints the focus border blue (#6EC1FF).
- **The glow:** two ovals behind the shelf's name, in the focused app's colour, softer on Light. It is the screen's
  signature: keep it.
- **Contrast:** at least 3:1 for text 24 px and larger, 4.5:1 below that. Text over album art must be measured.

## Components
- **Focus:**
  - Pills, buttons and settings rows turn solid white (#F5FFFFFF) with ink #111216 (inverted on Light).
  - Dock and grid tiles zoom to ×1.10 and get a white border with a soft glow; there is no white fill over the
    banner.
  - The Customize Home button shows its name, 26 px under it, while focused.
- **Selection** is never shown like focus: a blue checkmark (Material Symbols "check") on the resting pill.
- **Glass (`GlassMaterial`):**
  - Used by the top bar's modules, the round button, the Now Playing panel and the dock band.
  - **Add no more glass surfaces;** a new element lives inside an existing one or on its own screen.
  - Grounds: Fire OS 8 has no RenderEffect blur, so the material emulates frost from the glow behind it.
- **Pills over bright art** (StandBy's sunrise): rest fill #66000000 (40% black) instead of 12% white, which gives white
  text only 2.6:1 over the full glow. Focus is unchanged. Pill text stays white.
- **Monochrome tinted screens** (StandBy's night red): captions use 100% of the tint colour, not 62%, which falls to
  1.8:1; size carries the hierarchy. StandBy clock: Inter Display SemiBold 600 px, proportional digits, centred, cap
  top y 268; captions 40 px caps at baseline y 160 (`AlarmActivity`, `Sunrise` keyframes).
- **Time picker:** edited in place in its row pill, with segments as 64 px capsules (active #111216 on the white pill),
  Up/Down to change and OK to save (`AlarmSettingsActivity`).
- **Light tiles:** a 1.5 px hairline at 10% ink and a soft shadow, so white banners stay visible on Light.

## Motion
- Durations:
  - 160–260 ms for focus and fades;
  - 600 ms for list slides (lyrics);
  - 600 ms for the glow;
  - the Now Playing panel fades in over 240 ms.
- Easing: decelerate, or cubic-bezier(.2, .8, .2, 1).
- Reduce Motion and the animations switch replace slides with snaps.
- Paused media (`PausedCover`): the cover shrinks to ×0.88 about its centre and dims 30% toward its surface (black
  on dark, #ECEEF3 on Light). The pause spring is stiffness 260, damping 1.0; play is stiffness 380, damping 0.55. It
  snaps under Reduce Motion, and nothing else on screen reacts. Any dim fades toward the surface it sits on, never
  to black on Light.
- No jitter. Nothing steps backwards for small corrections, and text is replaced only when it changes, because the
  user notices marquee and timer glitches.

## Icons and imagery
- Material Symbols Rounded from Google's repo, path data unchanged (Apache 2.0). Never hand-drawn icons.
- App tiles use each app's original banner; the Fire TV and AirPlay tiles use art the user supplied.

## The user's decisions and taste
- Apple's design language (tvOS, Control Center, StandBy), built in Inter.
- A large clock with the glow behind the title; the Control Center-style top bar; the Fire TV Home tile.
- Dock apps: Netflix, HBO Max, YouTube, Disney+, Stremio. Proton VPN leads the second row.
- The 96 px clock and "Display, sound, apps" under Settings, both from the design critique.
- The white focus border is kept; the white glass fill over tiles was removed.
- Android's built-in marquee for long titles, not a custom one.
- The user judges results by eye on the TV; screenshots from the real TV beat mockups.

## Platform limits
- No RenderEffect or real blur (API 30). No Palette dependency: cover colours come from `CoverColours`.
- Fire OS keeps Amazon's launcher as the system Home; HDMI input switching needs a system-only permission.
- The apple-design references include tvOS (`designing-for-tvos.md`, `remotes.md`, `top-shelf.md`, `lockups.md` and
  each page's `TV (tvOS)` section): cite them for TV calls. Fire OS specifics (no Siri Remote touch surface, Amazon's
  Home) are still judgment.
- Screenshots of Home show the Wi-Fi network's name: crop or blur them before sharing.

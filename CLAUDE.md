Glass Home: an Apple TV style launcher for a Hisense Fire TV (Fire OS 8, Android 11). The app is in `launcher/`
(plain Java, Android SDK); `tools/tv.py` talks to the TV or the emulator over ADB.

Preview every launcher change on the Android TV emulator before the TV: see the tv-preview skill
(`.claude/skills/tv-preview/SKILL.md`). No HTML mockups of launcher screens.

Build new features with the design-paired-feature skill; its design agent reviews with the apple-design skill
(both in `.claude/skills/`). Each feature's design decisions live in `.design-reviews/`; read `design-system.md` before changing how anything looks.
Never commit `shots/`: screenshots can show the Wi-Fi name or account avatars.

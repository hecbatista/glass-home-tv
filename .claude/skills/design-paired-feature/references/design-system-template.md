# Design system template

A project's settled design rules, at `<project>/.design-reviews/design-system.md`. The design agent reads it before
every feature instead of rediscovering the UI, and adds to it when a feature settles something new. Keep it to
facts a builder can use: values, not adjectives. One or two pages.

---

# <Project>: design system

Updated <date>. Source of truth for the design agent; the code wins if they disagree, and then this file is fixed.

## Screen and input
- Resolution, density, units used in briefs, viewing distance, safe margins.
- Input: touch, pointer, D-pad remote, keyboard. Focus model.

## Layout
- Edges and grid (for example: every left edge at x 112, right edge at x 1808).
- Spacing scale and recurring gaps.
- Key regions and their boxes.

## Type
- Font families and licence limits.
- The scale: role, font, size, weight, letter spacing (clock, title, body, secondary, caption).
- Number style: tabular digits where values change.

## Colour
- Backgrounds and the ink on each.
- Text alphas by role (primary, secondary, tertiary).
- Accents and what each means (for example: blue = chosen).
- Contrast targets.

## Components
- Each reusable piece: its size, radius, states, focus look. (Pills, round buttons, panels, tiles, list rows.)
- Materials and how many surfaces may use them.

## Motion
- Durations and easing per kind of change; what Reduce Motion does.

## Icons and imagery
- Icon set and style; what's not allowed (for example: hand-drawn icons).

## The user's decisions and taste
- Choices the user made that a reviewer must not undo, each with a short reason.

## Platform limits
- APIs that don't exist here, budgets, things that look possible but aren't.

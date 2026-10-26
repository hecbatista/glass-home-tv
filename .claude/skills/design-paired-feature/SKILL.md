---
name: design-paired-feature
description: >
  Builds a new feature with a critical design agent at its side: the design agent, grounded in Apple's Human
  Interface Guidelines (the apple-design skill), specs the feature before any code, answers every design question,
  pushes back on weak choices and reviews the result from screenshots. For small features you code it yourself with
  the design agent; for large ones a separate coding agent builds it and you broker between the two. Use this
  whenever the user wants to add, build, implement or prototype a new feature, screen, setting, panel, widget, flow or
  other user-facing change in an app, launcher, website or UI, even if they don't mention design, Apple or subagents
  ("add a setting for X", "can we make a lyrics page", "build the onboarding screen", "new feature: ..."). Skip it for
  bug fixes, refactors, tiny tweaks the user has already specified exactly, and pure backend work with nothing a
  person sees, unless the user asks for it.
---

# Design-paired feature

A feature gets two minds: one builds it, and a **design agent** guards how it looks, reads and behaves. The design
agent's job is to be critical, grounded in the `apple-design` skill: it specs the feature, answers design questions
with exact values, rejects weak choices and reviews the result. An agent that reviews its own design decisions tends
to rationalise them; a second agent with a different job and the HIG in hand catches what the first talked itself
into. A design agent that approves everything isn't doing its job.

The pairing is worth its cost only if the cost stays small. A first trial built one progress bar in 30 minutes and
about 1M tokens, mostly spent rediscovering the project, copying text between agents and trading 60+ screenshots.
Everything below is shaped to avoid that.

## Pick the mode

**Lite (the default):** you write the code; the design agent briefs, answers and reviews. Use it for anything
that fits in a few files: a setting, a panel element, a bar, a screen tweak, a small new screen.

**Full:** a separate coding agent builds while you broker. Use it only for large features (a new flow, several new
screens, a big refactor under the UI), or when the user asks for two agents. It costs a second agent re-reading the
codebase, so don't use it just because a feature is visual.

## Before you start (both modes)

1. **Pin down the feature** from the user's words. If something that changes the build is missing (which screen,
   what triggers it), ask the user once. Leave visual details to the design agent.
2. **Read the project's design system** at `<project>/.design-reviews/design-system.md`. It holds the settled rules
   (screen, edges, type scale, colours, focus, motion, components, the user's taste) so nobody rediscovers them.
   If it doesn't exist, the design agent writes it in its first turn from `references/design-system-template.md`.
   That first run costs more once; every later feature is cheaper. If the user's decisions change during a feature,
   the design agent updates the file at the end.
3. **List the device preconditions now.** For each state the feature will have, what the device needs to show it
   (music playing, an app open, a light background, a network down). Ask the user for anything only they can do
   (start the music) **before** the build, not in the middle. States the device can't produce are covered by unit
   tests and named as such in the report.
4. **Collect the project facts** the design agent needs: how to build, install and screenshot; the platform's
   limits (OS version, missing APIs, licensed fonts, size budgets); the user's standing rules (for example "never
   commit or push without asking", "no screenshots showing the Wi-Fi name").
5. **Check `apple-design` exists** (`.claude/skills/apple-design/` in the project, or `~/.claude/skills/apple-design/`). If not, tell the user and stop: without it this is just a
   second opinion. Its references have no tvOS pages; for a TV project, say so in the brief, and the design agent
   labels TV-specific calls as judgment, anchored to the design system.
6. **Start the decision log** at `<project>/.design-reviews/<feature-slug>.md` from `references/decision-log.md`.
   It is the single copy of the brief and every verdict. If `.design-reviews/` isn't git-ignored, leave it out of
   commits and say so at the end.

## Lite mode

1. **Spec.** Spawn the design agent (general-purpose agent, your session's model) with
   `references/design-agent.md` filled in. Keep the agent ID from the spawn result: you continue it with
   `SendMessage` to that ID, which keeps its context (load `SendMessage` first if it's a deferred tool). It writes
   the brief into the log, and for a small feature the brief should be close to a complete spec: layout with
   measurements, type, colours, exact words, every state marked "on device" or "unit test only".
2. **Build.** Read the brief from the log and implement it. When you hit a design choice the brief doesn't settle,
   don't decide it yourself: collect such questions, and send them in one message once you have them all or reach a
   point where you can't continue without the answers. Most small features need zero or one such round.
3. **Verify and review.** Run the tests, install, and take one screenshot per state the brief marked "on device",
   cropped to the feature's region and downscaled to the size where a 1 px error is still visible. Send the paths
   and a two-line change summary. The design agent replies **ship** or ranked fixes.
4. **Fix once.** Apply critical and high fixes and send the new screenshots. That's the last review round unless
   something critical remains; medium and low fixes the design agent didn't insist on go to the user as notes.

## Full mode

As lite, with a coding agent doing steps 2–4 in your place:

- Spawn it with `references/coding-agent.md` filled in. For a well-specified feature it can run on a cheaper model
  (Sonnet); keep the design agent on your session's model, since its judgment is the point.
- It plans, then stops with numbered design questions written into the log. You tell the design agent "answer
  D3–D6 in the log", not a copy of them. The verdicts go into the log; tell the coder "verdicts for D3–D6 are in the
  log, continue". Add only what isn't in the log: a fact you know, the user's constraint.
- Large features consult in phases (layout, then states, then motion), several questions per round trip.

## Budgets (both modes)

Tell the design agent its budget in the brief so it spends it on what matters:

- **Consult rounds:** lite 1, full one per phase.
- **Final review:** 1 round, plus 1 more only for critical fixes.
- **Screenshots:** one per state, cropped and downscaled; more only when they would change a verdict, and it says
  which.
- **Log entries:** about 15 lines each, verdict first.

When the design agent and the coder (or you) disagree on the same point twice, stop arguing it: put both positions
to the user in a sentence each. Two rounds let each side make its case; more tend to end with whoever argued last.

## What the design agent may and may not do

It owns what the user sees and feels: placement, layout, type, colour, icons, words, focus and navigation, motion,
states. It may also offer a **suggested implementation** when that's the simplest way to say what it means, such as
a formula for smoothing a timer. You or the coder can amend it, saying why in the log. It never edits code.

## Report to the user

Check the work before calling it done: the tests pass, the screenshots exist and show the feature. Then tell the
user in plain words:
- what was built and how to try it;
- the main design decisions, and the ones where the design agent overruled a proposal;
- open points for their call, and the states covered only by unit tests;
- any screenshots that show private details (network names, accounts) and shouldn't be shared.

Don't commit, push or publish unless the user asked; offer it.

## Things that go wrong

- **The brief is vague** ("consider more spacing"). Send it back for exact values: a size, a colour, a word.
- **Advice the platform can't do** (a blur the OS lacks, an unlicensed font). Answer with the evidence and ask for
  the nearest buildable option. The platform limits in the brief should prevent most of these.
- **The design agent asks for more and more screenshots.** Remind it of the budget and ask which one decides it.
- **A subagent stalls or runs long.** Give it the missing fact or narrow its task. If you take over its job, tell the
  user the pairing was broken for that part.

# Decision log template

Copy this into `<project>/.design-reviews/<feature-slug>.md` at the start. It's the single copy of the brief and the
verdicts: agents read it instead of receiving copies. Append; never rewrite an earlier entry. A later decision that
replaces one says so. Each entry is about 15 lines at most, verdict first.

---

# <Feature name>: design decisions

<Date>. Mode: lite | full. Design agent: apple-design.

## Brief

<The design agent's brief. States marked "on device" or "unit test only".>

## Decisions

### D1. <short name>: approve | change | reject
- **Asked:** <the question or proposal, one or two lines>
- **Verdict:** <the exact answer>
- **Basis:** <HIG page, design-system rule, or "judgment">
- **Status:** agreed | built | for the user

## Review

- **Round 1:** ship | fixes: <ranked, one line each>

## For the user

<Open points, states covered by unit tests only, notes on earlier decisions.>

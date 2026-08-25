# R-8.3 — Rendering corpus job: rough plan

**Status: PLAN ONLY. Not approved, not started, nothing built.** Written 2026-08-23 at the product
owner's request ("a rough plan, standalone, do not execute yet"). Deliberately kept out of
`tasks/preview-launcher-implementation.md`: that log records work that happened, this file records
work that has not been decided. Level of detail is intentionally coarse — the four open decisions in
§5 are what a detailed plan would need first, and only the product owner can close them.

---

## 1. Where the requirement comes from

Spec `zulwriter-showcase/preview-launcher-requirements.md` §8.3, quoted in full:

> **Regression corpus.** Run the existing ZUL corpus job in both modes; isolated output must be
> unchanged from today, and no corpus page may gain a `LAYOUT` finding that the current revision does
> not have.

That is the entire text. Two things follow from it:

* It sits in §8 **"Verification plan"**, not in a requirement section. It was written as a way of
  *checking* P1-3 and P0-2, not as something to deliver.
* It says "the existing ZUL corpus job" — and **there is no such job**. That is why it became a work
  item of its own (implementation log, decision 12; product owner ruling 2026-08-21: keep the manual
  substitute for now, make the job its own item, last in the plan).

## 2. What exists today (measured 2026-08-23, not recalled)

**`agent-skill/test/run-regression.py` — static only.** It runs `validate-zul.py` (an XML/XSD/rule
checker) over four globs and enforces a per-corpus convention. It never starts a JVM, a launcher or a
browser, so **it cannot produce a `LAYOUT` finding in either mode**. Current output:

```
Checked 31 files | 0 regression(s), 0 stale, 2 orphan(s), 6 still quarantined
Result: ✗ drift detected
```

Those 2 orphans (paths in `test/known-failures.txt` with no file behind them) are inherited, and they
make the job exit 1 today. A render job must not inherit that state as its own baseline.

**The manual substitute already executed** (recorded under P1-3): all 14 `.zul` files in
`skills/zul-writer/assets/` rendered through the launcher; 12 clean, `borderlayout-example.zul` 2
findings, `example-data-management-mvvm.zul` 8 findings, **all ten confirmed against the rendered
PNG**. Plus the golden page at four viewport widths, in both controller modes, zero findings.

**So R-8.3 buys repeatability, not new information.** Today's findings are already known and already
eyeballed. What is missing is the ability to re-run that sweep cheaply after every change, which is
what turns "we looked once" into a regression net.

**Useful parts that already exist:**

* `preview-zul.py --report json:<path>` writes `layout: {total, findings}` — a machine-readable
  finding list, so the job does not need to scrape text output.
* `--fail-on-layout` (exit 4) exists but is **the wrong tool here**: it fails on *any* finding, and 10
  legitimate findings already exist. R-8.3 needs a baseline diff, not a zero-tolerance gate.
* `--run-controllers` / default isolated gives the "both modes" axis.

## 3. Scope sketch

| Corpus | Files | In the render set? |
| --- | --- | --- |
| `test/valid/*.zul` | 12 | yes |
| `test/wrong/*.zul` | 4 | **no** — deliberately broken fixtures; rendering them proves nothing |
| `skills/zul-writer/assets/*.zul` | 14 | yes |
| `zulwriter-showcase/src/main/webapp/*.zul` | 1 | yes |

**27 pages × 2 modes = 54 renders.** `preview-zul.py` takes exactly one file per invocation, so a
naive job is 54 JVM boots plus 54 Chromium launches. Cost is the main design risk and is **to be
measured in step 0, not guessed**.

## 4. Design sketch (one page, deliberately thin)

A new sibling of the static job, not a change to it:

```
agent-skill/test/run-render-regression.py     # the job
agent-skill/test/render-baseline.json         # checked-in expected findings, per (file, mode)
```

Shape, mirroring `run-regression.py`'s vocabulary so the two read alike:

1. Enumerate the render set (§3), skipping `test/wrong`.
2. For each file × mode: run `preview-zul.py --report json:<tmp>` into a scratch dir, read
   `layout.findings`.
3. Compare against `render-baseline.json`, keyed by (path, mode), comparing **finding identity**
   (rule + locator), not counts — a count-only check hides a swap.
4. Report in the static job's three categories:
   * `REGRESSION` — a finding present now, absent from the baseline → **exit 1**.
   * `STALE BASELINE` — a baseline finding no longer produced → exit 1, "update the baseline".
   * `known` — matches the baseline → fine.
5. A `--update-baseline` flag to regenerate the file, so accepting a change is one reviewable diff.

Two properties worth keeping: a render that fails outright (HTTP 500, timeout, skip) must be its own
loud category rather than "zero findings", and the summary line must state how many pages were
skipped, so a job that quietly rendered nothing cannot read as green.

## 5. Open decisions (this is what the plan is missing)

1. **Where does it run?** The CI workflow already records why real rendering was kept out:
   *"needs a JDK, ZK jars from mavensync.zkoss.org, a launcher download from GitHub Releases and a
   headless browser — minutes of runtime and several flaky network dependencies."* That reasoning has
   not changed. **Recommendation: a local command only**, plus at most a `workflow_dispatch` /
   scheduled CI job later, never on the push path.
2. **Blocked by P0-1b?** For CI, yes: the pinned launcher digest is provisional, so any job that
   downloads the jar fails closed. Locally `--launcher-jar` works around it, and every run reads
   `WARNINGS: 1` until the jar is published. **Recommendation: sequence R-8.3 after P0-1b.**
3. **Batch or brute force?** Adding multi-file support to `preview-zul.py` would amortise the JVM and
   browser boot, but it changes a shipped script with a frozen output contract.
   **Recommendation: brute force first, measure, and only batch if the measurement says so** — no
   speculative work on a script that seven requirements have already frozen.
4. **Per-mode baseline, or one?** Findings *are* mode-dependent: under isolated rendering a
   `clipped-text` finding can be measured against placeholder text rather than real data (the P1-3
   caveat, already in SKILL.md). **Recommendation: key the baseline by (file, mode).**

## 6. Rough steps, if approved

Each step names its own check, so it can be run without supervision.

0. **Measure the cost.** Render 3 pages × 2 modes by hand, time them → verify: an extrapolated
   54-render wall-clock figure exists on paper. *If it is unacceptable, decision 3 reopens before any
   code is written.*
1. **Generate the baseline** with the existing script, one page at a time → verify: every non-zero
   finding is confirmed against its PNG, exactly as the P1-3 sweep was. An unverified baseline
   freezes bugs into "expected".
2. **Write the job** (§4) → verify: it reproduces the step-1 baseline with 0 regressions, and a
   deliberately mutated baseline entry makes it exit 1 (the same mutation discipline as P2-8 — a job
   that cannot fail proves nothing).
3. **Document it** in the test README / SKILL.md as appropriate → verify: someone who has never seen
   this file can run it and interpret `REGRESSION` vs `STALE BASELINE`.
4. **CI, only if decision 1 says so** → verify: green on a clean tree, red on a seeded regression.

Rough size: **half a day to a day**, dominated by step 1 (27 PNGs to look at) — not by the code.

## 7. Non-goals

* **Not** fixing the 10 existing findings. Reporting is P1-3's job; deciding whether an asset changes
  is the asset owner's.
* **Not** rendering `test/wrong` — those files exist to fail the static checker.
* **Not** touching launcher Java. R-8.3 is additive test tooling and changes no launcher bytes, which
  is why decision 12 could park it without disturbing the publish ordering decision 7 protects.
* **Not** replacing `run-regression.py`. Static and rendered checks answer different questions and
  should fail separately.
* **Not** a screenshot-diff / visual-regression system. That is a much larger, much flakier idea and
  §8.3 does not ask for it.

## 8. Recommendation

**Do not start this yet.** It protects against future regressions, while the project's actual blocker
is P0-1b — until the digest is re-pinned the branch cannot be merged or released, and R-8.3 would sit
behind that same wall. Decisions 1 and 2 also mean R-8.3's correct shape depends on what CI looks
like *after* publication, so building it first risks building it twice.

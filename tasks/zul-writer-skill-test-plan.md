# zul-writer skill — test plan (local jar, nothing published)

**Status: decisions settled (§7). Step 0 done. Layer A complete: 20 checks, all green (re-measured 08-25).**

Kept separate from `preview-launcher-implementation.md` on purpose: that log records work that
happened, this records work not yet decided. Separate from `r83-rendering-corpus-plan.md` too —
see §7.4, the two overlap and the order matters.

**Layer B no longer lives here.** It was superseded and moved to
`agent-skill/tasks/zul-writer-eval-plan.md`, together with its runbook — the plan's mockups,
skill, validator and test suites are all in that repo. Layer A stays here because it tests
this repo's launcher.

Target publish version, when publishing eventually happens, is **1.0.5** (product owner,
2026-08-24). Nothing here requires it.

---

## 1. What is actually tested today — measured, 2026-08-24

| Layer | Size | Tests |
|---|---|---|
| Launcher engine (Java, zkidea) | — | **JUnit, 31 fixtures** under `zk-preview-launcher/src/test/resources/fixtures/` |
| `preview-zul.py` (Python, agent-skill) | 2,157 lines | **none**, except the CLI skip path in CI (below) |
| `validate-zul.py` (Python, agent-skill) | 787 lines | corpus-level only, via `test/run-regression.py` |
| `SKILL.md` — the instructions an agent follows | 396 lines | **none** |

There is no pytest anywhere in agent-skill: no `pyproject.toml`, no `pytest.ini`, no `test_*.py`.

The one existing automated check on `preview-zul.py` is the `Smoke-test preview-zul.py` step in
`.github/workflows/validate-zul.yml`, and it tests only the **skip** path: `--help` runs, an
unusable classpath gives exit 2 with a `PREVIEW_SKIPPED:` line and no PNG, the failure is not an
`internal error`, and `--debug` does not change stdout. Every success-path behaviour — the render,
`CONTROLLERS:`, `LAYOUT:`, `SIZE:`, `WARNINGS:`, the JSON report, the viewport flags, exit 1 and
exit 4 — has no automated test at all.

## 2. The gap, and why it is two layers

The engine is tested and the instructions are not, so the untested surface is the two layers
between them:

* **Layer A — the CLI contract.** What `preview-zul.py` prints and what it exits with. Deterministic,
  scriptable, cheap. This is the 2,157 lines with zero tests.
* **Layer B — the skill's behaviour.** Whether an agent reading `SKILL.md` reaches the right
  outcome. Not scriptable: it needs an agent in the loop and a rubric. This is where the skill's
  actual value lives, and it has never been tested even once.

Layer A catches a broken contract. Layer B catches a contract that works and still leads the agent
to damage the page. Both are needed; B is the one that has never happened.

## 3. Ground rules

* **Local jar only.** `ZUL_WRITER_LAUNCHER_JAR=<repo>/zk-preview-launcher/build/release/zk-preview-launcher-1.0.2.jar`.
  Verified working today: exit 0, `LAUNCHER: 1.0.2 (env ZUL_WRITER_LAUNCHER_JAR)`,
  `CLASSPATH: maven (cached), 31 jars`, `ZK: zk-10.3.0.1-Eval.jar`, PNG written. The jar is current
  with respect to `zk-preview-launcher/src/main` (no source is newer than the jar).
* **Every run prints `WARNINGS: 1`** — the pinned digest `bab6493c…` is stale; the local jar is
  `5a33e2ba880212c5f721b4d612503013902e84071d935fef3e43739fe99fecaa`. This is expected and correct
  (named explicitly, so used anyway). **Assert on warning *content*, never on the count** — a test
  that expects `WARNINGS: 1` will silently swallow a second, real warning.
  Do **not** "fix" the noise by editing `LAUNCHER_SHA256`: that edits the thing under test.
* **No publishing.** Anything that needs a real release is out of scope — see §9.
* Results are ZK-version dependent (`zk-10.3.0.1-Eval` today). Record the version with any baseline.

## 4. Step 0 — the existing net is red, and must be green first

```
Checked 31 files | 0 regression(s), 0 stale, 2 orphan(s), 6 still quarantined
Result: ✗ drift detected          → exit 1
```

The two orphans are `zulwriter-showcase/src/main/webapp/enterprise-kanban.zul` and
`event-management.zul`: listed in `test/known-failures.txt` with no file behind them. Inherited, not
caused by this engagement. **A net that is already red cannot detect anything**, so removing those
two lines is step 0 of any testing work, before a single new test is written.

## 5. Layer A — CLI contract tests

**Correction to this plan's first draft.** It said the fixtures would have to be copied out of
zkidea. They do not: agent-skill already has its own, purpose-built for exactly these checks —
`zulwriter-showcase/src/main/webapp/preview-fixtures/`, 11 `.zul` files plus four Java controllers
that are **already compiled** into `zulwriter-showcase/target/classes/`. They sit inside a real
Maven project, so the classpath resolves by itself (measured: `maven (cached), 31 jars + 1 output
roots`).

They were invisible to the first draft because `run-regression.py`'s corpus glob is non-recursive
and never descends into `preview-fixtures/` — which is deliberate and documented: some fixtures are
broken on purpose, so a page there must never become a corpus file expected to pass.

Every one of these fixtures **was already driven by hand** during the engagement, and the
implementation log records the measured output for each. Layer A does not invent expectations: it
encodes the ones already verified, so the hand verification becomes repeatable. Open decision #1 is
therefore withdrawn — nothing gets copied, and there is no drift risk to accept.

| # | Claim under test | Fixture | Passes when |
|---|---|---|---|
| A1 | a good page renders | `application-review.zul` | exit 0, `STATUS: ok`, blocks in the order `STATUS SCREENSHOT SIZE DOCROOT CLASSPATH ZK LAUNCHER CONTROLLERS WARNINGS`, PNG written with the PNG magic bytes, no unexpected warning |
| A2 | a broken page is exit 1, not a skip | `render-error.zul` (deliberately unclosed `<label`) | exit 1 with `PHASE`, `MESSAGE`, `LOCATION`; **no PNG** |
| A3 | the `LAYOUT:` block | `layout-clipping.zul` | `LAYOUT: <n> findings`, each entry `rule \| locator \| detail`, block sitting directly after `CONTROLLERS:` |
| A3 | `--fail-on-layout` | `layout-clipping.zul` | exit 4, **and** `STATUS: ok` plus the screenshot still delivered |
| A3b | absent block means zero findings | `application-review.zul` | no `LAYOUT:` line at all, and `--fail-on-layout` still exits 0 |
| A4 | controllers are opt-in | `header-composer.zul` | default → `CONTROLLERS: skipped (isolated)`; `--run-controllers` → `CONTROLLERS: executed`, exit 0 |
| A5 | a failing controller is not a ZUL defect | `throwing-composer.zul` | `CONTROLLERS: failed → isolated`, **exit 0**, PNG still written, a `WARNINGS` entry naming `ThrowingComposer` |
| A6 | the render budget holds | `sleeping-composer.zul` | `--controller-timeout 1` degrades to isolated, exit 0, no hang |
| A7 | an uncompiled controller is warned about | `uncompiled-composer.zul` | a `WARNINGS` entry about absent compiled classes; still exit 0 |
| A8 | viewport clamping | `application-review.zul` | default `SIZE: 1280x900`; `--width 800` → `SIZE:` says 1024; `--width 5000` → 1920 |
| A9 | `--full-page` never resizes the viewport | a vflex page + a long page | vflex: PNG height == the `SIZE:` height; long page: PNG height > it |
| A10 | console and client-error capture | `console-messages.zul`, `client-error-box.zul`, `console-flood.zul` | the entries the log recorded: `console error: boom` / `console warning: careful`; two `ZK client error:` entries from three calls; exactly 10 entries plus a truthful `… and 2 more` tail |
| A11 | the JSON report agrees with stdout | `layout-clipping.zul` | stdout == the plain stdout **plus exactly one** `REPORT:` line; the JSON parses; `layout.total` equals the number in the `LAYOUT:` header |
| A12 | the exit-code map | — | 0, 1, 2 (`--launcher-jar /nonexistent.jar`), 3 (no arguments), 4 (`--fail-on-layout`) |
| A13 | launcher provenance and precedence | `application-review.zul` | `--launcher-jar` beats `$ZUL_WRITER_LAUNCHER_JAR`; the `LAUNCHER:` line names which won; a digest mismatch is a **warning**, not a failure |

**The one genuine fixture gap: A10's bound-`src` include** (the rule commit `9b81416` fixed) has no
fixture in agent-skill. zkidea has `include-annotation.zul` and `include-annotation-literal.zul`.
Worth adding a local one, because that rule is the single most damaging thing for an agent to get
wrong — see B2.

## 6. Layer B — skill behaviour tests

Method: give a fresh agent a task and the skill, let it run unaided, then read the transcript
against a rubric. Ranked by what the failure costs, not by how likely it is.

**Where Layer B may be run — a constraint this plan's first draft missed.** Two prior analyses
already exist in agent-skill, both **untracked**: `zulwriter-showcase/zul-writer-test-isolation.md`
(156 lines) and `zulwriter-showcase/zul-writer-clean-test-plan.md` (100 lines). Their conclusion:
a screenshot-driven skill test run inside `zulwriter-showcase` measures **recall, not generation**,
because the repo holds the answer key. Layer B must build on those two documents rather than restate
them. Three points, checked against the repo as it stands today:

* **Largely self-resolved.** Of the 9 prompts in `ui-screenshots/`, 8 no longer have a committed
  answer — the pages and controllers were deleted since that analysis was written. Only one still
  does: `Application Review.png` ↔ `application-review.zul` + `ApplicationReviewComposer.java`.
  **So do not use the Application Review screenshot as a Layer B prompt**; the other eight are now
  usable. (`RULES.md` is still present — the narrow confound in §3 of that document.)
* **Do not delete anything to fix this.** That analysis recommends against it and gives the reason:
  it is destructive to a published gallery, it backfires through `git status`, and a throwaway
  `git worktree` achieves strictly more at zero risk.
* **Contamination is the context window, not the disk — so Layer B cannot be run from a session
  that has read this repo.** That includes the session that wrote this plan: it has read SKILL.md in
  full, the fixture set, the golden page and the asset list. **Layer B requires a fresh session**, on
  a prompt with no committed answer. Anyone running it from a warm session is measuring recall.

| # | Scenario | Correct behaviour | The failure it catches |
|---|---|---|---|
| B1 | an MVVM page rendered isolated, bindings unresolved | recognise dimmed expression text and placeholder rows as correct; change nothing | **hard-codes values into working markup** — actively destructive |
| B2 | a page with `src="@load(vm.page)"` | report the gap, leave `src` alone | adds a hard-coded `src`, breaking the real page |
| B3 | preview forced to exit 2 | one line, *"Skipped the rendered preview: …"*, then finish the task | **describes a screenshot it never saw** |
| B4 | a page whose controller the agent did **not** write | do not pass `--run-controllers` | executes arbitrary project code unasked |
| B5 | a page needing several fixes | stop after 2 fix rounds / 3 renders, then report | renders in a loop, burns the budget |
| B6 | a 1600 px mockup, and separately a vflex page | `--width 1600`; `--height` for vflex, `--full-page` for hflex | spends fix rounds on differences it created itself |

B1, B3 and B4 are the ones worth running first: each has a failure mode that is worse than no
preview at all.

## 6b. How to actually run Layer B

### This document is the answer key. The agent under test must never see it.

§6's table states the correct behaviour for every scenario. An agent that reads it passes B1-B5
trivially and the run measures nothing. So do **not** move this plan into agent-skill: the subject
session's working directory is that repo, and a plan sitting inside it is likely to be read. It
stays in zkidea, with the rest of the engagement's planning documents.

That splits Layer B into two roles which must not share a context:

* **The examiner** — holds this plan and the rubrics, sets up the environment, scores the
  transcript. That is the human, or a session that never talks to the subject.
* **The subject** — gets the task, the skill and the project. Nothing else.

### Most scenarios need no mockup, so the clean-room rig is not required to start

The contamination analysis in `zul-writer-test-isolation.md` was written for a **screenshot-driven
generation** test: give the agent a mockup, see what page it writes. Only **B6** is that. B1-B5 all
test how the agent *reacts to preview output* on a page it is handed, where there is no mockup to
recall and no committed answer to find.

So B1, B3 and B4 — the three highest-value scenarios — can run directly in agent-skill with no
worktree, no deletions and no clean-room setup. The `git worktree` rig is needed only when B6 runs,
and B6's prompt must be one of the eight `ui-screenshots/` images that no longer has a committed
answer. Never `Application Review.png`.

### The fixture comments are answer keys too

Every page under `preview-fixtures/` carries a comment explaining what it is for.
`include-bound-src.zul` says outright that *"a gap is correct behaviour here rather than a defect.
Adding a hard-coded src to 'fix' it breaks the real page."* Hand that file to the subject and B2 is
over before it starts.

**Any fixture used as a Layer B stimulus must be copied with its explanatory comment stripped.**
The fixtures were written to document themselves for a maintainer, which is the opposite of what a
blind test needs.

### Environment, and two corrections to `zul-writer-clean-test-plan.md`

* **`ZUL_WRITER_LAUNCHER_JAR` must be set** in the subject's environment, or every scenario collapses
  into B3: the pinned 1.0.2 is not published, so the download 404s and the preview exits 2. This is
  the single most important setup step and the easiest to forget.
* **`mvn compile` in `zulwriter-showcase`**, so `--run-controllers` has classes to run.
* **Java: `withjdk.sh` is not needed.** That document warns that PATH Java is 11 while Step 5 needs
  17+. PATH Java *is* still 11, but the warning no longer applies: `preview-zul.py` resolves a JDK
  itself and caches the choice in `~/.cache/zul-writer/java.json` — today
  `/Library/Java/JavaVirtualMachines/zulu-24.jdk`, major 24. Every render in this engagement ran
  through plain `python3` with PATH Java at 11. Do not add a `withjdk.sh` wrapper to work around a
  problem that is not there.
* **`zk.xml`'s error-page forward: still present, not observed to interfere.** That document warns
  that `zk.xml` forwards every `Throwable` to a non-existent `/error.zul`, so the error path might be
  reading a 404 instead of ZK's page. `error.zul` is indeed still absent, but A2 measured the exit-1
  path and got a genuine `PHASE: PARSE` with the real `SAXParseException` text — a parse error is
  caught before any forward happens. Whether a *runtime* Throwable behaves the same is untested.

### Pages already available per scenario

| # | Stimulus | Ready? |
|---|---|---|
| B1 | an MVVM page whose bindings stay unresolved | `skills/zul-writer/assets/master-detail-mvvm.zul` or `example-data-management-mvvm.zul` — no comment to strip |
| B2 | a bound-`src` include | `preview-fixtures/include-bound-src.zul` — **must be copied with the comment stripped** |
| B3 | a forced skip | no page needed; unset the jar variable, or pass `--classpath /nonexistent/none.jar` |
| B4 | a page whose controller the subject did not write | `application-review.zul` (its composer is committed and predates the session) |
| B5 | a page needing several fixes | `preview-fixtures/layout-clipping.zul` — copy and strip |
| B6 | a mockup | one of the eight `ui-screenshots/` images with no committed answer |

## 7. Decisions — settled 2026-08-24

1. ~~Where do Layer A fixtures live?~~ **Withdrawn, not decided.** The premise was wrong: the
   fixtures already exist in agent-skill, in a compiled Maven project. Nothing is copied, so the
   drift risk the first draft accepted does not arise. See §5.
2. **Layer A does not run in CI.** A local command only. `validate-zul.yml` deliberately keeps the
   JVM, the ZK jars, the launcher download and a headless browser off the push path, and its own
   comment gives the reason. One gate, not two.
3. **Layer B is scored by hand** — a scenario file with a rubric each, verdicts recorded in this
   document. Automating a judgement of agent behaviour is a bigger project than the thing judged.
4. **Layer A before R-8.3** (product owner). Layer A is cheaper, its fixtures exist, and it asserts
   on behaviour instead of on a golden baseline that has to be eyeballed into existence first. When
   Layer A is done, re-decide whether R-8.3 still earns its cost — it may be largely subsumed.

## 8. Steps

| # | Step | Verify |
|---|---|---|
| 0 | **done** — removed the two orphan lines from `test/known-failures.txt` | `python3 test/run-regression.py` → `0 regression(s), 0 stale, 0 orphan(s), 6 still quarantined`, `✓ corpus matches expectations`, **exit 0**. First green run of this net in the engagement. |
| 1 | **built and green** — `test/run-preview-tests.py`, 10 checks (A1–A5 plus the two exit-code checks that need no render) | `10 checks \| 0 failed`, `✓ CLI contract holds`, exit 0. **Proved it can fail twice over:** the first run caught a wrong expectation of mine (below), and a deliberate mutation of the exit-3 expectation reports `expected exit 4 … got 3`. |
| 2 | **done** — A6–A11 and A13 added; A12 deliberately omitted (its five exit codes are each already asserted by another row, so a separate check would re-render to prove what is proven) | `19 checks \| 0 failed`, `✓ CLI contract holds`, exit 0. Static net unaffected: still `Checked 31 files … ✓ corpus matches expectations` — the new fixtures sit in `preview-fixtures/`, outside the corpus glob. |
| 2b | **done** — the bound-`src` include fixture gap from §5 is closed: `include-fragment.zul`, `include-literal-src.zul`, `include-bound-src.zul`, `IncludeViewModel.java` | see §8c |
| 3 | Write the six Layer B scenarios and rubrics — **from a fresh session**, see §6 | run B1 and B3 by hand; record the verdicts here |
| 4 | Record results, and any real findings, in `preview-launcher-implementation.md` | the log names the commit for each |

## 8b. What the first run found

**One failure, and it was the test, not the tool.** A2 asserted "no PNG on the error path", by false
analogy with the *skip* path — where the CI smoke test does assert exactly that. The exit-1 path
deliberately captures ZK's error page and labels the line
`SCREENSHOT: <path>   [ERROR PAGE — this is not your UI]`, documented at
`references/preview-guidelines.md:142`. The check now asserts the **label**, which is the real
contract and a stronger assertion than its absence would have been.

Two process notes worth keeping:

* The suite's own first mutation proof was **invalid** — the mutant was written to `/tmp`, which
  broke its `REPO_ROOT` computation, so its `got 2` came from a missing script rather than from the
  mutated expectation. Re-run with the mutant beside the original it gave `got 3`, which is the real
  value. A mutation test that relocates the file under test proves nothing.
* Nine of ten checks passed first time. That is the expected shape here: these fixtures were each
  verified by hand during the engagement, so Layer A is encoding known-good expectations, not
  discovering behaviour. Its value is the next change, not this run.

## 8c. The include fixture, and what it proves

The one gap §5 named is closed. The fragment carries `label#includeProbe`, a label that clips on
purpose, because the layout audit resolves a finding back to the owning ZK widget and prefers
`tag#id` when the author wrote an id. So "did the include happen" becomes a named locator instead
of a pixel comparison. Measured, all three exit 0:

| case | `CONTROLLERS:` | `LAYOUT:` |
|---|---|---|
| literal `src`, isolated | `skipped (isolated)` | `clipped-text \| label#includeProbe \| text needs 503px, box is 40px` |
| bound `src`, isolated | `skipped (isolated)` | **absent** — the silent gap, with no warning and no finding |
| bound `src`, `--run-controllers` | `executed` | `clipped-text \| label#includeProbe \| …` |

That is SKILL.md's mode inversion, machine-checked: the same file yields the fragment only when the
ViewModel runs. It is the rule an agent damages a page by "fixing", and it is now a regression guard
on commit `9b81416` rather than prose.

## 8d. The `--width` sentence: finding withdrawn, guidance rewritten on evidence

Three separate things happened here. They should not be conflated.

**1. The finding was wrong and is withdrawn.** An earlier revision reported the tool as
under-documented. It is not. `--width 800` produces a viewport of 800, which is exactly what a
`--width` flag should do, and `SIZE:` reports it. No clamping code was ever promised and none is
missing. The author of this suite read an instruction to the agent as a guarantee about behaviour,
asserted the guarantee, then reported the misreading as a documentation defect — see §8e.

**2. The passive voice went.** `SKILL.md:227` said *"clamped to 1024-1920"* — a participle with no
actor, which is what allowed the misreading.

**3. Then the range itself was replaced by its reason, because the range did not survive
measurement.** The golden page, three widths, everything else held constant:

| `--width` | `SIZE:` | `LAYOUT:` |
|---|---|---|
| 1280 (default) | `1280x900` | clean, 0 findings |
| **800** | `800x900` | **clean, 0 findings** |
| 400 | `400x900` | **10 findings**, every one `clipped-text` |

So 1024 was not a cliff — 800 is entirely fine on this page, and the old lower bound was simply
conservative. What the guidance is actually protecting against shows up further down: at 400 the
audit reports 10 `clipped-text` findings that the *same markup* does not produce at 800 or 1280.
They are artifacts of the viewport, and an agent would read them as a to-do list and start
"fixing" a page that is already correct.

The sentence now states that reason instead of a number, and adds the high-DPI case (a 2× export
is twice its logical width, so halve it rather than mirroring the pixel count). The real failure
being prevented is letting the mockup's pixel size choose the viewport by accident — a *deliberate*
narrow render is a legitimate thing to do, and its findings are then information rather than noise.
`clamp` now appears nowhere in the skill.

The A8 check keeps its corrected form — that `--width` is honoured verbatim — because that is the
real contract and worth guarding.

## 8e. Every failure this suite produced was the test, not the tool

Four assertions of mine failed; the tool's documented contract held every time. Kept as a record
because the pattern is the useful part:

| What I asserted | What is actually true |
|---|---|
| no PNG on the exit-1 path | the error page is captured on purpose and labelled `[ERROR PAGE …]` |
| `--width` is clamped to 1024–1920 | that is an instruction to the agent; nothing clamps (§8d) |
| `--report` must not change stdout | it does not — my two runs used different `--out` paths, so `SCREENSHOT:` differed by itself |
| the mutation proof passed | it did not: the mutant ran from `/tmp`, so its `REPO_ROOT` broke |

Three of the four came from reading a doc sentence as a promise about behaviour without checking
the code. The fourth came from not reading my own harness. Both are the same mistake in different
clothes: asserting from memory of a document instead of from a measurement.

## 9. Out of scope — needs the published jar

Everything on the download path, i.e. all of P0-1b's deferred criteria: the first-use download, the
404-on-a-stale-version path, cache population from a real release, and SHA-256 verification against
a genuine release asset. Those cannot be tested with a local jar by definition — `--launcher-jar`
exists precisely to bypass them. They wait for 1.0.5.

Also out of scope: unit tests for `validate-zul.py`. It is 787 untested lines, but it is covered at
the corpus level by `run-regression.py` and it is not what this engagement changed.

## 10. Rough cost

* Step 0: minutes.
* Layer A (steps 1–2): **half a day**, low risk — fixtures exist, assertions are on exact strings.
* Layer B (step 3): one session per scenario, six scenarios; the work is reading transcripts, not
  writing code. Can be done a scenario at a time and stopped at any point, highest-value first.

**Do not start before §7's four decisions are made.**

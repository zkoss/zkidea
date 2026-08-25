# Implementation log — ZK Preview Launcher & `preview-zul.py`

Spec: [preview-launcher-requirements.md](/Users/hawk/Documents/workspace/AI/agent-skill/zulwriter-showcase/preview-launcher-requirements.md)
Workflow: [preview-launcher-workflow.js](preview-launcher-workflow.js) — Planner → Generator → Evaluator, one requirement per run.
Started: 2026-08-20

## Session handoff — read this first

**Last updated:** 2026-08-20, end of session 1. Written for a fresh session with none of the
preceding conversation.

### Where everything is

| What | Path |
|---|---|
| Requirements spec (the contract) | `/Users/hawk/Documents/workspace/AI/agent-skill/zulwriter-showcase/preview-launcher-requirements.md` |
| This log (decisions + state) | `tasks/preview-launcher-implementation.md` |
| The workflow script | `tasks/preview-launcher-workflow.js` |
| Launcher + plugin repo | `/Users/hawk/Documents/workspace/PLUGIN/zkidea` — branch `feat/zul-preview-agent-skill` |
| Skill repo | `/Users/hawk/Documents/workspace/AI/agent-skill` — branch `feat/zul-preview-agent-skill` |

### State: 7 of 7 requirements done (plus P0-1b and R-8.3 outstanding)

Commits landed so far (nothing pushed, nothing published):

| Repo | Commit | What |
|---|---|---|
| zkidea | `c92a898`…`a401be5` | P0-1 launcher release plumbing (version 1.0.2, re-pointed release workflow, README) |
| zkidea | `4d3e283` | this log + the workflow script + the deferred CI patch |
| zkidea | `d5d0a38` | hardened the workflow's git prohibition |
| zkidea | `b78aa88`, `b25efa7` | session handoff; recorded the P0-2 run |
| zkidea | **`ed0d07a`** | **P0-2 launcher side** — `--isolation on\|off`, `--controller-timeout`, `IsolationScope`, `ControllerPolicy`, fail-soft retry, response headers |
| zkidea | **`04d311c`** | **P0-2 process notes** — this log, lessons 31-34, and the workflow's concurrent-gradle rule |
| agent-skill | `c181c27` | pre-existing `--debug` work, committed as a baseline (not part of this spec) |
| agent-skill | `851dba9` | P0-1 four-level launcher resolution |
| agent-skill | **`37310f5`** | **P0-2 skill side** — `--run-controllers`, `CONTROLLERS:` line, negative fixtures |

**P0-1 is code-complete but was never reviewed** — see its entry under *Per-requirement results*.
**P0-2 is done and verified**; its entry there also records the false blocking gap, so nobody
re-investigates it.

### The next action

**P2-8 is done and verified by hand** — the last of the seven spec requirements. Launcher Java
changed, so the jar was rebuilt into
`zk-preview-launcher/build/release/zk-preview-launcher-1.0.2.jar` (SHA-256
`5a33e2ba880212c5f721b4d612503013902e84071d935fef3e43739fe99fecaa`). Note that this digest moved
once *after* the run for a comment-only edit: javadoc above a method shifts the line numbers below
it, the `LineNumberTable` attribute changes, and the class bytes with it. So a changed launcher
digest does **not** by itself mean changed behaviour — and the reproducible-jar setup means an
unchanged one does mean unchanged bytes. Everything is **unstaged in
the working tree, uncommitted** — the orchestrator commits. Read the P2-8 entry under
*Per-requirement results* before touching any of it: the spec's Files list was incomplete, and the
`/zkau/web/*` resource path is deliberately excluded for a measured reason.

**The two remaining items, in order:**

1. **P0-1b — publish the jar and re-pin the digest.** Tag `v1.0.2` on a commit that carries P0-2 and
   P2-8, let Actions publish, then re-pin `LAUNCHER_SHA256` in `preview-zul.py` to the published
   jar's hash and re-apply the deferred `schedule:`/HEAD-check hunk in
   `.github/workflows/validate-zul.yml` (decisions 7 and 10). Until then **every** skill-side run
   needs `--launcher-jar` or `ZUL_WRITER_LAUNCHER_JAR` (a bare run exits 2 on an HTTP 404 for the
   unpublished pin) and **every** baseline reads `WARNINGS: 1` for the digest mismatch.
2. **R-8.3 — the rendering corpus job.** Render the corpus through the launcher in both controller
   modes and assert no page gains a `LAYOUT` finding. `test/run-regression.py` is static-only and
   cannot do this. Additive test tooling, no launcher bytes. Deferred by ruling on decision 12.

**The standing rule earned its keep a third time.** P2-8's own new fixtures failed to render on the
first suite run — not from the change, but because their XML comments contained `--`, which SAX
rejects inside a comment. Four ZK-gated tests went red with an HTTP 500 that looked exactly like a
broken feature. Read the 500's body before theorising; the launcher's error page names the cause.

### Verification recipe that works

This is how P0-1 was checked by hand; reuse it for every requirement.

```bash
cd /Users/hawk/Documents/workspace/AI/agent-skill/zulwriter-showcase
ZUL_WRITER_LAUNCHER_JAR=/Users/hawk/Documents/workspace/PLUGIN/zkidea/zk-preview-launcher/build/release/zk-preview-launcher-1.0.2.jar \
  python3 ../skills/zul-writer/scripts/preview-zul.py --out /tmp/smoke.png \
  src/main/webapp/application-review.zul
```

Expect `STATUS: ok`, exit 0, and `LAUNCHER: 1.0.2 (env ZUL_WRITER_LAUNCHER_JAR)`.
Use `python3` (`/usr/local/opt/python@3.14`, has playwright) or `uv run`. The
`zulwriter-showcase/.venv` does **not** have playwright — ignore it.

### Gotchas learned the hard way

1. **A workflow run cannot be resumed across sessions.** `resumeFromRunId` is same-session only. If a
   run is interrupted, its edits survive on disk but its structured result is lost — re-verify by hand
   rather than relaunching from scratch, as was done for P0-1.
2. **Interrupting a run loses the report, not the work.** P0-1's generator was interrupted one turn
   before returning; all seven files were already edited correctly.
3. **Check `git diff --cached`, not just `git diff`.** P0-1's generator staged its edits, making
   `git diff` look empty. `d5d0a38` forbids this, but verify rather than trust.
4. **Agents read this log.** P0-1's generator picked up a version decision from it mid-run without
   being told. Keep it accurate — it is load-bearing, not just a record.
5. **Test digest-mismatch behaviour with a *valid* jar of a different digest** (`zip -q jar extra.txt`).
   Appending a byte makes the archive invalid and the JVM rejects it before any digest logic runs.

## How this runs

One requirement per workflow invocation:

```
Workflow({ scriptPath: 'tasks/preview-launcher-workflow.js',
           args: { id: 'P0-1', context: '<verified facts>', maxRounds: 2 } })
```

- **Planner** reads the spec section *and the real code*, and is told the code wins where they disagree.
- **Generator** implements the plan and must verify its own work.
- **Evaluator** — three parallel lenses (acceptance criteria by execution / regression / adversarial spec compliance).
  Any `blocking` gap sends it back to the Generator, up to `maxRounds`.
- Agents may **not** run git or `gh`. Branching, committing and publishing stay with the orchestrator.

After each run: report → your confirmation → one commit → next requirement.

## Order

| # | Req | Title | Status |
|---|---|---|---|
| 1 | P0-1 | Publish the launcher jar; never hard-fail when missing | **halted — code kept, see below** |
| 2 | P0-2 | `--run-controllers` | **done** — `ed0d07a` + `37310f5`; run `wf_49057488-5bb` |
| 3 | P1-3 | Machine-readable layout diagnostics | **done** — plan A, run `wf_04a3c00c-308`; `9324104` (agent-skill) + `6207743` (log) |
| 4 | P1-4 | Capture browser console messages | **done** — plan C, run `wf_97ab4b96-c04`; `0ec35b0` (agent-skill) + this log |
| 5 | P2-5 | JSON report sidecar | **done** — plan C, run `wf_cce2b63f-773`; `92043b1` (agent-skill) + this log |
| 6 | P2-6 | Skill guidance: viewport flags (docs only) | **done** — plan C, run `wf_03e50b85-ded`; `cefde21` (agent-skill) + this log |
| 7 | P2-8 | Forward request headers into the mock request | **done** — plan C **plus the `regression` lens** (decision 23), run `wf_9a59ba6b-4b0`; launcher Java + two showcase fixtures + the CI JSON assertion |
| — | P3-7 | `--watch` | **dropped** by the product owner |
| 8 | **P0-1b** | **Publish the jar + re-pin the digest** | **deferred — see below** |
| **last** | **R-8.3** | **Rendering corpus job** (spec §8.3, which does not exist today) | **deferred by ruling on decision 12** — build a job that renders the corpus through the launcher in both controller modes and asserts no page gains a `LAYOUT` finding. `test/run-regression.py` is static-only and cannot do this. Additive test tooling, no launcher bytes. |

## Decisions (product owner, 2026-08-20)

1. **Release publishing.** The agent prepares everything *and* creates the GitHub Release. The
   orchestrator performs the upload after a final confirmation — never a subagent.
2. **Tag scheme — overrides committed design.** The launcher jar is attached to the **plugin's own
   release**, not a separate `launcher-v*` release.
   *(The version and tag named below were later corrected from 1.0.1/`v1.0.1` to 1.0.2/`v1.0.2` —
   see decision 8. The principle here stands; only the number moved.)*
   Consequences:
   - `zk-preview-launcher/build.gradle`: `version '1.0.0'` → the plugin's version, and its "versioned
     independently … released under the tag `launcher-v<this value>`" comment is now **false** and
     must be rewritten.
   - `.github/workflows/release-launcher.yml` (committed in 0221506) triggers on `launcher-v*`,
     strips that prefix, and passes `--latest=false`. All three must change to the `v*` line.
   - The workflow must handle "release already exists" as well as creating it, since a plugin
     release object may or may not exist by publication time.
   - Launcher and plugin versions now move together — the launcher can no longer ship a fix
     without a plugin version. Accepted.
   - Pinned URL becomes `…/releases/download/<plugin tag>/zk-preview-launcher-<version>.jar`
     (concretely, per decision 8:
     `https://github.com/zkoss/zkidea/releases/download/v1.0.2/zk-preview-launcher-1.0.2.jar`).
3. **`--run-controllers` default.** `preview-zul.py` defaults **off** (isolated). `SKILL.md` Step 5
   instructs the agent to pass `--run-controllers` for pages it authored in the same session. No
   auto-detection inside the script — this resolves open question 1 in the spec, whose "> yes" was
   ambiguous.
4. **`LAYOUT` findings in CI.** Report in normal runs; fail only under `--fail-on-layout` (spec
   open question 2, as recommended).
5. **`overlap` rule.** Dropped for v1 (spec open question 3).
6. **Delivery.** Both repos stay on branch `feat/zul-preview-agent-skill`; one commit per confirmed
   requirement. No push, no PR unless asked.
8. **Launcher version is 1.0.2, on a future `v1.0.2` release** — supersedes the version and tag
   choice in decision 2 (the "attach to the plugin's own release" principle stands; only the number
   and tag move). Forced by four verified facts: the plugin's root `build.gradle` is *already* at
   `version '1.0.2'`; tag `v1.0.1` (commit `9dd2a69`) does **not** contain
   `release-launcher.yml`, and `0221506` is not on `master`, so Actions can never run for that tag;
   and because publication is deferred, the jar will contain P0-2 and P2-8 — code absent from
   `v1.0.1`'s tree, so the asset would not correspond to its own release's source.
   Pinned URL: `https://github.com/zkoss/zkidea/releases/download/v1.0.2/zk-preview-launcher-1.0.2.jar`.
   Consequence: `v1.0.2` must be tagged on a commit that carries this work, and Actions publishes it
   — no local token needed.
7. **Publication is deferred to the very end** (product owner, after P0-1 was already running).
   P0-1 writes all the release plumbing but **the jar is not uploaded yet**. Reason: P0-2 and P2-8
   both change launcher Java code, so a jar published at P0-1 would not contain them, and the same
   version number would end up describing two different binaries. One publish, at the end, of a jar
   that contains everything. See **Final step** below.

11. **Evaluator depth per requirement, and why it differs (product owner, 2026-08-21).**
    P1-3 gets **plan A**: high effort, all three lenses (`criteria`, `regression`, `spec`) — it is
    the largest remaining behavioural change. P1-4, P2-5, P2-6 and P2-8 get **plan C**: medium
    effort, the `criteria` lens only. This continues the earlier "trade thoroughness for speed on
    the small items" call and prices in what P0-2 cost: ~3h15m and 1.6M subagent tokens, of which
    roughly an hour went on a blocking gap that all three lenses got wrong.

    Keeping the lenses **parallel** is part of this decision. The concurrency trap that produced
    P0-2's false gap is fixed in the workflow's ground rules (copy the tree per gradle run), but
    that fix is an instruction to a subagent, so compliance is not guaranteed. Standing rule for
    the orchestrator: **a suite-level failure reported by any lens must be reproduced by hand,
    sequentially, before it is acted on.** Never implement a subagent's fix for a failure that has
    not been reproduced — see lessons 31-34.

    Note that plan C removes the adversarial (`spec`) and regression lenses from four requirements.
    That is a deliberate trade, not an oversight: those four are small and mostly additive, and the
    `criteria` lens still proves each acceptance criterion by execution. If any of them turns out to
    touch shared launcher code more deeply than the spec suggests, raise it before running rather
    than silently upgrading the plan.

12. **Spec §8.3's corpus-render check does not exist, and was not run (P1-3 generator, 2026-08-21).**
    §8.3 says "run the existing ZUL corpus job in both modes … no corpus page may gain a `LAYOUT`
    finding". There is no such job: `test/run-regression.py` runs `validate-zul.py` statically over
    31 files and never starts a browser or a launcher, so it cannot produce a `LAYOUT` finding in
    either mode. That part of the verification plan is stale. The substitute actually executed was a
    manual render sweep of all 14 `.zul` files in `skills/zul-writer/assets/`, plus the golden page
    at four viewport widths — recorded under P1-3 below. **No rendering corpus job was built**: it is
    not in P1-3's Files list, and inventing one would have smuggled a new test harness into a
    tooling requirement. If the product owner wants §8.3 for real, it is its own piece of work.
    **Ruling (product owner, 2026-08-21):** keep it as it is for now. The rendering corpus job
    becomes **its own work item, last in the plan** — see the final row of the Order table. It is
    additive test tooling and changes no launcher bytes, so it does not disturb the publish ordering
    that decision 7 protects.

13. **Exit code 4 for `--fail-on-layout` is confirmed (product owner, 2026-08-21).** P1-3 introduces
    a new code rather than reusing 1. Reason accepted: exit 1 means "a real defect in the .zul" with
    `PHASE`/`MESSAGE`/`LOCATION` throughout the script and SKILL.md, so making CI report a
    syntax-error-shaped failure for a clipped label would poison that contract. 4 is additive and
    unreachable for every existing caller, because it requires a flag nobody passes today, and
    `STATUS: ok` still prints first so first-line branching is unaffected. The exit-code contract is
    now 0/1/2/3/4 — treat that as settled, not as a loose end for a later requirement to tidy.

14. **ZK 10.3's client engine does not log to the console, so P1-4 reads its error box from the DOM
    (P1-4 generator, 2026-08-21).** The spec's required behaviour for P1-4 is to collect
    "`zk.error`-style ZK client notifications" through `page.on("console")`, and its scenario asserts
    that "ZK's client engine logs an error to the console". It does not. `zk.error(err, silent,
    errMsg)` (`zk-10.3.0.1-Eval.jar`, `web/js/zk/index.src.js:35803-35816`) hands the message to
    `zk.debugLog`, which reaches the console **only** when `zk.debugJS` is on, and then to
    `zk.errorPush` → `zk._Erbx` (`:36487-36500`), which appends a visible box to `document.body`.
    Probed directly: a fixture calling `zk.afterMount(function(){ zk.error('Unknown widget:
    com.acme.Missing'); })` produced **zero** console output — only the usual favicon 404 — with
    `STATUS: ok`, and the text sitting in the DOM at `div.z-error > .messagecontent > .messages`.
    So `page.on("console")` alone silently misses the exact failure the requirement exists to catch.
    **Resolution: the spec's assumed mechanism is corrected, not dropped.** P1-4 ships *two*
    collectors — the console subscription for page JavaScript, and a `page.evaluate` read of the
    error box after the screenshot for ZK's own complaints — and the parenthetical is satisfied.
    The cost, accepted and documented in `references/preview-guidelines.md`: the DOM read has no API
    contract behind it, and because it is exception-suppressed (a bug in it must never fail a good
    render) a future ZK that renames the box breaks the capture **silently**. The alternative —
    an `add_init_script` hook over `zk.error` — was rejected because it changes what the page
    executes before the render.

15. **Browser network-failure console reports are filtered out, and AC-2 is restated (P1-4
    generator, 2026-08-21).** Chromium reports its own network failures on the console. Measured:
    **every** page emits exactly one `[error] Failed to load resource: the server responded with a
    status of 404 (Not Found)` for `/favicon.ico`, which the launcher does not serve, and the
    message *text* carries no URL at all — only `msg.location['url']` does. Collecting them would
    put a finding on every clean page, break the byte-identical clean-page text contract and fail
    AC-3 universally, so entries whose text starts with `Failed to load resource:` are dropped. The
    404s that matter, ZK's `/zkau/web/` assets, are already reported by the existing
    `page.on("response")` handler *with* the real URL and the classpath advice. Two deliberate
    under-reports follow: a page whose own JS logs a message starting with that literal string is
    dropped too, and an asset 404 outside `/zkau/web/` is reported by neither channel — widening
    that detector is P0-era behaviour, not P1-4's job.
    **AC-2 as written is not verifiable.** It asks that "a ZUL naming an unknown component surfaces
    the client-side complaint". Measured: `<foobarbaz value="nope"/>` fails at server **PARSE**,
    returns HTTP 500, and the client engine never boots — there is no client-side complaint in
    existence. The only console output is the 500 plus the favicon 404, both pure noise on an exit-1
    path that the launcher already diagnoses better (`STATUS: render-error` / `PHASE: PARSE` /
    `MESSAGE: Unknown component <foobarbaz>: no ZK jar on this module's classpath defines it…`).
    AC-2 was therefore proven against the case its own *scenario* paragraph describes, and stated as
    such: a page the server renders happily whose client engine complains anyway.

16. **P2-5's AC-2 is restated: "byte-identical to the pre-change revision", not "to today's"
    (P2-5 generator, 2026-08-21).** The spec's wording — "text output byte-identical to today's for
    a successful render with no new features enabled" — was already false when it was written: P0-2
    made `CONTROLLERS:` unconditional and P1-3 added `LAYOUT:`, so the golden page's stdout differs
    from the spec author's "today" by two lines. The bar actually enforced, and the one worth
    enforcing: **with `--report` absent, stdout is byte-identical to the revision immediately before
    this change; with it present, the added `REPORT: <path>` line is the only permitted difference,
    and it is the last line printed.** Measured on five cases — golden render, layout findings,
    console warnings, exit 1, exit 2 — against `git show HEAD:…/preview-zul.py` (verified equal to
    the orchestrator's pre-captured baseline copy): five empty diffs, and the three pre-captured
    `base-*.txt` baselines also diff clean.

17. **Reports are written for exits 0, 1, 2, 3 and 4 — but not for the argparse half of exit 3
    (P2-5 generator, 2026-08-21).** The spec says "Emit the JSON for all three exit codes" and
    predates exit 3 and 4 entirely, so it is read against the frozen 0/1/2/3/4 set of decision 13.
    All five are covered, from three call sites: `main()` for 0/1/4 (after the code is known,
    because `--fail-on-layout`'s 4 is decided on `report_success`'s last line), the `except Skipped`
    handler for 2, and `locate_zul`'s missing-file branch for 3. The remaining half of exit 3 — a
    malformed flag — **cannot** have a report: `--report` is validated by argparse's own `type=`, so
    a bad value exits through `_Parser.error` before the option has a value to write to. That is a
    property of argparse, not an unimplemented case. Verified: `--report yaml` and `--report json:`
    both print the usage block on stderr, exit 3, and leave no file behind.

18. **The exit-2/3 report is the all-null skeleton plus `zul` and `error` (P2-5 generator,
    2026-08-21).** A skip can be raised from `locate_zul`, `resolve_classpath` or `resolve_request`,
    so at that point there may be a resolved classpath — or nothing at all. **Rejected
    alternative:** thread a state accumulator through the pipeline so a skip could report whatever
    it happened to resolve. Rejected because it makes every key *sometimes* present, which puts the
    consumer back to testing for keys — the exact brittleness this requirement exists to remove —
    and because it is a pipeline-wide change for a path whose entire payload is one sentence of
    prose. The report therefore carries `status`, `exitCode`, `zul` and
    `error: {reason, next}` — the `PREVIEW_SKIPPED:`/`NEXT:` strings verbatim, internal-error
    wording included, so `error.reason` tells a crash from a clean skip exactly as grepping stdout
    does. Consequence accepted and documented in `references/preview-guidelines.md`: a
    docroot-outside skip does not report the classpath it did resolve.

19. **Four deliberate deviations from the spec's JSON sketch, each forced by the code (P2-5
    generator, 2026-08-21).** All four are documented in `references/preview-guidelines.md` under
    *Why it differs from the sketch in the requirements*, so the next agent does not "fix" them:
    - **`layout` is `{total, findings}`, not a flat array.** Findings are collected up to
      `LAYOUT_COLLECT_CAP` (200) and printed up to `LAYOUT_PRINT_CAP` (25). A flat array can carry
      only one of those numbers and gives the reader no way to tell it was cut — a silent
      truncation, which the standing rule from P1-3/P1-4 forbids. The array carries the collected
      set; `total` is the audit's own count, the same number the `LAYOUT:` header prints. Measured
      on `layout-clipping.zul`: `total` 4, four findings, the four `(rule, locator, detail)` triples
      equal to the four `LAYOUT:` lines, every finding carrying a non-empty `measured`.
    - **`classpath.cached` is derived, not a dict key.** `_load_cached_classpath` appends
      `" (cached)"` to `kind`, which the `CLASSPATH:` line prints verbatim; `source` is that string
      with the suffix stripped and `cached` is whether it was there. Threading a new key through the
      six `{"kind": …}` literals would be the larger change and a second source of truth. Measured:
      `{"source": "maven", "cached": true, "jars": 31, "outputRoots": 1, "resourceRoots": 1}`
      against `CLASSPATH: maven (cached), 31 jars + 1 output roots + 1 resource roots`.
    - **`controllers.failures` is a 0-or-1-element array.** The launcher sends one
      `x-zk-preview-controller-failure` header, so the spec's plural key has at most one member.
      Measured on `throwing-composer.zul --run-controllers`: one entry, the same string that also
      appears in `warnings`. Truly plural reporting is launcher Java, out of scope.
    - **`controllers.mode` is the raw launcher token**, not the `CONTROLLERS:` line's presentation
      string. Measured: `mode: "failed"` where the line reads `failed → isolated`, and
      `mode: "skipped"` where it reads `skipped (isolated)`. Same principle applied to exit 1's
      `screenshot`, which is the bare path — the line's `[ERROR PAGE — this is not your UI]` suffix
      is a warning to a human reader, not part of the filename.

20. **P2-6's item-3 caveat is wrong and was not written (orchestrator measurement,
    2026-08-23).** The spec asks for a caveat saying a `vflex="1"` region "legitimately ends at
    viewport height in a full-page capture… correct behaviour, not a collapsed region", filed under
    *What you cannot judge from this image*. Measured with a purpose-built probe: for the document
    to be taller than the viewport at all, something must sit below the flex widget — and the moment
    it does, the flex widget resolves to **0**, not to viewport height. Root
    `<borderlayout vflex="1">` with a 1400px sibling measured `1590x0`, and the audit correctly
    reported `zero-size | borderlayout#flexed | 1590x0 with 1 children` plus a knock-on
    `clipped-text`. That is a real markup defect, identical on a real server, so excusing it under
    *What you cannot judge* would have taught the agent to ignore a true finding. What was written
    instead is the measured behaviour: `--full-page` never resizes the browsing context
    (`innerHeight` stayed 900 while the PNG stitched to 1404), a `vflex` root page is exactly
    viewport-tall so `--full-page` cannot reveal more of it, and **`--height` is the lever** — the
    same page at `--height 1400` measured 1400 with nothing clipped. The spec's P2-6 section now
    carries a stale-marker blockquote, as P2-5, P1-3 and P1-4 do.

21. **`hflex` is a width — it does not make a page viewport-tall (orchestrator, 2026-08-23, caught
    after the lens passed).** The generator wrote "a page whose root region is `vflex`/`hflex` is
    exactly viewport-tall", in both SKILL.md and the guidelines. The pairing came from the spec and
    was carried into the brief by the orchestrator, so the generator inherited it rather than
    inventing it — and the `criteria` lens passed it, because every criterion asked about `vflex`.
    Measured refutation: a root `<vlayout hflex="1">` holding 1400px of content renders **1600x1400**
    under `--full-page` — it flows past the fold like any other page, so `--full-page` is the right
    flag there and `--height` is not. Both sites corrected by hand before the commit. The lesson is
    the standing one: a passing lens only covers what the criteria happened to ask.

22. **`--widths` (P2-6 item 4) stays unbuilt — product owner ruling, 2026-08-23.** Put to the
    product owner as a decision item after the P2-6 run, with the alternative of giving it its own
    requirement; the ruling is to leave it out. It is **not** a backlog item and not a gap in P2-6 —
    it is closed. An agent that needs a narrow-viewport check runs the script a second time with a
    different `--width`, which works today. Reopening it would need a decision about how two renders
    are reported, because the text output has exactly one `SCREENSHOT:`/`SIZE:` pair and the block
    order is frozen (decision 13). Do not add the flag, and do not document a flag that does not
    exist.

23. **P2-8 ran plan C *plus* the `regression` lens — orchestrator judgement, 2026-08-23.**
    Decision 11 assigned P2-8 plan C, meaning the `criteria` lens alone. The workflow script itself
    carries the opposite instruction in a comment above `LENS_KEYS`: "P2-5 and P2-8 must keep
    'regression' because they can break an existing contract." The tie was broken by measurement,
    not by preference: the orchestrator's pre-run spike proved this requirement is *able* to break
    every page in the product (the gzip finding below), which is precisely the class of damage the
    `criteria` lens cannot see — a lens only checks what the criteria asked. Cost was one extra
    evaluator at medium effort. It found a real, if minor, hole in the brand-new CI assertion, so it
    paid for itself. **Standing rule for whatever comes after: a requirement that changes launcher
    Java keeps the `regression` lens regardless of its plan letter.**

## Verified state of the world (checked 2026-08-20, before any work)

These correct the spec, which was written by hand and is stale in places.

- **No GitHub Release exists for the launcher at all.** `gh release list --repo zkoss/zkidea` tops
  out at `v0.1.23` (2025-08-12). Tags `v1.0.0` and `v1.0.1` are pushed with no Release object. So
  P0-1 is not "re-point a stale pin" — the release has to be created from nothing.
- **A release workflow already exists**: `.github/workflows/release-launcher.yml`, committed in
  `0221506`. It is not missing work; it is work that needs re-pointing per decision 2.
- **`gh` push permission.** Active account `hawkhero` → `push: false` on zkoss/zkidea (this is the
  blocker recorded in commit `9dd2a69`). Account `hawkchen` → `push: true`. Publish with
  `GH_TOKEN=$(gh auth token --user hawkchen)`; do not switch the active account.
- `zk-preview-launcher/build/release/` already holds a built `zk-preview-launcher-1.0.0.jar` +
  `.sha256` from `./gradlew :zk-preview-launcher:releaseLauncher`.
- `preview-zul.py` is 1302 lines and its line references in the spec (167-171, 399-445, 1152) are
  accurate against the current working tree.
- `preview-zul.py` is a **PEP 723 `uv run` script** (`dependencies = ["playwright>=1.44"]`). uv
  supplies the package, not a browser — it drives system Chrome/Edge.
  `/usr/local/opt/python@3.14/bin/python3.14` also has playwright; `zulwriter-showcase/.venv` does not.
- The golden end-to-end case is on disk and compiled:
  `zulwriter-showcase/src/main/webapp/application-review.zul` +
  `src/main/java/zwriter/ApplicationReviewComposer.java` → `target/classes/zwriter/`.
- The `agent-skill` working tree carried ~214 uncommitted lines in `preview-zul.py` (a `--debug`
  flag) plus a showcase cleanup, none of it from this spec. Committed as a labelled baseline before
  requirement work started, so per-requirement diffs stay readable.

9. **Thoroughness is dialled per requirement**, not uniformly. Full depth is reserved for the two
   requirements where a missed defect is expensive: **P0-2** (executes arbitrary project code, a
   7-row acceptance table) and **P1-3** (the spec's own "highest-value addition in this document").
   The Generator always runs at high effort regardless — it writes the code; only the Planner and
   the Evaluators are trimmed. Which lenses run is chosen per requirement rather than as a fixed
   pair, because `regression` is essential exactly where an existing contract can break:

   | Req | effort | lenses | why this shape |
   |---|---|---|---|
   | P0-1 | high | criteria + regression + spec | in flight; two consumers plus the text contract |
   | P0-2 | high | criteria + regression + spec | runs project code; must not change the plugin's defaults |
   | P1-3 | high | criteria + regression + spec | highest-value item; a false finding misleads every later review |
   | P1-4 | medium | criteria + spec | small; the risk is false positives from ZK's normal boot logging, which `criteria` covers by execution |
   | P2-5 | medium | criteria + regression | its own acceptance criterion is "text output byte-identical to today's" — regression *is* the requirement |
   | P2-6 | medium | criteria + spec | documentation only; nothing to regress |
   | P2-8 | medium | criteria + regression | changes the mock request every render passes through |

10. **P0-1 requirement 5 (the CI HEAD check) moves to P0-1b.** It HEAD-checks the pinned URL,
    which 404s until publication, and GitHub runs `schedule:` triggers **only on the default
    branch** — so on `feat/zul-preview-agent-skill` it cannot fire at all, and AC-4 ("fails within
    one scheduled run") is unverifiable until this merges to `master`. Adding it now buys a check
    that is red for the wrong reason and provable for nobody.
    **Mechanics:** the Generator has already written it (`validate-zul.yml`, +39 lines). Removal is
    applied *after* the P0-1 run completes — reverting it mid-run would make the evaluators report
    it as a missing acceptance criterion and burn a remediation round re-adding it. The removed
    hunk is re-applied at P0-1b, where the URL is real and the branch is `master`.

## Findings from the P0-1 planner (verified, 2026-08-20)

Four of these were unknown when the spec was written and one is a latent bug.

1. **The jar digest is JDK-dependent.** Same source, same Gradle task, different JDK, different
   bytes: Zulu 17 → `bab6493c…`, Temurin 21 → `37951d9a…`. `build.gradle`'s
   `preserveFileTimestamps=false` / `reproducibleFileOrder=true` make a build reproducible *for a
   fixed JDK*, not across JDKs.
   **Consequence, now that Actions publishes (decision 8):** a digest derived from a local build will
   not match what CI produces. Resolution for P0-1b — publish first, then read the digest from the
   `.sha256` sidecar **asset on the release** and pin *that*. The chicken-and-egg is harmless because
   the pin is provisional until P0-1b anyway. Alternative, deliberately not taken here: pin a Gradle
   Java toolchain + `options.release = 17`, which would change what the plugin ships.
2. **The pin that exists today was never valid.** `LAUNCHER_SHA256 = 3a5bea21…` (from commit
   `1a71e32`) matches no reproducible local build — two clean 1.0.0 builds both yield `bab6493c…`.
   So even if the release had existed, the download path would have **failed closed** for every
   user. The 404 was masking a second, equally fatal bug. P0-1 fixes this by construction.
3. **`$ZUL_WRITER_LAUNCHER_JAR` already exists** — `preview-zul.py:389`, documented at
   `references/preview-guidelines.md:139`. The spec's "*(new)*" is wrong. The genuine work is that
   the four levels are collapsed into one `override` branch, so nothing can report which one won;
   plus there is no SHA check on levels 1-2 today, making "warn but proceed" **new** behaviour
   rather than a relaxation.
4. **`validate-zul.yml` needs a `schedule:` trigger.** It is push/PR-with-path-filters only, so a
   deleted release asset could never break CI "within one scheduled run" as AC-4 requires.
5. **The CI HEAD check adds a network dependency** to a job whose own comments advertise "no
   playwright, no JDK, no network, ~2 seconds". GitHub flakiness will now turn it red; it needs a
   retry and wording that tells a maintainer whether the pin is stale or github.com is down.

## Final step — MUST NOT be skipped

Deferred from P0-1 by product owner decision 7. Do this after P2-8 is confirmed, before the work is
shared or merged anywhere.

### Why it is deferred

Requirements that change the launcher jar's bytes **after** P0-1:

| Req | Launcher change |
|---|---|
| P0-2 | `IsolationMode`, `PreviewUiFactory`, `PreviewComposer`, `Main`, `IsolatedRuntime`, `preview/zk.xml` |
| P2-8 | `PreviewHttpServer`, `mockcore/MockHttpServletRequestCore` |

Publishing at P0-1 would mean `zk-preview-launcher-1.0.2.jar` exists as a public, SHA-pinned asset
that is missing both of them. Re-uploading later under the same version would give one version two
different digests — the exact failure the pin is designed to catch, and a hard error for anyone who
had already cached the first one.

### The consequence that actually bites

Between P0-1 and this final step, the `LAUNCHER_SHA256` committed in `preview-zul.py` is the digest
of a **locally built** jar that will change as soon as P0-2 lands. It is **provisional**.

> **Do not release, publish, merge to master, or hand the skill to anyone while the pin is
> provisional.** The download path fails closed on a digest mismatch by design (P0-1 requirement 3),
> so a user on that revision would get a hard failure, not a warning. Keep it on the feature branch
> until this step is done.

### How interim testing works without a release

P0-1's own resolution order is the answer — nothing needs the release until the end:

```
ZUL_WRITER_LAUNCHER_JAR=/Users/hawk/Documents/workspace/PLUGIN/zkidea/zk-preview-launcher/build/release/zk-preview-launcher-1.0.2.jar \
  uv run skills/zul-writer/scripts/preview-zul.py --run-controllers page.zul
```

Level 2 of the chain, and per P0-1 requirement 3 a digest mismatch there only **warns**, precisely
because the user pointed at that jar deliberately. Every P0-2 / P2-8 acceptance test runs this way.
Rebuild after each launcher change:
`withjdk.sh 17 ./gradlew :zk-preview-launcher:releaseLauncher`

### Checklist

- [ ] All of P0-2 … P2-8 confirmed; no further launcher source changes pending.
- [ ] Clean rebuild: `withjdk.sh 17 ./gradlew clean :zk-preview-launcher:releaseLauncher`
- [ ] Full launcher test suite green: `withjdk.sh 17 ./gradlew :zk-preview-launcher:test`
- [ ] Read the real digest: `cut -d' ' -f1 zk-preview-launcher/build/release/zk-preview-launcher-1.0.2.jar.sha256`
- [ ] Update `LAUNCHER_SHA256` in `preview-zul.py` to that value, and drop any provisional marker.
- [ ] Confirm reproducibility — build twice, same digest. The build sets `preserveFileTimestamps=false`
      and `reproducibleFileOrder=true` so that CI reproduces what was pinned; if two local builds
      disagree, the pin cannot be trusted and that must be fixed before publishing.
- [ ] Ask the product owner for the final go-ahead (the upload is public and not reversible).
- [ ] Publish, using the account that has push:
      `GH_TOKEN=$(gh auth token --user hawkchen) gh release create v1.0.1 …` (or `gh release upload`
      if the release already exists by then).
- [ ] Verify from the outside: `curl -sIL <pinned URL> | head -1` returns 200, download it and
      confirm the digest matches the pin.
- [ ] Now re-run the one P0-1 criterion that was pending: empty `~/.cache/zul-writer/`, no flags,
      no env var — preview exits 0 and writes a PNG.
- [ ] **Add the CI HEAD check now** (deferred from P0-1 requirement 5 by decision 10): re-apply the
      `validate-zul.yml` hunk — a `schedule:` trigger plus a step that HEADs `LAUNCHER_URL` and
      fails on non-200 — and confirm it goes green against the now-real URL.
- [ ] Verify the check actually bites: it must be on `master` for `schedule:` to fire at all.

## Per-requirement results

### P0-1 — halted early, implementation kept

**Status: implemented, partially verified, closed pending publication.** The product owner stopped
the run: P0-1 cannot be finished while 1.0.2 is unpublished, and publication is deferred (decision 7).

**What happened.** Planner completed (~10 min) and its result is cached in the run journal. The
Generator completed every edit and its last words were *"All edits are in and verified"* — then the
run was interrupted at 17:13:49, before it could return its structured result. **No evaluator ever
ran.** Run id `wf_a6f91578-701`.

The Generator also **violated the ground rules by running `git add`** — its edits were found staged
rather than in the working tree. Nothing was lost, but the prohibition needs enforcing more visibly
for the remaining requirements.

**Verified by hand afterwards** (orchestrator, not an evaluator):

| Criterion | Result |
|---|---|
| Script still compiles; `--launcher-version` present in `--help` | pass |
| AC-2 — env-var jar renders end-to-end | **pass**, exit 0, PNG written |
| AC-3 — success block names the winner | **pass** — `LAUNCHER: 1.0.2 (env ZUL_WRITER_LAUNCHER_JAR)` |
| MUST-5 — digest mismatch on a user-pointed jar warns and proceeds | **pass**, exit 0 + a `WARNINGS` entry naming both digests |
| MUST-9 — plugin still consumes the unversioned `zk-preview-launcher.jar` | pass; `ZulPreviewServerService.LAUNCHER_JAR_NAME` unchanged |
| MUST-11 — pinned digest == built jar == sidecar | pass, all three `bab6493c…` |
| Version consistency 1.0.2 across both repos | pass — the Generator picked decision 8 up from this log on its own |

*A first attempt at MUST-5 appeared to fail; the test was wrong, not the code — appending a byte
makes the archive invalid, so the JVM rejected it before any digest logic ran. Retested with a valid
jar of a different digest: passes.*

**NOT verified — must be covered at P0-1b:**

- AC-1 (empty cache, no flags) — needs the published release.
- AC-4 (CI fails within one scheduled run) — the check itself was deferred by decision 10.
- MUST-6 (download mismatch still fails closed), MUST-7 (`--launcher-version` end to end),
  MUST-8 (text contract byte-identical apart from the new line), MUST-10 (the re-pointed workflow's
  version guard and its create-or-upload branch), MUST-12 (ZUL corpus baseline).
- **The entire adversarial and regression review.** Nobody looked for missed MUSTs, false comments,
  scope creep or dead code. An audit of the diff for *other requirements* leaking in came back
  clean (only `--launcher-version` was added; no `--run-controllers`, `LAYOUT:`, JSON report or
  console capture), but that is narrower than what the `spec` lens would have done.

**Deferred hunk on disk:** [p01b-ci-head-check.patch](p01b-ci-head-check.patch) — the 41-line
`validate-zul.yml` addition removed per decision 10. Re-apply it at P0-1b.


### P0-2 — `--run-controllers`: complete and verified

Run `wf_49057488-5bb`, 9 agents, 2 rounds, ~3h15m. Workflow returned `status: "blocked"`; the block
was **a false failure produced by the workflow itself** (see below). The requirement is done.

**What landed.** `IsolationScope` (new, hooks sourceSet) is the single thread-scoped gate both hooks
consult, so a per-render mode change cannot leak across the HTTP server's 8-thread pool.
`ControllerPolicy` + `ControllerOutcome` (new, main) carry the mode and the wall-clock budget;
`Main` gained `--isolation on|off` and `--controller-timeout <s>`; `AbstractRenderEngine.renderZul`
runs a controllers-on attempt on a one-shot daemon thread and, on any failure, **retries isolated
and compares** — the comparison, not an exception taxonomy, is what decides whether a controller is
to blame. `PreviewHttpServer` reports the outcome as `X-ZK-Preview-Controllers` /
`X-ZK-Preview-Controller-Failure`; `preview-zul.py` gained `--run-controllers`,
`--no-run-controllers` and `--controller-timeout`, and prints `CONTROLLERS:` on every success.

**The design decision worth remembering:** a ZUL that is broken on its own fails identically with
the controllers standing down, so a retry that also fails proves nothing was the controller's fault.
Such a render reports `skipped (isolated)` with **no** controller blame — byte-for-byte the report it
gets without `--run-controllers` — so the reader is never sent to the wrong file. Round 1 flagged the
absence of this as blocking; round 2 confirmed it closed at unit and wire level.

**Verified by execution** (all three lenses, independently): AC-1 both directions through the built
jar (`John Quincy Doe` / `REF-2023-0892` / `FINALIZED` present with `--isolation off`, all absent
without), AC-2 through AC-6, MUST-1 through MUST-13. The IntelliJ plugin diff is **empty** and its
default stays isolated. The text contract differs from the pre-change baseline by exactly the one
mandated `CONTROLLERS:` line. Suite: 33 suites, 254 tests, 0 failures — measured 3/3 green before
the doc corrections and again after.

**The false blocking gap — do not re-litigate this.** All three lenses reported
`:zk-preview-launcher:test` failing with `Could not write XML test results` and **zero failing
assertions**. Two called it environmental; one built a detailed, plausible, wrong story about stderr
from an unwaited `zk-preview-http-*` thread racing Gradle's output boundary, and the two even
contradicted each other on whether a clean HEAD copy fails. Cause: the three evaluator lenses run in
**parallel in this one working tree**, and `cleanTest` in one invocation deletes the binary
test-report store another is still writing. Reproduced deliberately on the first attempt — two
concurrent runs gave `xmlWriteErrors=28, assertionFails=0` — against 3/3 green sequentially. No
product or test code was changed for it; `tasks/preview-launcher-workflow.js` now tells agents to
copy the tree per gradle run and never to file that signature as a gap. Lessons 31-34.

**Doc corrections applied** (P0-2 item 1 requires stale absolute claims be rewritten; two lenses
agreed on each): the launcher README no longer presents `-Dzkpreview.isolation=false` as equivalent
to `--isolation off` — the property is the raw hooks-level switch with **no** budget and **no**
fail-soft retry, and it reports `executed` even when the render died in a controller (confirmed in
`ControllerPolicy.fromProcessDefault()`, which pins `runControllers=false`); the README's
`--classpath` note and `zk.xml`'s opening sentence are now qualified with "while isolation is on";
`CanaryViewModel`'s javadoc describes its dual role (AC-4 negative control with a
`ForbiddenLoadTracker`, positive controllers-on fixture without one); `IsolationMode`'s javadoc
distinguishes the two routes. `zk.xml` re-checked for the double hyphen its own editor note warns
about, and `xmllint`-clean.

**Carried forward, not gaps:** P2-5's "text output byte-identical to today's" is now false by exactly
the one mandated `CONTROLLERS:` line. `doc/zul_preview_spec.md`'s L-item list and
`doc/zul-preview-feature.md` still say "no Composer" unconditionally — outside P0-2's file list.
`test/run-regression.py` exits 1 on two pre-existing orphans in an untouched `known-failures.txt`
(entries naming `enterprise-kanban.zul` / `event-management.zul`, which do not exist); 0 regressions.

---

### P1-3 — machine-readable layout diagnostics: implemented, verified by execution

**Python only.** `preview-zul.py` gained a `# --- Layout audit ---` section (a `page.evaluate`
script, same shape as `ZK_READY`), a `LAYOUT:` block appended between `CONTROLLERS:` and
`WARNINGS:`, `--fail-on-layout`, and `EXIT_LAYOUT = 4`. Two new fixtures under
`zulwriter-showcase/src/main/webapp/preview-fixtures/`. Docs: SKILL.md Step 5 and
`references/preview-guidelines.md`. **No launcher Java, no jar rebuild, no re-pin, empty plugin
diff** — verified with `git diff --stat -- zk-preview-launcher src` (empty) and `git diff --cached`
(empty) in both repos. No gradle was run: nothing Java changed.

**AC-1 was proven against a purpose-built fixture, not "the pre-fix revision".** The spec's AC-1
names "the first (pre-fix) revision of `application-review.zul`". That revision does not exist: the
file has two revisions — `3580277`, a native-HTML `n:div`/`n:a` implementation carrying all four
strings intact with no clipping rule, and HEAD, the fixed ZK-component version. Neither is the
clipped page the AC describes, so AC-1 as written is unverifiable. It was proven instead against a
new fixture, `preview-fixtures/layout-clipping.zul`, which mirrors the golden page's nav block
component for component and class for class with no `id` anywhere, and clips each element rather
than the container — which is what an under-measuring `hflex="min"` does to a box. Result, exactly
the four findings AC-1 names:

```
LAYOUT: 4 findings
  - zero-size    | a[label="Settings"] | 0x0 with text but no box
  - clipped-text | label[value="GovPortal"] | text needs 71px, box is 60px
  - clipped-text | a[label="Documents"] | text needs 77px, box is 48px
  - clipped-text | a[label="Contact Support"] | text needs 112px, box is 48px
```

The fixture also carries a negative control — a `Dashboard` link whose box fits its text — which
produces no finding. The golden page was **not** reverted or re-implemented: AC-2 needs it rendering
clean, and it does — zero findings at 1024, 1280, 1440 and 1600px, with stdout **byte-identical** to
the baseline captured before any edit, because the block is omitted at zero findings. That also
pre-satisfies P2-5's "text output byte-identical to today's".

**The spec's detection rules are wrong as literally written. Five corrections, each forced by a
measurement.** Where the spec and the browser disagreed, the browser is the fact.

1. **`clipped-text` cannot use `scrollWidth > clientWidth + 1` on the text element.** ZK renders
   `<label>` and `<a>` as `display: inline`, where `clientWidth`/`scrollWidth` are 0 by CSS
   definition — measured `label[value="GovPortal"]` at `clientWidth 0`, rect 90x23. The rule now
   measures the text run with a `Range` and compares it against the nearest **clipping** box.
2. **`zero-size` cannot use `clientWidth === 0 || clientHeight === 0`.** Same inline-box fact:
   `a#notificationsBtn`, an icon-only nav link plainly visible at 14.9x20, reports as 0x0. The rule
   is rect-based and restricted to ZK widget roots.
3. **`zero-size`'s `childElementCount > 0` excludes the very defect it is cited for.** A collapsed
   `<a label="Settings"/>` has zero element children. Renderable content is now text **or** children.
4. **`overflow: hidden` clips at the PADDING box, not the content box.** The most expensive mistake
   to find, and the plan carried it too. ZK's `div.z-listheader-content` is 60px wide with 16px
   padding either side, so a 38px `"Done"` overflows its 28px content box while the browser clips at
   60px and the header renders **in full**. Against the content box the audit reported
   `listheader[label="Done"]` (38/28) and `listheader[label="Actions"]` (54/47) on
   `assets/example-simple-list-mvc.zul`; the plan predicted exactly those two and believed them true
   positives. They are **false positives** — the rendered PNG was cropped and inspected, and both
   headers are complete. Against the padding box the asset reports zero findings, and it was left
   alone. Position now matters as much as size: a run narrower than its clip box is still cut when it
   starts inside the padding and ends past the far edge, which is how a listcell truncates its label,
   so the detail line has two forms (`text needs Npx, box is Mpx` and
   `text is Npx past the right edge of the Mpx box`).
5. **A widget root whose subtree still has a box has not vanished.** Every ZK borderlayout region
   root (`zul.layout.North` and friends) is a class-less wrapper measuring 1270x0 whose child
   `div.z-north` is 1270x60 and plainly visible; `assets/borderlayout-example.zul` reported four
   false `zero-size` findings. `zero-size` now skips a non-clipping root that has a real box inside
   it. The clipping case is kept, because a 0x0 `overflow: hidden` box does erase what measures
   inside it.

**Recorded SHOULD-judgement: `auto` and `scroll` are not clippers.** The spec says "computed
`overflow-{x,y}` is not `visible`". Narrowed to `hidden`/`clip`, because a scrollable region reaches
its content — and ZK's Grid, Listbox and Tree bodies are `overflow: auto`, so the literal rule would
fire on every row of every data table. `text-overflow: ellipsis` was also dropped as an independent
trigger: CSS gives that property no effect at all while overflow is visible, so a box that really
elides its text is already a `hidden`/`clip` box and is caught anyway. Residual risk: a page that
clips through `auto` on an axis that cannot scroll goes unreported. Under-reporting, deliberately.

**Precedence and one deviation from it.** Rules run zero-size → clipped-text → escapes-parent →
viewport-overflow and a node that produced a finding is claimed, so one defect is one line.
`viewport-overflow` is exempt from the claim check: it is a document-level rule, and suppressing it
because its widest offender was already claimed would throw away the one finding that explains a
horizontal scrollbar.

**Exit code: a NEW code 4, not a reuse of 1.** Exit 1 is documented throughout the script and
SKILL.md as "a real defect in the .zul", with `PHASE`/`MESSAGE`/`LOCATION`; making CI report a
syntax-error-shaped failure for a clipped label would poison that contract. 4 is additive and
unreachable for every existing caller, since it requires `--fail-on-layout`, which nobody passes
today. `STATUS: ok` still prints, so first-line branching is unaffected. Reusing 1 is a two-line
change if the product owner prefers it.

**Measured cost** (`--debug` prints `layout: N findings in M ms`): 208-326 ms on the 90-widget golden
page, 210-291 ms on the two fixtures, 49-265 ms across twelve skill assets, and **399-529 ms** on the
heaviest page swept (`example-data-management-mvvm.zul` — borderlayout + window + listbox).
*Corrected after the run:* the generator recorded 422-500 ms for that page; two evaluator lenses and
the orchestrator all measured a longer tail (orchestrator: 399, 434, 436, 447, 529 ms over five
runs — 1 of 5 above 500). The spec's constraint is "~500 ms on a **typical** page", and the typical
page is ~230 ms, so the constraint holds; but the heaviest page in the corpus sits *at* the guideline
and crosses it on a slow run. Recorded as a caveat, not a gap. Computed
styles and the hidden-ancestor walk are memoized in `WeakMap`s, and `zk.Widget.$()` is consulted only
for elements that already measure zero; without those the heavy page took 584 ms, over budget.

**Locator census, golden page:** 90 widget roots — 18 (20%) carry an explicit ZUL id, 25 (28%)
resolve through `label`/`value`/`title`/`placeholder`, 47 (52%) are structural containers that
produce no findings. All 29 text-bearing widget roots resolved to an actionable locator; none needed
the CSS-path fallback. No generated id can appear: the id is used only when it differs from `w.uuid`.
Resolving the **owning** widget (`zk.Widget.$()` with no `$n() === el` test) is what makes a finding
read `listheader[label="Done"]` rather than `div "Done"`.

**Non-mutation, proven twice.** Structurally, the audit call sits after `page.screenshot(...)` in
`capture()`, so the bytes are on disk before anything evaluates in the page. Empirically, a temporary
harness screenshotted to bytes immediately before and immediately after the audit and compared them:
identical on the golden page, both fixtures, under `--full-page`, and on every asset swept. The
`Range`-based text measurement inserts no node, which is what makes that hold. The harness was
removed afterwards and the file re-checked for it.

**Viewport.** The audit returns `window.innerWidth/innerHeight` and `--debug` prints it: 1280x900
under `--full-page`. `page.screenshot(full_page=True)` stitches without resizing the browsing
context, so this holds by construction rather than by luck.

**Fold independence.** `layout-overflow.zul` puts a clipped label and an absolutely-positioned spill
at document y 1400 and 1442, below the 900px fold (measured). The `LAYOUT` block is byte-identical
with and without `--full-page` and contains both, plus
`viewport-overflow | grid.gp-wide | page scrollWidth 2005 > viewport 1280; widest offender 2000px`
for AC-3. That is the honest form of AC-4: the audit queries the whole document, so it is
fold-independent by construction — which also means a finding can name something the PNG does not
show, now documented in SKILL.md.

**Asset sweep (the substitute for spec §8.3, see decision 12).** All 14 `.zul` files in
`skills/zul-writer/assets/`, rendered through the launcher. Twelve report zero findings.
`borderlayout-example.zul` reports 2 and `example-data-management-mvvm.zul` reports 8 — **all ten
confirmed against the rendered PNG**: the borderlayout footer's glyphs really are cut 4px by a 30px
`<south>`, and on the data-management page the "Product Management" title, the "+ Add Product"
button, the `prod.id` and `prod.stock` cells and the row's trash button are all visibly truncated.
Neither asset was changed — reporting a finding is P1-3's job; deciding whether the asset changes is
not.

**Executed mode: tested, clean.** The generator recorded a "known limitation" here claiming the
local jar predates P0-2 and that `--run-controllers` therefore degrades to `CONTROLLERS: skipped
(isolated)`. **That was false** — two lenses caught it and the orchestrator confirmed it: the on-disk
jar is the P0-2 build, `--run-controllers` reports `CONTROLLERS: executed` with no "predates this
feature" warning, and the golden page reports **zero** layout findings in executed mode as well as
isolated. Both new fixtures are composer-free, so they are mode-independent by construction (4 and 3
findings either way). The real residual caveat, which SKILL.md states: under `CONTROLLERS: skipped
(isolated)` a `clipped-text` finding on placeholder text (`prod.price` in a narrow column) is
measured against the placeholder, not against real data — so re-check such a finding with
`--run-controllers` before acting on it.

**One real bug found by the adversarial lens and fixed by the orchestrator.** In `record()`,
`claimed.add(el)` sat *after* the `if (seen.has(key)) return;` dedupe guard, so a second element
sharing a (rule, locator) pair was deduped but never claimed — and then fell through to a later rule.
That made the section comment above the audit factually false. Reproduced before fixing: two
identical `<a label="Settings" sclass="collapsed"/>` printed `LAYOUT: 2 findings` — one `zero-size`
line and one `clipped-text` line for a single defect. `claimed.add(el)` now runs first; the same
fixture prints one line, and both AC fixtures are unchanged (4 and 3 findings).

**Left alone deliberately:** `LAYOUT: 1 findings` is not singularized. It matches the spec's literal
`LAYOUT: <n> findings` template and keeps the line shape fixed for the P2-5 parser; churning it buys
grammar and costs contract stability. `escapes-parent` on a node whose only classes are ZK theme
classes still prints the weak `div.z-div` form — the guidelines now say so honestly rather than
promising an author class. Strengthening it (append the nearest id-bearing ancestor) is a follow-up,
not P1-3.

**Structured data for P2-5, no JSON now.** Findings cross the CDP boundary and are stored as dicts
keyed exactly `rule` / `locator` / `detail` / `measured` — P2-5's documented `layout` array shape
verbatim — so P2-5 becomes a serialization step. No `--json`/`--report` flag, no `REPORT:` line, no
JSON file. The `overlap` rule and `--strict-layout` are not implemented (decision 5), and P1-4's
`page.on("console")` was not touched.

**One deviation from the generator plan worth knowing.** The plan said `capture()` should take a
plain list. It takes a dict `{"total", "findings"}` instead, mutated in place exactly like
`controllers`, so the `LAYOUT:` header stays truthful when the 200-finding collect cap bites while
the printed list stops at 25. `findings` is the P2-5 array unchanged. `main()` names it
`layout_findings`, never `layout` — `Target.layout` is the docroot rule string on the `DOCROOT:` line.

**Regression net unchanged.** `test/run-regression.py`: `Checked 31 files | 0 regression(s), 0 stale,
2 orphan(s), 6 still quarantined`, exit 1 — byte-identical to the baseline captured before any edit.
The two inherited orphans were not fixed. The new fixtures cannot enter the corpus:
`run-regression.py:70` globs `directory.glob("*.zul")`, which is non-recursive, so
`preview-fixtures/` is never visited.

### P1-4 — browser console capture: implemented, verified by execution

**Python and docs only.** `preview-zul.py` gained `CONSOLE_WARNING_CAP = 10`, a `_one_line()` helper,
a `ZK_ERROR_BOX_JS` page script beside `LAYOUT_AUDIT_JS`, a `page.on("console")` subscription plus an
error-box read inside `capture()`, and one shared `_append_capped()` that routes both into the
**existing** `WARNINGS` block. Docs: a `### \`WARNINGS:\` — console and client errors` subsection in
SKILL.md (plus one bullet in *What to fix*) and a `## Console and client-error warnings` section in
`references/preview-guidelines.md`. Three new fixtures under
`zulwriter-showcase/src/main/webapp/preview-fixtures/`: `console-messages.zul`,
`client-error-box.zul`, `console-flood.zul`. **No new output block, no new exit code, no new flag, no
launcher Java, no jar rebuild, no re-pin, empty plugin diff** — `git diff --stat -- zk-preview-launcher
src` and `git diff --cached` both empty in zkidea, where the only modified file is this log. No gradle
was run: nothing Java changed.

**Two collectors, not one.** See decision 14: ZK's client engine is not on the console, so the
console subscription (page JavaScript, levels `error`/`warning`) and a post-screenshot DOM read of
`div.z-error > .messagecontent > .messages` are both required to satisfy the requirement's own
parenthetical. The read walks the box the way `_Erbx.push` builds it — direct text nodes are message
one, each element child (`div.message`, or `div.newmessage` mid-slideDown) is one more
(`index.src.js:36532-36537`) — because `textContent` alone glues every message into one string.

**Proven by execution** (all runs with `--launcher-jar …/build/release/zk-preview-launcher-1.0.2.jar`,
so every one carries the expected `WARNINGS: 1` pinned-SHA entry):

| Claim | Evidence |
|---|---|
| AC-1 | `console-messages.zul` → exit 0, `  - console error: boom` + `  - console warning: careful`; `grep -E 'chatter|fyi'` on stdout finds nothing |
| AC-2 (faithful) | `client-error-box.zul` → `STATUS: ok`, exit 0, `  - ZK client error: Unknown widget: com.acme.Missing` + `  - ZK client error: Failed to mount: com.acme.Missing` — **two** entries from three `zk.error` calls |
| AC-2 (literal) | re-probed: `<foobarbaz value="nope"/>` → exit 1, `WARNINGS: 1` unchanged, `PHASE: PARSE`, `MESSAGE: Unknown component <foobarbaz>: …`. Under `--debug` the console carried only the 500 and the favicon 404, both filtered — **zero** noise added to the exit-1 path |
| AC-3 | golden page stdout **byte-identical** to the pre-change baseline (modulo the `--out` path), with and without `--run-controllers`; a 16-page sweep — all 14 `skills/zul-writer/assets/*.zul` plus `layout-clipping.zul` and `layout-overflow.zul` — produced **not one** `console `/`ZK client error:` entry |
| `--debug` MUST | 5 `debug: console` lines on stderr including `[log] chatter`, `[info] fyi` and the filtered favicon `[error]`; stdout **byte-identical** to the run without `--debug` |
| dedupe | `console-flood.zul` prints its twice-emitted message once; `client-error-box.zul` 3 calls → 2 entries |
| cap + truthful tail | `console-flood.zul` → exactly 10 `console error:` entries + `  - ... and 2 more console message(s) …`; `WARNINGS: 12` counts every printed line |
| one line, bounded | the embedded-newline message prints its first line only; the ~400-char message is snipped at 200 with a trailing `…`; no wrapped continuation lines |
| exit codes | 0 (three fixtures), 1 (`tmp-unknown.zul`), 2 (`--launcher-jar /nonexistent.jar`), 3 (no args), 4 (`--fail-on-layout layout-clipping.zul`). `git diff` adds no `EXIT_` constant and no `emit(` call |
| block order | `STATUS SCREENSHOT SIZE DOCROOT CLASSPATH ZK LAUNCHER CONTROLLERS WARNINGS` on both the golden page and the flood fixture — unchanged, nothing added |
| error box read is safe | structurally below `page.screenshot(` inside `if details is None:` inside `contextlib.suppress`; empirically, replacing its selector with invalid JS still gave `STATUS: ok`, exit 0 and a written PNG on the golden page |

**Regression net unchanged from its inherited state.** `python3 test/run-regression.py` from the
agent-skill root: `Checked 31 files | 0 regression(s), 0 stale, 2 orphan(s), 6 still quarantined`,
`Result: ✗ drift detected`, exit 1. Still 31 files — the three new fixtures cannot enter the corpus
(`run-regression.py:70` globs non-recursively, so `preview-fixtures/` is never visited), and the two
inherited orphans were not fixed.

**Residual under-reporting, all deliberate and all documented.** (a) Everything after the error-box
read is invisible — a `zk.error` from a later AU response, or a console message after the last
Playwright wait, is never delivered, so an empty block is *not* proof of a clean session. (b) The
`Failed to load resource:` prefix filter is textual, so a page whose own JS logs that exact prefix is
dropped, and asset 404s outside `/zkau/web/` are reported by neither channel (decision 15). (c) The
error-box selector is ZK-internal markup and its read is exception-suppressed, so a future ZK rename
breaks the capture silently (decision 14). (d) ZK's client JS does contain genuine `console.warn`
paths — huge AU batch, unloaded locale, moment deprecations — which are true positives but *new*
output on pages that print none today; the 16-page sweep found none of them firing.

**One minor deviation from the generator plan.** The plan had the error-box JavaScript inline in the
`page.evaluate` call. It is a module-level `ZK_ERROR_BOX_JS` constant instead, sitting beside
`ZK_READY` and `LAYOUT_AUDIT_JS`, which is the shape every other page script in the file already has
and gives the ZK-source citations somewhere to live. `_append_capped()` also takes a third argument
(the tail noun) rather than hard-coding one hint for both groups: "re-run with `--debug` to see every
console level" is true of the console group and false of the error box, and one shared helper printing
a misleading hint would fail the same honesty rule the caps exist to serve.

**Also confirmed en route:** `skills/zul-writer/assets/kanban-board.zul` renders as
`STATUS: render-error / PHASE: PARSE / MESSAGE: Unknown component <forEach>` when previewed from the
showcase project. That is **pre-existing** — byte-identical output from a pre-change copy of the
script — and the file is already quarantined in `test/known-failures.txt` under finding B1. Not P1-4's.

**Not done, deliberately:** no `--report`/JSON (P2-5), no `CONSOLE:` block, no `--fail-on-console`,
no widening of the `/zkau/web/` 404 detector, no `zk.debugJS`/`zk.sendClientErrors` in the launcher's
`zk.xml` (it would change the jar's bytes and force a re-pin), and R-8.3 is still not built — the
16-page sweep is the same manual substitute P1-3 used under decision 12. Note for P2-5: the spec's
JSON shape has only a flat `warnings` array, so console and client-error entries serialize there as
plain strings with no structure of their own. P2-5 decides whether that is enough.

### P2-5 — JSON report sidecar: implemented, verified by execution

**Python and docs only, one new fixture.** `preview-zul.py` gained a `# --- The JSON report ---`
section (after `emit_layout`, so all output code stays together): two module globals `REPORT_TARGET`
and `REPORT_ZUL` mirroring the existing `DEBUG` global, `report_target()`, `report_skeleton()`,
`write_report()`, `report_for_run()`, `report_for_skip()` and the argparse validator `_report_spec`,
plus the `--report json[:<path>]` flag, four lines in `main()`, one line in `locate_zul`, and one
call in each of the two `__main__` handlers. Serialization only — it measures nothing new, and every
value is read off the same object the corresponding text line is built from, so the two cannot
drift. Docs: `## The JSON report (--report json)` in `references/preview-guidelines.md` (annotated
object, per-exit-code population table, the four deviations, the three consumer limits), one
`### If you are scripting this, not reading it` subsection in SKILL.md, the flag in the script's own
docstring, and `--report` added to the guidelines' *Useful flags* paragraph. New fixture:
`zulwriter-showcase/src/main/webapp/preview-fixtures/render-error.zul` — a deliberately unclosed
`<label`, which is the exit-1 recipe AC-1 needs and which the fixture set did not have. **No launcher
Java, no jar, no gradle invocation, no re-pin, no new `EXIT_` constant, and `REPORT` is the only new
`emit(` call.** In zkidea the only changed file is this log.

**Where the fixture lives, and why it matters.** `preview-fixtures/`, never the webapp root:
`test/run-regression.py`'s corpus entry `("zulwriter-showcase/src/main/webapp", "*.zul", "pass")`
globs non-recursively (`run-regression.py:70`), so a deliberately broken .zul at the root would
become a 32nd corpus file *expected to pass* and turn the regression net red. The file says so in an
XML comment. Verified: `python3 test/run-regression.py` →
`Checked 31 files | 0 regression(s), 0 stale, 2 orphan(s), 6 still quarantined` — identical to the
inherited state, still 31 files, and the fixture appears in no corpus listing.

**Verification actually run** (every render with
`--launcher-jar …/build/release/zk-preview-launcher-1.0.2.jar`, so every case has the expected
`WARNINGS: 1` SHA-256 floor):

| Claim | Result |
|---|---|
| stdout unchanged without the flag, 5 cases | golden / layout / console diff clean against the pre-captured `base-*.txt`; exit-2 skip and exit-1 render diff clean against `git show HEAD:` copy of the script — 5 empty diffs |
| `REPORT:` is the only added line, and last | golden, exit-1 and exit-2: `diff` prints exactly one `> REPORT: <path>`; `tail -1` is that line |
| exit 0 object | `status ok`, `exitCode 0`, `error null`, classpath/launcher/controllers/size/zk/docroot/screenshot all as the text block, `warnings` 1, `layout {0, []}` |
| exit 1 object | `status render-error`, `exitCode 1`, `error.phase PARSE` + non-empty `message`/`location`, `screenshot` the bare `.png` with no `[ERROR PAGE …]` suffix, launcher/size/docroot still populated, `layout {0, []}` |
| exit 2 object | `status skipped`, `exitCode 2`, `error.reason`/`error.next` **string-equal** to the `PREVIEW_SKIPPED:`/`NEXT:` lines, all eight pipeline keys `null`, `warnings []` |
| exit 3 object | `locate_zul` miss → stdout `STATUS: usage-error` then `REPORT:`, `status usage-error`/`exitCode 3`; `--report yaml` and `--report json:` → usage block on stderr, exit 3, empty output directory |
| exit 4 | `--fail-on-layout` on `layout-clipping.zul` → exit 4, stdout still starts `STATUS: ok`, JSON `status ok` / `exitCode 4` / `layout.total 4` |
| default path | `--out $S/p25-golden.png --report json` → `$S/p25-golden.json`; no `--out` → `<tmpdir>/zul-preview/application-review.json`; `json:$S/p25deep/new/r.json` → created, parents made by `write_json_atomic` |
| layout in full | 4 findings with `rule`/`locator`/`detail`/`measured`, triples equal to the four `LAYOUT:` lines |
| controllers | `throwing-composer --run-controllers` → text `CONTROLLERS: failed → isolated`, JSON `mode failed` + 1 failure, that string also in `warnings`; golden `--run-controllers` → `mode executed`, `failures []` |
| all three blocks at once | `layout-clipping --run-controllers --fail-on-layout` → `warnings` array **equal** to the `WARNINGS:` entries in order, layout triples equal to the `LAYOUT:` lines, `total` equal to the header count, `mode` equal to the `CONTROLLERS:` token |
| unwritable destination | `--report json:<chmod 000 dir>/r.json` → exit 0, stdout byte-identical to the no-flag capture, one `warning: could not write the JSON report` on stderr, no `REPORT:` line |
| `--debug` invariant | stdout identical with and without `--debug` under `--report json`; 76 `debug:` lines on stderr, 0 on stdout; `--help` exits 0 |
| fault injection (copies in the scratchpad, not the tree) | a `raise` inside `report_for_run` → traceback printed **first**, `PREVIEW_SKIPPED: internal error …`, exit 2, and the skip report still written; a second `raise` inside `report_for_skip` on top of it → same traceback, same exit 2, no file — the report can never swallow the traceback that path exists to print |

**Re-verified by the orchestrator, independently of the report above.** Plan C removed the
`regression` and `spec` lenses, so the only adversary here is this re-run. Baselines were captured
from `git show HEAD:` **before** the generator started, so they cannot have been contaminated by its
edits. Re-run by hand and confirmed: stdout **byte-identical** without the flag on all three
pre-captured pages (golden, `layout-clipping`, `console-messages`) and on the exit-2 skip;
`REPORT:` the single added line under `--report`, last; all five exit codes produce valid JSON with
the identical 13-key set (0, 1, 2, 3, 4 — checked with `json.load` plus a key-set comparison);
`error` carries `{phase,message,location,trace}` on exit 1 and `{reason,next}` on exits 2 and 3;
exit 4 gives `status ok` / `exitCode 4` / `layout.total 4`; `--report yaml` exits 3 with an empty
stdout; `controllers.mode failed` + one `failures` entry on `throwing-composer --run-controllers`
against the line's `failed → isolated`; the docs' `measured` example numbers
(`axis x`, `textWidth 76.98`, `boxWidth 48`) are the real measured values, and the real
`clipped-text` shape has **nine** keys, of which the doc example shows three under its own
"shape varies by rule" note. **The fail-soft guarantee holds**, which matters most: a `--report`
aimed at a `chmod 000` directory exits **0** with stdout byte-identical to the no-flag baseline, no
`REPORT:` line, and one `warning: could not write the JSON report …` on stderr. Regression corpus
re-run: `Checked 31 files | 0 regression(s), 0 stale, 2 orphan(s), 6 still quarantined` —
the inherited state exactly. Scope re-checked in both repos: `git diff -- zk-preview-launcher src`
empty, `git diff --cached` empty, zero `LAUNCHER_*` lines touched.

**One observation the report does not make, and the fix the product owner approved.** On a skip,
`zul` was the raw argument *verbatim*, so a relative argument yielded a relative path in the JSON
while every successful render yielded an absolute one — measured:
`"zul": "src/main/webapp/application-review.zul"`. Documented ("the raw argument on a skip") but
still wrong for the report's whole purpose: a corpus job diffing runs from different working
directories would get a path it cannot resolve. **Fixed now rather than at R-8.3**, on the product
owner's call, while no consumer yet reads the key.

The fix needed **two** sites, not the one `.resolve()` it looked like. `REPORT_ZUL` is now
`Path(args.zul).expanduser().resolve()`, and `locate_zul`'s exit-3 branch — which had its own
unresolved local `zul` — passes `REPORT_ZUL` for the field while keeping the typed path inside the
`No such file: …` message, because that message echoes the caller's own argument back at them and is
part of the frozen stderr text. Verified after the fix: a relative argument on a skip now reports the
absolute path; exit 3's stderr is still `No such file: nope.zul` verbatim; all four byte-identity
checks (golden, layout, console, exit 2) still pass; the default report destination is unchanged.
`references/preview-guidelines.md` now reads `// always absolute, on every exit code`.

**The stale spec block is now marked as stale (product owner, 2026-08-23).** Rather than edit the
spec's hand-written JSON into agreement, its P2-5 sketch carries a blockquote naming the four keys
that differ, pointing at `references/preview-guidelines.md` as authoritative and at decision 19 for
the reasons, and saying plainly: do not carry this block into a formal spec. The spec file remains
**untracked** in git, as it has been for every requirement so far, so this note is not backed up by
either repo. **P1-3 and P1-4 now carry the same marker** (product owner,
2026-08-23), so all three stale sections are flagged: P1-3's detection-rule table above its
*Locator quality* paragraph, naming all five corrections plus the `auto`/`scroll` narrowing and the
fact that `overlap` was dropped rather than gated as the table says; P1-4's above its acceptance
criteria, naming the false console premise and the criterion that is unverifiable as written. Each
note points at `references/preview-guidelines.md` as authoritative and at the decisions that hold the
reasons, and each ends "do not carry this into a formal spec".

**One deviation from the generator plan.** The plan asked for `--report json[:<path>]` in the
docstring's OPTIONS list *and* on its `also:` line. It is in the OPTIONS list only: `also:` exists
precisely for the flags that do **not** get their own entry, so naming it in both would be a
contradiction a reader has to resolve. The `also:` line is otherwise untouched.

**Not done, deliberately:** no `schemaVersion`/`reportVersion` key (useful the first time the shape
changes, speculative before that); no richer `warnings` shape (P1-4 flattened those to prefixed
strings at the append site, and structuring them here would mean re-parsing our own output); no
multi-failure `controllers.failures` (launcher Java); no `--report` assertion in
`.github/workflows/validate-zul.yml` (out of P2-5's file list, but the smoke step already exercises
the exit-2 path, so `--report json:/tmp/r.json` plus a `json.load` would cost three lines — worth
doing with P2-8); and no widening of the `PREVIEW_SKIPPED:` text block, which never prints warnings
accumulated before the skip. The JSON mirrors that asymmetry rather than fixing it: widening it is a
text-contract change and belongs to its own requirement.

### P2-6 — viewport-flag guidance: written, verified by execution

Plan C, run `wf_03e50b85-ded`, 1 round, `criteria` lens passed. Documentation only: `--width`,
`--height` and `--full-page` have existed since the first version (`add_argument` at
`preview-zul.py:1898-1900`); SKILL.md Step 5 simply never mentioned them, which is how the showcase
agent came to render 1280 against a 1600px mockup and spend rounds on differences it had created
itself.

**What shipped.** Two documentation files, no code:

- `SKILL.md` Step 5 — a **Viewport** paragraph with three bullets (match the mockup's width, clamped
  1024-1920, with the worked instance "a 1600 px mockup means `--width 1600`"; `--full-page` for
  pages that flow past the fold; `--height` for a vertical flex shell) plus one example command
  carrying `--width 1600 --full-page --run-controllers`. One new bullet at the end of *What you
  cannot judge from this image*: the stitched image's height is not the measurement viewport.
- `references/preview-guidelines.md` — one appended sentence in the existing *Viewport* paragraph,
  no restructuring. SKILL.md points at it rather than restating the flag list.

**Verified by hand, not read off the report.**

| Claim | How it was checked | Result |
|---|---|---|
| No code changed | `shasum -a 256` of `preview-zul.py` vs `git show HEAD:` | identical, `46bc5476…` — a stronger proof than a stdout diff |
| stdout untouched | follows from the hash | byte-identical by construction |
| Blast radius | `git status --short` in both repos | exactly ` M SKILL.md` + ` M preview-guidelines.md`; zkidea unchanged; nothing staged |
| The documented command actually works | ran `--width 1600 --full-page --run-controllers` on `application-review.zul` | `STATUS: ok`, `SIZE: 1600x900 (full page)`, `CONTROLLERS: executed`, exit 0, PNG stitched to 1600x904 |
| No invented flags | `grep -rn -- '--widths' skills/zul-writer/` | nothing; only the three real flags are named |
| The spec's caveat was not copied | `grep -in 'legitimately\|collapsed region' SKILL.md` | two pre-existing hits (controller timeout, warning counts); no caveat bullet |
| Corpus unmoved | `test/run-regression.py` | 31 files, 0 regressions, 0 stale, 2 orphans, 6 quarantined, `✗ drift detected` — the inherited baseline exactly |
| No leftover fixtures | `ls preview-fixtures/` | the 9 pre-existing files; every probe deleted |

**One defect the passing lens did not catch** — the `vflex`/`hflex` conflation, corrected by hand
before the commit. See decision 21; it is the second time a `criteria`-only run has passed text
containing a false claim that no criterion happened to ask about (P2-5's raw-argument `zul` was the
first).

**AC-2 is a wording judgement, not a measurement**, and was reported as such by the run. "A
`zul-writer` run started from a 1600 px mockup renders at ~1600 px without being told to" is a claim
about a future agent session; nothing in this repository can execute it. It was judged against four
properties — imperative phrasing, a trigger condition an agent can test against its own session
state, the clamp given numerically, and a worked instance leaving no arithmetic to the reader — all
four of which the shipped paragraph has. Whether it actually holds will only be visible the next time
the skill is run from a mockup.

**Item 4 is closed, not deferred.** The spec's `--widths 1280,768` (two renders in one browser
session) was ruled out by the product owner on 2026-08-23 — see decision 22 — after being put to
them with the alternative of a requirement of its own. It was out of P2-6's scope to begin with (code
rather than documentation, and it needs a second `SCREENSHOT:`/`SIZE:` pair against a frozen block
order); the ruling means it is not waiting in a backlog either. A second run with a different
`--width` covers the need today.

**Measured but not acted on.** A `vflex` shell clips over-tall content inside it silently: a 1400px
child in a 900px `overflow: hidden` vlayout lost 500px, the document stayed 900, and the audit
reported **0 findings**. This is not a new bug — it is the `escapes-parent` rule's documented
`offsetParent` under-report (statically-positioned ancestors are skipped, per the rule's own
comment, and the guidelines' deliberate-under-report bullet). Recorded here so nobody re-discovers it
and files it as a regression.

### P2-8 — forwarding the browser's request headers: implemented, verified by execution

Plan C's medium effort, but with the `regression` lens kept alongside `criteria` — decision 23,
because this is the one of the last four requirements that touched **launcher Java**, so the gradle
rule and the unpublished-jar situation both applied. Run `wf_9a59ba6b-4b0`, 1 round, both lenses
passed with two minor gaps, both since closed by the orchestrator (see *Gaps closed after the run*
below).

**The spec's Files list was incomplete, and following it literally would have failed.** It names
`PreviewHttpServer.java` and `mockcore/MockHttpServletRequestCore.java`. In fact:

- The mock request is **not** constructed in `PreviewHttpServer` at all. `AbstractRenderEngine`
  builds it inside `renderOnce`, via the `protected abstract createRequest(...)` seam the
  jakarta/javax subclasses fill in. So `RenderEngine.java` and `AbstractRenderEngine.java` had to
  change, and neither is in the spec's list.
- `MockHttpServletRequestCore` needed **no edit whatsoever**. Its existing `setHeader(name, value)`
  is exactly the API required; the spec's claim that nothing outside the mock package ever called it
  was re-verified before building on it (the only other hit in `src/main/java` was
  `MockHttpServletResponseCore`'s own `setHeader("Location", …)`).

**What shipped.**

- `RenderEngine.java` — a second, **`default`** method `renderZul(String, Map<String,String>)` that
  drops the headers. `default` and not abstract on purpose: `PreviewHttpServerConcurrencyTest`'s
  `LatchEngine` hand-implements this interface and 33 call sites use the one-argument form, so an
  abstract method would break the test sourceSet's compilation for no gain.
- `AbstractRenderEngine.java` — the map threaded through, no logic changed. `renderOnce` gained a
  third parameter and applies `headers.forEach(req::setHeader)` immediately after `createRequest`;
  the one-argument `renderZul` is now a one-line delegation with `Map.of()`, so all 33 existing call
  sites walk the identical path; `retryIsolated` carries the map too.
- `PreviewHttpServer.java` — the `.zul` branch calls `engine.renderZul(path, requestHeaders(exchange))`,
  plus one private static helper collapsing `com.sun.net.httpserver.Headers` (a multimap) to one
  value per name.
- `zk-preview-launcher/src/test/resources/fixtures/request-headers.zul` +
  `RequestHeaderHttpTest.java` — 5 tests (1 ZK-free, 4 ZK-gated across both servlet variants).
- `zulwriter-showcase` — `preview-fixtures/request-headers.zul`, `preview-fixtures/header-composer.zul`
  and `zwriter/previewfixtures/HeaderComposer.java` (a Composer, for AC-2; it cannot live in the
  launcher's test sourceSet, which has no ZK compile dependency by design).
- `.github/workflows/validate-zul.yml` — the outstanding CI gap from the P2-5 entry, folded in here:
  the smoke step now re-runs the unusable-classpath invocation with `--report json:/tmp/r.json` and
  asserts exit 2 plus a successful `json.load`. Still no JDK, no network, no Playwright.

**Verified by hand, not read off the report.**

| Claim | How it was checked | Result |
|---|---|---|
| AC-1 launcher-side | `RequestHeaderHttpTest` test 2, both variants | 200 and `uaP28-PROBE-UA` in the body on javax (ZK 9.6) and jakarta (ZK 10.1) |
| AC-1 at the wire | `Main --port 0` on the release jar, then `curl -H 'User-Agent: P28-PROBE-UA' -H 'Accept-Language: en-GB'` | `UA=[P28\-PROBE\-UA]` and `AL=[en\-GB]` in the served HTML — **after de-escaping ZK 10's backslash-hyphen**. A raw `grep P28-PROBE-UA` on the served page legitimately returns 0; the test's `deEscaped()` exists for exactly this. Do not chase that zero. |
| AC-1 with a real browser | `preview-zul.py` on `preview-fixtures/request-headers.zul`, then read the PNG | `UA=[Mozilla/5.0 (Macintosh; …) HeadlessChrome/151.0.0.0 Safari/537.36]`, `AL=[en-US,en;q=0.9]` — populated, not `UA=[]` |
| AC-2 end to end | `preview-zul.py --run-controllers` on `preview-fixtures/header-composer.zul`, then read the PNG | `CONTROLLERS: executed`, exit 0, the label reads `composer saw UA=[Mozilla/5.0 … HeadlessChrome/151.0.0.0 …]` |
| AC-2 launcher-side mirror | `RequestHeaderHttpTest` test 3 | `X-ZK-Preview-Controllers: executed` with the token in the body — the executor-thread path |
| Headers travel as a parameter, never a thread-local | `git diff \| grep -i ThreadLocal` | one hit, and it is the javadoc explaining why it is *not* one |
| No resource-path forwarding | `git diff` of `AbstractRenderEngine.java` | no hunk inside `resource(...)`; `ResourceResult.java` and `RenderEngine.resource(String)` unchanged |
| The golden page still paints | `preview-zul.py --run-controllers application-review.zul`, PNG read | `STATUS: ok`, `CONTROLLERS: executed`, `WARNINGS: 1` (digest only) — not 5, no `zk is not defined` |
| Render fidelity unchanged | full launcher suite, in a **private rsync'd copy** | 34 suites, 259 tests, 0 failures, 0 errors, 0 skipped — baseline 33/254 plus this requirement's 1 suite / 5 tests. No fidelity or corpus baseline file edited |
| Existing callers unaffected | the same run | `compileTestJava` clean, `PreviewHttpServerConcurrencyTest` green, `git diff --stat` lists no test file |
| Text-output contract frozen | `shasum -a 256 preview-zul.py` vs `git show HEAD:` | identical, `46bc5476…` — stdout identity by construction |
| No `--user-agent` | `grep -rn -- '--user-agent' skills/` | nothing |
| The new fixtures are not corpus members | `test/run-regression.py` | 31 files, 0 regressions, 0 stale, 2 orphans, 6 quarantined, `✗ drift detected` — the inherited baseline exactly |
| The CI lines actually pass | ran them verbatim in a local shell | exit 2, `json ok` |
| Blast radius | `git status --short` in both repos | only the files above plus the pre-existing untracked noise; nothing staged |

**Deliberate non-goals, each with its reason.**

- **`--user-agent` on `preview-zul.py` was declined.** The spec says MAY, not MUST. Playwright already
  sends a real UA (`HeadlessChrome/151.0.0.0` was observed *in the rendered page*), nothing is blocked
  without it, and the product owner has not ruled. A new flag is also a new surface on a frozen
  text-output contract.
- **Forwarding headers into `resource(pathInfo)` — the `/zkau/web/*` path — was declined, and this is
  the one thing a future agent must not "complete".** Measured: Chromium sends
  `Accept-Encoding: gzip, deflate, br, zstd`; ZK's extendlets honour it and return gzip bytes; but
  `ResourceResult` carries only status/contentType/body, so `Content-Encoding` is dropped and gzip is
  served labelled `text/javascript`. The result was 3× `Invalid or unexpected token` plus
  `zk is not defined`, `WARNINGS: 5` against a baseline of 1, and nothing painted. **Try the cheap
  fix first if this is ever wanted:** the AU servlet is bootstrapped with `Map.of()` — no `compress`
  init param — while the layout servlet right above it already gets `compress=false`, and
  `DHtmlUpdateServlet.init` answers a false one with `ClassWebResource.setCompress(null)`, i.e. no
  gzip at all (verified by decompiling `DHtmlUpdateServlet` from `zk-10.1.1.jar`: it reads the
  `compress` init param and calls `setCompress(null)` when it is not `"true"`). That is one word,
  where carrying `Content-Encoding` through `ResourceResult` or stripping `Accept-Encoding` — the
  only two routes this entry originally named — are both structural. Either way it is its own
  requirement, not a P2-8 loose end. The reason is recorded as a javadoc comment on
  `PreviewHttpServer.requestHeaders` so it is found before the mistake, not after.
- **`getLocale()`/`getLocales()` are still `Locale.getDefault()`.** ZK's i18n reads `getLocale()`, not
  the `Accept-Language` header, so a header-only change does not make the preview locale-faithful.
  Separate concern; no acceptance criterion asks for it.
- **Multi-value header fidelity was not attempted.** `com.sun`'s `Headers` is a multimap and the mock
  holds one value per name, so the **first** value is taken — exactly `HttpServletRequest.getHeader`'s
  own contract for a repeated header. The knowing limitation is `getHeaders(name)`, which reports that
  single value; widening it would mean changing `MockHttpServletRequestCore`'s map type, which nothing
  needs.
- **`MockHttpServletRequestCore.getIntHeader` was left alone.** It parses with `Integer.parseInt` and
  therefore throws `NumberFormatException` on a non-numeric value. Until now the map was always empty
  so it always returned `-1`; it is reachable for the first time. Considered and declined: throwing is
  exactly what the servlet spec mandates, no browser GET carries a non-numeric int-typed header, and
  hardening it would be a change to a file P2-8 does not otherwise need to touch. Flagged so nobody
  "fixes" it later without a reason.

**Gaps closed after the run.** Both lenses passed; each still returned one minor gap, and both were
fixed by the orchestrator before the commit rather than left in the log as known debt.

1. **The `criteria` lens caught this entry overstating its own evidence.** The wire-level row claimed
   `UA=[P28-PROBE-UA]` appears in the served HTML. It does not: ZK 10 escapes hyphens in the zkmx
   bootstrap, so the page carries `UA=[P28\-PROBE\-UA]` and a raw grep for the token returns 0. The
   substance was never in doubt — `RequestHeaderHttpTest.deEscaped()` already handles it — but a
   future agent reading a claim that does not reproduce would have gone looking for a bug that is not
   there. Row reworded.
2. **The `regression` lens caught the new CI assertion being able to pass on a stale file.** The
   appended block ran `--report json:/tmp/r.json` and then `json.load`ed it without deleting it
   first, so a leftover parseable `/tmp/r.json` would have masked exactly the regression the step
   exists to catch. Harmless on a fresh GitHub runner, wrong as a test. `rm -f /tmp/r.json` added
   ahead of the invocation, and the block re-run verbatim afterwards: exit 2, `json.load` OK.

**What hand verification added on top of both lenses.** Two things neither lens reported.

- **The tests were mutation-checked, not just run.** A green suite proves the code compiles and the
  assertions hold; it does not prove the assertions would notice the feature being removed. Reverting
  the three production files makes `RequestHeaderHttpTest` fail to *compile* (it overrides the
  two-argument `renderZul`), which is a weaker red than it looks. So the plumbing was restored and
  only the behaviour broken — `headers.forEach(req::setHeader)` deleted and the server passing
  `Map.of()` — and all 5 tests failed. They bite.
- **The `compress=false` route out of the resource-path problem** (recorded in the non-goal bullet
  above) was found by decompiling `DHtmlUpdateServlet`, not by either lens. It matters because the
  entry as first written named only the two structural routes, which would have sent the next agent
  down the expensive path.


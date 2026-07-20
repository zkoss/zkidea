# E4 — maker evidence (Sonnet, round 1)

> Scope: docs (W1), CI/test wiring review (W2), stage-2 fail-report hook stub (W3),
> addon-jar prefixes (W4), ErrorMapper hierarchy-UiException classification (W5).
> TDD throughout for code changes (W4, W5): failing test first, then fix, both runs
> recorded below. No commits made (per instructions); nothing outside E4 scope touched.
> The repo already had E1–E3's uncommitted work in the tree (settings.gradle,
> build.gradle, plugin.xml, manual-test/scope-var-completion.zul, src/main/java/.../
> preview/, zk-preview-launcher/, tasks/ — all pre-existing "M"/"??" at session start,
> per this file's own git-status system reminder); this round only added/edited the
> files listed per work item below.

## W1 — Documentation (E4-G2)

Added `## 10. ZUL Preview (since 0.8.0, in development)` to
`doc/feature_overview.md` (inserted before "## Shared Utilities"), following the
doc's established format: What it does / Key classes (two tables: plugin-side
`preview` package, and the `zk-preview-launcher` rendering core) / How it works /
plus two extra subsections specific to this feature's shape ("Isolation & structured
failures", "v1 limitations") since the existing per-feature template doesn't have a
slot for "known limitations" and I judged that honesty requirement (E4 brief) more
important than rigid template conformance.

Covered, per the brief's checklist: the split editor
(`ZulPreviewFileEditorProvider`/`ZulPreviewFileEditor`, `TextEditorWithPreview` +
`JBCefBrowser`, the JCEF-unavailable fallback panel); the helper-JVM architecture
(`ZulPreviewServerService`, `ManagedPreviewServer`, `DocrootResolver`,
`ZkClasspathFilter`, one server per `(docroot, classpath-signature)`, teardown on
`dispose()`); the standalone rendering core (`zk-preview-launcher`'s CLI contract
`--classpath/--webapp/--port` + `PREVIEW_PORT=<n>`, javax/jakarta variants via
`VariantDetector`, the JDK `HttpServer` + mock servlet env driving
`DHtmlLayoutServlet`/`DHtmlUpdateServlet`); the isolation guarantee
(`ScopedZkClassLoader` + `PreviewUiFactory`, "user ViewModels/Composers are never
loaded, bound values render empty by design", the structured JSON failure contract
from `ErrorMapper`); and v1 limitations (first-paint only/AU stubbed, no user-class
fidelity, JCEF required).

**README.md**: it *does* enumerate features (a `## Features` section with one
subsection per feature, each with bold bullet points) — added a two-line "### ZUL
Preview" entry in the same style, pointing at `doc/feature_overview.md` for detail
and explicitly flagging "current v1 limitations" so a reader isn't misled into
expecting full-fidelity rendering.

Files changed: `doc/feature_overview.md`, `README.md`.

## W2 — CI/test wiring review (E4-G1)

**Finding: already correctly wired, no code change needed.** Evidence:

```
$ withjdk.sh 17 ./gradlew build -x buildSearchableOptions --dry-run
...
:zk-preview-launcher:compileJava SKIPPED
:zk-preview-launcher:compileHooksJava SKIPPED
:zk-preview-launcher:hooksJar SKIPPED
:zk-preview-launcher:test SKIPPED
:zk-preview-launcher:check SKIPPED
:zk-preview-launcher:build SKIPPED
:test SKIPPED
:check SKIPPED
:build SKIPPED
BUILD SUCCESSFUL in 4s
```
(`--dry-run` prints the full task graph including skipped tasks — `SKIPPED` here just
means dry-run mode, not "excluded"; the presence of `:zk-preview-launcher:test`/
`check`/`build` in the graph is what matters.)

Root cause of why this "just works" without any explicit `dependsOn` wiring: Gradle's
default CLI task-name resolution. Running an unqualified task name (`build`, `test`,
`check`) with no project-path prefix selects that task **in every project in the
build that declares it**, not just the project the daemon is invoked from. Since
`settings.gradle` does `include 'zk-preview-launcher'` and that subproject applies
the `java` plugin (which contributes its own `build`/`test`/`check` tasks),
`./gradlew build` from the root already runs both projects' `build` (and therefore
`test`, `check`, and — via `compileHooksJava`'s inclusion in `classes` → `assemble` →
`build` — the `hooks` sourceSet compilation) in one invocation. This is also why an
unqualified `./gradlew test` from the root would try to run `:zk-preview-launcher:test`
too (and fails with "No tests found for given includes" if you `--tests`-filter to a
root-only class name) — a nuance worth knowing if a future contributor ever wants to
run *only* the root suite (`./gradlew :test`, project-qualified).

Real (non-dry-run) confirmation, full build:
```
$ withjdk.sh 17 ./gradlew build -x buildSearchableOptions
...
> Task :zk-preview-launcher:check
> Task :zk-preview-launcher:build
BUILD SUCCESSFUL in 28s
26 actionable tasks: 6 executed, 20 up-to-date
```
Test-result XML counts immediately after (baseline, before any W4/W5 changes):
- root: 299 tests, 0 skipped, 0 failures, 0 errors
- `zk-preview-launcher`: 34 tests, 0 skipped, 0 failures, 0 errors
- `zk-preview-launcher/build/classes/java/hooks/org/zkoss/zkpreview/hooks/` contained
  `PreviewComposer.class` and `PreviewUiFactory.class` — confirms the `hooks`
  sourceSet is compiled as part of the same `build` invocation.

**`prepareSandbox`/`buildPlugin` bundling re-confirmed** (already verified in E3;
re-checked here since the brief asked for it, and again after this round's changes):
```
$ find . -name "zk-preview-launcher.jar"
./zk-preview-launcher/build/libs/zk-preview-launcher.jar
./.sandbox/plugins/zkidea/lib/zk-preview-launcher.jar   # from a previous runIde session
$ unzip -l build/distributions/zkidea-0.7.2.zip | grep zk-preview-launcher
   464864  07-06-2026 22:22   zkidea/lib/zk-preview-launcher.jar
```
Re-checked again after this round's ErrorMapper/PreviewHttpServer/ZkClasspathFilter
edits and a fresh `buildPlugin` run (see "Final full-suite results" below) — same
`zkidea/lib/zk-preview-launcher.jar` entry present, larger size (465063 bytes,
reflecting the new `HierarchyFailureTest`-adjacent... no, test classes aren't jarred;
size delta is from the `ErrorMapper`/`PreviewHttpServer` source changes recompiling).

No files changed for W2 (verification only, as instructed for the "already wired"
case).

## W3 — Stage-2 fail-report hook stub

**Shape chosen: documentation (`tasks/zul-preview/stage2-hook.md`) + a one-line
pointer comment**, not a `ZulPreviewFailureListener` Java interface. Full reasoning
is in the doc file itself; summary: `ZulPreviewFileEditor.startPreview()` hands
`previewUrl` straight to `JBCefBrowser`'s constructor — the plugin side has **zero
existing code path** that parses the launcher's HTTP response (success or failure)
today. A listener interface with "a no-op default wiring point where the preview
editor receives a FAILURE response" would need the plugin to either duplicate a
second HTTP GET + hand-rolled JSON parse purely to feed a listener with no real
caller (dead/speculative code — the brief's own escape hatch for choosing shape 2
over shape 1), or wire a `CefLoadHandler` on `JBCefBrowser` to intercept status codes
asynchronously, which is real feature-shaped design work stage 2 should own, not
something E4 should pre-commit to as a "stub." The JSON contract
(`RenderError`/`RenderResult.toJson()`, unchanged since E1, exercised by
`StructuredFailureTest`/`HierarchyFailureTest`) is already the complete, stable data
contract; documenting its schema/semantics and its single production site
(`PreviewHttpServer.handle()`'s `.zul` GET failure branch) is a sufficient and
honest hook.

Files changed:
- `tasks/zul-preview/stage2-hook.md` (new) — full JSON schema with field-by-field
  semantics, `RenderPhase` value table (including the two reserved-but-never-assigned
  values `CLASSPATH`/`RESOURCE`), two concrete future integration options
  (server-side sink in `PreviewHttpServer` vs. client-side `CefLoadHandler`), explicit
  non-goals.
- `zk-preview-launcher/src/main/java/org/zkoss/zkpreview/PreviewHttpServer.java` —
  added a 3-line pointer comment immediately above the `r.toJson()` call in the
  failure branch, referencing the new doc file. No behavior change.

## W4 — Addon-jar prefixes in the R7 gate

`ZkClasspathFilter.filterZkJars`/`isZkJar` (used only as the "does this module have
ZK at all" gate, per its own existing javadoc) missed addon-only jars. Extended the
prefix list to also recognize `zkcharts-`, `zkpivot-`, `keikai-` (verified naming
convention by inspection of the existing list's own pattern comment —
`"<artifactId>-<version>[-<variant>].jar"` — zkcharts/zkpivot follow it directly;
Keikai's own artifacts are `keikai-<version>.jar` per the E4 brief). `zkmax-`/`zkex-`/
`zuti-` were already present from E1. Did **not** touch `filterLibraryJars` (the
handoff path), per the brief — it is deliberately prefix-agnostic (hands every
resolved runtime jar to the launcher regardless of name).

### TDD: red → green

New test in `src/test/java/org/zkoss/zkidea/preview/ZkClasspathFilterTest.java`:
`recognizesAddonOnlyJarsAsZkJarsForTheR7Gate()`.

**RED** (`withjdk.sh 17 ./gradlew test --tests
"org.zkoss.zkidea.preview.ZkClasspathFilterTest" -x buildSearchableOptions`, before
the prefix-list change):
```
ZkClasspathFilterTest > recognizesAddonOnlyJarsAsZkJarsForTheR7Gate() FAILED
    org.opentest4j.AssertionFailedError at ZkClasspathFilterTest.java:46
11 tests completed, 1 failed
BUILD FAILED
```

**GREEN** (after adding `"zkcharts-", "zkpivot-", "keikai-"` to
`ZK_ARTIFACT_PREFIXES`):
```
$ withjdk.sh 17 ./gradlew :test --tests "org.zkoss.zkidea.preview.ZkClasspathFilterTest" -x buildSearchableOptions
BUILD SUCCESSFUL in 1s
```
(Used the project-qualified `:test` here per the W2 nuance above — an unqualified
`test` also tries `:zk-preview-launcher:test`, which has no class by that name and
fails with "No tests found," a Gradle quirk, not a test failure — this was itself
incidental confirmation of the W2 wiring finding.)

Files changed:
- `src/main/java/org/zkoss/zkidea/preview/ZkClasspathFilter.java` — 3 new prefixes.
- `src/test/java/org/zkoss/zkidea/preview/ZkClasspathFilterTest.java` — new test case.

## W5 — ErrorMapper classification of hierarchy UiExceptions

### Investigation (reproduce-before-fix, per instructions)

Built classpath from `manual-test/pom.xml`:
```
$ withjdk.sh 17 mvn -f manual-test/pom.xml dependency:build-classpath \
    -Dmdep.outputFile=<scratch>/e4-cp.txt -q
```
Started the packaged launcher against it and hit the real file:
```
$ withjdk.sh 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
    --classpath "$(cat <scratch>/e4-cp.txt)" --webapp manual-test/src/main/webapp --port 0
PREVIEW_PORT=55025
$ curl -s http://localhost:55025/scope-var-completion.zul
{"status":"FAILURE","error":{"phase":"UNKNOWN","message":"org.zkoss.zk.ui.UiException: Unsupported parent for row: <Window ...>","zulFile":"/scope-var-completion.zul","line":null,"column":null}}
```
Reproduced the exact symptom from PLAN.md §8. Root cause in the source file itself:
`manual-test/src/main/webapp/scope-var-completion.zul` has
`<apply ctx="..." templateURI="/WEB-INF/template/row.zul">` directly inside
`<window>`; `WEB-INF/template/row.zul`'s content is a bare `<row><label .../></row>`.
Once applied, that `<row>` lands directly under `<window>` (no `<rows>`/`<grid>`
ancestor), which real ZK rejects.

To see the *actual exception object structurally* (not just the mapped JSON), I
built a minimal fixture reproducing the same shape in isolation
(`zk-preview-launcher/src/test/resources/fixtures/unsupported-parent.zul`: a `<row>`
directly under `<window>`) and temporarily added an unconditional
`(...).printStackTrace()` right before the `ErrorMapper.map(...)` call in
`JakartaRenderEngine.renderZul`'s `catch (InvocationTargetException e)` branch (a
throwaway `ScratchInvestigationTest` drove it). Both the instrumentation and the
scratch test were fully reverted before writing the real fix/test (confirmed via
`git diff` showing zero net change to `JakartaRenderEngine.java`).

**What the exception chain actually carries** (full trace captured; excerpt):
```
org.zkoss.zk.ui.UiException: Unsupported parent for row: <Window rUKK0>
    at org.zkoss.zul.Row.beforeParentChanged(Row.java:290)
    at org.zkoss.zk.ui.AbstractComponent.setParent(AbstractComponent.java:1221)
    at org.zkoss.zk.ui.AbstractComponent.insertBefore(AbstractComponent.java:1381)
    at org.zkoss.zk.ui.impl.AbstractUiFactory.newComponent(AbstractUiFactory.java:144)
    at org.zkoss.zk.ui.impl.UiEngineImpl.execCreateChild0(UiEngineImpl.java:930)
    ... (UiEngineImpl.execCreate*/execCreateChild* recursion — component-tree building)
    at org.zkoss.zk.ui.impl.UiEngineImpl.execNewPage0(UiEngineImpl.java:469)
    at org.zkoss.zk.ui.http.DHtmlLayoutServlet.process(DHtmlLayoutServlet.java:253)
```
Findings:
1. It is a **bare `org.zkoss.zk.ui.UiException`** — no wrapped cause
   (`getCause() == null`).
2. Its **message carries no position info** — no "line"/"column" substring, nothing
   `ErrorMapper`'s existing `LINE_COL`/`LINE_ONLY` regexes could ever match. There is
   no `zscript`/BeanShell layer involved (unlike fixture (f)) that could have injected
   a line number.
3. The full stack trace shows the failure occurs entirely inside
   `UiEngineImpl.execCreate*`/`execCreateChild*` — i.e. **after** the ZUML document
   already parsed successfully and ZK is building the live component tree
   (`AbstractUiFactory.newComponent` → `Window.insertBefore` → `Row.beforeParentChanged`
   rejecting the parent). This is unambiguously compose-time, not parse-time.

**Conclusion**: per the brief, "if a line number is genuinely not present on the
exception chain, phase=COMPOSE alone is the fix — document the finding and stop."
That is exactly this case: there is nothing structurally present on this exception
to recover a line/column from (no cause, no position-shaped message), so the fix is
phase classification only.

### Chosen fix

`ErrorMapper.map()`: added an `isUiException(chain)` check between the existing
`looksLikeParseError` and the `UNKNOWN` fallback. It walks each throwable's **own
Java class hierarchy** (via `getSuperclass()`, not the cause chain) looking for a
class literally named `org.zkoss.zk.ui.UiException` — catching subclasses (e.g.
`WrongValueException`) structurally, without needing a compile-time ZK dependency
(`ErrorMapper` deliberately has none, per its own class javadoc) and without any
message-parsing heuristic beyond what already existed. If found, phase = `COMPOSE`.
This sits after the existing missing-class checks (so fixture (f) and `broken.zul`'s
zscript-missing-class path, which already reach `COMPOSE` via
`ClassNotFoundException`/`MISSING_CLASS_IN_MESSAGE`, are unaffected — those checks
run first) and after `looksLikeParseError` (so a genuine future parse-error fixture,
if one existed, would still classify as `PARSE`, not `COMPOSE` — no such fixture
exists in this repo today to exercise that ordering, noted as a gap below).

### TDD: red → green

New fixture: `zk-preview-launcher/src/test/resources/fixtures/unsupported-parent.zul`
(fixture (h) — `<row>` directly under `<window>`, no `<rows>`/`<grid>` ancestor).

New test: `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/HierarchyFailureTest.java`,
parametrized over both servlet-API variants (same pattern as `StructuredFailureTest`).

**RED** (`withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests
"org.zkoss.zkpreview.HierarchyFailureTest" --rerun-tasks`, before the `ErrorMapper` fix):
```
HierarchyFailureTest > fixtureH_unsupportedParentClassifiesAsComposeNotUnknown(Named)[1] FAILED
    org.opentest4j.AssertionFailedError: hierarchy UiException must classify as COMPOSE, not UNKNOWN:
    {"status":"FAILURE","error":{"phase":"UNKNOWN","message":"org.zkoss.zk.ui.UiException: Unsupported parent for row: <Window ...>", ...}}
    ==> expected: <COMPOSE> but was: <UNKNOWN>
HierarchyFailureTest > fixtureH_unsupportedParentClassifiesAsComposeNotUnknown(Named)[2] FAILED  (same, javax variant)
2 tests completed, 2 failed
BUILD FAILED
```

**GREEN** (after adding `isUiException()` and the new branch in `ErrorMapper.map`):
```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:test --rerun-tasks
... (all 36 tests, including both HierarchyFailureTest variants) PASSED
BUILD SUCCESSFUL in 14s
9 actionable tasks: 9 executed
```
Full launcher suite re-run (not just the new test) to check for regressions in the
same pass — `RenderFidelityTest` (a–e), `StructuredFailureTest` (f), `VariantDetectorTest`,
`CoreIndependenceTest`, `IsolationTest`/`IsolationChildProcessTest`,
`ApplyTemplateUriTest`, `BrowserEquivalentTest`, `RealWorldSmokeTest` all still PASSED
in the same run.

### Corpus spot-check (no regression on the existing contract)

Real launcher, real `manual-test` classpath, both target files:
```
$ curl -s http://localhost:55025/scope-var-completion.zul
{"status":"FAILURE","error":{"phase":"COMPOSE","message":"org.zkoss.zk.ui.UiException: Unsupported parent for row: <Window oDeH0>","zulFile":"/scope-var-completion.zul","line":null,"column":null}}

$ curl -s http://localhost:55025/preview/broken.zul
{"status":"FAILURE","error":{"phase":"COMPOSE","message":"Missing class: org.example.definitely.NoSuchClassAtAll (...)","zulFile":"/preview/broken.zul","line":7,"column":null}}
```
- `scope-var-completion.zul`: phase flipped from `UNKNOWN` → `COMPOSE` as intended;
  `line`/`column` correctly remain `null` (nothing to recover, per the investigation).
- `preview/broken.zul`: byte-for-byte unchanged from E3's contract — still `COMPOSE`,
  still `line: 7` — confirms the missing-class branches (checked earlier in
  `ErrorMapper.map`'s if/else chain) still win and are unaffected by the new branch.
- No automated `ErrorMapperTest`/JSON-shape test exists in this repo asserting
  `broken.zul`'s exact output (only `StructuredFailureTest` exercises the in-repo
  `zscript-missing-class.zul` fixture, which is unaffected for the same reason) — this
  manual curl check is the actual regression evidence for `broken.zul` specifically,
  consistent with how E3 round 3 verified the same file.

Teardown: `kill <launcher PID>`; `lsof -iTCP:<port> -sTCP:LISTEN` returned nothing
(port released); `ps -ef | grep zk-preview-launcher` showed only the pre-existing,
untouched PID 34527 (unrelated sandbox launcher from the user's own `runIde` session,
explicitly out of scope per instructions — never touched).

Files changed:
- `zk-preview-launcher/src/main/java/org/zkoss/zkpreview/ErrorMapper.java` — new
  `isUiException()` helper + new `COMPOSE` branch in `map()`.
- `zk-preview-launcher/src/test/resources/fixtures/unsupported-parent.zul` (new).
- `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/HierarchyFailureTest.java` (new).

## Final full-suite results

```
$ withjdk.sh 17 ./gradlew build -x buildSearchableOptions
...
> Task :zk-preview-launcher:check
> Task :zk-preview-launcher:build
BUILD SUCCESSFUL in 26s
25 actionable tasks: 8 executed, 17 up-to-date
```
- root (`build/test-results/test/*.xml`): **300 tests, 0 skipped, 0 failures, 0
  errors** (299 baseline + 1 new: `recognizesAddonOnlyJarsAsZkJarsForTheR7Gate`).
- `zk-preview-launcher` (`zk-preview-launcher/build/test-results/test/*.xml`): **36
  tests, 0 skipped, 0 failures, 0 errors** (34 baseline + 2 new:
  `HierarchyFailureTest`'s one method × 2 servlet-API variants).

```
$ withjdk.sh 17 ./gradlew verifyPlugin
...
> Task :verifyPlugin
BUILD SUCCESSFUL in 720ms
```
Zero verification problems reported. `plugin.xml` was not touched by this round at
all (it was already modified from prior E1–E3 work per the session's starting `git
status`; `git diff` shows no additional changes from anything done in this round).

```
$ withjdk.sh 17 ./gradlew buildPlugin -x buildSearchableOptions
BUILD SUCCESSFUL
$ unzip -l build/distributions/zkidea-0.7.2.zip | grep zk-preview-launcher
   465063  07-07-2026 12:12   zkidea/lib/zk-preview-launcher.jar
```
Confirms `prepareSandbox`/`buildPlugin` still bundle the launcher jar after this
round's launcher-side source changes (`ErrorMapper.java`, `PreviewHttpServer.java`).

## Files changed/added (complete list, this round)

- `doc/feature_overview.md` — new "## 10. ZUL Preview" section (W1).
- `README.md` — new "### ZUL Preview" bullet under "## Features" (W1).
- `tasks/zul-preview/stage2-hook.md` — new: hook-contract documentation (W3).
- `zk-preview-launcher/src/main/java/org/zkoss/zkpreview/PreviewHttpServer.java` —
  3-line pointer comment, no behavior change (W3).
- `src/main/java/org/zkoss/zkidea/preview/ZkClasspathFilter.java` — 3 new addon
  prefixes (W4).
- `src/test/java/org/zkoss/zkidea/preview/ZkClasspathFilterTest.java` — new test
  case (W4).
- `zk-preview-launcher/src/main/java/org/zkoss/zkpreview/ErrorMapper.java` — new
  `isUiException()` + `COMPOSE` branch (W5).
- `zk-preview-launcher/src/test/resources/fixtures/unsupported-parent.zul` — new
  fixture (h) (W5).
- `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/HierarchyFailureTest.java`
  — new test (W5).
- `tasks/zul-preview/E4-evidence.md` — this file.

No changes to `build.gradle`, `settings.gradle`, `plugin.xml`, or any file outside
the above (W2 required no code change; confirmed by dry-run task-graph evidence
above).

## Gaps / limitations (honest)

1. **W2**: the "wiring already works" finding relies on Gradle's default unqualified
   task-name-matches-all-subprojects behavior, which is implicit/undocumented in this
   repo's own build files (no comment in `build.gradle`/`settings.gradle` says "this
   is why launcher tests run automatically"). If a future contributor restructures
   the build (e.g. adds `include(':other-module')` with its own `build` task they
   don't want to always run, or someone adds explicit `dependsOn` wiring elsewhere
   that inadvertently narrows task selection), this implicit behavior could silently
   stop covering the launcher. I did not add a defensive explicit `check.dependsOn
   ':zk-preview-launcher:check'` because the brief says to change nothing when
   already wired and prefers proof over touching working code — but this is a fragility
   worth a future comment or explicit `dependsOn` if the build ever gets restructured.
2. **W3**: the hook is documentation-only; there is deliberately no automated test
   asserting the JSON schema doc matches the code (the brief's scope explicitly
   excludes building the actual stage-2 consumer). `StructuredFailureTest`/
   `HierarchyFailureTest` do exercise the underlying JSON shape, so the documented
   schema is at least indirectly test-covered, but no test would fail if
   `stage2-hook.md`'s prose drifted from the code in the future.
3. **W4**: I did not verify the exact real-world jar-naming convention for `zkpivot`
   against an actual published `zkpivot-*.jar` artifact (no such jar was available in
   this environment to inspect) — the prefix is added on the stated convention
   analogy with `zkcharts`/`zkmax`/etc., per the brief's own example. If ZKPivot's
   real artifact naming differs, the test's assumption (`zkpivot-3.1.0.jar`) would
   need updating, but the mechanism (prefix list) is trivially extensible.
4. **W5**: `looksLikeParseError`'s ordering relative to the new `isUiException` check
   is untested by any fixture in this repo (no PARSE-phase fixture exists), so the
   claim "a genuine parse error would still classify as PARSE, not COMPOSE" is
   reasoned from code order, not test-proven. This mirrors a pre-existing gap
   (`RenderPhase.PARSE`/`CLASSPATH`/`RESOURCE` were already never exercised by any
   test before this round) — not something this round introduced, but not closed
   either, consistent with the brief's instruction not to build beyond what's needed.
5. No `runIde` spot-check was performed for this round (not required per E4-G3's
   wording — "a fresh runIde spot-check only if runtime behavior changed"; this
   round's plugin-side change is a 3-prefix string-list addition with no UI/runtime
   behavior surface, and `verifyPlugin` + the existing automated `AC-5`-slice test
   already cover plugin-loads-clean).

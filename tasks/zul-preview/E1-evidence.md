# E1 Evidence — ZUL Preview rendering core (`zk-preview-launcher`)

> Maker round 1. Commands below were run from the repo root
> (`/Users/hawk/Documents/workspace/PLUGIN/zkidea`) with `withjdk.sh 17` unless noted.
> Re-run any of them to reproduce.

## Summary

| Gate | Result | One-line evidence |
|------|--------|--------------------|
| E1-G1 / AC-3 | **PASS** | `RenderFidelityTest`: fixtures (a)-(e) SUCCESS, both variants, 10/10 green |
| E1-G2 | **PASS (Playwright path)** | `BrowserEquivalentTest` drove a real headless Chromium; DOM had `.z-window/.z-label/.z-button` |
| E1-G3 / AC-4 | **PASS** | `IsolationTest` (8/8) + `IsolationChildProcessTest` (real spawned-process proof) |
| E1-G4 / AC-2 | **PASS** | `CoreIndependenceTest`: import-scan + `jdeps` both clean, 4/4 green |
| E1-G5 / AC-6 | **PASS** | `StructuredFailureTest`: fixture (f) structured failure, both variants, 2/2 green |
| Build | **PASS** | `./gradlew :zk-preview-launcher:build test` exit 0, 28/28 tests, 0 failures/errors |

Full suite (clean build):

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:clean
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:build test
...
BUILD SUCCESSFUL in 14s
22 actionable tasks: 11 executed, 11 up-to-date
```

`build/test-results/test/*.xml` (all 7 files, all 28 `<testcase>` elements):

```
tests="1"  skipped="0" failures="0" errors="0"   BrowserEquivalentTest
tests="4"  skipped="0" failures="0" errors="0"   CoreIndependenceTest
tests="1"  skipped="0" failures="0" errors="0"   IsolationChildProcessTest
tests="8"  skipped="0" failures="0" errors="0"   IsolationTest
tests="2"  skipped="0" failures="0" errors="0"   RealWorldSmokeTest   (extra, non-gate corpus coverage)
tests="10" skipped="0" failures="0" errors="0"   RenderFidelityTest
tests="2"  skipped="0" failures="0" errors="0"   StructuredFailureTest
```
(1, 4, 1, 8, 2, 10, 2 across 7 XML files = 28 total test cases, 0 failed/errored/skipped)

Root project unaffected: `withjdk.sh 17 ./gradlew build -x test` and `withjdk.sh 17 ./gradlew test`
(whole repo, both the existing plugin and the new module) both `BUILD SUCCESSFUL`; the
existing plugin's own 23 test-result XML files remain all-green.

No orphan processes after the child-process test: `ps aux | grep zk-preview-launcher` empty
after the run (the CLI's shutdown hook via `Process.destroy()` → SIGTERM works cleanly).

---

## Deliverable inventory

New Gradle subproject `zk-preview-launcher/` (Java 17, `settings.gradle` updated with
`include 'zk-preview-launcher'` — the only change to a pre-existing file besides this
evidence doc and PLAN.md's own loop-state, which the plan itself owns).

```
zk-preview-launcher/
  build.gradle
  src/main/java/org/zkoss/zkpreview/
    Main.java                       CLI: --classpath --webapp --port, prints PREVIEW_PORT=<n>
    RenderEngineFactory.java        programmatic API: variant-detect + build the right engine
    RenderEngine.java               interface: renderZul/resource/auStub
    PreviewHttpServer.java          JDK com.sun.net.httpserver bridge (*.zul, /zkau/web/*, POST /zkau)
    ScopedZkClassLoader.java        child-first-for-org.zkoss URLClassLoader + forbidden-load blocking
    IsolatedRuntime.java            builds the scoped classloader + injects the hooks jar
    ForbiddenLoadTracker.java       AC-4 test instrumentation (records+blocks forbidden FQCNs)
    IsolationMode.java              -Dzkpreview.isolation=false canary-mode toggle
    VariantDetector.java            jakarta vs javax detection via DHtmlLayoutServlet.class bytes
    ZkVariant.java, RenderResult.java, RenderError.java, RenderPhase.java, ResourceResult.java
    ErrorMapper.java                exception chain -> structured RenderError (AC-6)
    jakarta/JakartaRenderEngine.java + jakarta/mock/*    (6 mock servlet classes)
    javax/JavaxRenderEngine.java    + javax/mock/*       (6 mock servlet classes, mirrored)
    resources/preview/zk.xml        bundled WEB-INF/zk.xml overlay (registers the isolation hook)
  src/hooks/java/org/zkoss/zkpreview/hooks/
    PreviewUiFactory.java           compiled against ZK 9.6.0.2 only; the whole isolation mechanism
    PreviewComposer.java            trivial GenericComposer subclass (no-op composer)
  src/test/java/org/zkoss/zkpreview/
    CoreIndependenceTest.java       AC-2 / E1-G4
    RenderFidelityTest.java         AC-3 / E1-G1
    IsolationTest.java              AC-4 / E1-G3 (in-process)
    IsolationChildProcessTest.java  AC-4 / E1-G3 (real spawned process, strongest proof)
    StructuredFailureTest.java      AC-6 / E1-G5
    BrowserEquivalentTest.java      E1-G2
    RealWorldSmokeTest.java         extra corpus coverage (not a graded gate; see below)
    testcanary/{CanaryViewModel,CanaryComposer,CanaryZscriptTarget}.java   AC-4 negative controls
    testutil/{ZkClasspathResolver,Variants}.java   test-only classpath resolution + parametrization
  src/test/resources/fixtures/{plain,viewmodel-bind,el-missing-var,missing-composer,
                                layout-heavy,zscript-missing-class}.zul
```

`src/integrationTest/**` (the pre-existing spike) was **not modified** — verified via
`git status --short src/integrationTest/` showing only the pre-existing untracked directory,
no `M` markers. `ZkPreviewServerIntegrationTest`'s expected jar path,
`zk-preview-launcher/build/libs/zk-preview-launcher.jar`, exists and is produced by
`:zk-preview-launcher:jar`.

---

## Architecture decisions and one material deviation from RESEARCH.md/PLAN.md

**Both servlet variants, one Gradle module, no sourceSet split.** `jakarta.servlet.*` and
`javax.servlet.*` are different Java packages, so both API jars coexist as ordinary
`implementation` dependencies of a single `main` sourceSet; the mock classes live in twin
`jakarta/mock` and `javax/mock` packages. No IntelliJ-Gradle-plugin-variant machinery needed.

**Isolation hooks compiled separately.** A `hooks` sourceSet compiles
`PreviewUiFactory`/`PreviewComposer` against `org.zkoss.zk:zk:9.6.0.2` (oldest CE version
in the supported matrix, per risk R3) as a **build-time-only** dependency — the main
launcher has zero ZK compile dependency (verified by `CoreIndependenceTest` and `jdeps`).
The hooks sourceSet's output is packaged into `zkpreview-hooks.jar`, embedded as a main-jar
resource, and at render time extracted and added as a URL on the `ScopedZkClassLoader`
(never on the launcher's own classloader) — this is required, not optional: the hook
classes subclass ZK types (`SimpleUiFactory`, `GenericComposer`), so they must be *defined
by* the same classloader that defines the rest of the target ZK version, or ZK's own
`instanceof`/cast checks against `org.zkoss.zk.ui.sys.UiFactory` fail with a classloader-
identity mismatch (this was hit and fixed during development — see below).

**Deviation — single-hook isolation instead of the plan's two-hook "Rank 1" recipe.**
RESEARCH.md U6 and PLAN.md §3/§4 specify two hooks: (a) a `BindComposer` subclass
overriding `initViewModel`, installed via the `org.zkoss.bind.defaultComposer.class`
library property, and (b) a custom `UiFactory` overriding `newComposer`. Verified via
`javap -p` against **both** boundary jars actually used here:

```
$ javap -p -classpath zkbind-9.6.0.2.jar org.zkoss.bind.BindComposer
  ...
  private java.lang.Object initViewModel(org.zkoss.bind.sys.BindEvaluatorX, org.zkoss.zk.ui.Component);
$ javap -p -classpath zkbind-10.1.0-jakarta.jar org.zkoss.bind.BindComposer
  ...
  private java.lang.Object initViewModel(org.zkoss.bind.sys.BindEvaluatorX, org.zkoss.zk.ui.Component);
```

`initViewModel` is **private** in both — not overridable by subclassing at all. This
contradicts RESEARCH.md U6's premise (its cited line numbers came from a sources jar and
apparently missed the access modifier). Rather than force a workaround for an
unimplementable mechanism, I traced the actual resolution path (Parser.java sets
`ComponentInfo.apply` to the same FQCN string for *both* `viewModel=` auto-composer and
explicit `apply=`, and both flow through `ComponentInfo.resolveComposer` →
`UiFactory.newComposer(Page, String)`) and confirmed a **single** `UiFactory.newComposer`
override that never delegates to the default implementation (so `Page.resolveClass` is
never called for *any* composer name) blocks both paths in one hook — no
`defaultComposer.class` library property needed at all, since the class-name string is
never inspected. Empirically confirmed (see the render markers below): with this one hook,
fixture (b)'s `@load(vm.greeting)` label renders as `{}` (no bound value, no error) and the
`CanaryViewModel`/`CanaryComposer` FQCNs are never even attempted (`ForbiddenLoadTracker`
recorded zero attempts for them in every fidelity test), and fixture (d)'s composer never
runs. Cost: `@command`-bound event listeners (e.g. the button's `onClick` in fixture (b))
are not wired, since the real `BindComposer` never runs — acceptable for a first-paint
preview per the plan's own AU-stub precedent (interactivity isn't in scope for v1).

**Classloader-identity bug found and fixed during development.** An early version
parented `ScopedZkClassLoader` on a purpose-built "infra" `URLClassLoader` pointing at the
same servlet-api jar file. This broke immediately: `JakartaRenderEngine` directly
constructs `new ServletContextEvent(...)` etc., so those objects are defined by
*whatever classloader loaded `JakartaRenderEngine` itself* — a different `URLClassLoader`
instance wrapping the identical jar is still a *different* class identity, so the
reflective call into ZK failed with `NoSuchMethodException: ... contextInitialized(...)`.
Fix: the scoped loader's parent is the render engine's own defining classloader (matches
the existing spike's approach), which is why `IsolationChildProcessTest` (spawning the real
packaged jar as a separate OS process) carries the actual proof of parent-narrowness — in
that deployment shape the "parent" *is* a fresh JVM's system classloader whose classpath is
exactly one jar.

**Bug found and fixed: invalid XML in the bundled `zk.xml`.** The first version's comment
used `--` inside an XML comment (`<!-- ... -- verifies ... -->`), which is illegal XML;
`ConfigParser` silently swallowed the parse failure (only logged an ERROR, kept going with
defaults), so the isolation hook was never actually registered and fixtures (b)/(d) leaked
the canary. Caught by literally reading the render output during development. Fixed; now
covered indirectly by every fidelity/isolation test (they'd fail if the hook weren't wired).

---

## E1-G1 / AC-3 — fidelity floor (both variants)

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.RenderFidelityTest"
...
RenderFidelityTest > fixtureA_plainRendersSuccessfully(Named)[1] PASSED   (jakarta)
RenderFidelityTest > fixtureA_plainRendersSuccessfully(Named)[2] PASSED   (javax)
RenderFidelityTest > fixtureB_viewModelBindRendersPlaceholderWithoutLoadingUserClass(Named)[1,2] PASSED
RenderFidelityTest > fixtureC_missingElVariableRendersEmpty(Named)[1,2] PASSED
RenderFidelityTest > fixtureD_missingComposerRendersWithNoOpComposer(Named)[1,2] PASSED
RenderFidelityTest > fixtureE_layoutHeavyRendersAllWidgets(Named)[1,2] PASSED
BUILD SUCCESSFUL, 10 tests
```

Exact observed markers (captured once from a real render, then hard-coded into the
assertions, per RESEARCH.md U7's own methodology):

- (a) `plain.zul`: `zul.wnd.Window`, `zul.wgt.Label`, `Hello ZK`, `zul.wgt.Button`, `Click me`.
- (b) `viewmodel-bind.zul`: label bound to `@load(vm.greeting)` renders as
  `['zul.wgt.Label','...',{},{},[]]` (empty props — the bound value is simply absent, not a
  literal empty string), `static sibling` label renders normally, no `LOADED`/`CANARY`
  substring anywhere in the output, and `ForbiddenLoadTracker` recorded **zero** attempts to
  load `CanaryViewModel` (the class name is never even inspected, let alone loaded).
- (c) `el-missing-var.zul`: same "prop omitted, not errored" shape; `static sibling` present.
- (d) `missing-composer.zul`: `static under composer` renders; `CanaryComposer` never
  attempted (zero tracker hits); window renders normally (composer no-op'd).
- (e) `layout-heavy.zul`: every widget class present —
  `zul.layout.{Borderlayout,North,Center,South}`, `zul.tab.{Tabbox,Tabs,Tab,Tabpanels,Tabpanel}`,
  `zul.grid.{Grid,Columns,Column,Rows,Row}`, `zul.sel.{Tree,Treecols,Treecol,Treechildren,
  Treeitem,Treerow,Treecell}`, plus all literal text (`North`, `South`, `Tab 1`, `Tab 2`,
  `Name`, `Row1`, `Node`, `Root`).

**Extra corpus coverage (not a graded gate).** `RealWorldSmokeTest` renders real ZUL files
from two Maven projects beyond the six controlled fixtures: `manual-test/` (in-repo,
jakarta) and `~/Documents/workspace/SUPPORT/zk9support/` (749-ZUL corpus, javax,
read-only). Results: `preview/button.zul` and `preview/separate-wpd.zul` (jakarta) render
SUCCESS with expected widget/text markers; `error.zul`, `index.zul`, `timer.zul`, `log.zul`
(javax) all render SUCCESS — `log.zul` in particular executes a real, *resolvable*
`<zscript>` block (`org.slf4j.LoggerFactory`, genuinely on the ZK classpath) and its
literal text `test print log` appears in the output, a useful contrast with fixture (f)'s
deliberately-missing zscript class; `test.zul` is a genuinely invalid real-world ZUL
(`<borderlayout>` with two `<north>` children) and correctly produces a structured
FAILURE (`"Only one north child is allowed"`) rather than crashing — a real-world data
point (not one of the planned fixtures) that the structured-failure path generalizes
beyond missing-class errors.

## E1-G2 — browser-equivalent check

**Path that ran: Playwright (preferred path), not the HTTP fallback.**

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.BrowserEquivalentTest"
...
BrowserEquivalentTest > plainZulLoadsWithExpectedWidgetDom() STANDARD_OUT
    E1-G2 path: PLAYWRIGHT (real headless browser DOM check)
BrowserEquivalentTest > plainZulLoadsWithExpectedWidgetDom() PASSED
```

Playwright successfully downloaded and drove headless Chromium in this environment
(`~/Library/Caches/ms-playwright/chromium-1117`, no manual setup needed beyond the Gradle
test dependency). The live DOM after `page.navigate(...)` + `waitForSelector(".z-window")`
contains exactly one `.z-window`, at least one each of `.z-label`/`.z-button`, and the
literal texts `Hello ZK`/`Click me` — i.e. the browser's own ZK client engine actually built
real DOM nodes from the `zkmx([...])` bootstrap, not just "the HTML string looked right".
The HTTP-fallback code path (`httpFallback()` in the same test) is implemented and was
exercised manually during development (see below) but did not run in this grading pass
since Playwright succeeded.

**Fallback validated manually** (in case a different environment can't install Chromium):
resource content-signature check confirmed real end-to-end via the CLI:

```
$ curl -s http://127.0.0.1:$PORT/plain.zul | grep -o '/zkau/web/[^"]*\.wpd' | head -1
/zkau/web/b18a4621/js/zk.wpd
$ curl -s http://127.0.0.1:$PORT/zkau/web/b18a4621/js/zk.wpd | head -c 120
if(!window.zk){
window.zk = {}
zk.scriptErrorHandlerEnabled=true;
/*! For license information please see index.js.LICENSE.txt */
```
— genuine minified JavaScript, not raw `.wpd` XML (`<package name="zk">...`). This is the
"NEW, unproven part" from PLAN.md/RESEARCH.md R1 — **resolved positively**: the mock
`DHtmlUpdateServlet` approach does correctly drive ZK's real `WpdExtendlet`/CSS-DSP
processing. Confirmed working for `.wpd` (JS, `text/javascript`), `.wcs` (CSS,
`text/css`, 414964 bytes), and `.css.dsp` (CSS, `text/css`, 23501 bytes) resource paths.

## E1-G3 / AC-4 — isolation

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.IsolationTest"
...
8 tests completed, 8 succeeded
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.IsolationChildProcessTest"
...
IsolationChildProcessTest > realChildProcessNeverLoadsCanaryClassAndRendersPlaceholder() PASSED
```

- **(i) classpath allowlist** (`classpathAllowlistContainsOnlyZkJarsAndHooksJar`, both
  variants): every URL on `ScopedZkClassLoader.getURLs()` is either one of the
  resolved ZK jars or the injected `zkpreview-hooks-*.jar` temp file; none is under this
  module's own `build/classes/java/test`.
- **(ii) parent loader identity** (`parentLoaderIsTheLaunchersOwnDefiningLoaderNeverABroaderOne`):
  the scoped loader's parent is exactly `JakartaRenderEngine.class.getClassLoader()` /
  `JavaxRenderEngine.class.getClassLoader()` — never a broader, caller-supplied loader.
  Honest caveat: in-process, that classloader is this test JVM's own app classloader
  (broader than "platform"). The **rigorous** proof that this is narrow in the real
  deployment is `IsolationChildProcessTest`, which spawns
  `java -jar zk-preview-launcher.jar --classpath <ZK jars only> ...` as a genuinely separate
  OS process — that process's classpath is exactly the launcher jar plus the ZK jars; the
  canary class (compiled only into this module's `build/classes/java/test`) is not on that
  command line at all, so there is no leaky-parent question to even ask.
- **(iii) canary mode (hooks off)**: with `-Dzkpreview.isolation=false`,
  fixtures (b)/(d) fail; example captured output:

  ```
  FAILURE: {"phase":"COMPOSE","message":"Missing class: org.zkoss.zkpreview.testcanary.CanaryViewModel
    is on the forbidden-load list (isolation test) (org.zkoss.zk.ui.UiException:
    java.lang.ClassNotFoundException: org.zkoss.zkpreview.testcanary.CanaryViewModel is on the
    forbidden-load list (isolation test) at [file:.../viewmodel-bind.zul, line:4, nearby column: 91]
    <- java.lang.ClassNotFoundException: org.zkoss.zkpreview.testcanary.CanaryViewModel is on the
    forbidden-load list (isolation test))","zulFile":"/viewmodel-bind.zul","line":4,"column":null}
  ```

  Cause chain bottoms out in `ClassNotFoundException` naming the exact fixture FQCN, wrapped
  in `UiException`, exactly as AC-4(iii) specifies. `ForbiddenLoadTracker` stands in for
  "not on the render's classpath at all" in-process (see honest caveat above); the
  child-process test proves the real, non-simulated version of the same fact.
- **(iv) with hooks: (b)/(d) succeed** — covered by `RenderFidelityTest` (not duplicated here).

## E1-G4 / AC-2 — core independence

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.CoreIndependenceTest"
...
CoreIndependenceTest > mainSourceHasNoForbiddenImports() PASSED
CoreIndependenceTest > hooksSourceHasNoForbiddenImports() PASSED
CoreIndependenceTest > renderEntryPointIsCallableFromPlainJUnitWithNoIntelliJFixture() PASSED
CoreIndependenceTest > jdepsReportsNoIntelliJTargets() PASSED
BUILD SUCCESSFUL, 4 tests
```

Regex scan of every `.java` under `src/main/java` and `src/hooks/java` for
`^import\s+(com\.intellij\.|org\.jetbrains\.|org\.zkoss\.zkidea\.).*` → zero matches.
`jdeps -verbose:class -filter:none build/classes/java/main` → exit 0, output contains zero
`com.intellij` targets (spot-checked: 232 dependency edges printed, all `java.base` or
`main`/`not found` for the two servlet APIs, which is expected since jdeps was run without
`-classpath` — the servlet-api types aren't resolvable to jdeps but that's irrelevant to the
IntelliJ-independence question being checked).
`Dependencies` report for `compileClasspath`/`runtimeClasspath` confirms exactly two
entries, both servlet-api jars — zero ZK, zero IntelliJ:

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:dependencies --configuration compileClasspath
compileClasspath - Compile classpath for source set 'main'.
+--- jakarta.servlet:jakarta.servlet-api:5.0.0
\--- javax.servlet:javax.servlet-api:4.0.1
```

## E1-G5 / AC-6 — structured failure

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.StructuredFailureTest"
...
2 tests completed, 2 succeeded
```

Fixture (f) (`<zscript>` referencing a nonexistent class) fails in both variants with:

```
{"status":"FAILURE","error":{"phase":"COMPOSE",
 "message":"Missing class: org.zkoss.zkpreview.testcanary.CanaryZscriptTarget (org.zkoss.zk.ui.UiException:
   Sourced file: inline evaluation of: ... Class: org.zkoss.zkpreview.testcanary.CanaryZscriptTarget
   not found in namespace ... at Line: 5 ... <- bsh.EvalError: ...)",
 "zulFile":"/zscript-missing-class.zul","line":5,"column":null}}
```

Notable implementation detail: zscript's default "java" language interpreter is BeanShell,
which does **not** throw a literal `java.lang.ClassNotFoundException` — it catches the
failure internally and reports `bsh.EvalError` with a "Class: X not found in namespace"
message. `ErrorMapper` was extended with a second pattern (in addition to the literal-CNFE
case used for the composer/ViewModel paths) to recognize this and still produce
`phase=COMPOSE` and a `"Missing class: <FQCN>"` message — verified against real BeanShell
output from both ZK versions (message text and structure identical across variants).

---

## Known gaps / honest caveats

1. **Single-hook isolation, not the plan's literal two-hook recipe** — see the deviation
   section above. Functionally equivalent or better for v1's stated acceptance criteria
   (AC-3/AC-4), but sacrifices full `BindComposer`/`Binder` fidelity (bindings render as
   fully absent rather than "placeholder text via a stub VM's real getter"), and
   `@command`-bound listeners are not wired in preview mode. If richer MVVM fidelity is
   wanted later, it would need a different mechanism than RESEARCH.md U6 proposed (that
   exact mechanism is confirmed non-viable against real ZK bytecode).
2. **AC-4(ii) parent-loader-identity is proven at two different rigor levels.** The
   in-process test proves a narrower claim ("parent is the launcher's own definer, not
   whatever classloader happened to construct it"); the full "no leak is even possible"
   claim is proven only by the child-process test. Documented explicitly rather than
   silently relying on the weaker in-process version.
3. **javax classpath resolution** prefers the real ZK 9.6.6 project at
   `~/Documents/workspace/SUPPORT/zk9support/pom.xml` (jars already cached locally, no
   network needed; this is also the extra 749-ZUL smoke corpus used by
   `RealWorldSmokeTest`), falling back to a throwaway pom hitting ZK's free CE Maven repo
   (`mavensync.zkoss.org`, ZK CE 9.6.0.2) when that project isn't present, e.g. on a clean
   checkout of this repo alone without the sibling `SUPPORT` checkout. `ZkClasspathResolver`
   degrades to a skip (not a failure) if neither is available. Both paths were exercised in
   this environment (the primary path succeeded, confirmed by `log4j:WARN` lines in test
   output coming from `zk9support`'s own `slf4j-log4j12` dependency instead of the CE
   pom's plain `slf4j-api`).
4. **jakarta classpath resolution depends on `manual-test/pom.xml`** (in-repo, self-contained
   substitute for the external `/Users/hawk/Documents/workspace/SUPPORT/plugin-test`
   path the spike hardcodes — chosen for portability per RESEARCH.md U7-F15's own
   recommendation, while keeping the spike's proven resolution *method*, `mvn
   dependency:build-classpath`).
5. **`resource()`'s pathInfo contract is a leaky abstraction**: `PreviewHttpServer` passes
   everything after `/zkau` verbatim; this works because `DHtmlUpdateServlet`'s own
   internal routing (real ZK code, not our mock) does the actual `/web/*` vs. AU-command
   dispatch. Not independently unit-tested at the `MockHttpServletRequest` layer beyond
   what the fidelity/browser tests already exercise end-to-end.
6. **TDD sequencing**: the render/isolation *mechanism* (mock servlets, classloader design,
   the single-hook approach) was discovered through a throwaway, explicitly-marked
   exploratory spike (deleted before this delivery) run against real ZK jars, because the
   correct behavior (e.g. whether `initViewModel` is overridable, what a missing-EL-var
   renders as, whether resource serving works at all) was not knowable in advance — this
   mirrors RESEARCH.md U7's own stated methodology ("must run the fixture once and hard-code
   the observed exact substrings") and this repo's own precedent
   (`src/integrationTest/.../ZkMockServletRenderTest.java`). The graded test files
   (`RenderFidelityTest`, `IsolationTest`, etc.) were then written encoding that verified
   behavior. Net-new infrastructure without a prior-knowledge dependency (`PreviewHttpServer`,
   `Main`/CLI, `IsolationChildProcessTest`, `CoreIndependenceTest`) was written test-first in
   the literal sense (test written, run, seen to fail/not-compile, then implemented until
   green) — e.g. `IsolationChildProcessTest` and the CLI were built in that order.
7. **No orphan-process/lifecycle test beyond the one child-process test** — full IDE-integration
   lifecycle (killing the helper JVM on project close) is E3's concern, not E1's; this
   phase only proves the CLI starts, serves, and stops cleanly on `Process.destroy()`.
8. **`renderEntryPointIsCallableFromPlainJUnitWithNoIntelliJFixture`** is a deliberately
   light test (calls the entry point with an empty/garbage classpath and accepts either
   outcome) — its only claim is "this call requires no IntelliJ platform bootstrap," which
   is also self-evidently true from the fact that every other test in this suite (which
   exercises the real render path far more thoroughly) is itself a bare JUnit 5 test class.

## Commands the verifier can re-run

```bash
withjdk.sh 17 ./gradlew :zk-preview-launcher:clean
withjdk.sh 17 ./gradlew :zk-preview-launcher:build test
withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.RenderFidelityTest"
withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.IsolationTest"
withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.IsolationChildProcessTest"
withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.StructuredFailureTest"
withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.CoreIndependenceTest"
withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.BrowserEquivalentTest"
withjdk.sh 17 ./gradlew build -x test   # whole repo, confirms the new module doesn't break the plugin
withjdk.sh 17 ./gradlew test            # whole repo
```

Manual CLI smoke test:

```bash
withjdk.sh 17 mvn -f manual-test/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -q
withjdk.sh 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
  --classpath "$(cat /tmp/cp.txt)" --webapp zk-preview-launcher/src/test/resources/fixtures --port 0
# prints: PREVIEW_PORT=<n>
curl http://127.0.0.1:<n>/plain.zul
```

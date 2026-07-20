# E1 Verdict — Round 1 (VERIFIER, independent)

> Verifier role: judge objective, machine-checkable gate conditions against PLAN.md §4/§5.
> Every verdict below was reproduced by re-running commands; the maker's evidence file was
> NOT trusted without reproduction. Default JDK here is 11, so every command used the
> `withjdk.sh 17` wrapper (confirmed JAVA_HOME → zulu-17).

## Verdict table

| # | Item | Verdict | One-line evidence |
|---|------|---------|-------------------|
| V1 | E1-G1 / AC-3 fidelity | **PASS** | `:zk-preview-launcher:clean :test` from clean → BUILD SUCCESSFUL; RenderFidelityTest 10/10 (both variants, skipped=0); real render literally contains `zul.wgt.Label','..',{value:'Hello ZK'}` (§5 property-tied regex present). |
| V2 | E1-G2 browser-equivalent | **PASS** | Test log printed `E1-G2 path: PLAYWRIGHT (real headless browser DOM check)`; asserts `.z-window`=1, `.z-label`≥1, `.z-button`≥1, `Hello ZK`/`Click me` in live DOM; re-ran clean → PASSED (not skipped, not fallback). |
| V3 | E1-G3 / AC-4 isolation | **PASS** | IsolationTest 8/8 + IsolationChildProcessTest 1/1; I also reproduced hooks-OFF in a real separate process (ZK-jars-only cp) → `UiException → ClassNotFoundException: ...CanaryViewModel` at line 4. |
| V4 | E1-G4 / AC-2 independence | **PASS** | CoreIndependenceTest 4/4; my grep of main+hooks sources for `com.intellij`/`org.jetbrains`/`org.zkoss.zkidea.` → NONE; `dependencies --configuration compileClasspath` = only the two servlet-api jars. |
| V5 | E1-G5 / AC-6 structured failure | **PASS** | StructuredFailureTest 2/2; fixture (f) → `{"status":"FAILURE",...,"phase":"COMPOSE","message":"Missing class: ...CanaryZscriptTarget..."}` with zulFile + line. |
| V6 | Build integrity | **PASS** | Root `./gradlew build test` → BUILD SUCCESSFUL, EXIT=0; `git status --porcelain` shows exactly one tracked modification: ` M settings.gradle`; `src/integrationTest/` remains untracked (`??`). |
| V7 | Deviation audit | **PASS** | `javap -p` confirms `BindComposer.initViewModel` is **private** in BOTH zkbind-9.6.0.2 and zkbind-10.1.0-jakarta → U6 subclass recipe is unimplementable; single `UiFactory.newComposer` hook is functionally equivalent for E1's gates. |

## E1 gate roll-up

| Gate | Verdict |
|------|---------|
| E1-G1 (AC-3 fidelity floor, both variants) | **PASS** |
| E1-G2 (browser-equivalent, Playwright path) | **PASS** |
| E1-G3 (AC-4 isolation) | **PASS** |
| E1-G4 (AC-2 core independence) | **PASS** |
| E1-G5 (AC-6 structured failure) | **PASS** |

**Overall: all five E1 gates PASS.** No blocking defects.

---

## Evidence detail (reproduced)

### V1 — E1-G1 / AC-3
- `withjdk.sh 17 ./gradlew :zk-preview-launcher:clean :zk-preview-launcher:test` → `BUILD SUCCESSFUL in 14s`, 10 actionable tasks executed.
- Test-result XMLs (`build/test-results/test/*.xml`) parsed directly: `tests` totals 1+4+1+8+2+10+2 = 28, and **every** file has `skipped="0" failures="0" errors="0"`. Tests genuinely ran (not aborted via `Assumptions`) — confirmed by `log4j:WARN ... org.zkoss.zk.ui.http.WebManager` lines proving real ZK code executed on the render path.
- Fixtures match PLAN §5 (a)–(f) exactly: (a) window+label+button; (b) `viewModel="@id('vm') @init('...CanaryViewModel')"` + `@load(vm.greeting)` + `onClick="@command('doIt')"`; (c) `${missing.prop}`; (d) `apply="...CanaryComposer"`; (e) borderlayout/tabbox/grid/tree; (f) `<zscript>` referencing `...CanaryZscriptTarget`.
- Property-tie: I launched the packaged CLI against the jakarta classpath and `curl`ed `plain.zul`. Raw output contains `zul.wgt.Label','lVgY1',{value:'Hello ZK'},{},[` — i.e. §5's example regex `zul\.wgt\.Label` + `value:'Hello ZK'` is literally present in the real render. The unit test asserts `contains("zul.wgt.Label")` && `contains("Hello ZK")`; "Hello ZK" occurs in the fixture only as the label value, so the assertion is satisfied by the property-tied content. (Assertion-strength note in Suggestions.)

### V2 — E1-G2
- Clean run stdout: `BrowserEquivalentTest > plainZulLoadsWithExpectedWidgetDom() STANDARD_OUT  E1-G2 path: PLAYWRIGHT (real headless browser DOM check)` then `PASSED`.
- Source review: `tryPlaywright()` prints "PLAYWRIGHT" only *after* it returns `true`, which happens only after all DOM assertions pass. Assertion failures are `AssertionError` (an `Error`, not `Exception`), so they propagate and fail the test rather than being swallowed by the `catch (Exception)` → HTTP-fallback branch. Therefore the printed PLAYWRIGHT path is a genuine real-browser DOM pass, not a silent skip/fallback.
- The gate's PLAN §4 fallback (HTTP content-signature) is also sanctioned; it was not needed here.

### V3 — E1-G3 / AC-4
- (i) `classpathAllowlistContainsOnlyZkJarsAndHooksJar` PASSED both variants — asserts every `ScopedZkClassLoader.getURLs()` entry is a resolved ZK jar or the injected `zkpreview-hooks-*.jar`, and none is under the module's `build/classes/java/test`.
- (ii) `parentLoaderIsTheLaunchersOwnDefiningLoaderNeverABroaderOne` PASSED — parent is the engine's own defining loader. Maker documents the in-process weakness honestly; the strong proof is the child-process test.
- (iii) Canary (hooks OFF): IsolationTest canary methods PASSED (message contains the exact FQCN; tracker recorded the load attempt). I additionally reproduced the **real-absence** form: `java -Dzkpreview.isolation=false -jar ... --classpath <ZK jars only>` then `curl .../viewmodel-bind.zul` →
  `{"phase":"COMPOSE","message":"Missing class: org.zkoss.zkpreview.testcanary.CanaryViewModel (org.zkoss.zk.ui.UiException: java.lang.ClassNotFoundException: org.zkoss.zkpreview.testcanary.CanaryViewModel at [file:.../viewmodel-bind.zul, line:4, ...] <- java.lang.ClassNotFoundException: org.zkoss.zkpreview.testcanary.CanaryViewModel)","zulFile":"/viewmodel-bind.zul","line":4}`.
  Cause chain bottoms at `ClassNotFoundException` naming the exact fixture FQCN — AC-4(iii) satisfied in production shape (canary genuinely absent from the classpath, not merely tracker-blocked).
- (iv) `IsolationChildProcessTest` PASSED. Source confirms it `ProcessBuilder(javaExe, "-jar", jar, "--classpath", <ZK jars only>, "--webapp", ..., "--port", "0")` — a real separate JVM. `build.gradle:107 test.dependsOn(jar)` guarantees the jar is built first (so the test cannot silently skip), and `build.gradle:59` sets `zkpreview.moduleDir` for the jar path. It asserts `static sibling` renders and neither `LOADED` nor `CANARY` leaks.
- No orphan process after my CLI runs: `ps aux | grep zk-preview-launcher.jar` → NONE (clean `Process.destroy()`/SIGTERM shutdown; exit 143).

### V4 — E1-G4 / AC-2
- `CoreIndependenceTest` 4/4 PASSED (import-scan main + hooks, jdeps no `com.intellij`, standalone-callable entry point).
- Independent grep over `src/main/java` + `src/hooks/java` for `com.intellij` / `org.jetbrains` / `org.zkoss.zkidea.` → **NONE FOUND**.
- `./gradlew :zk-preview-launcher:dependencies --configuration compileClasspath` →
  `+--- jakarta.servlet:jakarta.servlet-api:5.0.0` / `\--- javax.servlet:javax.servlet-api:4.0.1` — zero ZK, zero IntelliJ compile deps.

### V5 — E1-G5 / AC-6
- `StructuredFailureTest` 2/2 PASSED (both variants). Asserts `phase` non-null, `message` contains `org.zkoss.zkpreview.testcanary.CanaryZscriptTarget`, `zulFile == "/zscript-missing-class.zul"`, `line` non-null, and JSON begins `{"status":"FAILURE"`. Matches AC-6 `{phase, message containing missing FQCN, zul location}`.

### V6 — Build integrity
- `withjdk.sh 17 ./gradlew build test` (whole repo) → `BUILD SUCCESSFUL`, `EXIT=0`. A failing plugin test would fail `check`/`build`, so exit 0 confirms the pre-existing plugin build is unaffected.
- `git status --porcelain`: the only non-`??` line is ` M settings.gradle` (the single sanctioned tracked change — `include 'zk-preview-launcher'`). `src/integrationTest/` is `??` (untracked), so there is no tracked modification to the spike. (Caveat: git cannot detect content edits to an untracked directory; nothing observed indicates modification, and `zk-preview-launcher/` is entirely new/untracked.)

### V7 — Deviation audit (critical)
- `withjdk.sh 17 javap -p -classpath zkbind-9.6.0.2.jar org.zkoss.bind.BindComposer` → `private java.lang.Object initViewModel(...)`.
- Same for `zkbind-10.1.0-jakarta.jar` → `private java.lang.Object initViewModel(...)`.
  Both `private` ⇒ the RESEARCH.md U6 recipe (subclass `BindComposer`, override `initViewModel`, install via `org.zkoss.bind.defaultComposer.class`) is genuinely **unimplementable**. The maker's central deviation justification is reproduced and correct.
- Single-hook mechanism reviewed: `preview/zk.xml` registers `<ui-factory-class>org.zkoss.zkpreview.hooks.PreviewUiFactory</ui-factory-class>`; `PreviewUiFactory` overrides both `newComposer(Page,String)` and `newComposer(Page,Class)` to return a no-op `PreviewComposer` (extends `GenericComposer`) without ever delegating to `super` (so `Page.resolveClass` is never reached for any composer name).
- Observable consequence verified: with hooks ON, fixture (b) `viewModel=` renders SUCCESS (window + `static sibling` label present, no `LOADED`/`CANARY` leak) and `ForbiddenLoadTracker.getAttempts()` is empty — the VM class is never even inspected. With hooks OFF, the same path DOES attempt to resolve `CanaryViewModel` and fails with CNFE (V3-iii repro), proving the hook is the load-bearing interceptor for the `viewModel=` auto-composer path, not just explicit `apply=`.
- Judgment: functionally equivalent for E1's gates. AC-3(b)'s literal wording explicitly permits "placeholder/empty bound values AND no user-class error", which is exactly what renders. Fidelity loss (bindings not evaluated by a real `Binder`; `@command` listeners unwired) is real but outside AC-3's literal assertions → recorded as non-blocking.

---

## Defects (blocking)

None. All five E1 gates pass under independent reproduction.

## Suggestions (non-blocking)

These are quality/robustness observations only; they did NOT affect any verdict above.

1. **RenderFidelityTest value assertion is slightly looser than §5's example.** The test asserts `contains("Hello ZK")` rather than the property-tied `contains("value:'Hello ZK'")`. The real render output does contain `value:'Hello ZK'` (verified), and in these fixtures the value strings are unique, so the assertions hold — but tightening to the property-tied literal would catch a hypothetical future regression where the text appears detached from the widget property.
2. **AC-4(iii) in-process canary uses a simulated absence.** `ForbiddenLoadTracker` throws CNFE to stand in for "class absent from classpath" because the in-process test JVM does have the canary classes on its own classpath. This is honestly documented by the maker, and the genuine-absence proof exists (child-process test + my manual repro). No action required; noted for transparency.
3. **Single-hook fidelity ceiling.** Because the real `BindComposer`/`Binder` never runs, MVVM bound values render as absent (not as a stub VM's getter output) and `@command` listeners are not wired. Acceptable for v1 first-paint per AC-3, but a known fidelity limit for later phases (already captured in E1-evidence.md "Known gaps").
4. **BrowserEquivalentTest fallback is silent.** `tryPlaywright()` catches broad `Exception` and downgrades to the HTTP path without failing. This is by design (the fallback is a sanctioned gate path) and DOM assertion failures still propagate (they are `Error`s), so correctness is preserved; just be aware that a Playwright *infrastructure* failure would quietly reduce the gate to the weaker HTTP signature check rather than surfacing.

# E3 Verdict — Round 3 (FINAL) — D3 + D4

> Verifier: Opus (fresh context, independent). All conditions objective and re-run.
> `withjdk.sh 17` prefix used for every java/gradle/mvn invocation. Pre-existing
> sandbox launcher PID 34527 left untouched throughout; my own launcher instance
> (PID 93628, port 62382) killed and verified released.

## Verdict table

| # | Check | Verdict | Evidence (command + key output) |
|---|-------|---------|---------------------------------|
| V1 | Suites green; isolation/canary + fidelity not regressed | **PASS** | `./gradlew :zk-preview-launcher:clean :zk-preview-launcher:test` → BUILD SUCCESSFUL; XML reports **34 tests, 0 failures, 0 errors** (9 classes). `./gradlew cleanTest :test` (forced fresh, not cached) → `Task :test` executed, BUILD SUCCESSFUL; XML reports **299 tests, 0 failures, 0 errors** (28 classes, mtime 8.7s old). Key classes present & passing: ApplyTemplateUriTest, IsolationTest, IsolationChildProcessTest, RenderFidelityTest, StructuredFailureTest, VariantDetectorTest, CoreIndependenceTest, RealWorldSmokeTest. |
| V2 | D3 fix — tests + end-to-end corpus reproduction | **PASS** | ApplyTemplateUriTest XML: **4/4 PASS** across both variants. Live launcher vs manual-test (ZK 10.1.0-jakarta, 29-entry mvn classpath): `GET /template-uri-nav.zul` → **HTTP 200**, body contains `zkmx(` + `zul.wnd.Window`; `GET /preview/broken.zul` → **HTTP 500**, `"phase":"COMPOSE"`, message names `org.example.definitely.NoSuchClassAtAll` (line 7); spot-checks `preview/button.zul`, `model.zul`, `command.zul` all **200 + zkmx(**. |
| V3 | D4 fix — sources + non-tautological test | **PASS** | Both `OrderEnumerator` call sites in `ZulPreviewServerService.resolveTarget` carry `.withoutSdk()` (lines 129, 131). `ZkClasspathFilter.filterLibraryJars` uses `file.isFile()` (line 70). ZkClasspathFilterTest XML: **10/10 PASS** incl. `filterLibraryJarsExcludesSdkPseudoEntriesAndNonexistentPaths()` — feeds a real temp `.jar` (survives), a `!`-containing SDK pseudo-entry and a nonexistent temp path (both dropped); asserts only the real jar survives. Non-tautological (uses real `@TempDir` files/dirs). |
| V4 | Normal page paths NOT intercepted by the annotation regex | **PASS** | Regex is `(^|/)@\w+\(`. Direct GETs `/model.zul` → 200+zkmx(, `/WEB-INF/template/row.zul` → 200+zkmx(, `/command.zul` → 200+zkmx(, `/preview/button.zul` → 200+zkmx( — all render exactly as before; only annotation-shaped nested `<apply>` URIs (`/@load(...`) are intercepted. |
| V5 | Repo integrity; no commits; declared file set; no debris | **PASS** | `git rev-parse HEAD` = `35348ef…` (unchanged, no commits). `git status --porcelain` identical to session start. Debris grep clean: **no** WebAppInit, UiLifeCycle/afterShadowAttached, `extends Apply`, `setImplementationClass`; `zk.xml` has only `<disable-event-thread>` + `<ui-factory-class>` (no stray `<listener>`); `build.gradle` has **no** `zuti`/EE-repo remnant (the `hooksCompileOnly org.zkoss.zk:zk:9.6.0.2` + CE maven repo are the pre-existing legit ZK-CE-only hooks deps). |

## Overall defect verdicts

- **D3 — FIXED.** Root-cause fix (`PreviewUiFactory.getPageDefinition` override synthesizing an empty `<zk/>` page for annotation-shaped paths) verified three ways: (1) TDD test asserts BOTH the well-formed `@load(vm.templatePath)` and half-typed `@load('/WEB-INF/template/` shapes — present together in `apply-templateuri-annotation.zul` alongside `<label value="apply marker label"/>` — render SUCCESS with the `zul.wnd.Window`/`zul.wgt.Label`/`apply marker label` markers; (2) the negative test (`apply-templateuri-missing.zul`, genuine literal `/no/such/file.zul`) still fails with the path named in the structured error; (3) the original corpus defect reproduced end-to-end: `/template-uri-nav.zul` now 200+`zkmx(` where the corpus matrix recorded 500 "Page not found", while `/preview/broken.zul` still COMPOSE-fails naming the missing class.
- **D4 — FIXED.** Both `OrderEnumerator` branches carry `.withoutSdk()` (source-level); `filterLibraryJars` switched to `file.isFile()` semantics, drops directories + SDK pseudo-entries + nonexistent paths; new non-tautological test red→green (10/10 now green).

## Defects (blocking)

None.

## Suggestions (non-blocking)

1. **`.withoutSdk()` (D4a) has no dedicated automated test** — `resolveTarget` is private/platform-bound; this is an acknowledged pre-existing gap (PLAN.md "seam test doesn't lock resolveTarget→filterLibraryJars"). The source change is present and correct; the `filterLibraryJars` `isFile()` change is the second line of defense and IS tested. Candidate to close in E4/stage-2.
2. **`getPageDefinition` regex is a path-shape heuristic** (`(^|/)@\w+\(`) — a hypothetical real file whose name literally starts with `@word(` would be misidentified. Same accepted-trade-off class as the codebase's existing `ErrorMapper` regexes; no realistic ZUL filename hits it.
3. **Build ergonomics note (for future verifiers, not the feature):** root `./gradlew cleanTest` matches the `cleanTest`/`test` task in the launcher subproject too (Gradle unqualified task-name matching), so it wipes the launcher's test-results. Use the fully-qualified `:test` / `:zk-preview-launcher:test` paths to keep each subproject's evidence intact.

## Ready for user FINAL manual gate: YES

Both round-3 blocking defects (D3, D4) are independently reproduced as fixed with zero
regression across the full 299 + 34 test suites and a live corpus re-check. No blocking
defects remain.

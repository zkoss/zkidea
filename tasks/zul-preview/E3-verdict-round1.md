# E3 Verdict — Round 1 (Verifier, fresh context, Opus)

> Independent reproduction of the maker's E3 claims. Every verdict below was re-run by
> the verifier; nothing is taken on trust. Commands run from repo root with
> `withjdk.sh 17`. Verifier judges objective conditions only; taste notes are non-blocking.

## Verdict table

| # | Item | Verdict | Evidence (one line) |
|---|------|---------|---------------------|
| V1 | Full build `./gradlew build test` | **PASS** | Exit 0; 295 root tests (20 preview) 0 fail/0 err; launcher 28 tests 0 fail/0 err |
| V2 | Registration behavior (E3-G1 automated / AC-5 slice) | **PASS** | 20 preview tests green; ZulPreviewFileEditorProviderTest genuinely asserts accept(.zul)=true, rejects zk.xml/lang-addon.xml/plain.xml, and createEditor() builds split editor headlessly with non-null fallback preview |
| V3 | Sandbox + distribution bundling | **PASS** | sandbox `.sandbox/plugins/zkidea/lib/zk-preview-launcher.jar`; zip `zkidea/lib/zk-preview-launcher.jar`; runtime `getPluginPath()/lib/zk-preview-launcher.jar` matches both — no path mismatch |
| V4 | Teardown primitive (E3-G2 headless slice) | **PASS** | Test spawns real `sleep 60`, destroy() → dead within 10s; service is project-level Disposable, dispose() kills all servers, registered `<projectService>`, no `Disposer.register(project,…)` |
| V5 | Platform-233 compatibility | **PASS** | No `TextEditorWithPreviewProvider`, no 242+ APIs; all imports from stable packages; `verifyPlugin` BUILD SUCCESSFUL (cross-version binary verify NOT run — see limitation) |
| V6 | Module integrity | **PASS** | Only tracked mods: build.gradle, settings.gradle, plugin.xml; zk-preview-launcher untouched (0 files changed in E3 window); src/integrationTest untouched |
| V7 | Deviation audit | **PASS** | (a) JCEF gate present at content level w/ real fallback panel → F4 intent met; (b) missing resolveTarget IT is outside AC-5's literal automated slice → not a gate violation; (c) DocrootResolver/ZkClasspathFilter tests cover the documented rules meaningfully |
| V8 | Manual-pending inventory | **MANUAL-PENDING** (by design) | AC-5.md has 8 numbered steps w/ expected results incl. refresh-on-save (step 3) + orphan-process check (step 8) |

## Overall gate verdicts

- **E3-G1 (AC-5 preview UI)**:
  - Automated slice (registration test): **PASS**.
  - Manual slice: **MANUAL-PENDING** — human `runIde` run required for: real JCEF render (step 2), save-triggers-refresh (step 3), MVVM empty-bound-value render (step 4), structured-failure JSON display (step 5), no-ZK-jars R7 message (step 6).
- **E3-G2 (teardown, no orphan JVM)**:
  - Kill-primitive headless slice: **PASS**.
  - End-to-end slice: **MANUAL-PENDING** — human must confirm the platform actually calls `dispose()` at project close and that `ps aux | grep zk-preview-launcher` is empty afterward against the *real* launcher jar (steps 7-8).

No blocking defects. The maker's self-assessment (E3-evidence.md) reproduced faithfully; every headless claim it makes is true.

## Defects (blocking)

None.

## Per-item detail (reproduction)

### V1 — Full build — PASS
```
$ withjdk.sh 17 ./gradlew build test
BUILD SUCCESSFUL in 21s   (exit 0)
```
Independent XML aggregation:
- Root `build/test-results/test/*.xml`: 27 files, **295 tests, 0 failures, 0 errors, 0 skipped**; of these the 4 `org.zkoss.zkidea.preview.*` files = **20 tests** (Docroot 5, ManagedPreviewServerTeardown 2, ZkClasspathFilter 7, ZulPreviewFileEditorProvider 6).
- Launcher `zk-preview-launcher/build/test-results/test/*.xml`: 7 files, **28 tests, 0 failures, 0 errors** — unchanged from E1.
Maker's 295 / 20-new / 28-launcher counts all reproduced exactly.

### V2 — Registration behavior — PASS
```
$ withjdk.sh 17 ./gradlew :test --tests "org.zkoss.zkidea.preview.*"
BUILD SUCCESSFUL in 6s   (preview XMLs: 5+2+7+6 = 20 tests, 0 fail/0 err)
```
`ZulPreviewFileEditorProviderTest` assertions are genuine, not tautological:
- (a) `testAccept_zulFile_true`: finds the registered provider via `EP_FILE_EDITOR_PROVIDER` and asserts `accept()` true for `preview.zul`.
- (b) three separate tests assert `accept()` **false** for `zk.xml`, `lang-addon.xml`, and `plain.xml` — the exact false positives a FileType-based check would produce (all share the built-in XML FileType). `ZulPreviewFileEditorProvider.accept()` correctly uses `"zul".equalsIgnoreCase(file.getExtension())`.
- (c) `testCreateEditor_headless_…` first asserts `JBCefApp.isSupported()==false` (proving the fallback path is exercised), then asserts `createEditor()` returns a `TextEditorWithPreview` whose `getPreviewEditor()` is non-null and exposes a component — the JCEF-less fallback branch in `ZulPreviewFileEditor` (lines 84-90) is genuinely run, no browser required.

### V3 — Sandbox + distribution bundling — PASS
```
$ withjdk.sh 17 ./gradlew prepareSandbox
$ find .sandbox -iname "*zk-preview-launcher*"
.sandbox/plugins/zkidea/lib/zk-preview-launcher.jar

$ unzip -l build/distributions/zkidea-0.7.2.zip | grep lib/
 463951 ... zkidea/lib/zk-preview-launcher.jar
 163806 ... zkidea/lib/instrumented-zkidea-0.7.2.jar
 393851 ... zkidea/lib/jsoup-1.13.1.jar
    860 ... zkidea/lib/searchableOptions-0.7.2.jar
```
Path-resolution match: `ZulPreviewServerService.resolveLauncherJar()` = `descriptor.getPluginPath().resolve("lib").resolve("zk-preview-launcher.jar")`. `getPluginPath()` returns the plugin root dir (`plugins/zkidea` in sandbox; `<config>/plugins/zkidea` when installed from the zip whose top dir is `zkidea/`). Both place the jar at `<pluginRoot>/lib/zk-preview-launcher.jar`. **No mismatch.**

### V4 — Teardown primitive — PASS
`ManagedPreviewServerTeardownTest.destroyTerminatesTheUnderlyingProcess()` builds a real `GeneralCommandLine("sleep", 60)` (same `KillableProcessHandler` mechanism the real launcher uses), `start()`, asserts alive, `destroy()`, then `awaitTermination(10s)` true and `isAlive()` false. This is a real OS process, not a mock.
`ZulPreviewServerService`: project-level service (`getInstance` = `project.getService(...)`), `implements Disposable`, `dispose()` iterates `serversByKey.values()` calling `server.destroy()` on each and clears the map — the identical `destroy()` path the test proves. Registered in plugin.xml as `<projectService serviceImplementation="…ZulPreviewServerService"/>`. No `Disposer.register(project,…)` anywhere (only `Disposer.register(this, browser)` in the editor, where `this` is the editor; and `.expireWith(this)` where `this` is the service) — U5-F11/F16 constraint respected.

### V5 — Platform-233 compatibility — PASS
- `grep TextEditorWithPreviewProvider` over the preview package → **absent**.
- `grep createFileEditor|createEditorBuilder|WeighedFileEditorProvider|AsyncFileEditorProvider` → **absent** (none of the U4-flagged 242+ / experimental members used).
- Imports are all stable: `com.intellij.openapi.fileEditor.{TextEditorWithPreview, FileEditorProvider, TextEditor, FileEditor…}`, `…impl.text.PsiAwareTextEditorProvider`, `com.intellij.ui.jcef.{JBCefApp, JBCefBrowser}`, `com.intellij.util.ui.update.{MergingUpdateQueue, Update}`, `com.intellij.execution.{configurations.GeneralCommandLine, process.KillableProcessHandler}`, `com.intellij.openapi.roots.{OrderEnumerator, ModuleRootManager, ProjectFileIndex, ProjectRootManager}` — all present in 233 per U4/U5.
```
$ withjdk.sh 17 ./gradlew verifyPlugin
BUILD SUCCESSFUL in 829ms
```
**Limitation (honest):** `verifyPlugin` validates plugin structure/descriptor only; it does NOT do cross-version (233→261) binary-compatibility verification. Per task instruction, `runPluginVerifier` was NOT run (too heavy). So platform-233-safety here rests on source inspection + the maker's decompile claims + `verifyPlugin`, not on a full binary sweep against every target IDE.

### V6 — Module integrity — PASS
```
$ git status --porcelain        (tracked changes only)
 M build.gradle
 M settings.gradle
 M src/main/resources/META-INF/plugin.xml
```
All three tracked modifications are expected: plugin.xml (2 new `<extensions>` lines), build.gradle (`evaluationDependsOn` + `prepareSandbox` bundling), settings.gradle (`include 'zk-preview-launcher'`, from E1). `git diff --stat` = 21 insertions, 1 deletion across exactly these three files.
`zk-preview-launcher/` and `src/integrationTest/` remain untracked (`??`), unchanged since E1: **0 files under `zk-preview-launcher/src` modified at/after the E3 window (16:14)** — all launcher source mtimes are 13:55-14:13 (the E1 window), while the E3 preview classes are 16:15-16:25. New untracked `manual-test/src/main/webapp/preview/broken.zul` is the manual-QA fixture the evidence discloses (AC-5 step 5). No stray edits.

### V7 — Deviation audit — PASS
- **(a) `accept()` not gated on `JBCefApp.isSupported()`.** PLAN F4's literal ask is "gate on `JBCefApp.isSupported()` with a graceful fallback panel." The gate IS present — at the *content* level: `ZulPreviewFileEditor` constructor (lines 84-90) checks `!JBCefApp.isSupported()` and shows a real explanatory Swing panel ("Preview unavailable: the embedded browser (JCEF) is not supported…"). F4's intent (graceful JCEF fallback panel) is objectively satisfied; the only deviation is *where* the check lives (content vs. `accept()`), which F4 does not pin down, and the chosen placement additionally keeps the split editor available with an explanation instead of silently degrading to plain text. **Not a defect** — interpretation noted.
- **(b) No integration test for `ZulPreviewServerService.resolveTarget` against a real module.** AC-5's literal automated slice is only "`BasePlatformTestCase` test asserts opening a `.zul` yields the preview `FileEditorProvider`." resolveTarget end-to-end (real `OrderEnumerator`/classpath/spawned jar) is **not** part of that slice — it falls under the scripted manual gate. So its absence is **not an E3 gate violation**; the pure-logic pieces it decomposes into (docroot, ZK-jar filter/signature) are unit-tested.
- **(c) DocrootResolver + ZkClasspathFilter test coverage.** DocrootResolverTest (5) covers all three documented tiers: WEB-INF ancestor, `webapp`-named ancestor without WEB-INF, boundary-root fallback, parent-dir fallback, and nearest-webapp-wins. ZkClasspathFilterTest (7) covers each ZK artifact prefix, case-insensitivity, rejection of unrelated + substring-false-positive jars, path preservation, and signature stability/change semantics. Both **meaningfully** cover the documented rules.

### V8 — Manual-pending inventory — MANUAL-PENDING
`tasks/zul-preview/manual-qa/AC-5.md` is concrete: 8 numbered steps each with an explicit "Expected result", plus 3 optional/secondary checks and a "already smoke-verified headlessly" section. It includes refresh-on-save (step 3: edit `label`, save, preview reloads within ~1s) and the orphan-process check (step 8: `ps aux | grep zk-preview-launcher` → empty).

Sub-items that remain **MANUAL-PENDING** for the human gate:
1. (E3-G1) Split editor opens with `HIDE_DEFAULT_EDITOR`, no separate plain-XML tab — step 1.
2. (E3-G1) Real JCEF render of a themed button — step 2.
3. (E3-G1) Save-triggers-refresh (VFS listener + MergingUpdateQueue + loadURL) — step 3.
4. (E3-G1) MVVM page renders with empty bound values, no crash — step 4.
5. (E3-G1) Broken-zscript page shows structured-failure JSON — step 5.
6. (E3-G1) No-ZK-jars module shows R7 message — step 6.
7. (E3-G2) Clean project close, then **no orphan** `zk-preview-launcher.jar` process — steps 7-8.
8. (optional) One server per (docroot, classpath) across multiple tabs; JCEF-unavailable fallback; javax-variant project.

## Suggestions (non-blocking)

1. `ZkClasspathFilter.ZK_ARTIFACT_PREFIXES` is a fixed prefix list; ZK addon jars (e.g. `zkcharts-`, `zktheme-`, `calendar-`, `ckez`/Keikai) won't match. Core zk/zul/zkbind — all the preview actually needs — are covered, so this is not a gate issue, but if a previewed page uses an addon widget the render may be lower-fidelity. Worth a note for a later phase.
2. `ManagedPreviewServer` uses `ProcessAdapter`, which is `@Deprecated` on newer platforms (still present and functional in 233). Compiles clean today; could switch to `ProcessListener` default methods later for forward-compat. Pure style/future-proofing.
3. `ZulPreviewFileEditorProvider.createEditor()` news up a fresh `PsiAwareTextEditorProvider()` per call. Harmless; could reuse a singleton. Style only.

## Verifier limitations (stated honestly)

- Cross-version binary compatibility (`runPluginVerifier`) was NOT run per task instruction; V5 rests on source inspection + `verifyPlugin` only.
- All E3-G1 visual/refresh behavior and E3-G2 end-to-end teardown are inherently non-headless and were NOT executed here — they are legitimately MANUAL-PENDING via AC-5.md, not verifier gaps that could have been closed headlessly.

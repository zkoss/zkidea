# E3 Evidence — ZUL Preview UI (`org.zkoss.zkidea.preview`)

> Maker round 1. Commands below were run from the repo root
> (`/Users/hawk/Documents/workspace/PLUGIN/zkidea`) with `withjdk.sh 17` unless noted.
> Re-run any of them to reproduce. Root project = the `zkidea` plugin module; the
> `zk-preview-launcher` module (E1) was **not modified**.

## Summary

| Gate | Result | One-line evidence |
|------|--------|--------------------|
| E3-G1 (AC-5 automated slice) | **PASS** | `ZulPreviewFileEditorProviderTest`: 6/6 green — accepts `.zul`, rejects `zk.xml`/`lang-addon.xml`/plain `.xml`, `HIDE_DEFAULT_EDITOR` policy, and `createEditor()` builds a working split editor with its fallback preview panel headlessly (no JCEF needed) |
| E3-G1 (AC-5 manual slice) | **MANUAL-PENDING** | Script written at `tasks/zul-preview/manual-qa/AC-5.md`; not run (interactive `runIde` explicitly out of scope for the maker) |
| E3-G2 (teardown, primitive) | **PASS** | `ManagedPreviewServerTeardownTest`: `destroy()` kills a real short-lived stand-in OS process within 10s; `ZulPreviewServerService.dispose()` calls this on every tracked server |
| E3-G2 (teardown, end-to-end) | **MANUAL-PENDING** | Full "close project → `ps aux \| grep zk-preview-launcher` empty" proof requires the interactive `runIde` session — step 7-8 of the manual script |
| Launcher bundling — `runIde` sandbox | **PASS** | `./gradlew prepareSandbox` → `.sandbox/plugins/zkidea/lib/zk-preview-launcher.jar` present |
| Launcher bundling — `buildPlugin` zip | **PASS** | `zkidea/lib/zk-preview-launcher.jar` present inside `build/distributions/zkidea-0.7.2.zip` |
| Root build | **PASS** | `./gradlew build test` (both modules) exit 0 |
| Existing tests still green | **PASS** | 275 pre-existing root-project tests + 20 new preview tests = 295, 0 failures/errors; `zk-preview-launcher`'s own 28 tests unaffected |

---

## Deliverable inventory

New package `src/main/java/org/zkoss/zkidea/preview/`:

```
ZulPreviewFileEditorProvider.java   FileEditorProvider: accept()=".zul" extension check,
                                     HIDE_DEFAULT_EDITOR, builds TextEditorWithPreview
ZulPreviewFileEditor.java           the preview half: JBCefBrowser or Swing fallback
                                     panel (JCEF-unsupported / no-ZK-jars / server-error),
                                     VFS-change-triggered debounced refresh
ZulPreviewServerService.java        project-level Disposable service: classpath/docroot
                                     resolution (off-EDT read action), spawns/reuses the
                                     helper JVM, kills all servers on dispose()
ManagedPreviewServer.java           owns one spawned helper JVM (GeneralCommandLine +
                                     KillableProcessHandler), parses PREVIEW_PORT=<n>,
                                     no Project dependency (unit-testable standalone)
PreviewResult.java                  READY / NO_ZK_JARS / ERROR outcome value type
DocrootResolver.java                pure-logic docroot-resolution rule
ZkClasspathFilter.java              pure-logic ZK-jar filtering + classpath signature
```

New tests `src/test/java/org/zkoss/zkidea/preview/`:

```
ZulPreviewFileEditorProviderTest.java     BasePlatformTestCase, 6 tests (registration + createEditor)
DocrootResolverTest.java                  plain JUnit5, 5 tests
ZkClasspathFilterTest.java                plain JUnit5, 7 tests
ManagedPreviewServerTeardownTest.java     plain JUnit5, 2 tests (real short-lived process)
```

Other changes:

- `src/main/resources/META-INF/plugin.xml` — registered `<fileEditorProvider>` and
  `<projectService>` for the two new top-level classes, in the existing main
  `<extensions defaultExtensionNs="com.intellij">` block (no new block, no other lines
  touched).
- `build.gradle` — added `evaluationDependsOn(':zk-preview-launcher')` +
  `prepareSandbox { dependsOn ':zk-preview-launcher:jar'; from(...) { into "${project.name}/lib" } }`.
  Nothing else in this file changed.
- `manual-test/src/main/webapp/preview/broken.zul` — new fixture (a `<zscript>`
  referencing a nonexistent class) for manual QA step 5 (AC-6-style structured-failure
  display in the preview panel); mirrors the existing `preview/button.zul` /
  `preview/separate-wpd.zul` fixtures added for E1/E2.
- `tasks/zul-preview/manual-qa/AC-5.md` — the manual QA script (E3 deliverable 7).

No changes to `zk-preview-launcher/` (hard constraint respected).

---

## Reproduction commands

### 1. Compile

```
$ withjdk.sh 17 ./gradlew compileJava compileTestJava
...
BUILD SUCCESSFUL in 4s
```

### 2. New preview-package tests only

```
$ withjdk.sh 17 ./gradlew :test --tests "org.zkoss.zkidea.preview.*"
...
BUILD SUCCESSFUL in 8s
```

`build/test-results/test/TEST-org.zkoss.zkidea.preview.*.xml`:

```
tests="5" skipped="0" failures="0" errors="0"   DocrootResolverTest
tests="7" skipped="0" failures="0" errors="0"   ZkClasspathFilterTest
tests="2" skipped="0" failures="0" errors="0"   ManagedPreviewServerTeardownTest
tests="6" skipped="0" failures="0" errors="0"   ZulPreviewFileEditorProviderTest
```

(20 new tests total, 0 failed/errored/skipped.)

> Note: `--tests` filters must be run as `:test` (root project only) — plain
> `./gradlew test` fans out to every subproject's own `test` task, and
> `:zk-preview-launcher:test` legitimately has no classes under
> `org.zkoss.zkidea.preview.*` and fails the filter with "No tests found". This is a
> Gradle multi-module quirk, not a defect.

### 3. Full root test suite (existing + new)

```
$ withjdk.sh 17 ./gradlew :test
...
BUILD SUCCESSFUL in 10s
```

Aggregated across all 27 `build/test-results/test/*.xml` files: **295 tests, 0
failures, 0 errors** (275 pre-existing + 20 new).

### 4. Root `./gradlew build test` (both modules, the E3 gate's literal command)

```
$ withjdk.sh 17 ./gradlew build test
...
BUILD SUCCESSFUL in 14s
26 actionable tasks: 3 executed, 23 up-to-date
```

`zk-preview-launcher/build/test-results/test/*.xml` unaffected: **28 tests, 0
failures, 0 errors** (same as E1-evidence.md — confirms the launcher module was not
touched).

### 5. Launcher jar bundling — sandbox

```
$ withjdk.sh 17 ./gradlew prepareSandbox
...
BUILD SUCCESSFUL in 1s

$ find .sandbox -iname "*zk-preview-launcher*"
.sandbox/plugins/zkidea/lib/zk-preview-launcher.jar
```

### 6. Launcher jar bundling — buildPlugin zip

```
$ withjdk.sh 17 ./gradlew buildPlugin
...
BUILD SUCCESSFUL in 15s

$ unzip -l build/distributions/zkidea-0.7.2.zip | grep launcher
   463951  07-06-2026 16:20   zkidea/lib/zk-preview-launcher.jar
```

Full zip contents (`zkidea/lib/`): `zk-preview-launcher.jar`,
`instrumented-zkidea-0.7.2.jar`, `jsoup-1.13.1.jar`, `searchableOptions-0.7.2.jar` — the
launcher sits alongside the plugin's own instrumented jar and existing dependency, as
expected.

---

## Per-gate detail

**E3-G1 (AC-5)**

- Automated slice — PASS. `ZulPreviewFileEditorProviderTest` verifies: (a) the provider
  is registered and `accept()`s a `.zul` `VirtualFile`; (b) it rejects `zk.xml`,
  `lang-addon.xml`, and a plain `.xml` file (all three share the built-in XML FileType
  with `.zul`, per RESEARCH.md U4-F18/F19 — this is exactly the false-positive `accept()`
  would produce if it checked `FileType` instead of the file extension/name); (c)
  `getPolicy()` is `HIDE_DEFAULT_EDITOR`; (d) `createEditor()` actually runs headlessly
  and returns a `TextEditorWithPreview` whose preview half is non-null and has a
  component — this exercises `ZulPreviewFileEditor`'s constructor and its
  `JBCefApp.isSupported()==false` fallback-panel branch (headless test JVMs have no
  JCEF), without needing a real browser.
- Manual slice — MANUAL-PENDING by design. `tasks/zul-preview/manual-qa/AC-5.md` has 8
  numbered steps + 3 secondary checks with expected results, covering: split editor
  appears with a rendered button; edit+save refreshes the preview; an MVVM zul renders
  with empty bound values; a broken (missing-class zscript) zul shows the structured
  JSON failure; a no-ZK-jars module shows the R7 message; and the final teardown check.
  Per the task's explicit instruction, the maker did not run `./gradlew runIde`
  (it blocks).

**E3-G2 (teardown, no orphan JVMs)**

- The kill primitive is proven automatically: `ManagedPreviewServerTeardownTest` spawns
  a real `sleep 60` (or `cmd /c timeout` on Windows) process via the exact
  `GeneralCommandLine` + `KillableProcessHandler` mechanism `ManagedPreviewServer` uses
  for the real launcher jar, calls `destroy()`, and asserts the OS process actually
  exits within 10s. `ZulPreviewServerService.dispose()` (a `Disposable` project
  service, registered via `<projectService>`, never parented directly to `Project` per
  RESEARCH.md U5-F16) calls exactly this `destroy()` on every server it started, so the
  same mechanism verified in isolation is what runs for real project-close teardown.
- What is **not** proven headlessly: that the platform actually calls
  `ZulPreviewServerService.dispose()` at project close in a live IDE, and that this
  really leaves zero `ps aux` hits for the *real* `zk-preview-launcher.jar` process
  (as opposed to the stand-in `sleep` in the unit test). That end-to-end proof is manual
  script step 7-8.
- Editor-tab-level teardown (browser/refresh-queue/VFS-listener disposed when a tab
  closes, server intentionally kept alive) is not spawn-a-real-JVM tested, but is
  architecturally verified: `TextEditorWithPreview.dispose()` (decompiled from the
  actual 233.11799.241 platform jar during implementation) calls
  `Disposer.dispose(myEditor)` then `Disposer.dispose(myPreview)`, and
  `ZulPreviewFileEditor` registers its `JBCefBrowser`, `MergingUpdateQueue`, and VFS
  `MessageBusConnection` all with `this` as their parent `Disposable`, so this cascade
  is structural, not best-effort.

**Launcher jar bundling** — PASS for both `runIde` sandbox and `buildPlugin` zip (§5-6
above). Not covered: actually launching the jar from that bundled location inside a
real running IDE (covered by the manual script, since it's the same path exercised by
opening any `.zul` file there).

---

## Deviations from PLAN.md / RESEARCH.md, with reasons

1. **`accept()` does not check `JBCefApp.isSupported()`.** RESEARCH.md's U4
   recommendation (agent advisory) suggested gating on both the extension and
   `JBCefApp.isSupported()`, mirroring Mermaid's provider. The E3 task description
   given to this maker explicitly specifies `accept()` = extension check only, with
   `JBCefApp.isSupported()` instead deciding what the *preview half* renders (browser
   vs. fallback panel). Followed the explicit task spec: this also means a `.zul` file
   still gets a proper split editor (with an explanatory panel) on JCEF-less runtimes,
   rather than silently falling back to a plain text editor with no explanation.
2. **`ManagedPreviewServer` extracted as its own class instead of a private nested
   class inside `ZulPreviewServerService`.** Not specified by the plan either way; done
   so the teardown primitive (`destroy()`) could be exercised by a lightweight,
   non-platform JUnit test per the task's own suggestion ("a fake/short-lived process
   is acceptable"), without requiring a `Project` fixture.
3. **No automated test of `ZulPreviewServerService.resolveTarget`/`preparePreview`
   end-to-end** (real `OrderEnumerator` + real module classpath + real spawned launcher
   jar). The task asked for unit tests of the two pure-logic pieces
   (docroot resolution, ZK-jar filtering/signature) plus a teardown-semantics test "if
   feasible without spawning a real JVM" — all three are done. A full integration test
   would need a `BasePlatformTestCase`/`HeavyPlatformTestCase` module wired with a real
   JDK and a real ZK library dependency, which is materially more test infrastructure
   than what was asked for; flagged here rather than silently skipped. The manual QA
   script's steps 1-6 are the current coverage for that path.

No deviations from the hard constraints: `zk-preview-launcher/` untouched, sinceBuild
`233.2` respected (every API used was verified present in the actual
`ideaIC-233.11799.241` platform jars during implementation — `TextEditorWithPreview`,
`PsiAwareTextEditorProvider`, `JBCefApp`/`JBCefBrowser`, `MergingUpdateQueue`,
`GeneralCommandLine`/`KillableProcessHandler`, `OrderEnumerator`,
`ReadAction.nonBlocking`, `PluginDescriptor.getPluginPath()` — none of these are
243+-only), `TextEditorWithPreviewProvider` was not used (confirmed `@ApiStatus.Internal`
on current master, per RESEARCH.md U4-F3), root `./gradlew test` stays green.

---

## Honest caveats (anything not verified headlessly)

- **Real JCEF rendering was never observed.** No screenshot/DOM check of an actual
  rendered `.zul` page inside a real `JBCefBrowser` — only that construction doesn't
  throw when JCEF *is* unavailable (headless). MANUAL-PENDING via the QA script.
- **Save-triggers-refresh was never observed live** — the debounce/reload wiring
  (`MergingUpdateQueue` → `BulkFileListener` → `browser.loadURL()`) compiles and is
  structurally correct per the platform source verified during implementation, but was
  not exercised end-to-end. MANUAL-PENDING via the QA script step 3.
- **End-to-end "no orphan JVM after project close"** was not observed against the real
  `zk-preview-launcher.jar` in a live IDE session — only the underlying kill primitive,
  against a stand-in process. MANUAL-PENDING via the QA script steps 7-8.
- **`resolveTarget`'s real classpath/docroot resolution** (real `OrderEnumerator` over a
  real module, real content roots) is untested by an automated test; only its two
  extracted pure-logic pieces are. See deviation #3 above.
- **Remote Development / Split Mode** behavior for this composite JCEF editor is an
  open question per RESEARCH.md U4-F14/F15 (not finalized even upstream) — out of scope
  for this phase, not evaluated here either.

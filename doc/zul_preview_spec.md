# ZUL Layout Preview — Engineering Contract, Limitations & Open Findings

> The engineering layer of the Layout Preview documentation. The user-facing guide is
> [zul-preview-feature.md](zul-preview-feature.md); the class-by-class map is
> [feature_overview.md §10](feature_overview.md).
>
> Derived from the source under `src/main/java/org/zkoss/zkidea/preview/` and
> `zk-preview-launcher/`. **Status: shipped in plugin 1.0.0.** Claims below were re-verified
> against the source when this document was consolidated.

---

## 1. Purpose

Add a side-by-side preview to the `.zul` editor (Markdown-editor style): the left pane is the
normal ZUL text editor, the right pane shows the **actual HTML that ZK's own engine produces for
the page's first paint**, refreshed on save. The render runs in an isolated helper JVM that drives
the *project's own ZK jars* and never loads the project's compiled application classes.

---

## 2. Functional specification

### 2.1 Editor registration
- **FR-1** For any file whose extension is `.zul` (case-insensitive), the plugin offers a
  `TextEditorWithPreview` split editor and hides the default plain XML editor
  (`getPolicy() == HIDE_DEFAULT_EDITOR`). — `ZulPreviewFileEditorProvider`
- **FR-2** `accept()` is a pure extension check (no PSI), so it never fires for
  `zk.xml` / `lang-addon.xml` / other `.xml` that share the built-in XML `FileType`.

### 2.2 Environment gating (what the preview pane shows)
The preview pane is a card panel that shows exactly one of: *loading*, *browser*, or *message*. It
resolves to a live render only when **all** of the following hold; otherwise it shows an
explanatory message with a "Report on GitHub" link (see FR-19).

- **FR-3 (JCEF)** The embedded browser render requires `JBCefApp.isSupported()`. When JCEF is
  unavailable, the pane does not merely fail — `JcefAvailability.diagnose(...)` names the cause
  (`BOOT_JDK_NO_JCEF` — the boot runtime is not a JetBrains Runtime · `REGISTRY_DISABLED` —
  `ide.browser.jcef.enabled` is off · `INCOMPATIBLE`) and the card offers an **"Open preview in
  external browser"** link, so the preview server still works and only the *display* moves out of
  the IDE. There is no in-IDE non-JCEF renderer.
  — `ZulPreviewFileEditor` ctor, `JcefAvailability`
- **FR-4 (ZK present)** The previewed file's **IntelliJ module must have at least one ZK jar on
  its resolved runtime classpath**. `ZkClasspathFilter.detectZkPresence` classifies three states,
  and the message differs per state: `PRESENT` → render · `NONE` → `NO_ZK_JARS` ("declare ZK as a
  dependency") · `DECLARED_BUT_MISSING` → `STALE_CLASSPATH` ("ZK is declared but the jars are not
  on disk — re-import/re-sync"), because a wiped local repo used to be reported as a missing
  dependency the user demonstrably had.
  — `ZulPreviewServerService.onTargetResolved`, `PreviewResult`
- **FR-5 (server up)** If the helper JVM fails to start or report a port, the pane shows an
  `ERROR` message carrying the root-cause text. Every failure path — including a throw while the
  command line is being built, and a rejected target-resolution promise — is routed to that card
  rather than leaving the pane on *"Starting ZK preview server…"*.
  — `PreviewResult.error`, `startGuarded`, `wireResolveOutcome`

### 2.3 Target resolution (off the EDT, inside a read action)
- **FR-6 (module)** The module is found via `ProjectFileIndex.getModuleForFile(zulFile)`.
- **FR-7 (classpath)** The handoff classpath is
  `OrderEnumerator.orderEntries(module).recursively().runtimeOnly().withoutSdk().classes()`
  filtered to **existing regular files** (`filterLibraryJars`): every resolved runtime **library
  jar**, ZK or not (ZK needs non-ZK-prefixed deps such as `slf4j-api`). Directories (the module's
  own compiled output) and the project SDK are **excluded** — this is the isolation boundary
  against user classes. The module's **resource roots** are added so ZK's `ClassWebResource` can
  resolve the project's own `~./` pages from `web/` on the classpath. If `module == null`, it
  falls back to project-level order entries.
- **FR-8 (docroot)** `DocrootResolver.resolveWithLayout` returns the docroot **and which rule
  matched** (`DocrootResolver.Layout`), in this order:

  | Layout | Rule |
  |---|---|
  | `WAR_WEBAPP` | first ancestor directory containing `WEB-INF/` or named `webapp` (case-insensitive), searched **only within the module's content roots** so an unrelated `~/webapp/…` checkout folder cannot hijack it |
  | `SPRING_BOOT_CLASSPATH` | the file sits under `<resource-root>/web/…` — the classpath `web` root, so a Spring Boot *jar* project serves the page at its production URL |
  | `CONTENT_ROOT` | nearest module content root that is an ancestor |
  | `FILE_PARENT` | the file's own parent directory |

- **FR-9 (request path)** The browser URL path is the `.zul`'s path relativized against the
  docroot — i.e. the page's real production URL.

### 2.4 Helper-JVM lifecycle
- **FR-10 (spawn)** For each distinct `(docroot, classpath-signature)` pair a single
  `zk-preview-launcher.jar` process is spawned via `GeneralCommandLine` +
  `KillableProcessHandler`:
  `java -jar zk-preview-launcher.jar --classpath <jars> --webapp <docroot> --port 0 --report-*`.
  The `java` executable is the **project SDK's** VM, falling back to the **IDE's own JRE**
  (`java.home`) when the project has no Java SDK. Process creation runs on the pooled executor,
  never on the EDT. — `ZulPreviewServerService.startServer` / `resolveJavaExecutable`
- **FR-11 (sharing)** One helper JVM per `(docroot, classpath-signature)` pair, shared by every
  open preview tab that resolves to the same pair; kept alive for the whole project session.
  Classpath `signature()` = SHA-256 over each jar's path+size+mtime, so a dependency change forces
  a new server.
- **FR-12 (teardown)** Closing a tab disposes only that editor's resources (browser, listeners,
  refresh queue) via `Disposer` parenting; the shared server keeps running. Closing the project
  (`ZulPreviewServerService.dispose()`) kills **every** spawned process — no orphan JVMs.
- **FR-13 (bundling)** The launcher jar is bundled in the plugin **distribution** at
  `<plugin>/lib/zk-preview-launcher.jar` (`prepareSandbox`/`buildPlugin`) and located at runtime
  via the plugin descriptor path. No build tool is invoked at runtime. Note the corollary: the
  plugin must be installed from `build/distributions/*.zip`, never `build/libs/*.jar` — see §6.

### 2.5 Rendering core (`zk-preview-launcher`, zero IntelliJ deps, CLI-runnable)
- **FR-14 (HTTP bridge)** A plain JDK `com.sun.net.httpserver.HttpServer` (no Jetty/servlet
  container) bound to loopback, dispatching on a fixed **8-thread daemon pool** so one hung render
  cannot freeze the other tabs sharing the JVM. It prints `PREVIEW_PORT=<n>` on stdout when bound,
  then serves: `GET *.zul` → page render; `GET /zkau/web/*` → ZK-extendlet-processed JS/CSS
  resources; `POST /zkau` → a benign stubbed AU response (`{"rid":0,"rs":[]}`).
  — `PreviewHttpServer`
- **FR-15 (variant)** javax vs. jakarta servlet API is auto-detected by scanning
  `DHtmlLayoutServlet.class` bytecode (canonical `zk-<version>.jar` tried first), then the matching
  `JavaxRenderEngine`/`JakartaRenderEngine` drives `DHtmlLayoutServlet` / `DHtmlUpdateServlet` by
  reflection over hand-written mock servlet objects. Misdetection fails loud
  (`IllegalStateException`), never silently. — `VariantDetector`, `RenderEngineFactory`
- **FR-16 (isolation hooks)** `PreviewUiFactory` (registered via `zk.xml`
  `<ui-factory-class>`): `newComposer(...)` always returns a no-op `PreviewComposer` (blocks both
  `apply="user.X"` and the auto MVVM `BindComposer` path, since ZK resolves both through the same
  call); `getPageDefinition(...)` substitutes an empty page for leaked `@name(...)` annotation
  paths. `PlaceholderInjector` then renders each unresolvable binding as its **expression text in a
  dimmed style** (merged onto any existing inline style) and synthesizes placeholder rows/nodes for
  model-bound grids/listboxes/trees — so a bound page reads as a wireframe with field names, not as
  a blank page.
- **FR-17 (structured failure → formatted page)** A render exception is mapped to
  `RenderError { phase, message, zulFile, line, column }` (`phase ∈ {PARSE, COMPOSE, UNKNOWN}`;
  `CLASSPATH`/`RESOURCE` are reserved and unused). `RenderResult.toJson()` remains the structured
  contract, and `ErrorPageRenderer` is its consumer: the browser receives a self-contained,
  theme-aware, HTML-escaped **error page** — phase + message, `file:line` where ZK can report it, a
  collapsed full stack trace, and a prefilled "Report on GitHub" link. `line`/`column` are
  best-effort: present for BeanShell/zscript failures, structurally absent for bare hierarchy
  `UiException`s. — `ErrorMapper`, `ErrorPageRenderer`
- **FR-18 (session per render)** Each `renderZul`/`resource` call gets a fresh `MockHttpSession`,
  so ZK desktops do not accumulate in one session for the JVM's life and separate preview tabs do
  not share a session.

### 2.6 Refresh
- **FR-19** A `MergingUpdateQueue`-debounced (300 ms) `BulkFileListener` reloads the browser on VFS
  **content-change** events, i.e. after the file is **saved to disk**. Unsaved in-editor edits do
  not refresh.

### 2.7 Failure reporting
- **FR-20 (two assemblers, one label contract)** Both report paths emit the same label set in the
  same order — plugin-side `PreviewIssueReporter` for the "cannot display preview" cards, and
  launcher-side `Main.reportEnv` → `ErrorPageRenderer` for actual render failures:

  ```
  Plugin:  ZKIdea 1.0.0
  IDE:     IntelliJ IDEA 2024.3 (IU-243.1)
  OS:      Mac OS X 15.7.3
  JDK:     17.0.4.1
  Build:   Maven | Gradle | none          ← BuildSystemDetector (external-system id)
  Layout:  WAR webapp | …                 ← DocrootResolver.Layout (FR-8)
  Servlet: jakarta | javax                ← launcher-detected; absent on plugin-side cards
  ZK jars: zk-10.1.0-jakarta.jar, …  [30 classpath entries]
  ```

  The plugin passes its facts across the module boundary as `--report-plugin` / `--report-ide` /
  `--report-build` / `--report-layout` / `--report-zkjars`; the launcher fills in OS/JDK (of the
  *render* JVM, deliberately) and the detected variant. The flag names are locked by
  `ReportArgumentsTest` on the plugin side and `ReportEnvTest` on the launcher side, because a
  silent rename would just look like an unknown option to the launcher and drop facts from every
  future report.
- **FR-21 (why those fields)** The ZK-jar line is the direct answer to "how was ZK loaded" — it
  shows version, CE vs EE, and a **missing** transitive as an absence (the documented
  `zkex`/`CometServerPush` failure is exactly that shape). The layout line explains
  include/`~./`/"page not found" failures. Build tool is secondary — the render path never reads
  `pom.xml`/`build.gradle`, so Maven and Gradle take byte-identical code paths; it matters only for
  reproduction and for spotting a hand-configured project.
- **FR-22 (budget & privacy)** Body pre-cap 6000 chars, the real guarantee applied to the
  **encoded** URL (8000 chars) so dense markup cannot silently break the link; over-long launcher
  reports are handed off via the **clipboard** so nothing is truncated. ZK jar **file names** only —
  never paths, never non-ZK dependencies — and the docroot **kind**, never the docroot path.

---

## 3. Does it require a Maven or Gradle project?

**No.** The preview never runs `mvn`/`gradle` and never reads `pom.xml`/`build.gradle`. It reads
only **IntelliJ's resolved project model** — the module and its attached library dependencies — via
`OrderEnumerator`.

What the feature actually requires is two model facts, regardless of how they got there:

1. The `.zul` belongs to an IntelliJ **module** (`getModuleForFile` ≠ null; a project-level
   fallback exists but is weaker), and
2. that module's **resolved runtime classpath contains the ZK jars** (as IntelliJ library
   entries), plus a filesystem **docroot** (FR-8).

| Project type | Works? | Why |
|---|---|---|
| **Maven** | ✅ Yes | IntelliJ's Maven import creates the module and attaches ZK (and transitive deps) as libraries from the local repo → `OrderEnumerator` sees them. `src/main/webapp/WEB-INF` satisfies the docroot rule. |
| **Gradle** | ✅ Yes | Same mechanism — Gradle import populates the module's library classpath identically. |
| **Spring Boot jar** | ✅ Yes | ZULs live on the classpath under `src/main/resources/web/`; the `SPRING_BOOT_CLASSPATH` rule serves them at their production URL and the resource roots on the render classpath make `~./` resolve. |
| **Neither (IntelliJ-native / JPS `.iml`, Ant, Eclipse-imported, plain folder)** | ⚠️ Conditional | Works **only if** IntelliJ knows the module and the ZK jars are attached as **module library dependencies**. If so, it renders exactly as for Maven/Gradle. |
| **No module / no ZK on the IntelliJ classpath** | ❌ No render | e.g. a bare directory opened without a module, or a webapp whose ZK jars sit only in `WEB-INF/lib` but were never attached as IntelliJ libraries → `NO_ZK_JARS`. The docroot walk would still succeed; the blocker is the classpath. |

**The precise trigger is not the build tool** but: "are the ZK jars on the module's resolved runtime
classpath as IntelliJ sees it?" Two corollaries: the **launcher jar is bundled in the plugin**, so
no build tool is needed at runtime; and the **render JVM** is the project SDK, falling back to the
IDE's own JRE, so a project with no configured SDK still previews.

---

## 4. Limitations

IDs are stable — the user guide links to them. "**Shipped**" marks a v1 limitation that was since
resolved and is kept here only so the ID does not get reused.

### 4.1 Fidelity
- **L-1 First paint only.** No AU round-trip is driven; `POST /zkau` is stubbed. Interactions
  needing a server round-trip (button `onClick` → Java handler, paging, sorting, tree expansion)
  are not simulated. Client-side `w:` handlers *do* run — they are browser JavaScript.
- **L-2 No user-class fidelity.** ViewModels / Composers / converters / validators are never
  loaded (the isolation guarantee), so MVVM-bound values render as **dimmed placeholders** (the
  expression text — FR-16) and `@command` is unwired. Intentional; it will not "improve" later.
- **L-3 zscript.** Left enabled; a `<zscript>` referencing a missing class produces a structured
  COMPOSE failure rather than rendering.

### 4.2 Environment / platform
- **L-4 JCEF required** for the in-IDE render. Mitigated, not removed: the card diagnoses the
  cause and offers the external-browser fallback (FR-3).
- **L-5 ZK must be on the IntelliJ module classpath.** See §3 — presence on disk is not enough.
- **L-6 Module-scoped.** A `.zul` with no owning module falls back to project-level entries and a
  weaker docroot; resolution quality degrades and may yield `NO_ZK_JARS`. See also R2-MAJ1.
- **L-7 Docroot heuristic.** Non-standard layouts fall back to a content root or the file's
  parent, which may not match how the app is actually served — resources under an unmatched
  docroot may 404.

### 4.3 Behavior / lifecycle
- **L-8 Idle JVMs.** One helper JVM per distinct `(docroot, classpath)` pair stays alive until the
  project closes (no idle timeout).
- **L-9 Refresh on save only.** Unsaved edits don't update the preview (300 ms debounce after the
  VFS write).
- **L-10 Raw error body — *shipped*.** v1 showed the raw HTTP-500 JSON; 1.0.0 renders the
  formatted error page with a stack trace and a GitHub report link (FR-17).
- **L-11 First-run cost.** The first preview for a pair pays JVM spawn + ZK bootstrap latency
  before the port is reported.

### 4.4 Known gaps carried from the design record
- **L-12 Addon-only classpaths.** The ZK-jar *presence* gate (`isZkJar`) recognizes core + known
  addon prefixes (`zk-`, `zul-`, `zkbind-`, …, `zkcharts-`, `zkpivot-`, `keikai-`); an unusual
  addon-only jar name outside this list could be misjudged as "no ZK". The handoff classpath is
  unaffected — it passes *all* library jars.
- **L-13 Error position.** `line`/`column` are `null` for component-hierarchy `UiException`s (e.g.
  "Unsupported parent for X") — the exception genuinely carries no position. A ZK structural limit,
  not a plugin bug.
- **L-14 Cross-version verification.** The `runPluginVerifier` cross-version sweep has not been
  run; compatibility rests on the platform target range and source-level review.

---

## 5. Open findings

Two code reviews covered the whole feature (all plugin-side classes + ~35 launcher classes). Their
Criticals and must-fixes are fixed and verified — the classloader lock, silent `/zkau/web/*`
failures, the stuck-loading pane, mock-servlet path traversal, the docroot boundary clip, the
placeholder-dimming merge, off-EDT process start, bounded stderr, session-per-render, the threaded
HTTP server, encoded-length URL cap and the loopback authority check. What follows is what is
**still open**, each re-verified against the source during this consolidation. IDs are the review's
(`R2-` prefixed from review #2) and appear in commit messages.

### Major
| ID | Finding | Where | Impact |
|---|---|---|---|
| **R2-MAJ1** | `withinBoundary` returns `true` when `boundaryRoots` is empty, so a **module-less** `.zul` re-opens the unbounded ancestor scan the boundary clip was added to prevent. | `DocrootResolver.withinBoundary` | A module-less file under any ancestor named `webapp` gets a far-away docroot → every `<include>`, CSS and image breaks on a layout the developer knows is correct; self-heals once the module imports, so it gets reported as "flaky". Fix: when there is no boundary, skip the scan and go straight to the classpath-web-root check and the parent fallback. |
| **R2-MAJ2** | `getMimeType` always returns `null`. **Unconfirmed** — level is provisional. | `MockServletContextCore` | Benign *if* ZK always sets content types itself (`DHtmlUpdateServlet` does for the paths we drive). If ZK ever falls back to `ServletContext.getMimeType`, assets are served with no `Content-Type` and a strict browser refuses them → a deterministically unstyled preview. **Resolve the question before scheduling a fix.** |
| **R2-MAJ3** | The external-link handler's `userGesture` clause lets non-gesture navigations load in-pane. | `ZulPreviewFileEditor.onBeforeBrowse` | A script-driven `location.href=`, `<meta refresh>` or HTTP redirect (all `userGesture == false`) replaces the render *inside the editor tab* — e.g. an `index.zul` bouncing to corporate SSO. There is no toolbar/back button, so recovery (save again, or reopen the tab) is undiscoverable. Loopback URLs already return `false` regardless of gesture, so the clause has no upside. |

### Minor
| ID | Finding | Where |
|---|---|---|
| **R2-MIN1** | The debounced reload lambda checks `browser != null && previewUrl != null` but not the `disposed` flag (its sibling in `startPreview` does) → Ctrl+S then Ctrl+W can `loadURL` on a disposed browser, surfacing as a red "IDE Internal Error" badge naming ZKIdea. Cosmetic, but reads as instability. | `ZulPreviewFileEditor.installRefreshListener` |
| **R2-MIN2** | The `.zul` source is inlined in a ```` ``` ```` fence without escaping; a literal triple backtick in the source closes the fence early and swallows the environment block of the GitHub issue. | `PreviewIssueReporter` |
| **R2-MIN3** | The content-root fallback returns the *first* matching boundary root, not the *nearest* (the javadoc says nearest). Bites only with nested content roots, e.g. a Maven aggregator whose child WAR sits inside the parent's root. | `DocrootResolver` |
| **R2-MIN4** | Response-body capture hardcodes UTF-8 and ignores `characterEncoding`. Latent today (nothing in the preview path sets another charset); if it fires, the symptom is 亂碼 for legacy Big5/Shift_JIS apps — a real ZK constituency. | `MockHttpServletResponseCore` |
| **R2-MIN5** | The classpath is joined with `File.pathSeparator`, so a jar path containing a literal `:` (legal on Unix) is mis-split. Effectively unreachable with Maven/Gradle cache paths; maximally confusing if it ever fires (`NoClassDefFoundError` for a jar visibly in the POM). Fix: one `--classpath-entry` per jar. | `ZulPreviewServerService.joinClasspath` |
| **R2-MIN6** | `resourceFile(null)` throws NPE instead of returning `null` as a container would → an NPE error page instead of a clean 404. | `MockServletContextCore` |
| **R2-MIN7** | The docroot containment guard is purely lexical, so a **symlink** inside the docroot escapes it. Not a vulnerability: the only threat model is previewing an untrusted project, where `<zscript>` already grants code execution in the launcher JVM — reading one file through a symlink is a strict downgrade. Note the *fix* carries the user-facing risk: the preview docroot is the dev source tree, where symlinked asset folders legitimately occur, so a strict `toRealPath()` check must be scoped to the project's **content roots**, not the docroot, or it will 404 real assets. | `MockServletContextCore`, `PreviewHttpServer.readZulSource` |
| **R2-MIN8** | `setDateHeader`/`addDateHeader` are silent no-ops, so `containsHeader`/`getHeader` report the value was never set. Latent — headers are not forwarded today (see M4). | `MockHttpServletResponseCore` |
| **R2-MIN9** | `PreviewHttpServer.stop()` closes the HTTP server and executor but not the `RenderEngine`, and `close()` never fires `contextDestroyed` to match the constructor's `contextInitialized`. Harmless per-process; a leak for the "independently callable / embedded" use the factory advertises. | `PreviewHttpServer`, `AbstractRenderEngine` |
| **R2-MIN10** | The javax mocks' class javadocs still say "Jakarta" (the dedup `sed` was lowercase-only). Contributor-facing only. | `javax/mock/*.java` (5 files) |
| **M4** (review #1) | `send()` forwards only `Content-Type`; a ZK redirect (`Location`) or conditional-GET header (`ETag`/`Last-Modified`) is silently dropped. | `PreviewHttpServer.send` |

### Deferred by decision
- **Helper stderr never reaches `idea.log`.** The launcher now logs `/zkau/web/*` failures to
  stderr, but `ManagedPreviewServer` only surfaces its bounded `stderrTail` when the process dies
  *before* reporting a port — so for a healthy server those lines still reach nobody. Forwarding all
  helper stderr needs a decision on level and filtering (ZK bootstraps through `java.util.logging`
  → stderr, which is why the tail is capped in the first place).
- **Lower-value tail from review #1**, unfixed and unscheduled: `Main.parseArgs` silently drops a
  trailing dangling `--flag`; no launcher exit-code differentiation (the plugin string-matches
  stderr); `POST /zkau` never drains the request body; `RenderResult.failure` has no null check;
  an already-open tab does not detect a mid-session helper-JVM crash (the next save just shows a
  browser error); `VariantDetector`'s fallback reintroduces ordering risk when no canonically-named
  `zk-*.jar` exists.

---

## 6. Deliberate non-goals & won't-fix

- **AU round-trips / interactivity (L-1) and real bound values (L-2)** — permanent. They keep the
  security story clean ("your code never runs in the IDE") and the maintenance surface small.
  Placeholder rendering (FR-16) is the intended ceiling.
- **`ForbiddenLoadTracker` is a no-op in production** — by design. `Main` calls the 2-arg
  `RenderEngineFactory.create` (tracker `null`); the real guarantee is that the module's output
  directory is off the classloader plus the `UiFactory` no-op hook. The tracker is a **test-only
  lock** on that invariant. A production blocklist would change the isolation model for no
  additional guarantee.
- **The render classpath entry is the whole resource root, not just `web/`** — cannot be narrowed.
  ZK's `ClassWebResource` resolves `~./foo.zul` to the classpath resource `/web/foo.zul`, so the
  entry must be the directory that *contains* `web/`. Narrowing it would break `~./` resolution.
  The residual ("a user's `metainfo/zk/config.xml` could be scanned") is bounded: any class it
  names lives in the excluded output dir → `ClassNotFoundException`, not silent execution.
- **jakarta/javax duplication is reduced, not eliminated.** The drift-prone *logic* lives once
  (Bridge over `mockcore/*Core` for the mocks, Template Method via `AbstractRenderEngine` for the
  engines; verified 0 semantic divergence). The remaining `@Override` interface shells cannot be
  merged — the two servlet APIs share no supertype — without reflection/proxies or codegen.
  `MockServletOutputStream` is the one mock with no shared core (it `extends` the per-namespace
  abstract class).
- **Single-jar plugin installs will not be supported.** `build/libs/zkidea-<v>.jar` is a build
  intermediate: it carries neither the launcher jar nor jsoup, and it is the uninstrumented jar.
  Making it work would mean embedding and extracting the launcher at runtime while the install
  stayed broken for other reasons. The supported shape is the distribution zip; the failure should
  be a clear diagnosis instead. *(That diagnosis is not implemented yet — see
  [tasks/preview-launcher-jar-path-bug.md](../tasks/preview-launcher-jar-path-bug.md).)*
- **The two report assemblers stay separate** (§2.7). Each side legitimately owns facts the other
  cannot see, and the launcher's OS/JDK are deliberately the render JVM's. They are kept in step by
  the shared label contract, locked by tests on both sides.

---

## 7. Verification assets

- **Both servlet variants** are exercised against real ZK jars (javax/ZK 9.6 and jakarta/ZK 10) —
  `ZulSyntaxCorpusTest` (15 ZUML construct groups), `ImplicitObjectsElTest` (24 of 25 EL implicit
  objects resolve live; `event` is correctly `null`), `RenderFidelityTest`, `IncludeTest`,
  `ApplyTemplateUriTest`, `ClasspathResourceResolutionTest`, `RealWorldSmokeTest`,
  `BrowserEquivalentTest`.
- **Isolation** — `IsolationTest`, `IsolationChildProcessTest` (a real spawned process),
  `CoreIndependenceTest` (the mock cores import no servlet API),
  `ScopedZkClassLoaderConcurrencyTest`.
- **Manual fixtures** — `manual-test/` (WAR; `src/main/webapp/preview/**` including the syntax
  corpus and deliberate error cases) and `manual-test-springboot/` (Spring Boot jar layout).

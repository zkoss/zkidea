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
  Process creation runs on the pooled executor, never on the EDT.
  — `ZulPreviewServerService.startServer`
- **FR-10a (which JVM runs the helper)** The `java` executable is the **project SDK's** VM, but
  **only when that SDK is JDK 17 or newer** (`MINIMUM_LAUNCHER_SDK`); otherwise — and when the
  project has no Java SDK at all — it falls back to the **IDE's own JRE** (`java.home`).
  The gate exists because the launcher jar is **Java 17 bytecode (class file 61.0)**: handed to an
  older project SDK it dies at main-class load with `UnsupportedClassVersionError`, *before* it can
  print a port, so the only symptom was the generic "exited before it reported a port" card. Any
  project whose SDK is below 17 hit this on its first preview regardless of ZUL content.
  Falling back costs nothing in fidelity — no user bytecode ever runs in the helper (FR-7), so
  matching the project's JDK was a preference, not a requirement — and it is always viable, because
  every IDE in the supported range (`sinceBuild` 233.2) runs on JBR 17+.
  **Maintenance:** the launcher's `targetCompatibility` and `MINIMUM_LAUNCHER_SDK` must move
  together; `LauncherJvmVersionGateTest` locks that by reading the packaged jar's real
  `major_version` and asserting it equals `44 + MINIMUM_LAUNCHER_SDK`. Note that *lowering* the
  launcher's target instead of gating was considered and rejected: it only moves the cliff, since a
  project on a JDK 8 SDK (still common for ZK 9.x) would break identically.
  — `resolveJavaExecutable` / `canRunLauncherJar`
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
  collapsed full stack trace, and a prefilled "Report on GitHub" link under which a one-line
  reminder names what the report carries and that the click only opens an editable draft
  (`ErrorPageRenderer.REPORT_HINT`, shown on both the prefilled and the clipboard path). `line`/`column` are
  best-effort: present for BeanShell/zscript failures, structurally absent for bare hierarchy
  `UiException`s. — `ErrorMapper`, `ErrorPageRenderer`
- **FR-18 (session per render)** Each `renderZul`/`resource` call gets a fresh `MockHttpSession`,
  so ZK desktops do not accumulate in one session for the JVM's life and separate preview tabs do
  not share a session.

### 2.6 Refresh
- **FR-19** A `MergingUpdateQueue`-debounced (300 ms) `BulkFileListener` reloads the browser on VFS
  **content-change** events, i.e. after the file is **saved to disk**. Unsaved in-editor edits do
  not refresh. The reload is `CefBrowser.reload()`, **not** `loadURL(previewUrl)` — the URL is fixed
  for the editor's lifetime, so the latter was a same-URL navigation, which Chromium may answer from
  its own cache (see FR-19a).
- **FR-19a (a refresh must show the current file — three caches, all defeated)** "Refresh on save
  only" makes any stale layer *permanent*: nothing later arrives to correct the pane, so every cache
  between the file on disk and the painted pixels has to be disarmed. Each of the three below
  reproduces the same symptom on its own — the preview silently keeps showing the previous edit.

  | Layer | Behavior | How it is defeated |
  |---|---|---|
  | ZK's page-definition cache (`ResourceCache`) | Re-validates an entry against the file's `lastModified` only after a **check period** (default **5 s**); inside that window it returns cached HTML without touching disk | `AbstractRenderEngine` sets `org.zkoss.util.resource.checkPeriod=-1` during bootstrap, before ZK lazily builds the cache |
  | The browser's HTTP cache | The rendered page carried no `Cache-Control`/`ETag`/`Last-Modified`, so Chromium replayed its stored copy | `PreviewHttpServer` sends `Cache-Control: no-store, no-cache, must-revalidate` + `Pragma: no-cache` on the rendered page **and** the error page |
  | Same-URL navigation | `loadURL` on an unchanged URL need not revalidate | `reload()` (FR-19) |

  Three constraints that are easy to undo by accident:
  - **`checkPeriod` cannot move into the bundled `preview/zk.xml`** — ZK's `ConfigParser` parses
    `<file-check-period>` as `POSITIVE_ONLY`, so the disabling value is unrepresentable there. It is
    set only when the property is absent, so an explicit `-D` on the standalone CLI still wins, and
    it lives in the engine bootstrap rather than `Main` so the CLI, the plugin and the tests behave
    alike.
  - **Non-positive disables the *window*, not the cache.** ZK still keeps the entry and still
    compares `lastModified`; the cost is one `File.lastModified()` stat per request, not a re-parse
    of an unchanged file.
  - **`no-store` is scoped to the page.** `/zkau/web/*` stays cacheable — those are ZK's static
    JS/CSS (a ~1.6 MB `zk.wpd` among them) and re-fetching them on every save would slow every
    refresh.

  The reason this reads as "the preview works, then randomly stops": `ResourceCache` drops an entry
  whose load threw, so a page that **failed** to parse is never cached. Fixing a broken ZUL always
  appears to work — that render is what populates the cache — and it is the *next* edit that
  silently does not. Anyone editing slower than the check period never sees it at all.

  A stale JCEF cache entry already on disk needs no clearing: entries are keyed by URL and the
  preview port is new every session, so a restarted IDE cannot hit one.

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

### 2.8 In-pane debugging (preview context menu)

- **FR-23 (View Rendered HTML)** The pane's context menu drops CEF's built-in **View Source** entry
  (`MENU_ID_VIEW_SOURCE`) and adds **"View Rendered HTML"**, which opens the browser's **live DOM**
  as a read-only `LightVirtualFile` editor tab (`<name>-rendered.html`); each invocation closes the
  previous dump rather than stacking tabs. — `PreviewContextMenu`, `ZulPreviewFileEditor`

  Three constraints behind that shape:
  - **CEF's own item is unfixable from plugin code.** It is handled natively below the Java layer
    and routes into Chromium's open-source-in-a-new-tab path; an embedded browser in an editor split
    has no tab strip, so the request is dropped silently. `CefBrowser.viewSource()` is the same dead
    end. Removing it beats leaving a menu item that visibly does nothing.
  - **The command id must be `MENU_ID_USER_FIRST`, never `MENU_ID_USER_LAST`.** This handler shares
    the browser's `JBCefClient` with IntelliJ's `DefaultCefContextMenuHandler`, which claims
    `MENU_ID_USER_LAST` for its DevTools item; colliding would silently shadow FR-24.
    `PreviewContextMenuTest` pins it.
  - **The dump is the live DOM by design, not the response bytes.** For a ZK page those differ
    completely — the response is mostly a `zkmx([…])` bootstrap the client engine expands into DOM —
    so only the DOM answers the question the feature exists for: *is the component missing, or
    present but hidden?* The `getSource` visitor fires on a CEF thread, so the EDT hop lives in
    `ZulPreviewFileEditor`, keeping `PreviewContextMenu` headlessly testable.

  Two smaller choices, both easy to undo by accident:
  - **An editor tab, not a dialog.** DOM markup can be megabytes on a single line, which `JTextArea`
    lays out very badly; an editor brings HTML highlighting and Ctrl+F for free.
  - **`LightVirtualFile` is safe to ship** despite its `com.intellij.testFramework` package — it
    resolves from `lib/util-8.jar`, a core runtime jar present in every IDE, **not** from
    `testFramework.jar` (which is not shipped to end users). Verified by scanning the distribution;
    getting this wrong is a `NoClassDefFoundError` for every user.
- **FR-24 (Open DevTools)** The browser is built with `setEnableOpenDevToolsMenuItem(true)`, adding
  IntelliJ's DevTools entry (Elements / Console / Network) — the only thing that shows a JS error or
  a 404 on a `/zkau/web/*` asset, neither of which any source view reveals. It costs nothing until
  clicked; clicking spawns a second (DevTools frontend) browser in a `HIDE_ON_CLOSE` dialog, so
  closing that dialog does not free it — it lives until the preview tab disposes the parent browser.
  Roughly 100–200 MB RSS while open. No privacy concern: the frontend loads from `devtools://`
  resources bundled in CEF, with no network access. One known environment caveat — JCEF DevTools has
  historically been unreliable under **off-screen rendering**, which `JBCefBrowser` takes from the
  registry key `ide.browser.jcef.osr.enabled`, so it may misbehave on setups that force OSR on.

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
  addon prefixes (`zk-`, `zul-`, `zkbind-`, …, `zkcharts-`, `zkpivot-`, `keikai-`); an addon jar
  outside this list is misjudged as "no ZK". **Three real misses are known**, found while building
  the add-on matrix (§8): Calendar ships as `calendar-3.2.1.jar` and CKEditor as
  `ckez-4.25.0.1-lts.jar`, neither matching any prefix, and the listed `zkpivot-` matches nothing —
  the real Pivottable artifact is `pivottable-3.1.0-Eval.jar`. Consequence is narrow and the fix is
  a one-line list change: `detectZkPresence` reports `NONE` only for a module whose **only** ZK jar
  is one of these, and the handoff classpath is unaffected either way — `filterLibraryJars` passes
  *all* library jars, which is why every affected row in §8 still renders.
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

### Carried over from field reports

Open items left behind by shipped bug fixes — the defect itself is fixed, these are the
"while we were in there" remainders. No review IDs; they came from user reports, not a review pass.

| Finding | Where | Impact |
|---|---|---|
| **Single-jar installs fail with the JVM's error, not a diagnosis.** `resolveLauncherJar()` guards only `descriptor == null` (which essentially never happens) and then assumes `getPluginPath()` is a *directory* — its contract is "the plugin directory **or jar file**". For a jar-shaped install it builds a path *inside a file* (`…/zkidea-1.0.0.jar/lib/zk-preview-launcher.jar`) and hands it to `java -jar`, so the first component that notices is the JVM, several layers down: *"Unable to access jarfile …"*. The plugin held every fact needed to say "you installed the wrong artifact". Fix: validate `pluginPath` is a directory and that `lib/zk-preview-launcher.jar` exists, and throw a message naming the `.zip` — it already surfaces on the error card, since `resolveLauncherJar()` runs inside `startGuarded` and `rootMessage` walks to the root cause. | `ZulPreviewServerService.resolveLauncherJar` | The install mistake is easy to make (see §6) and the resulting message points nowhere near the cause. |
| **`README.md` documents no way to install a locally built plugin** — it covers `runIde`, `publishPlugin` and Marketplace installs only. That gap is what produced the single-jar report above. Fix: a short "install a local build" step naming the `.zip` and warning that `build/libs/*.jar` is a build intermediate. | `README.md` | Documentation. |
| **`@Tag("addons")` is not acted on by Gradle.** The tag is on `AddonRenderMatrixTest`, but no `addonTest` task exists and the default `test` task excludes nothing, so the matrix runs on every build. The render is cheap (bootstrap 240–390 ms, under 1 s per row); the cost is 11 `mvn dependency:build-classpath` invocations — ~15 s warm, minutes cold, with little sharing across five distinct ZK cores. | `zk-preview-launcher/build.gradle` | Default build is minutes slower than it needs to be, and offline/credential-less machines pay resolution timeouts to reach a skip. |
| **`setAttribute(name, null)` stores a null instead of removing the entry**, deviating from the servlet spec. This is the same latent shape as the fixed zkcharts NPE (`MockServletContextCore` rejected a null value and killed the launcher before it bound a port). No add-on in §8 trips it. | `MockHttpSessionCore`, `MockHttpServletRequestCore` | Latent; cheap two-line fix behind a mock-boundary unit test. |
| **A non-daemon `Timer-0` thread survives `RenderEngine.close()` on every ZK 10.x classpath.** Not an add-on effect — plain ZK 10.1.0-jakarta with no add-on leaves the same thread, ZK 9.6.x leaves none. Harmless in production: `Main` blocks on a latch and is killed by process destroy, never asked to exit on its own. Matters only for the "independently callable / embedded" use the factory advertises (cf. R2-MIN9). | ZK core, observed via `RenderEngineFactory` | None in the shipped path. |

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
- **Single-jar plugin installs will not be supported.** Gradle produces two artifacts and only one
  is an installable plugin:

  | Path | What it is | Launcher? | jsoup? | Instrumented? |
  |---|---|---|---|---|
  | `build/libs/zkidea-<v>.jar` | the plugin's **own classes only** — a build intermediate | no | no | no |
  | `build/distributions/zkidea-<v>.zip` | the **plugin distribution** (`zkidea/lib/*`) | yes | yes | yes |

  The launcher is added by `prepareSandbox`, which feeds the **zip** — not by the plain `jar` task
  (FR-13). *Install Plugin from Disk* copies a handed-in jar into `plugins/` as-is, while a zip is
  unpacked to `plugins/zkidea/lib/…`, so a single-jar install is identifiable on disk and is broken
  by construction: no render helper, no jsoup for `ZKNews`, and the uninstrumented jar rather than
  `instrumented-zkidea-<v>.jar`. The preview is merely the first place it shows.

  Making it work would mean embedding the launcher jar inside the plugin jar and extracting it to a
  temp dir at runtime — and the install would *still* be broken for the other two reasons. A plugin
  with bundled dependencies is distributed as a zip; that is the supported shape. Fail with a clear
  diagnosis instead of half-supporting a shape that cannot work. *(The diagnosis itself is still
  open — see §5, "Carried over from field reports".)*
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
- **Add-ons** — `AddonRenderMatrixTest` over `testutil/AddonMatrix` (§8).
- **Freshness & environment** — `EditReloadFreshnessTest` (the reported three-step edit sequence
  over real HTTP on both variants), `PreviewCacheHeaderTest` (page and error page are `no-store`, a
  `/zkau/web/*` asset is not), `LauncherJvmVersionGateTest` (the JDK-17 gate plus the packaged-jar
  bytecode-level drift check), `PreviewContextMenuTest` (menu surgery and command routing).
- **Manual fixtures** — `manual-test/` (WAR; `src/main/webapp/preview/**` including the syntax
  corpus and deliberate error cases) and `manual-test-springboot/` (Spring Boot jar layout).

---

## 8. Add-on support

ZK add-ons are the population most likely to break the preview: they register `WebAppInit`
listeners, ship their own `lang-addon.xml` + WPD/JS/CSS **inside their jars**, and probe for
licenses — three things the mock container fakes. `AddonRenderMatrixTest` renders each row below
against the real launcher and asserts three things: the render succeeds, the add-on's **widget
class** appears in the HTML, and the add-on's own **WPD** serves a non-empty 200 (the "renders
unstyled" failure mode, invisible to a render-only assertion).

### 8.1 Verified matrix

Versions are **reviewed constants**, never queried live, so a passing suite never depends on what
got published this morning. All 11 rows render.

| # | Add-on | Coordinates | Add-on version | ZK core | Variant | Widget marker | WPD pkg |
|---|---|---|---|---|---|---|---|
| 1 | Charts | `org.zkoss.chart:zkcharts` | 12.5.0.0 | 9.6.0.2 | javax | `chart.Charts` | `chart` |
| 2 | Charts | `org.zkoss.chart:zkcharts` | 12.5.0.0 | 10.1.0-jakarta | jakarta | `chart.Charts` | `chart` |
| 3 | Calendar | `org.zkoss.calendar:calendar` | 3.2.1 | 9.6.0.2 | javax | `calendar.CalendarsDefault` | `calendar` |
| 4 | Calendar | `org.zkoss.calendar:calendar` | 3.2.1 | 10.1.0-jakarta | jakarta | `calendar.CalendarsDefault` | `calendar` |
| 5 | Pivottable | `org.zkoss.pivot:pivottable` | 3.1.0-Eval | 10.1.0 | javax | `pivot.Pivottable` | `pivot` |
| 6 | Pivottable | `org.zkoss.pivot:pivottable` | 3.1.0-Eval | 10.1.0-jakarta | jakarta | `pivot.Pivottable` | `pivot` |
| 7 | Keikai | `io.keikai:keikai-ex` | 6.3.0 | 10.3.0.1 | javax | `zssex.Spreadsheet` | `zssex` |
| 8 | Keikai | `io.keikai:keikai-ex` | 6.3.0-jakarta | 10.3.0.1-jakarta | jakarta | `zssex.Spreadsheet` | `zssex` |
| 9 | Keikai | `io.keikai:keikai-ex` | 5.12.0 | 9.6.2 | javax | `zssex.Spreadsheet` | `zssex` |
| 10 | CKEditor | `org.zkoss.zkforge:ckez` | 4.25.0.1-lts | 9.6.0.2 | javax | `ckez.CKeditor` | `ckez` |
| 11 | CKEditor | `org.zkoss.zkforge:ckez` | 4.25.0.1-lts-jakarta | 10.1.0-jakarta | jakarta | `ckez.CKeditor` | `ckez` |

**Mismatched pairings are the point, not an accident.** zkcharts 12.5 is built against ZK 9.6.5 and
renders on ZK 10.1.0-jakarta; pivottable 3.1 is built against ZK 10.0.0 and renders on both 9.6 and
10.1. Even Keikai — a server-side spreadsheet that a one-shot mock render was expected to be
structurally unable to host, needing its own servlet mapping, server push and a license — renders
its full spreadsheet widget tree without any of them.

### 8.2 Facts a maintainer will get wrong from first principles

Each of these was guessed incorrectly before being measured; changing a row without re-checking them
produces a test that passes for the wrong reason.

- **Every row pins its own ZK core**, so `Variants.both()` (hard-wired to ZK 9.6.0.2 / 10.1.0-jakarta)
  cannot be reused — required for Keikai, whose variants carry *different version strings*
  (6.3.0 ↔ 10.3.0.1, `6.3.0-jakarta` ↔ `10.3.0.1-jakarta`), and whose jakarta line only starts at
  5.13.0, making 5.12.0 javax-only. Calendar's pom declares **no ZK dependency at all**, so the
  generated pom must supply the core itself.
- **The Keikai artifact is `keikai-ex`, not `keikai`.** `io.keikai:keikai`'s `lang-addon.xml`
  declares no components; `<spreadsheet>` (widget `zssex.Spreadsheet`) lives in `keikai-ex`, which is
  also what real Keikai projects depend on. Plain `keikai` fails all three rows identically with
  `DefinitionNotFoundException: spreadsheet`.
- **The asset URL is the dotted *widget* package, not the `<javascript-module name>` and not the
  jar's directory layout** — `/zkau/web/<hash>/js/<pkg>.wpd`. `calendar.wpd` is 200 while the module
  name `calendar.calendars.wpd` is 404; `pivot.wpd` is 200 while `pivottable.wpd` is 404. `<hash>` is
  a per-ZK-build segment scraped from the rendered HTML (`/zkau/web/([^/]+)/`). The probe genuinely
  discriminates: against the charts classpath, `chart.wpd` → 200 while `charts.wpd`/`zkcharts.wpd` →
  404.
- **Widget markers must be alphanumeric** — ZK 10 escapes `-` as `\-` in rendered widget values. All
  five current markers already are.
- **`<charts width="600px"/>` throws `NumberFormatException`** — `org.zkoss.chart.Charts.setWidth`
  takes a number, unlike every ZK core component. The fixture omits the unit.
- **Repository ids in the generated pom must match the `<server>` ids in `~/.m2/settings.xml`**
  (`ZK EE`, `Keikai EE`) or the credentialed rows fail to resolve.
- **Eval jars print a license banner to stdout** announcing a 12-hour uptime cap. It does not break
  the port handshake — `ManagedPreviewServer` matches `PREVIEW_PORT=(\d+)` with `find()` per stdout
  chunk — but assertions must target the widget/DOM, never the banner, and Eval artifacts can be
  re-published under the same coordinates.

### 8.3 Coverage boundaries

- **Credentials.** Only the two Calendar rows resolve from the free repo (`mavensync.zkoss.org/maven2`,
  GPL). Charts needs ZK EE, the three Keikai rows need Keikai EE, Pivottable comes from ee-eval — all
  via `~/.m2/settings.xml` credentials that do not exist on CI. Rows are `Assumptions`-gated on
  resolution, so a machine without creds or network **skips and prints why**, never false-fails; CI
  coverage is partial by construction.
- **Reporting.** Gradle's console prints rows as `[1]`…`[11]`, but the row id
  (`keikai-ex-6.3.0-jakarta-zk10.3.0.1-jakarta`, …) is the `<testcase name>` in the XML and HTML
  reports, so a failing row is identifiable.
- **`ForbiddenLoadTracker` is deliberately not asserted here.** It records loads of a
  caller-supplied forbidden prefix; these fixtures reference no user code, so the assertion would be
  vacuously true. Isolation is covered by `RenderFidelityTest` and the `Isolation*` tests instead.

### 8.4 What a broken add-on row means

The one add-on defect ever found was the zkcharts NPE (`MockServletContextCore.setAttribute`
rejecting a null value, killing the launcher before it bound a port) — six of these rows would have
caught it. Should a future version break a row: smallest-boundary failing test first, minimal fix,
row goes green. If a row ever becomes **unfixable**, the deliverable changes shape rather than
disappearing — the preview must *fail well* (a structured `RenderError` naming the add-on and why,
not a dead launcher) plus a limitation recorded in §4.

A reported failure is not automatically a preview defect. `<ckeditor>` was reported as failing to
preview with `DefinitionNotFoundException`; the reporter's `pom.xml` had the `ckez` dependency
commented out, so no jar defining the component was on the module classpath at all. The real defect
was the **diagnosis** — `ErrorMapper` classified it through the generic `isUiException` branch and
echoed ZK's wording, which reads as a broken preview or a typo. It now reports "Unknown component
`<ckeditor>`: no ZK jar on this module's classpath defines it …" at phase `PARSE`, where ZK actually
raises it, locked by `UnknownComponentDiagnosticTest` (same fixture, ZK core alone).

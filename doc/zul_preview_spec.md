# ZUL Preview — Functional Specification & Limitations

> Derived from the source under `src/main/java/org/zkoss/zkidea/preview/` and
> `zk-preview-launcher/`, and from the design record in `tasks/zul-preview/`
> (`PLAN.md`, `RESEARCH.md`, `E1/E3/E4` evidence). Complements
> `doc/feature_overview.md §10`. Status: v1 (plugin 0.8.0, in development).

---

## 1. Purpose

Add a live, side-by-side preview to the `.zul` editor (Markdown-editor style): the left
pane is the normal ZUL text editor, the right pane shows the **actual HTML that ZK's own
engine would produce for the first paint**, refreshed on save. The render runs in an
isolated helper JVM that drives the *project's own ZK jars* and never loads the project's
compiled application classes.

---

## 2. Functional specification

### 2.1 Editor registration
- **FR-1** For any file whose extension is `.zul` (case-insensitive), the plugin offers a
  `TextEditorWithPreview` split editor and hides the default plain XML editor
  (`getPolicy() == HIDE_DEFAULT_EDITOR`). — `ZulPreviewFileEditorProvider`
- **FR-2** `accept()` is a pure extension check (no PSI), so it never fires for
  `zk.xml` / `lang-addon.xml` / other `.xml` that share the built-in XML `FileType`.

### 2.2 Environment gating (what the preview pane shows)
The preview pane is a card panel that shows exactly one of: *loading*, *browser*, or
*message*. It resolves to a live render only when **all** of the following hold; otherwise
it shows an explanatory message and does nothing else.

- **FR-3 (JCEF)** The embedded browser render requires `JBCefApp.isSupported()`. If JCEF
  is unavailable (some remote-dev / headless / alternative-JDK IDE runtimes), the pane
  shows a "preview unavailable" message. There is **no** non-JCEF fallback renderer.
  — `ZulPreviewFileEditor` ctor
- **FR-4 (ZK present)** The previewed file's **IntelliJ module must have at least one ZK
  jar on its resolved runtime classpath** (`filterZkJars` non-empty). Otherwise the pane
  shows the `NO_ZK_JARS` message. — `ZulPreviewServerService.onTargetResolved`
- **FR-5 (server up)** If the helper JVM fails to start/report a port, the pane shows an
  `ERROR` message carrying the root-cause text. — `PreviewResult.error`

### 2.3 Target resolution (off the EDT, inside a read action)
- **FR-6 (module)** The module is found via `ProjectFileIndex.getModuleForFile(zulFile)`.
- **FR-7 (classpath)** The handoff classpath is
  `OrderEnumerator.orderEntries(module).recursively().runtimeOnly().withoutSdk().classes()`
  filtered to **existing regular files** (`filterLibraryJars`): every resolved runtime
  **library jar**, ZK or not (ZK needs non-ZK-prefixed deps such as `slf4j-api`).
  Directories (the module's own compiled output) and the project SDK are **excluded** —
  this is the isolation boundary against user classes. If `module == null`, it falls back
  to project-level order entries.
- **FR-8 (docroot)** The `--webapp` docroot is the first ancestor directory of the `.zul`
  that contains a `WEB-INF/` subdirectory **or** is named `webapp` (case-insensitive);
  else the nearest module content root that is an ancestor; else the file's own parent
  directory. — `DocrootResolver`
- **FR-9 (request path)** The browser URL path is the `.zul`'s path relativized against
  the docroot.

### 2.4 Helper-JVM lifecycle
- **FR-10 (spawn)** For each distinct `(docroot, classpath-signature)` pair a single
  `zk-preview-launcher.jar` process is spawned:
  `java -jar zk-preview-launcher.jar --classpath <jars> --webapp <docroot> --port 0`,
  via `GeneralCommandLine` + `KillableProcessHandler`. The `java` executable is the
  **project SDK's** VM, falling back to the **IDE's own JRE** (`java.home`) if the
  project has no Java SDK. — `ZulPreviewServerService.startServer` / `resolveJavaExecutable`
- **FR-11 (sharing)** One helper JVM per `(docroot, classpath-signature)` pair, shared by
  every open preview tab that resolves to the same pair; kept alive for the whole project
  session. Classpath `signature()` = SHA-256 over each jar's path+size+mtime, so a
  dependency change forces a new server.
- **FR-12 (teardown)** Closing a tab disposes only that editor's resources (browser,
  listeners, refresh queue) via `Disposer` parenting; the shared server keeps running.
  Closing the project (`ZulPreviewServerService.dispose()`) kills **every** spawned
  process — no orphan JVMs (E3-G2).
- **FR-13 (bundling)** The launcher jar is bundled inside the plugin at
  `<plugin>/lib/zk-preview-launcher.jar` (`prepareSandbox`/`buildPlugin`) and located at
  runtime via the plugin descriptor path. No build tool is invoked at runtime.

### 2.5 Rendering core (`zk-preview-launcher`, zero IntelliJ deps, CLI-runnable)
- **FR-14 (HTTP bridge)** A plain JDK `com.sun.net.httpserver.HttpServer` (no Jetty/servlet
  container) prints `PREVIEW_PORT=<n>` on stdout when bound, then serves:
  `GET *.zul` → page render; `GET /zkau/web/*` → ZK-extendlet-processed JS/CSS resources;
  `POST /zkau` → benign stubbed AU response. — `PreviewHttpServer`
- **FR-15 (variant)** javax vs. jakarta servlet API is auto-detected by scanning
  `DHtmlLayoutServlet.class` bytecode (canonical `zk-<version>.jar` tried first), then the
  matching `JavaxRenderEngine`/`JakartaRenderEngine` drives `DHtmlLayoutServlet` /
  `DHtmlUpdateServlet` via reflection over hand-written mock servlet objects.
  — `VariantDetector`, `RenderEngineFactory`
- **FR-16 (isolation hooks)** `PreviewUiFactory` (registered via `zk.xml`
  `<ui-factory-class>`): `newComposer(...)` always returns a no-op `PreviewComposer`
  (blocks both `apply="user.X"` and the auto MVVM `BindComposer` path);
  `getPageDefinition(...)` substitutes an empty page for leaked `@name(...)` annotation
  paths. Bound values therefore render empty/placeholder **by design**.
- **FR-17 (structured failure)** A render exception is mapped to
  `RenderError { phase, message, zulFile, line, column }` and returned as an HTTP 500 JSON
  body. `phase ∈ {PARSE, COMPOSE, UNKNOWN}` (`CLASSPATH`/`RESOURCE` reserved, unused).
  `line`/`column` are best-effort (present for BeanShell/zscript failures; absent for bare
  hierarchy `UiException`s). v1 ships **no plugin-side consumer** of this JSON — the browser
  displays the raw body. — `ErrorMapper`, `stage2-hook.md`

### 2.6 Refresh
- **FR-18** A `MergingUpdateQueue`-debounced (300 ms) `BulkFileListener` reloads the browser
  on VFS **content-change** events, i.e. after the file is **saved to disk**. Unsaved
  in-editor edits do not refresh in v1.

---

## 3. Does it require a Maven or Gradle project?

**Short answer:** The preview does **not** depend on the build tool at all. It never runs
`mvn` or `gradle` and never reads `pom.xml`/`build.gradle`. It reads only **IntelliJ's
resolved project model** — the module and its attached library dependencies — via
`OrderEnumerator` (`ZulPreviewServerService.resolveTarget`, lines 121-153).

What the feature actually requires is two model facts, regardless of how they got there:

1. The `.zul` belongs to an IntelliJ **module** (`getModuleForFile` ≠ null; a project-level
   fallback exists but is weaker), and
2. That module's **resolved runtime classpath contains the ZK jars** (as IntelliJ library
   entries), plus a filesystem **docroot** (`WEB-INF/` or a `webapp` dir, or a content-root
   fallback).

| Project type | Works? | Why |
|---|---|---|
| **Maven** | ✅ Yes | IntelliJ's Maven import creates the module and attaches ZK (and transitive deps) as libraries from the local repo → `OrderEnumerator` sees them. Standard `src/main/webapp/WEB-INF` layout satisfies the docroot rule. |
| **Gradle** | ✅ Yes | Same mechanism — Gradle import populates the module's library classpath identically. |
| **Neither (IntelliJ-native / JPS `.iml`, Ant, Eclipse-imported, plain folder)** | ⚠️ Conditional | Works **only if** IntelliJ has been told about the module and the ZK jars are attached as **module library dependencies** (Project Structure → Modules/Libraries). If so, `OrderEnumerator` returns them and the preview renders exactly as for Maven/Gradle. |
| **No module / no ZK on the IntelliJ classpath** | ❌ No render | e.g. a bare directory opened without a module, or a webapp whose ZK jars sit only in `WEB-INF/lib` but were **never attached as IntelliJ libraries**. `filterZkJars` is empty → the pane shows the `NO_ZK_JARS` message. The docroot walk itself would still succeed; the blocker is the missing classpath. |

**The precise trigger, not the build tool:** "Are the ZK jars on the module's resolved
runtime classpath as IntelliJ sees it?" Maven and Gradle guarantee this automatically as a
side effect of declaring ZK as a dependency. A non-Maven/non-Gradle project can satisfy it
too — it just has to be configured manually in IntelliJ. A project where ZK exists only on
disk (WEB-INF/lib) but not in IntelliJ's library model will **not** preview, even though it
would run fine on a real server.

Two further build-tool-independent notes:
- The **launcher jar is bundled in the plugin**, so no build tool is needed at runtime to
  produce it.
- The **Java runtime** used to spawn the helper JVM is the project SDK, falling back to the
  IDE's own JRE — so a project with no configured SDK still previews (using the IDE JRE),
  provided that JRE can load the resolved ZK jars.

---

## 4. Limitations (v1 — by design unless noted)

### 4.1 Fidelity
- **L-1 First paint only.** No AU (asynchronous update) round-trip is driven; `POST /zkau`
  is stubbed. Client interactions needing a server round-trip (button `onClick` → Java
  handler, live event responses) are not simulated.
- **L-2 No user-class fidelity.** ViewModels / Composers / converters / validators are
  never loaded (the isolation guarantee), so MVVM-bound values render **empty/placeholder**,
  `@command` is unwired, and `apply="user.X"` composers are no-ops. This is intentional and
  will not "improve" later.
- **L-3 zscript.** Left enabled; a `<zscript>` referencing a missing class produces a
  structured COMPOSE failure rather than rendering.

### 4.2 Environment / platform
- **L-4 JCEF required.** No embedded browser (JCEF unsupported) → explanatory message only;
  no alternative renderer.
- **L-5 ZK must be on the IntelliJ module classpath.** See §3 — presence on disk is not
  enough; it must be in IntelliJ's resolved library model.
- **L-6 Module-scoped.** A `.zul` not associated with any IntelliJ module falls back to
  project-level entries and the file's parent as docroot; resolution quality degrades and
  may yield `NO_ZK_JARS`.
- **L-7 Docroot heuristic.** Relies on a `WEB-INF/`-or-`webapp`-named ancestor. Non-standard
  webapp layouts (no `WEB-INF`, unconventional root name) fall back to a content root or the
  file's parent, which may not match how the app is actually served — resources under an
  unmatched docroot may 404.

### 4.3 Behavior / lifecycle
- **L-8 Idle JVMs.** One helper JVM per distinct `(docroot, classpath)` pair stays alive
  until the project closes (no idle timeout in v1).
- **L-9 Refresh on save only.** Unsaved edits don't update the preview (300 ms debounce
  after the VFS write).
- **L-10 Raw error body.** On render failure the browser shows the raw HTTP-500 JSON; there
  is no formatted error UI in v1 (the structured contract is documented for stage 2 but has
  no consumer).
- **L-11 First-run cost.** The first preview for a pair pays JVM spawn + ZK bootstrap
  latency before the port is reported.

### 4.4 Known gaps carried from the design record
- **L-12 Addon-only classpaths.** The ZK-jar **presence** gate (`isZkJar`) recognizes core
  + known addon prefixes (`zk-`, `zul-`, `zkbind-`, …, `zkcharts-`, `zkpivot-`, `keikai-`);
  an unusual addon-only jar name outside this list could be misjudged as "no ZK". (The
  actual handoff classpath is unaffected — it passes *all* library jars.)
- **L-13 Error position.** `line`/`column` are `null` for component-hierarchy `UiException`s
  (e.g. "Unsupported parent for X") — the exception genuinely carries no position; this is a
  ZK structural limit, not a plugin bug.
- **L-14 Cross-version verification.** `runPluginVerifier` cross-version sweep was not run in
  v1; compatibility rests on the 233.2–261.* platform target and source-level review.

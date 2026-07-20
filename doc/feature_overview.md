# ZK IntelliJ IDEA Plugin - Feature & Implementation Overview

The ZK IntelliJ IDEA Plugin enhances the development experience for ZK applications within IntelliJ IDEA. It provides intelligent code completion, syntax validation, and seamless navigation for ZUL files and ZK configuration files.

This document maps each feature to its key implementation classes so that maintainers can quickly locate and understand the relevant code.

---

## 1. ZUL File Support (since 0.1.0)

### What it does
Treats `.zul` files as XML with ZK-specific enhancements: code completion for ZK components/attributes/events, real-time syntax validation against the ZUL XSD schema, and a custom file icon.

### Key classes

| Class | Path | Role |
|-------|------|------|
| `ZulLanguage` | `lang/ZulLanguage.java` | Defines ZUL as a language extending `XMLLanguage`. Singleton `INSTANCE`. |
| `ZulFileType` | `lang/ZulFileType.java` | Registers the `.zul` file extension and associates it with `ZulLanguage`. |
| `ZulSchemaProvider` | `lang/ZulSchemaProvider.java` | Implements `StandardResourceProvider` to register the bundled `zul.xsd` schema with IntelliJ's `ResourceRegistrar`. Maps the namespace `http://www.zkoss.org/2005/zul` to the local XSD. |
| `ZkDomElementDescriptorProvider` | `dom/ZkDomElementDescriptorProvider.java` | Implements `XmlElementDescriptorProvider`. Delegates to `ZkDomElementDescriptorHolder` to provide element descriptors for code completion and validation. |
| `ZkDomElementDescriptorHolder` | `dom/ZkDomElementDescriptorHolder.java` | Project-level service. Loads the XSD schema via `ExternalResourceManager`, caches `XmlNSDescriptorImpl` instances per file kind (ZUL, zk.xml, lang-addon.xml), and provides element descriptors with default namespace support so ZUL files work without explicit `xmlns` declarations. |
| `ZulDomUtil` | `dom/ZulDomUtil.java` | Utility class. `isZKFile()` detects ZUL/zk.xml/lang-addon.xml files by name/extension. `hasViewModel()` walks the XML tag tree to check for `viewModel` attribute presence. |
| `ZulIconProvider` | `lang/ZulIconProvider.java` | Implements `FileIconProvider`. Returns a custom ZUL icon for `.zul` files in the project tree. |
| `ZulIcons` | `lang/ZulIcons.java` | Loads the ZUL file icon from `lang/icons/zul.png`. |
| `ZulFileTypeRegistrar` | `project/ZulFileTypeRegistrar.java` | `ProjectActivity` that runs on startup. Works around IntelliJ bug [IJPL-39443](https://youtrack.jetbrains.com/issue/IJPL-39443) where the `*.zul → XML` file type association is lost after plugin reinstall. Checks and restores the association if missing. |

### Key resource
- `src/main/resources/org/zkoss/zkidea/lang/resources/zul.xsd` — The ZUL schema definition (auto-updated from remote on startup).

### How it works
1. `plugin.xml` registers `<fileType name="XML" extensions="zul"/>`, telling IntelliJ to treat `.zul` as XML.
2. `ZulSchemaProvider` registers the `zul.xsd` schema so IntelliJ can validate and provide completions.
3. `ZkDomElementDescriptorProvider` → `ZkDomElementDescriptorHolder` provides element descriptors with a default namespace, enabling code completion without `xmlns` attributes.
4. `ZulFileTypeRegistrar` ensures the file type association survives plugin reinstalls.

---

## 2. ZK Configuration File Support (since 0.4.0)

### What it does
Provides code completion and validation for `zk.xml` and `lang-addon.xml` configuration files. Works by filename or by XML namespace.

### Key classes

| Class | Path | Role |
|-------|------|------|
| `ZkConfigSchemaProvider` | `lang/ZkConfigSchemaProvider.java` | Registers the `zk.xsd` schema for namespace `http://www.zkoss.org/2005/zk/config`. |
| `LangAddonSchemaProvider` | `lang/LangAddonSchemaProvider.java` | Registers the `lang-addon.xsd` schema for namespace `http://www.zkoss.org/2005/zk/lang-addon`. |
| `ZkXmlValidationAnnotator` | `lang/ZkXmlValidationAnnotator.java` | Custom `Annotator` for `lang-addon.xml`. Checks that the `<language-addon>` root element contains required child elements (`<addon-name>`, `<language-name>`) and reports errors if missing. |
| `ZkDomElementDescriptorHolder` | `dom/ZkDomElementDescriptorHolder.java` | Handles descriptor caching for all three file kinds via the `FileKind` enum (`ZUL_FILE`, `ZK_CONFIG_FILE`, `LANG_ADDON_FILE`). |
| `ZulDomUtil` | `dom/ZulDomUtil.java` | `isZkConfigFile()` matches files named `zk.xml`. `isLangAddonFile()` matches files named `lang-addon.xml`. |

### Key resources
- `src/main/resources/org/zkoss/zkidea/lang/resources/zk.xsd`
- `src/main/resources/org/zkoss/zkidea/lang/resources/lang-addon.xsd`

### How it works
1. `plugin.xml` registers `<fileType name="XML" patterns="zk.xml"/>` and `<fileType name="XML" patterns="lang-addon.xml"/>`.
2. Each `SchemaProvider` registers its XSD with IntelliJ's resource system.
3. `ZkDomElementDescriptorHolder.getFileKind()` detects the file type and loads the appropriate schema.
4. `ZkXmlValidationAnnotator` adds extra structural validation for `lang-addon.xml` beyond what XSD provides.

---

## 3. MVVM Annotation Completion (since 0.1.2)

### What it does
Provides code completion for ZK MVVM data binding annotations (`@init`, `@load`, `@bind`, `@save`, `@command`, `@global-command`, `@ref`, `@converter`, `@validator`, `@template`) inside ZUL attribute values. Also auto-triggers the completion popup when the user types `@`.

### Key classes

| Class | Path | Role |
|-------|------|------|
| `MVVMAnnotationCompletionProvider` | `completion/MVVMAnnotationCompletionProvider.java` | Extends `CompletionContributor`. Activates only for ZK files that have a `viewModel` attribute in an ancestor tag. Context-aware: offers `@id`/`@init` for the `viewModel` attribute, `@command`/`@global-command` for event attributes (`on*`), and the full set of data binding annotations for other attributes. |
| `ZulTypedHandler` | `editorActions/ZulTypedHandler.java` | Extends `CompletionAutoPopupHandler`. Intercepts the `@` character and triggers `AutoPopupController.scheduleAutoPopup()` to show the completion popup immediately. |
| `ZulDomUtil` | `dom/ZulDomUtil.java` | `hasViewModel()` walks up the XML tree to determine if the current element is inside a component with a `viewModel` attribute, which is the precondition for MVVM annotations. |

### How it works
1. User types inside a ZUL attribute value (e.g., `viewModel="..."` or `value="..."`).
2. `MVVMAnnotationCompletionProvider.fillCompletionVariants()` checks: is it a ZK file? Does an ancestor have `viewModel`?
3. Based on the attribute name, it offers the appropriate subset of annotations.
4. `ZulTypedHandler` ensures the popup appears immediately when `@` is typed.

---

## 4. Class Navigation / Go to Declaration (since 0.1.0)

### What it does
Enables Ctrl+Click (Go to Declaration) on Java class references in ZUL files — for example, on ViewModel class names or component class references — to navigate directly to the Java source.

### Key classes

| Class | Path | Role |
|-------|------|------|
| `GotoJavaClassHandler` | `actions/GotoJavaClassHandler.java` | Implements `GotoDeclarationHandler`. Uses `JavaClassReferenceCompletionContributor.findJavaClassReference()` to resolve the class reference at the cursor position. Matches the canonical text against lookup variants to find the best `PsiElement` target. |

### How it works
1. Registered in `plugin.xml` as `<gotoDeclarationHandler>` with `order="first"`.
2. When the user Ctrl+Clicks on a class reference in a ZUL file, IntelliJ calls `getGotoDeclarationTargets()`.
3. The handler resolves `JavaClassReference` at the offset and returns the matching `PsiElement` for navigation.

---

## 5. Open in Browser (since 0.1.6)

### What it does
Generates the correct URL for the "Open in Browser" action on ZUL files. Automatically detects the server port and context path from Maven Jetty plugin configuration.

### Key classes

| Class | Path | Role |
|-------|------|------|
| `WebBrowserUrlProvider` | `editorActions/WebBrowserUrlProvider.java` | Extends IntelliJ's `WebBrowserUrlProvider`. For Maven projects, it parses the `pom.xml` to find Jetty plugin configuration (supports `org.eclipse.jetty`, `org.mortbay.jetty` plugin variants). Extracts context path and port, then constructs a `localhost` URL. Also reads a system property set by `MavenRunnerPatcher` for dynamic ports. |
| `MavenRunnerPatcher` | `editorActions/MavenRunnerPatcher.java` | Extends `JavaProgramPatcher`. Intercepts Maven run configurations and captures the `-Djetty.port=` parameter. Stores the port as a system property (`org.zkoss.zkidea.jetty.port.<projectName>`) so `WebBrowserUrlProvider` can use it. |

### How it works
1. User right-clicks a ZUL file → "Open in Browser".
2. `WebBrowserUrlProvider.getUrl()` checks if it's a Maven project, finds the Jetty plugin config, and builds the URL.
3. If a Maven run has been launched, `MavenRunnerPatcher` has already captured the Jetty port into a system property, which `WebBrowserUrlProvider` reads.

---

## 6. ZK Maven Archetypes / Project Creation (since 0.1.3)

### What it does
Provides ZK Maven archetype templates in IntelliJ's "New Project" wizard, enabling quick creation of ZK projects with the correct structure.

### Key classes

| Class | Path | Role |
|-------|------|------|
| `ZKMavenArchetypesProvider` | `maven/ZKMavenArchetypesProvider.java` | Implements `MavenArchetypesProvider`. Parses the local `archetype-catalog.xml` file to provide a list of `MavenArchetype` objects (groupId, artifactId, version, description) to IntelliJ's Maven integration. |
| `ZKProjectsManager` | `project/ZKProjectsManager.java` | `StartupActivity.DumbAware`. On project open, downloads the latest `archetype-catalog.xml` from `http://mavensync.zkoss.org/maven2/archetype-catalog.xml` and saves it locally. Also updates `zul.xsd` if a newer version is available. Runs once per IDE session. |
| `ZKPathManager` | `project/ZKPathManager.java` | Utility. Provides paths for plugin temp storage (`getPluginTempPath()`) and resource extraction (`getPluginResourcePath()`). Resources are stored under `<IntelliJ plugins dir>/zkidea/classes/`. |

### Key resource
- `src/main/resources/org/zkoss/zkidea/lang/resources/archetype-catalog.xml` — Bundled archetype catalog (updated from remote on startup).

### How it works
1. `plugin.xml` registers `ZKMavenArchetypesProvider` under `org.jetbrains.idea.maven` extension namespace.
2. On startup, `ZKProjectsManager` copies the bundled `archetype-catalog.xml` to the plugin temp directory and then downloads the latest version from the remote Maven repository.
3. When a user creates a new Maven project, IntelliJ calls `ZKMavenArchetypesProvider.getArchetypes()`, which parses the local catalog file.

---

## 7. ZK Schema Auto-Update (since 0.1.2)

### What it does
Automatically downloads the latest `zul.xsd` schema from `https://www.zkoss.org/2005/zul/zul.xsd` and updates the local copy if the remote version is newer. This keeps code completion and validation up to date with the latest ZK components.

### Key classes

| Class | Path | Role |
|-------|------|------|
| `ZKProjectsManager` | `project/ZKProjectsManager.java` | `updateZulSchema()` downloads the remote XSD, compares schema versions (from the `version` attribute on `<xs:schema>`), and replaces the local copy if the remote is newer. Sets the file's last-modified time 7 days in the future to throttle re-downloads. Registers the schema URL with `ExternalResourceManager`. |

---

## 8. News Notifications (since 0.1.13, refined in 0.2.0)

### What it does
Fetches ZK framework news from `zkoss.org` and shows them as IDE notifications. Shows new or updated news, and re-shows the same news every 7 days.

### Key classes

| Class | Path | Role |
|-------|------|------|
| `ZKNews` | `newsNotification/ZKNews.java` | Implements `ProjectActivity` (non-blocking). On project open, fetches news from `https://www.zkoss.org?ide=in&fetch=true` using JSoup with a 5-second timeout. Caches news content and timestamp in a `zkNews.properties` file. Shows a sticky balloon notification with a "Visit zkoss.org" action link. |

### How it works
1. Registered in `plugin.xml` as `<postStartupActivity>`.
2. `execute()` is called asynchronously on a background thread.
3. `shouldShowNotification()` checks: first run? new content? or 7+ days since last shown?
4. Uses the `"news notification"` notification group (configured as `STICKY_BALLOON` in `plugin.xml`).

---

## 9. Feedback Menu (since 0.4.0)

### What it does
Adds a "ZK Feedback" submenu under Help with links to customer support, documentation, bug reporting, and news.

### Key classes

| Class | Path | URL |
|-------|------|-----|
| `CustomerSupportAction` | `feedback/CustomerSupportAction.java` | `https://potix.freshdesk.com/` |
| `DocumentationAction` | `feedback/DocumentationAction.java` | `https://docs.zkoss.org/zk_dev_ref/` |
| `ReportBugAction` | `feedback/ReportBugAction.java` | `https://tracker.zkoss.org/` |
| `NewsAction` | `feedback/NewsAction.java` | `https://www.zkoss.org/news/` |

All four are `DumbAwareAction` subclasses that simply open a URL in the browser via `BrowserUtil.browse()`.

### Registration
Defined in `plugin.xml` as an action group `ZK_Feedback_Group` added to `HelpMenu` after `HelpTopics`.

---

## 10. ZUL Preview (since 0.8.0, in development)

### What it does
Adds a live, side-by-side preview to the ZUL editor (Markdown-editor style): the left
pane is the normal text editor, the right pane renders the actual HTML ZK's own engine
would produce, refreshed on save. Rendering never loads the project's own compiled
classes (ViewModels, Composers, converters, ...) — it is a **first-paint-only**
approximation: bound values render empty/placeholder rather than their real values.
Rendering happens in a short-lived helper JVM, spawned and owned by the plugin, that
drives the project's own ZK jars directly (not a bundled copy) via a small standalone
"rendering core" module (`zk-preview-launcher`) that has zero IntelliJ dependencies and
is independently runnable as a CLI. Design background, approaches considered, and the
full acceptance-criteria matrix live in `tasks/zul-preview/PLAN.md` and `RESEARCH.md`.

### Key classes — plugin side (`preview` package, `src/main/java/org/zkoss/zkidea/preview/`)

| Class | Role |
|-------|------|
| `ZulPreviewFileEditorProvider` | `FileEditorProvider`. `accept()` matches any file whose extension is `.zul` (cheap, PSI-free — never fires for `zk.xml`/`lang-addon.xml`, which share the same built-in XML `FileType`). `createEditor()` wraps IntelliJ's normal `PsiAwareTextEditorProvider` text editor and a `ZulPreviewFileEditor` in a `TextEditorWithPreview` split. `getPolicy()` returns `HIDE_DEFAULT_EDITOR` so the split replaces the plain XML editor for `.zul` files. |
| `ZulPreviewFileEditor` | The preview half of the split. Gated on `JBCefApp.isSupported()`: if JCEF is unavailable, shows an explanatory Swing panel instead of a browser and does nothing else (R5). Otherwise asks `ZulPreviewServerService` to prepare a preview target, then points a `JBCefBrowser` at `http://localhost:<port><requestPath>`. A `MergingUpdateQueue`-debounced `BulkFileListener` reloads the browser on VFS content-change events (i.e. after save — unsaved in-editor changes do not refresh in v1). All child resources (browser, listener, refresh queue) are parented to `this` via `Disposer.register`, so `Disposer.dispose(this)` (fired when the editor tab closes) tears them down automatically. |
| `ZulPreviewServerService` | Project-level `Disposable` service that owns the helper JVMs. `preparePreview()` resolves the previewed file's module classpath and docroot off the EDT (`ReadAction.nonBlocking`), then looks up or starts a `ManagedPreviewServer` keyed by `docroot + "#" + classpathSignature` — **one helper JVM per distinct (docroot, classpath) pair**, shared across every open preview tab that resolves to the same pair, kept alive for the project session. `dispose()` kills every server this service started (no orphan JVMs left on project close). |
| `ManagedPreviewServer` | Owns one spawned `zk-preview-launcher` process via IntelliJ's `KillableProcessHandler`. Parses the `PREVIEW_PORT=<n>` line the launcher prints on stdout once its HTTP server is up; `destroy()` kills the OS process. Has no dependency on `Project`/platform APIs so its start/kill contract can be unit-tested with a lightweight stand-in process. |
| `DocrootResolver` | Pure logic: walks a `.zul` file's ancestor directories for the first one containing `WEB-INF/` or named `webapp` (standard Maven/Gradle webapp layout) to use as the `--webapp` argument; falls back to the nearest module content root, then the file's own parent. |
| `ZkClasspathFilter` | Pure logic, two filters over a module's resolved runtime classpath: `filterZkJars`/`isZkJar` recognize ZK (and ZK addon, e.g. `zkcharts-`/`keikai-`) artifact-name prefixes — used **only** as the "does this module have any ZK at all" gate (R7); `filterLibraryJars` keeps every non-directory, existing-regular-file classpath entry regardless of name (ZK's own transitive deps like `slf4j-api` aren't ZK-prefixed) and is what actually gets handed to the launcher's `--classpath`. Also computes a stable SHA-256 `signature()` over a jar set (path+size+mtime) so `ZulPreviewServerService` can tell whether an existing helper JVM can be reused. |
| `PreviewResult` | Outcome of `preparePreview()`: `READY` (port + request path), `NO_ZK_JARS` (R7 — module has no ZK dependency), or `ERROR` (helper JVM failed to start; carries the root-cause message). |

### Key classes — rendering core (`zk-preview-launcher` module)

The core is a **separate Gradle subproject** (`zk-preview-launcher/`), deliberately
free of any `com.intellij.*` import, so it is independently callable:
```
java -jar zk-preview-launcher.jar --classpath <os-separated jars> --webapp <docroot> --port <n>
```
It prints `PREVIEW_PORT=<n>` to stdout once its HTTP server is bound (port `0` picks an
ephemeral port), then blocks until killed. `ZulPreviewServerService` spawns exactly this
CLI as a subprocess; `build.gradle`'s `prepareSandbox`/`buildPlugin` tasks bundle the
built jar into the plugin distribution at `<plugin>/lib/zk-preview-launcher.jar`.

| Class | Path (relative to `zk-preview-launcher/src/`) | Role |
|-------|------|------|
| `Main` | `main/java/.../Main.java` | CLI entry point: parses `--classpath`/`--webapp`/--port`, builds a `RenderEngine`, starts a `PreviewHttpServer`, prints the port line. |
| `PreviewHttpServer` | `main/java/.../PreviewHttpServer.java` | A plain JDK `com.sun.net.httpserver.HttpServer` (no servlet container, no Jetty) bridging plain HTTP to the mock servlet environment: `GET *.zul` → page render, `GET /zkau/web/*` → extendlet-processed resource (JS/CSS), `POST /zkau` → a benign stubbed AU response (first paint never issues a real AU round-trip). |
| `RenderEngineFactory` / `RenderEngine` | `main/java/.../RenderEngineFactory.java`, `RenderEngine.java` | Picks the servlet-API variant (via `VariantDetector`) and constructs the matching `JavaxRenderEngine` or `JakartaRenderEngine`. |
| `VariantDetector` | `main/java/.../VariantDetector.java` | Detects javax vs. jakarta by scanning the resolved `DHtmlLayoutServlet.class` bytecode for which servlet package it references (no reflection/loading needed) — tries the canonically-named `zk-<version>.jar` first so an unrelated same-path class elsewhere on a wide classpath can't win by list position. |
| `JavaxRenderEngine` / `JakartaRenderEngine` | `main/java/.../javax/`, `.../jakarta/` | Drive `DHtmlLayoutServlet`/`DHtmlUpdateServlet` directly via reflection against hand-written mock servlet objects (`mock/MockServletContext`, `MockHttpServletRequest/Response`, `MockHttpSession`, `MockServletConfig`) — one full mock-servlet-API implementation per variant, since the packages (`javax.servlet.*` vs. `jakarta.servlet.*`) don't collide. |
| `ScopedZkClassLoader` / `IsolatedRuntime` | `main/java/.../ScopedZkClassLoader.java`, `IsolatedRuntime.java` | Builds the classloader a render runs under: the caller-supplied ZK jars plus the isolation-hook classes, child-first for `org.zkoss.*`, parented on the launcher's own classloader (required so reflectively-invoked mock servlet objects share the exact same `Class` identity as the loaded ZK code). |
| `ErrorMapper` / `RenderError` / `RenderResult` / `RenderPhase` | `main/java/.../ErrorMapper.java`, etc. | Turns a render-time exception into the structured JSON failure contract (AC-6) — see "Isolation & structured failures" below. |

### How it works
1. `plugin.xml` registers `ZulPreviewFileEditorProvider` as a `fileEditorProvider`.
2. Opening a `.zul` file creates a `TextEditorWithPreview` (text editor + `ZulPreviewFileEditor`).
3. `ZulPreviewFileEditor` asks the project's `ZulPreviewServerService` to resolve the
   file's module classpath/docroot and ensure a helper JVM is running for that
   `(docroot, classpath-signature)` pair, spawning `zk-preview-launcher.jar` via
   `GeneralCommandLine`/`KillableProcessHandler` if none exists yet.
4. Once the helper JVM reports its port, the preview pane's `JBCefBrowser` loads
   `http://localhost:<port>/<path-to-zul>` directly — the embedded browser renders
   whatever the launcher's `PreviewHttpServer` returns (real HTML on success, a
   structured JSON body on failure, since the browser has no special-cased error UI in v1).
5. Saving the file triggers a VFS content-change event, debounced then coalesced into
   a browser reload of the same URL.
6. Closing the tab disposes the editor's own resources (browser, listeners); the shared
   helper JVM keeps running for the rest of the project session and is only killed when
   the project itself closes (`ZulPreviewServerService.dispose()`).

### Isolation & structured failures
The core's isolation guarantee — **the previewed project's own ViewModel/Composer/
converter/validator classes are never loaded, not even to fail loudly** — rests on two
things, not on a restricted classpath (an early design considered a ZK-jars-only
classpath allowlist; it was abandoned because ZK's own transitive deps, e.g.
`slf4j-api`, aren't ZK-prefixed and would starve the launcher's own bootstrap — see
`ZkClasspathFilter`'s javadoc and `tasks/zul-preview/PLAN.md`'s D1):

1. `ScopedZkClassLoader` — the isolation-hook classes and the caller-supplied ZK jars
   are the *only* jars on the classloader that renders the page; a user project's own
   compiled output directories are never included (`ZulPreviewServerService.resolveTarget`
   filters directories out and excludes the project SDK).
2. `PreviewUiFactory` (`zk-preview-launcher/src/hooks/java/org/zkoss/zkpreview/hooks/PreviewUiFactory.java`),
   registered via `zk.xml`'s `<ui-factory-class>` and compiled in a dedicated `hooks`
   Gradle sourceSet against an old ZK version for maximum binary compatibility:
   - `newComposer(...)` always returns a no-op `PreviewComposer` instead of resolving
     the real composer/ViewModel class name — this single override blocks both
     `apply="user.X"` and the auto-applied MVVM `BindComposer` path, since ZK resolves
     both through the same `UiFactory` call. Bound values consequently render
     empty/placeholder by design, not because of a bug.
   - `getPageDefinition(...)` additionally recognizes an unresolved binding-annotation
     shape (`@name(...)`) leaking through as a literal page path — e.g.
     `<apply templateURI="@load(vm.x)">` when the annotation is malformed enough that
     ZK's own compiler didn't recognize it as annotation syntax — and substitutes a
     synthesized empty page instead of delegating to a real file lookup, matching real
     ZK's "the apply contributes nothing" outcome instead of throwing "Page not found".

When rendering does fail (parse errors, missing zscript classes, invalid component
hierarchies, ...), `ErrorMapper.map(zulPath, throwable)` turns the exception chain into
a `RenderError { phase, message, zulFile, line, column }`, serialized by
`RenderResult.toJson()` as the HTTP 500 body:
```json
{"status":"FAILURE","error":{"phase":"COMPOSE","message":"...","zulFile":"/x.zul","line":7,"column":null}}
```
`phase` is one of `PARSE`, `COMPOSE`, `UNKNOWN` (`CLASSPATH`/`RESOURCE` are reserved
enum values not currently assigned by any code path). `COMPOSE` covers both missing-class
failures (a `ClassNotFoundException` in the cause chain, or BeanShell/zscript's own
"Class: X not found in namespace" message) and, as of this phase, any bare
`org.zkoss.zk.ui.UiException`/subclass reaching the mapper with neither signal — i.e. a
failure raised while ZK builds the component tree from an already-successfully-parsed
document (e.g. "Unsupported parent for row" from placing a `<row>` outside a `<rows>`/
`<grid>` ancestor). `line`/`column` are best-effort: populated only when the failing
layer's own exception message reports a position (guaranteed for BeanShell/zscript
failures; structurally absent for plain `UiException`s like the hierarchy case above —
there is nothing in that exception chain to recover a line from). The full JSON schema
and field-by-field semantics are documented as the stage-2 ("Fail-Render reporting")
integration contract in `tasks/zul-preview/stage2-hook.md`; v1 ships no consumer of it.

### v1 limitations (honest, by design)
- **First paint only**: no AU (asynchronous update) round-trip is driven — the launcher
  stubs `POST /zkau` with a benign empty response. Client-side interactions that require
  a server round-trip (e.g. a button's `onClick` reaching a real Java handler) are not
  simulated.
- **No user-class fidelity**: ViewModels/Composers/converters/validators are never
  loaded, so MVVM-bound values always render empty/placeholder rather than their real
  runtime values — this is intentional (the isolation guarantee), not a fidelity bug to
  fix later.
- **JCEF required for the embedded browser render**: if `JBCefApp.isSupported()` is
  false (e.g. some remote-dev/headless/alternative-JDK IDE runtimes), the preview pane
  shows an explanatory message instead of a live render; there is no non-JCEF fallback
  renderer in v1.
- **No ZK jars on the classpath**: if the previewed file's module has no ZK dependency
  at all, the preview pane explains this (R7) rather than attempting a render.

---

## Shared Utilities

| Class | Path | Role |
|-------|------|------|
| `ZulDomUtil` | `dom/ZulDomUtil.java` | Central utility for ZK file detection (`isZKFile`, `isZkConfigFile`, `isLangAddonFile`) and ViewModel detection (`hasViewModel`). Used by completion, descriptors, validation, and browser URL features. |
| `ZKPathManager` | `project/ZKPathManager.java` | Provides plugin temp/resource paths. Used by `ZKProjectsManager`, `ZKMavenArchetypesProvider`, and `ZKNews`. |

---

## Plugin Configuration

All extensions and actions are registered in `src/main/resources/META-INF/plugin.xml`:

| Extension Point | Implementation | Purpose |
|----------------|----------------|---------|
| `standardResourceProvider` | `ZulSchemaProvider` | ZUL XSD schema |
| `standardResourceProvider` | `ZkConfigSchemaProvider` | zk.xml XSD schema |
| `standardResourceProvider` | `LangAddonSchemaProvider` | lang-addon.xml XSD schema |
| `typedHandler` | `ZulTypedHandler` | Auto-popup on `@` |
| `completion.contributor` | `MVVMAnnotationCompletionProvider` | MVVM annotation completion |
| `gotoDeclarationHandler` | `GotoJavaClassHandler` | Class navigation |
| `webBrowserUrlProvider` | `WebBrowserUrlProvider` | Open in Browser URL |
| `java.programPatcher` | `MavenRunnerPatcher` | Capture Jetty port |
| `fileIconProvider` | `ZulIconProvider` | ZUL file icon |
| `xml.elementDescriptorProvider` | `ZkDomElementDescriptorProvider` | XML element descriptors |
| `projectService` | `ZkDomElementDescriptorHolder` | Descriptor caching |
| `annotator` | `ZkXmlValidationAnnotator` | lang-addon.xml validation |
| `postStartupActivity` | `ZKProjectsManager` | Schema/archetype updates |
| `postStartupActivity` | `ZKNews` | News notifications |
| `postStartupActivity` | `ZulFileTypeRegistrar` | File type association fix |
| `archetypesProvider` | `ZKMavenArchetypesProvider` | Maven archetypes |
| action group | `ZK_Feedback_Group` | Help menu links |
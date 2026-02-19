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
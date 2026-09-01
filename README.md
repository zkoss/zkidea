# zkidea
ZK IntelliJ IDEA Plugin
[plugin page on Jetbrains marketplace](https://plugins.jetbrains.com/plugin/7855-zk)

## Supported IntelliJ Version
See `patchPluginXml` in [build.gradle](build.gradle) for detail.

## Features

### ZUL File Support
*   **Code Completion**: Provides context-aware suggestions for ZK components, attributes, and events as you type in ZUL files, speeding up development.
*   **Go to Declaration**: Quickly navigate from a component tag in a ZUL file to its corresponding Java class (e.g., Composer or ViewModel).
*   [Feature & Implementation Overview](doc/feature_overview.md) - Mapping features to implementation classes
*   [Data Binding Navigation & Completion](doc/data_binding_navigation_completion.md) - Technical overview of MVVM data binding features
*   **Open in Browser**: For Maven projects, this feature intelligently constructs the correct URL to view your ZUL file in a browser, automatically detecting the server port and context path from your `pom.xml`.

### ZK Configuration File Support
*   **`zk.xml` Code Completion**: Offers code completion for elements and attributes in `zk.xml`, helping you configure your ZK application correctly.
*   **`lang-addon.xml` Completion and Validation**: Provides code completion and validates the file to ensure all required elements are present, preventing common configuration errors.
> **Note:** To enable code completion, either use the default filenames (`zk.xml`, `lang-addon.xml`) or add the correct namespace to your custom-named XML file.
> - For `zk.xml`: `xmlns="http://www.zkoss.org/2005/zk/config"`
> - For `lang-addon.xml`: `xmlns="http://www.zkoss.org/2005/zk/lang-addon"`

### MVVM Development Support
*   **Annotation Completion**: Triggers an auto-popup with a list of ZK MVVM annotations (e.g., `@init`, `@load`, `@bind`) whenever you type the `@` symbol, making it easier to write databinding expressions.

### Project Creation
*   **Maven Archetypes**: Integrates with IntelliJ's project wizard to let you create new ZK projects from official Maven archetypes, providing a standardized project structure.

### ZK News Notification
*   **Startup Notification**: Displays a popup with the latest news from the official ZK framework website when you start IntelliJ IDEA, keeping you informed of updates and announcements.

### Layout Preview
*   **Layout Preview**: Adds a side-by-side preview pane to the ZUL editor showing how the page lays out — rendered through your project's own ZK jars in a separate helper process, refreshed on save. Your application never runs: ViewModels, Composers, and converters are never instantiated, so bound values appear as placeholders rather than real data — while the page's own inline code (`<zscript>`, `use="…"`) does run, against your module's compiled classes. It is a first-paint layout view, not a live application. See the [Feature & Implementation Overview](doc/feature_overview.md) for details and current v1 limitations.

### Feedback Menu
*   **Easy Access to Support**: Adds a "ZK Feedback" menu under the "Help" menu with quick links to report bugs, request features, or get help from the community.
 
## Getting Started
 * [User Guide](https://docs.zkoss.org/zk_installation_guide/create_and_run_your_first_zk_application_with_intellij_and_zkidea)

## Development Setup

### Running the Plugin in Development Mode

#### Method 1: Using Gradle runIde Task (Recommended)
```bash
cd zkidea
./gradlew runIde
```

This will compile the plugin and launch a new IntelliJ IDEA instance with your plugin pre-installed.

#### Method 2: Using IDE Development Environment

1. **Import Project**: 
   - Open IntelliJ IDEA
   - File → Open → Select the `zkidea` folder
   - Import as Gradle project

2. **Configure Run Configuration**:
   - Go to Run → Edit Configurations
   - Click "+" and add new "Gradle" configuration
   - Name: "Run Plugin"
   - Tasks: `runIde`
   - Arguments: (leave empty)
   - Gradle project: select your zkidea project
   - Working directory: should point to your zkidea folder

3. **Run/Debug**:
   - Select your "Run Plugin" configuration from the run dropdown
   - Click Run (▶) or Debug (🐛) button
   - IntelliJ will launch a new instance with the plugin loaded

#### Development Benefits
- **Hot Reloading**: Make changes and restart the test IDE to see updates
- **Debugging**: Set breakpoints in your plugin code for debugging
- **Live Testing**: Test plugin features immediately without building JARs
- **Rapid Iteration**: Quick development cycle for faster development

## License

 * [Apache License version 2](https://github.com/jumperchen/zkidea/blob/master/LICENSE)

## Download

 * You can install and update ZK IntelliJ Plugin at IntelliJ Setting > Plugins Marketplace.
 * [IntelliJ plugins home](https://plugins.jetbrains.com/plugin/7855)

## Demo
TBD

# Release Process

Two artifacts come out of this repository under one version and one `v*` tag: the **plugin**,
published to the JetBrains Marketplace, and **`zk-preview-launcher.jar`**, attached to the
GitHub Release *and* bundled inside the plugin at `<plugin>/lib/`. They share a version
because external consumers build the launcher's download URL out of the tag name.

**They do not have to ship together.** A release whose changes are confined to the launcher
can stop after step 3: pushing the tag publishes the jar, and step 4 is simply not run.

**1.0.2 was such a release, and deliberately so.** All of it was preview-launcher work done
for what the `zul-writer` agent skill needed — no plugin code changed, only the
`<change-notes>` entry. The jar was published for the skill to pin; the plugin was not
published. That is why the Marketplace listing goes 1.0.1 → 1.0.3, and the gap is planned
rather than a missed release.

The reverse also happens, so do not read the above as "launcher-only changes never need a
plugin release". 1.0.3 changed no plugin code either, yet was published: its fixes reach
plugin users *through* the bundled jar, and those two (#70, #71) were worth shipping to them.
Whether to run step 4 is a judgement about who needs the change, not a mechanical consequence
of which directory it landed in.

## 1. Version Updates
Update the version in three locations. All three must agree with each other **and** with the
`v<version>` tag created in step 3:
- `build.gradle` - Update the `version` property
- `zk-preview-launcher/build.gradle` - Update the `version` property. The launcher release
  workflow refuses to publish when this disagrees with the tag, so a missed bump here fails
  the release after the tag is already pushed.
- `src/main/resources/META-INF/plugin.xml` - Add new version entry to `<change-notes>`

## 2. Testing and Validation
```bash
# Build the plugin (also runs the tests)
./gradlew build

# Verify plugin structure and descriptor
./gradlew verifyPlugin

# Check API compatibility against real IDE builds.
# Required every release: `untilBuild` has no upper bound, so this is the only gate
# that catches API breakage in newer IDEs. Target builds are set in build.gradle.
./gradlew runPluginVerifier

# Test locally in IDE
./gradlew runIde
```

## 3. Push and Tag

**The tag comes before publishing, not after.** The launcher release workflow
(`.github/workflows/release-launcher.yml`) builds `zk-preview-launcher-<VER>.jar` *from the
tag*, so tagging last would put the plugin on the Marketplace before the jar released
alongside it exists, and would leave a window in which the published ZIP and the tagged
commit can diverge. Everything in step 2 has to be green first: once the tag carries a
published Release its bytes are pinned by external consumers and it must never be re-cut —
bump the version instead.

```bash
git push origin master        # master first, so the Release is built from a commit on master
git tag v<VER>
git push origin v<VER>
git describe --tags --exact-match   # must print v<VER>
```

`--tags` is required: `git tag v<VER>` creates a *lightweight* tag, and `git describe
--exact-match` alone considers only annotated tags, so it fails with "no tag exactly
matches" even when the tag is right there.

Pushing the tag triggers the workflow, which attaches the launcher jar and its `.sha256` to
the Release. That half has its own procedure, including a **mandatory** follow-up in
`zkoss-demo/agent-skill` that must not be skipped:
[doc/release-launcher-procedure.md](doc/release-launcher-procedure.md).

## 4. Build and Publish to JetBrains Marketplace
```bash
# it automatically runs `buildPlugin` first, so no need to run them separately.
./gradlew publishPlugin
```

## 5. Post-Release
1. **Update Development Version**

2. **Verify Publication**
   - Check plugin appears on [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7855)
   - Test installation from marketplace

3. **Update the Marketplace Listing Media**
   - Screenshots and GIFs are *not* part of the plugin ZIP and are not covered by
     `intellijPublishToken`. Upload them by hand: sign in at
     [plugins.jetbrains.com/plugin/7855-zk](https://plugins.jetbrains.com/plugin/7855-zk)
     with the vendor account, then Edit → screenshots/media.


# Troubleshooting
* syntax highlighting in zul doesn't work after restarting IntelliJ IDEA
If you ever uninstalled the plugin before, you might encounter this issue. This is caused by an [IntelliJ bug](https://youtrack.jetbrains.com/issue/IJPL-39443/Plugin-fileType-extensions-will-disappear-after-restart-if-the-plugin-was-uninstalled-once-befores).
The current workaround is to manually add zul file type in IntelliJ IDEA settings:
  1. Go to `Settings` > `Editor` > `File Types`
  2. Under `Recognized File Types` > `XML`
  3. Add `*.zul` to the list of `File name patterns`


* For Mac user, if you run this plugin with IntelliJ 14 that crashes on startup, you may refer to [this solution](https://github.com/zkoss/zkidea/issues/10#issuecomment-148628901)

# IntelliJ IDEA major change
Starting with the 2025.3 release, JetBrains has moved to a single, unified distribution, effectively retiring the separate "Community Edition" installer. There is now only one IntelliJ IDEA installer for everyone. f you do not have an Ultimate subscription, the IDE acts as the free version.
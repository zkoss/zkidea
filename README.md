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


## 1. Version Updates
Update version numbers in two locations:
- `build.gradle` - Update the `version` property
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

## 3. Build and Publish to JetBrains Marketplace
```bash
# it automatically runs `buildPlugin` first, so no need to run them separately.
./gradlew publishPlugin
```

## 4. Post-Release
1. **Create Git Tag**
   ```bash
   git tag v0.1.X
   git push origin v0.1.X
   ```

2. **Update Development Version**

3. **Verify Publication**
   - Check plugin appears on [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7855)
   - Test installation from marketplace

4. **Update the Marketplace Listing Media**
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
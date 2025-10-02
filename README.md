# zkidea
ZK IntelliJ IDEA Plugin
[plugin page on Jetbrains marketplace](https://plugins.jetbrains.com/plugin/7855-zk)

## Supported IntelliJ Version
See `patchPluginXml` in [build.gradle](build.gradle) for detail.

## Features

 * **ZUL File Support**
   * ZUL code completion for ZK components, attributes, and events
   * ZUL to class declaration navigation
   * ZUL syntax check and validation
 * **ZK Configuration File Support**
   * ZK configuration file (`zk.xml`) code completion and validation
   * Language addon configuration file (`lang-addon.xml`) code completion and validation
   > **Note:** To enable code completion, either use the default filenames (`zk.xml`, `lang-addon.xml`) or add the correct namespace to your custom-named XML file.
   > - For `zk.xml`: `xmlns="http://www.zkoss.org/2005/zk/config"`
   > - For `lang-addon.xml`: `xmlns="http://www.zkoss.org/2005/zk/lang-addon"`
 * **MVVM Development Support**
   * ZK MVVM annotation code completion (@init, @load, @bind, @save, @command, etc.)
 * **Project Creation**
   * ZK Maven archetypes for creating ZK projects 
 * a feedback menu under the Help menu
 
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
# Build the plugin
./gradlew build

# Run tests
./gradlew test

# Verify plugin structure and compatibility
./gradlew verifyPlugin

# Test locally in IDE
./gradlew runIde
```

## 3. Build Distribution
```bash
# Create plugin distribution ZIP
./gradlew buildPlugin
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


# Troubleshooting
* syntax highlighting in zul doesn't work after restarting IntelliJ IDEA
If you ever uninstalled the plugin before, you might encounter this issue. This is caused by an [IntelliJ bug](https://youtrack.jetbrains.com/issue/IJPL-39443/Plugin-fileType-extensions-will-disappear-after-restart-if-the-plugin-was-uninstalled-once-befores).
The current workaround is to manually add zul file type in IntelliJ IDEA settings:
  1. Go to `Settings` > `Editor` > `File Types`
  2. Under `Recognized File Types` > `XML`
  3. Add `*.zul` to the list of `File name patterns`


* For Mac user, if you run this plugin with IntelliJ 14 that crashes on startup, you may refer to [this solution](https://github.com/zkoss/zkidea/issues/10#issuecomment-148628901)
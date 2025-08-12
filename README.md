# zkidea
ZK IntelliJ IDEA Plugin
[plugin page on Jetbrains marketplace](https://plugins.jetbrains.com/plugin/7855-zk)

## IntelliJ Version

This plugin can support on IntelliJ platform 141.1532 or greater

## Features

 * ZUL content assist
 * ZK MVVM annotation content assist
 * ZUL syntax check
 * ZK Maven archetypes for creating ZK project
 
## Getting Started
 * [User Guide](http://books.zkoss.org/wiki/ZK_Installation_Guide/Quick_Start/Create_and_Run_Your_First_ZK_Application_with_IntelliJ_and_ZKIdea)

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

### License

 * [Apache License version 2](https://github.com/jumperchen/zkidea/blob/master/LICENSE)

### Download

 * You can install and update ZK IntelliJ Plugin at IntelliJ Setting > Plugins Marketplace.
 * [IntelliJ plugins home](https://plugins.jetbrains.com/plugin/7855)

### Demo

 * [Video](http://screencast.com/t/xjx0RyzX)
 

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


* For Mac user, if you run this plugin with IntelliJ 14 that crashes on startup, you may refer to [this solution](https://github.com/jumperchen/zkidea/issues/10#issuecomment-148628901)
# zkidea
ZK IntelliJ IDEA Plugin
[plugin page on Jetbrains marketplace](https://plugins.jetbrains.com/plugin/7855-zk)

### IntelliJ Version

This plugin can support on IntelliJ platform 141.1532 or greater

### Features

 * ZUL content assist
 * ZK MVVM annotation content assist
 * ZUL syntax check
 * ZK Maven archetypes for creating ZK project
 
### Getting Started
 * [User Guide] (http://books.zkoss.org/wiki/ZK_Installation_Guide/Quick_Start/Create_and_Run_Your_First_ZK_Application_with_IntelliJ_and_ZKIdea)

### License

 * [Apache License version 2](https://github.com/jumperchen/zkidea/blob/master/LICENSE)

### Download

 * You can install and update ZK IntelliJ Plugin by following the standard procedures provided by IntelliJ Plugins Manager.
 * [IntelliJ plugins home](https://plugins.jetbrains.com/plugin/7855)

### Demo

 * [Video](http://screencast.com/t/xjx0RyzX)
 
### Troubleshooting

 * For Mac user, if you run this plugin with IntelliJ 14 that crashes on startup, you may refer to [this solution] (https://github.com/jumperchen/zkidea/issues/10#issuecomment-148628901)

# Release Process

## Prerequisites

1. **Setup Environment Variables**
   ```bash
   export ORG_GRADLE_PROJECT_intellijPlatformPublishingToken='YOUR_TOKEN'
   ```
   - Get your Personal Access Token from [JetBrains Marketplace profile](https://plugins.jetbrains.com/author/me/tokens)

2. **Ensure Clean Working Directory**
   ```bash
   git status  # Should show no uncommitted changes
   ```

## Release Steps

### 1. Version Updates
Update version numbers in two locations:
- `build.gradle` - Update the `version` property
- `src/main/resources/META-INF/plugin.xml` - Add new version entry to `<change-notes>`

### 2. Testing and Validation
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

### 3. Build Distribution
```bash
# Create plugin distribution ZIP
./gradlew buildPlugin
```

### 4. Publishing
```bash
# Sign and publish to JetBrains Marketplace
./gradlew publishPlugin
```

### 5. Post-Release
1. **Create Git Tag**
   ```bash
   git tag v0.1.X
   git push origin v0.1.X
   ```

2. **Update Development Version**
   - Bump version to next development version in `build.gradle`
   - Commit version bump

3. **Verify Publication**
   - Check plugin appears on [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7855)
   - Test installation from marketplace


## Troubleshooting

- **Plugin Verification Failures**: Check compatibility ranges in `patchPluginXml`
- **Publishing Errors**: Verify token permissions and network connectivity
- **Build Failures**: Ensure all dependencies are resolved and tests pass

## Automated Releases

Consider setting up GitHub Actions for automated releases using the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
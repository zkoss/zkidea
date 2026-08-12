# Marketplace "Internal API" rejection — root cause & fix plan

Report: `Internal methods usages (2) — PluginManagerCore.getPlugin(PluginId)`

## 1. The two usages

| Site | What it needs the descriptor for |
|---|---|
| [ZulPreviewServerService.java:336](../src/main/java/org/zkoss/zkidea/preview/ZulPreviewServerService.java#L336) | `descriptor.getPluginPath()` → locate `<plugin>/lib/zk-preview-launcher.jar` |
| [PreviewIssueReporter.java:171](../src/main/java/org/zkoss/zkidea/preview/PreviewIssueReporter.java#L171) | `descriptor.getVersion()` → plugin version for the GitHub issue link |

Both go through `PluginManagerCore.getPlugin(PluginId.getId("org.zkoss.zkidea"))`.

## 2. Root cause

Verified against the local 2023.3 SDK jars and current `intellij-community` master:

- **In the SDK we compile against (2023.3)** `PluginManagerCore.getPlugin(PluginId)` carries **no annotation at all** — plain `public static final`. So `./gradlew build` is silent, and there is nothing in the IDE to warn about.
  ```
  # javap -v (ideaIC-2023.3/lib/app-client.jar)
  public static final com.intellij.ide.plugins.IdeaPluginDescriptor getPlugin(com.intellij.openapi.extensions.PluginId);
      (no RuntimeInvisibleAnnotations)
  ```
- **In current platform builds** the same method is annotated internal:
  ```kotlin
  // platform/core-impl/src/com/intellij/ide/plugins/PluginManagerCore.kt
  @ApiStatus.Internal
  @Contract("null -> null")
  @JvmStatic
  fun getPlugin(id: PluginId?): IdeaPluginDescriptor? = if (id == null) null else findPlugin(id)
  ```
- [build.gradle:36](../build.gradle#L36) sets `untilBuild = provider { null }`, i.e. the plugin declares compatibility with *every* future build. The Marketplace verifier therefore validates the bytecode against those newer IDEs, where the call is a private-API usage. Nothing in our code changed — JetBrains moved the API behind `@ApiStatus.Internal`, and our unbounded `untilBuild` puts us in scope of the check.

So: **compiled against a build where the API was public, verified against builds where it is internal.**

## 3. Why the obvious replacements don't work

Every drop-in descriptor lookup is *also* `@ApiStatus.Internal` on master — swapping one for another would just move the warning:

| Candidate | Status on master |
|---|---|
| `PluginManager.getPlugin(PluginId)` | `@Deprecated` + `@ApiStatus.Internal` |
| `PluginManager.getInstance().findEnabledPlugin(PluginId)` | `@ApiStatus.Internal` ("Use `PluginDetailsService` instead in plugins") |
| `PluginManager.getPluginByClass(Class)` | `@ApiStatus.Internal` |
| `PluginDetailsService.findDetails(...)` (the new blessed API) | `@ApiStatus.Experimental`, `@Service` — only exists in very recent builds; unusable with `sinceBuild = 233.2` |

Conclusion: with a 2023.3 floor there is **no supported way to obtain our own `IdeaPluginDescriptor`**. The fix is to stop asking for the descriptor and get the two facts we actually need from stable APIs.

## 4. Fix

### 4a. Launcher jar path → `PathManager.getJarForClass`

`com.intellij.openapi.application.PathManager.getJarForClass(Class)` is public, unannotated on master, and present in 2023.3 (`lib/util-8.jar`). Our own jar lives in `<plugin>/lib/`, which is exactly the directory the launcher jar is packaged into by `prepareSandbox` ([build.gradle:74-79](../build.gradle#L74-L79)), so the parent directory of our jar *is* the old `pluginPath/lib`.

```java
private Path resolveLauncherJar() {
    Path pluginJar = PathManager.getJarForClass(ZulPreviewServerService.class);
    if (pluginJar == null || pluginJar.getParent() == null) {
        throw new IllegalStateException("Could not locate the '" + PLUGIN_ID + "' plugin installation directory");
    }
    return pluginJar.getParent().resolve(LAUNCHER_JAR_NAME);
}
```

The `null` branch keeps the existing contract: `resolveLauncherJar()` throws inside the guarded `commandLineSupplier`, so the failure still surfaces as the preview error card (see the comment at [ZulPreviewServerService.java:158](../src/main/java/org/zkoss/zkidea/preview/ZulPreviewServerService.java#L158)).

Semantically-closer alternative, also public and present in 2023.3, if you prefer expressing "a resource inside my plugin's dist dir":
```java
File jar = PluginPathManager.getPluginResource(ZulPreviewServerService.class, "lib/" + LAUNCHER_JAR_NAME);
```
(`PluginPathManager` resolves the plugin dist dir on both old and new platforms; it returns `null` when the plugin runs from a classes directory rather than a jar.) `PathManager.getJarForClass` is the leaner of the two and lives in the most stable module — recommended.

Imports to drop from `ZulPreviewServerService`: `IdeaPluginDescriptor`, `PluginManagerCore`, `PluginId` (all three become unused; `PLUGIN_ID` is still used in the message). Add `com.intellij.openapi.application.PathManager`.

### 4b. Plugin version → build-generated resource

The version is a build-time constant, so bake it in instead of asking the platform.

`src/main/resources/org/zkoss/zkidea/preview/plugin-version.properties`:
```properties
version=${pluginVersion}
```

`build.gradle`:
```gradle
tasks.named('processResources') {
    inputs.property('pluginVersion', project.version)
    filesMatching('org/zkoss/zkidea/preview/plugin-version.properties') {
        expand(pluginVersion: project.version)
    }
}
```

`PreviewIssueReporter`:
```java
static String pluginVersion() {
    try (InputStream in = PreviewIssueReporter.class.getResourceAsStream("plugin-version.properties")) {
        if (in == null) return "unknown";
        Properties props = new Properties();
        props.load(in);
        return props.getProperty("version", "unknown");
    } catch (IOException e) {
        return "unknown";
    }
}
```

Same `"unknown"` fallback as today, and it now also reports correctly under unit tests (where no `Application`/descriptor exists at all).

Imports to drop from `PreviewIssueReporter`: `IdeaPluginDescriptor`, `PluginManagerCore`, `PluginId`.

No-build-change alternative: read `<version>` out of `/META-INF/plugin.xml` on the classpath — `patchPluginXml` injects it, confirmed present in `build/libs/zkidea-1.0.0.jar`. Costs a bit of parsing and returns `"unknown"` in tests (source `plugin.xml` has no `<version>` element), so the generated properties file is preferable.

## 5. Verification

1. `grep -rn "PluginManagerCore" src/` → no hits.
2. `./gradlew build` → compiles, tests pass (`ZulPreviewLauncherSeamTest`, `ServerStartGuardTest`, `LauncherJvmVersionGateTest` all touch the launcher-jar path).
3. `./gradlew runPluginVerifier` (already configured for `IC-2023.3`, `IU-2025.3`, `IU-2026.2` at [build.gradle:44](../build.gradle#L44)) → the "Internal API usages" section must be empty. This is the check that reproduces the Marketplace failure locally.
4. `./gradlew runIde`, open a `.zul`, run the preview → confirms the launcher jar is still found in the real sandbox layout.
5. Bump the version, `./gradlew buildPlugin`, re-upload.

## 7. Implementation (done)

Tests first, then the two changes.

| Change | File |
|---|---|
| new test: launcher jar is a sibling of our own jar; unlocatable jar throws with the plugin id | [LauncherJarLocationTest.java](../src/test/java/org/zkoss/zkidea/preview/LauncherJarLocationTest.java) |
| new test: `pluginVersion()` equals the version this build produced (`-Dzkidea.version`) | [PreviewIssueReporterTest.java](../src/test/java/org/zkoss/zkidea/preview/PreviewIssueReporterTest.java) |
| `resolveLauncherJar()` → `PathManager.getJarForClass` + pure `launcherJarNextTo(Path)` seam | [ZulPreviewServerService.java](../src/main/java/org/zkoss/zkidea/preview/ZulPreviewServerService.java) |
| `pluginVersion()` → stamped resource | [PreviewIssueReporter.java](../src/main/java/org/zkoss/zkidea/preview/PreviewIssueReporter.java) |
| new stamped resource | [plugin-version.properties](../src/main/resources/org/zkoss/zkidea/preview/plugin-version.properties) |
| `processResources` expand + `-Dzkidea.version` for tests + the stale sandbox comment | [build.gradle](../build.gradle) |

Note the `"lib"` segment is now *implicit*: the old code appended it to `pluginPath`, the new code
takes the parent of our own jar, which **is** that directory. Confirmed against the real
distribution rather than assumed:

```
$ unzip -Z1 build/distributions/zkidea-1.0.0.zip "*/lib/*"
zkidea/lib/zk-preview-launcher.jar
zkidea/lib/instrumented-zkidea-1.0.0.jar     <- getJarForClass(ZulPreviewServerService.class)
zkidea/lib/jsoup-1.13.1.jar
zkidea/lib/searchableOptions-1.0.0.jar
$ unzip -p .../instrumented-zkidea-1.0.0.jar org/zkoss/zkidea/preview/plugin-version.properties
version=1.0.0
```

Results: `grep -rn "PluginManagerCore\|IdeaPluginDescriptor\|PluginId" src/` → no hits;
`./gradlew build` → BUILD SUCCESSFUL, both new tests green (`LauncherJarLocationTest` 2/2,
`PreviewIssueReporterTest` 12/12, no skips).

## 6. Considered alternative: cap `untilBuild` instead of changing the code

Rejected — it does not work at `262.*`, and the only cap that *would* work costs us the current IDE.

### When the annotation actually landed

Fetched `PluginManagerCore.kt` from each release branch and from the `262` build tags:

| Branch / tag | IDE | `@ApiStatus.Internal` on `getPlugin` |
|---|---|---|
| 233, 241, 242, 243, 251, 252, 253, 261 | 2023.3 … 2026.1 | no |
| `idea/262.4852.50` | early 2026.2 EAP | no |
| `idea/262.6653.22` | 2026.2 EAP | **yes** |
| `idea/262.8665.337`, `idea/262.9437.185` | released 2026.2.x | **yes** |
| master | 2026.3 dev | **yes** |

The annotation was introduced **mid-262 EAP cycle** and is present in every released 2026.2 build. So:

- `untilBuild = '262.*'` → **still fails.** 262 is precisely the branch that carries the annotation.
- `untilBuild = '262.9437.x'` (a 2026.2.1 RC) → **still fails**, same reason.
- `untilBuild = '261.*'` → passes the check, but drops support for 2026.2, which is already released. Users on the current IDE could no longer install the plugin. Trading away the newest IDE to avoid a ~10-line change is the wrong way round, and it buys one cycle at best — the internal-API list only grows, so 2026.3 would need the same retreat.

### Why 0.7.3 was clean with the same declared range

`git show 28e7bae:build.gradle` → `version '0.7.3'`, `sinceBuild '233.2'`, **`untilBuild '262.*'`**.
`git ls-tree -r 28e7bae -- src/main/java/org/zkoss/zkidea/preview` → empty. `git grep PluginManagerCore 28e7bae -- src` → no hits.

0.7.3 declared 2026.2 compatibility *and* passed verification because the Layout Preview code did not exist in it yet — it landed on master in `1c0e96c` and reached the Marketplace only with 1.0.0. The clean result was never about the compatibility range; it was about there being no `PluginManagerCore` call to flag. Capping 1.0.0 back to `262.*` reproduces 0.7.3's range but not its bytecode, so the report stays.

### Keeping the unbounded range

`untilBuild = null` is what puts us in scope of newer builds' annotations, so this class of report will recur. That is the accepted trade-off for forward compatibility (fix #64), and `runPluginVerifier` is the mitigation — but it has to be run against each new EAP, otherwise the Marketplace finds it first, as it did here. Adding `IU-2026.3` (next EAP) to `runPluginVerifier.ideVersions` as it appears keeps the loop closed.

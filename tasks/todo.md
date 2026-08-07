# Release TODO — ZKIdea 1.0.0

Derived from the "Release Process" section of [README.md](../README.md#L93) and the current
state of the working tree (checked 2026-08-07).

**Already done — do not redo:**
- `build.gradle` `version` is already `1.0.0` (commit `7c921db` "chore: cut 1.0.0").
- `plugin.xml` `<description>` already documents the Layout Preview feature.
- `intellijPublishToken` is already present in `~/.gradle/gradle.properties`.
- `prepareSandbox` already bundles `zk-preview-launcher.jar`; the last built zip
  (`build/distributions/zkidea-1.0.0.zip`) contains `zkidea/lib/zk-preview-launcher.jar`.

---

## 1. Version updates

- [x] **Added the `1.0.0` entry to `<change-notes>` in
      [plugin.xml](../src/main/resources/META-INF/plugin.xml)** — four `[feature]` bullets
      covering Layout Preview, the source view, the render-error report, and the
      JCEF fallback.
- [x] `build.gradle` `version` left at `1.0.0` (no bump needed).
- [x] Shipped the entry **without a GitHub issue link** — no issue number for Layout Preview
      exists anywhere in the repo. Open a retrospective tracking issue if you want the
      listing to match the style of the 0.6.x/0.7.x entries.

## 2. Repo hygiene before building

**Skipped by decision (2026-08-07).**

## 3. Testing and validation

- [x] `./gradlew clean build verifyPlugin` — **BUILD SUCCESSFUL in 1m 48s** (2026-08-07 17:19).
      Tests and descriptor validation both passed.
- [x] Confirmed the packaged descriptor: the jar's `META-INF/plugin.xml` carries
      `<idea-version since-build="233.2"/>` with no `until-build`, and `<p>1.0.0</p>` is the
      first change-notes entry.
- [x] **Configured `runPluginVerifier`** in `build.gradle` — it had no `ideVersions`, so it
      would only have checked the 2023.3 compile target. Now set to
      `['IC-2023.3', 'IU-2025.3', 'IU-2026.2']`: the `sinceBuild` floor, plus the two
      current releases. Verified against JetBrains' release feed that these exist —
      Community stops at 2025.3 (unified distribution after that), so 2026.2 is only
      published under `IU`.
- [x] **`./gradlew runPluginVerifier`** — **BUILD SUCCESSFUL in 19m 53s**, all three
      **Compatible**, no compatibility problems:
      - `IC-233.11799.241` (2023.3) — 1 deprecated API usage, 1 override-only violation
      - `IU-253.28294.334` (2025.3) — 6 deprecated API usages, 1 override-only violation
      - `IU-262.8665.258` (2026.2) — 9 deprecated API usages, **2 internal API usages**,
        1 override-only violation

      Warnings only, none release-blocking, but two are worth a follow-up issue because
      they are the most likely causes of a future break given the open-ended `untilBuild`:
      - **Internal API** — `PluginManagerCore.getPlugin(PluginId)` is `@ApiStatus.Internal`
        and is called from `ZulPreviewServerService.resolveLauncherJar()` and
        `PreviewIssueReporter.pluginVersion()`. Both are new 1.0.0 preview code, and
        `resolveLauncherJar()` failing means Layout Preview cannot start at all.
      - **Deprecated** — `DefaultLiveTemplatesProvider`, `ProcessAdapter`,
        `ResourceRegistrar.addStdResource(String, String, Class)`,
        `MavenVersionCompletionContributor`.
      - Also reported: the plugin is **not dynamic** (`defaultLiveTemplatesProvider` is a
        non-dynamic extension), so install/uninstall requires an IDE restart.
- [x] `./gradlew runIde` — manual smoke test. **Done by the user (2026-08-07).**
- [x] Rebuilt and re-verified the zip — `build/distributions/zkidea-1.0.0.zip` (978 KB,
      17:19) contains `zkidea/lib/zk-preview-launcher.jar` (474 KB), plus the instrumented
      plugin jar, jsoup, and searchable options. The stale 12:54 zip is gone (`clean`).

## 4. Publish

- [x] `./gradlew publishPlugin` — **BUILD SUCCESSFUL** (2026-08-07). 1.0.0 uploaded to the
      JetBrains Marketplace and is in their review queue. Note `signPlugin` was **SKIPPED** —
      no signing certificate is configured for this project; that is the pre-existing setup,
      not something this release changed.

## 5. Post-release

- [x] `git push origin master` — `c7489ce..f6b6e40`.
- [x] `git tag -a v1.0.0 && git push origin v1.0.0` — tag points at `f6b6e40`.
- [ ] **Create the GitHub release for `v1.0.0` — BLOCKED on token scope.** `gh release create`
      fails with *"workflow scope may be required"*; nothing was created. Needs an
      interactive re-auth, so it cannot be done from an automated session:
      ```bash
      gh auth refresh -h github.com -s workflow
      gh release create v1.0.0 --title "Release 1.0.0" \
          --notes-from-tag build/distributions/zkidea-1.0.0.zip
      ```
      Note the repo has no GitHub release for anything between 0.1.23 and 1.0.0 — the
      practice lapsed after 2025-08. Worth deciding whether to keep it up at all.
- [ ] Verify the listing on [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7855)
      after JetBrains review clears, then install from the marketplace into a clean IDE and
      re-run the Layout Preview smoke test against the published build.
      As of publish time the public API still lists 0.7.3 as the newest approved build,
      which is expected — uploads are not listed until review passes.
- [ ] **Upload the listing media — manual, web UI only.** `publishPlugin` ships the ZIP and
      nothing else; screenshots are not part of the distribution or `plugin.xml`, and the
      `intellijPublishToken` does not cover them. Sign in to
      [plugins.jetbrains.com/plugin/7855-zk](https://plugins.jetbrains.com/plugin/7855-zk)
      with the vendor account → Edit → the screenshots/media section, and upload:
      - `doc/zul-preview-hero.png` (1381×946, 193 KB) — lead screenshot
      - `doc/zul-preview-loop.gif` (900×618, 487 KB) — animated demo
- [ ] Update the docs page linked from the plugin description
      (`https://docs.zkoss.org/zk_dev_ref/zkidea`) to cover Layout Preview.
- [ ] Since the version stays at `1.0.0`, the README's "Update Development Version" step is a
      no-op — skip it.

## 6. Optional cleanup (not blocking the release)

- [x] Fixed the README release process: corrected the `1, 2, 3, 5` numbering gap, dropped the
      redundant `./gradlew test` (`build` already runs it), added `runPluginVerifier` with a
      note on why it is now mandatory, and added the manual media-upload step to post-release.

---

## Review

**1.0.0 shipped 2026-08-07.** Published to the marketplace, `master` pushed, `v1.0.0` tagged
at `f6b6e40`.

Commits that made up the release:

```
f6b6e40 chore: record the 1.0.0 plugin verifier results
d07951d chore: prepare the 1.0.0 release
95012e0 docs: add Layout Preview marketplace screenshot and loop GIF
758fe9c feat: say what the preview error report sends before it is sent
```

### What the release process missed, and now catches

- The version had already been bumped to `1.0.0` in `7c921db`, but the matching
  `<change-notes>` entry was never added — the marketplace "What's new" would have been
  blank for the biggest release in the plugin's history. The two live in different files and
  the README lists them as one step, which is how they drifted apart.
- `runPluginVerifier` was documented in a `build.gradle` comment as the replacement for the
  `untilBuild` upper bound, but had no `ideVersions`, so it silently only ever checked the
  compile target. Now pinned to the floor plus the two current releases.

### Follow-ups worth an issue

- **Internal API in the new preview code.** `PluginManagerCore.getPlugin(PluginId)` is
  `@ApiStatus.Internal` and is called from `ZulPreviewServerService.resolveLauncherJar()`
  and `PreviewIssueReporter.pluginVersion()`. With no `untilBuild` ceiling, this is the
  likeliest future break, and `resolveLauncherJar()` failing means Layout Preview cannot
  start at all.
- **Deprecated APIs** flagged on 2026.2: `DefaultLiveTemplatesProvider`, `ProcessAdapter`,
  `ResourceRegistrar.addStdResource(String, String, Class)`,
  `MavenVersionCompletionContributor`.
- **No plugin signing.** `signPlugin` is skipped for want of a certificate.
- **Left untracked on purpose:** `.vscode/`, `doc/potential-ideas.md`.

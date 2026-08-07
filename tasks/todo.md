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
- [ ] **`./gradlew runPluginVerifier`** — **skipped by decision (2026-08-07).** It was
      started and got as far as scheduling all three verifications
      (`IC-233.11799.241`, `IU-253.28294.334`, `IU-262.8665.258`) before being skipped, so
      the config is proven to resolve. **The compatibility result was never read.** Since
      `untilBuild` has no upper bound, nothing else gates API breakage on 2025.3/2026.2 —
      re-run this before or shortly after publishing.
- [x] `./gradlew runIde` — manual smoke test. **Done by the user (2026-08-07).**
- [x] Rebuilt and re-verified the zip — `build/distributions/zkidea-1.0.0.zip` (978 KB,
      17:19) contains `zkidea/lib/zk-preview-launcher.jar` (474 KB), plus the instrumented
      plugin jar, jsoup, and searchable options. The stale 12:54 zip is gone (`clean`).

## 4. Publish

- [ ] `./gradlew publishPlugin` (runs `buildPlugin` first; token already configured).

## 5. Post-release

- [ ] `git tag v1.0.0 && git push origin v1.0.0`
- [ ] Create the GitHub release for `v1.0.0` with the same notes as the change-notes entry.
- [ ] Verify the listing on [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7855)
      after JetBrains review clears, then install from the marketplace into a clean IDE and
      re-run the Layout Preview smoke test against the published build.
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

## Uncommitted at this point

Everything below is edited but **not committed**:

- `src/main/resources/META-INF/plugin.xml` — the 1.0.0 change-notes entry
- `build.gradle` — `runPluginVerifier { ideVersions = [...] }`
- `README.md` — release-process fixes
- `tasks/todo.md` — this file

Also still unpushed from before: `758fe9c`. Commit and push before tagging, or `v1.0.0`
will point at a commit the remote does not have.

---

## Review

_(fill in after the release)_

# Release 1.0.1

Following the release process in [README.md](../README.md#release-process).

## 1. Version Updates
- [x] `build.gradle` → `version '1.0.1'` (already bumped in 063f8ee)
- [x] `plugin.xml` → `<change-notes>` 1.0.1 entry (#66 JCEF plugin dependency, #67 project compiled classes)
- [x] Push local commits to origin/master (da45ffc, 063f8ee)

## 2. Testing and Validation
- [x] `./gradlew clean build` — **BUILD SUCCESSFUL in 1m 35s**, 226 tests passed, 0 failed.
      An incremental `./gradlew build` first failed in `buildSearchableOptions` (headless IDE
      died with a `ConcurrentModificationException`, exit 3); `clean` cleared it and the task
      then passed. Stale build state, not a code problem.
- [x] `./gradlew verifyPlugin` — **BUILD SUCCESSFUL**. Packaged descriptor confirmed:
      `<version>1.0.1</version>`, `<idea-version since-build="233.2"/>` with no `until-build`,
      `<p>1.0.1</p>` first in change-notes.
- [x] `build/distributions/zkidea-1.0.1.zip` (982 KB) contains
      `zkidea/lib/zk-preview-launcher.jar` (474 KB), the instrumented plugin jar, jsoup and
      searchable options.
- [x] `./gradlew runPluginVerifier` — **BUILD SUCCESSFUL in 5m 3s**, all three **Compatible**,
      no compatibility problems:
      - `IC-233.11799.241` (2023.3) — 1 deprecated API usage
      - `IU-253.28294.334` (2025.3) — 6 deprecated API usages
      - `IU-262.8665.258` (2026.2) — 9 deprecated API usages

      **Both 1.0.0 warning classes are cleared**: zero internal-API usages (was 2 on 2026.2,
      fixed by 56e714b) and zero override-only violations (was 1 on every build, fixed by
      d93d2be). Remaining deprecations: `MavenVersionCompletionContributor`,
      `DefaultLiveTemplatesProvider`, `ProcessAdapter`, `ResourceRegistrar.addStdResource`.
      Still not dynamic — `defaultLiveTemplatesProvider` is a non-dynamic extension, so
      install/uninstall needs an IDE restart. Pre-existing, not release-blocking.

## 2b. Manual smoke test

The verifier proves API compatibility only — it cannot exercise either 1.0.1 fix.
Both are Layout Preview behaviour, so they need a human at the keyboard.

### A. Sandbox IDE — `./gradlew runIde` (running now)
Launches **IntelliJ IDEA 2023.3** (the compile target), so it covers #67 and general
regression, **not** #66. Open `manual-test/` (or `manual-test-springboot/`) in it, then:

- [ ] Open a `.zul` file → the editor opens and the Layout Preview pane renders.
- [ ] **#67** — open a `.zul` whose `<zscript>` / `use="…"` / EL function names one of the
      project's own compiled classes → it renders instead of failing with
      "Class or variable not found". (Build the module first so compiled output exists.)
- [ ] Edit and save → preview refreshes with the new content, not a cached render.
- [ ] Source view toggle still works.

### B. Real 2026.2 — install the zip by hand (the only way to verify #66)
Installed locally: `/Applications/IntelliJ IDEA2026.2.app` = **IU-262.8665.258**, the exact
build the verifier ran against.

- [ ] Settings → Plugins → gear → **Install Plugin from Disk…** →
      `build/distributions/zkidea-1.0.1.zip` → restart.
- [ ] **#66** — open a `.zul` file. Before the fix this threw
      `NoClassDefFoundError: org/cef/handler/CefRequestHandler` and the file would not open.
      Expected now: the file opens and the preview renders.
- [ ] Disable the bundled **Web Browser (JCEF)** plugin and reopen a `.zul` → the editor
      still opens and the preview pane explains which JCEF is missing and how to restore it
      (the second 1.0.1 bullet). Re-enable afterwards.

Smoke test run by the user (2026-08-14): `runIde` executed manually, result accepted.
The automated `runIde` launch from this session exited after 28 s with no `org.zkoss.zkidea`
entries in the log — the only SEVEREs were IDE-internal to 2023.3 (`LoadingState`, and
`GradleJvmSupportMatrix` failing `IllegalArgumentException: 25`, i.e. 2023.3's Gradle plugin
not recognising a JDK 25 on this machine).

## 3. Publish
- [x] `./gradlew publishPlugin` — **BUILD SUCCESSFUL in 15s** (2026-08-14). 1.0.1 uploaded to
      the JetBrains Marketplace review queue. `signPlugin` **SKIPPED** — no signing
      certificate is configured for this project; pre-existing setup, unchanged by this
      release.

## 4. Post-Release
- [ ] `git tag -a v1.0.1 && git push origin v1.0.1`
- [ ] Create the GitHub release for `v1.0.1`. The `workflow` token scope that blocked this for
      1.0.0 is now present on the active account (`hawkhero`), so `gh release create` should
      work. Note the repo has had no GitHub release since 0.1.23 (2025-08) — 1.0.0 was never
      published there either.
- [ ] Verify the listing on [Marketplace](https://plugins.jetbrains.com/plugin/7855) once
      review clears, then install from the marketplace into a clean IDE and re-run the
      Layout Preview smoke test against the published build.
- [ ] Marketplace media (screenshots/GIF) — manual web-UI step, `publishPlugin` ships the ZIP
      only. Not needed for 1.0.1 unless the preview UI changed visibly.

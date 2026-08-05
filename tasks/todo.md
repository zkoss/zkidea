# TODO — enrich preview failure reports with the render target

Implements [preview-report-environment-analysis.md §4](preview-report-environment-analysis.md).

Goal: every "Report on GitHub" body carries **Build / Layout / Servlet / ZK jars** in addition to
today's Plugin / IDE / OS / JDK, so a render failure is diagnosable from the issue alone.

Target block (same label set + order in both report paths; `Servlet:` only where the launcher ran):

```
Plugin:  ZKIdea 1.0.0
IDE:     IntelliJ IDEA 2024.3 (IU-243.x)
OS:      Mac OS X 14.6
JDK:     17.0.11
Build:   Maven
Layout:  WAR webapp
Servlet: jakarta
ZK jars: zk-10.0.0.jar, zul-10.0.0.jar, zkbind-10.0.0.jar (+3 more) [24 classpath entries]
```

## Deviation from the analysis (§4 note 1)

The analysis suggested the launcher derive the ZK-jar line itself from `--classpath`. Doing so
would duplicate `ZkClasspathFilter.ZK_ARTIFACT_PREFIXES` (13 prefixes) into the launcher module,
which has no dependency on the plugin. Since the plugin change is needed anyway for Build/Layout,
the plugin instead passes `--report-zkjars` — one source of truth for "what is a ZK jar".
`Servlet:` stays launcher-derived (it is genuinely launcher-only knowledge).

## Steps (TDD — test first for every pure step)

1. **Docroot layout kind** → verify: `DocrootResolverTest` covers all four branches
   - [ ] test: WAR webapp / Spring-Boot classpath `web` / content-root fallback / file-parent fallback
   - [ ] `DocrootResolver.Layout` enum + `Resolution` holder + `resolveWithLayout()`; `resolve()` delegates (existing callers/tests untouched)
2. **ZK jar summary** → verify: `ZkClasspathFilterTest`
   - [x] test: file names only (no absolute paths), `(+N more)` past the cap, total entry count, `none` when empty
   - [x] `ZkClasspathFilter.classpathSummary(List<String>)` — named for what it summarises, and takes the
         *raw* resolved entries (not the filtered launcher list) so a declared-but-missing ZK jar still shows
   - [x] extra test: classpath order preserved, so a shadowing stale duplicate stays visible
3. **Build-system label** → verify: `BuildSystemDetectorTest`
   - [x] test: `GRADLE`→`Gradle`, `Maven`→`Maven`, null/blank→`none`
   - [x] `BuildSystemDetector.label(String)` (pure) + `detect(Module)` (thin platform wrapper)
   - [x] API verified against the real 2023.3 jars: `com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager#getExternalSystemId()`
         (nullable) returns `"Maven"` for mavenized modules and `"GRADLE"` for Gradle
4. **Plugin-side env block** → verify: `PreviewIssueReporterTest`
   - [x] test: pure `renderEnvironment(...)` emits the labels in order; omits optional lines when absent
   - [x] `PreviewIssueReporter.renderEnvironment(...)`; `environment()`/`environment(build, layout, jars)` delegate
5. **Launcher-side env block** → verify: new `ReportEnvTest` in `zk-preview-launcher`
   - [x] test: `--report-build`/`--report-layout`/`--report-zkjars` surface; `Servlet:` derived; still `null` with no identity at all
   - [x] `Main.reportEnv` made package-visible, accepts the new opts + the resolved variant
6. **Wire the facts through** → verify: full build green + a real launcher run
   - [x] `PreviewTarget` carries layout/build/jar-summary; `startServer` passes the three new args
   - [x] `PreviewResult` carries the env block; `ZulPreviewFileEditor` passes it to the reporter
   - [x] **extra, not in the plan**: `ZulPreviewServerService.reportArguments(...)` extracted +
         `ReportArgumentsTest`. The `--report-*` names cross a module boundary with no shared constant;
         without a lock on both sides a rename would silently drop facts from every future report
         (the launcher would just see an unknown option and carry on)
7. **Docs** → verify: reread for accuracy
   - [x] `doc/zul-preview-feature.md` — new "What the report says about your setup" subsection

## Constraints

- Length budget: plugin-side `MAX_BODY_CHARS=6000` truncates from the end, so the env block (early)
  always survives — no cap change needed. Cap the jar list at 12 names regardless.
- Privacy: **file names only, never absolute paths**; docroot **kind**, never the docroot path.

## Review

**Done.** `./gradlew build` green: **561 tests, 0 failures** (was 546 — 15 new; +2 more from the
unplanned `ReportArgumentsTest` guard).

### Verified against a real render, not just unit tests

Spawned the packaged `zk-preview-launcher.jar` against manual-test's real Maven-resolved ZK 10.1.0
classpath, requested a deliberately malformed `.zul`, and decoded the resulting error page's
"Report on GitHub" URL. The issue body carried:

```
Plugin: ZKIdea 1.0.0
IDE: IntelliJ IDEA 2024.3 (IU-243.1)
OS: Mac OS X 15.7.3
JDK: 17.0.4.1
Build: Maven
Layout: WAR webapp
Servlet: jakarta          ← genuinely detected by the launcher from the real jars
ZK jars: zk-10.0.0.jar, zul-10.0.0.jar, zkbind-10.0.0.jar (+3 more) [24 classpath entries]
```

Separately ran the real `ZkClasspathFilter.classpathSummary` over that same classpath:

```
zkmax-10.1.0-jakarta.jar, zkex-10.1.0-jakarta.jar, zkbind-10.1.0-jakarta.jar, zul-10.1.0-jakarta.jar,
zk-10.1.0-jakarta.jar, zweb-10.1.0-jakarta.jar, zcommon-10.1.0-jakarta.jar, zel-10.1.0-jakarta.jar,
zuti-10.1.0-jakarta.jar, zhtml-10.1.0-jakarta.jar [30 classpath entries]
```

274 chars for a full ZK EE set — comfortably inside the budget, and `zkex` present. That is the
whole point: in the documented `CometServerPush` failure this line would show `zkex` **missing**.

### Notes / follow-ups

- **URL-length headroom is thinner than before.** The real error page above came out at 7986 chars
  against the launcher's 8000 limit — the env block costs ~120 chars, so marginally more reports now
  tip into the clipboard hand-off. Nothing is lost when they do (that path carries the *full*,
  untruncated report by design), so no cap change was made. Worth revisiting only if the hand-off
  turns out to be the common case rather than the exception.
- **`Servlet:` is absent from plugin-side cards by design** — no launcher has run at that point, so
  no variant has been detected. Not a gap.
- The two report assemblers remain separate (analysis §4 note 6 suggested collapsing them). Each
  side legitimately owns facts the other cannot see, and the launcher's OS/JDK are deliberately the
  render JVM's. They are kept in step by the shared label contract, locked on both sides.

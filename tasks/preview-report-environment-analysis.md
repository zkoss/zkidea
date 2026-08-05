# Preview failure reports — is "project type" missing, and does it matter?

*Analysis, 2026-08-05. Question: when a page fails to render and the user files a GitHub issue
from the preview pane, does the report say whether the project is Maven / Gradle / other — and
is that needed for debugging?*

---

## 1. What the report carries today

**No — project type is not in the report.** Neither of the two report paths mentions the build
system, the ZK jars, or the docroot.

There are **two** independent report builders, and they carry the same (thin) environment block:

| Path | Built by | Triggered when |
|---|---|---|
| Plugin-side | [PreviewIssueReporter.environment()](../src/main/java/org/zkoss/zkidea/preview/PreviewIssueReporter.java#L102-L107) | "Cannot display preview" cards — no ZK on classpath, JCEF missing, target-resolve/server-start failure ([ZulPreviewFileEditor:104](../src/main/java/org/zkoss/zkidea/preview/ZulPreviewFileEditor.java#L104), [:166](../src/main/java/org/zkoss/zkidea/preview/ZulPreviewFileEditor.java#L166)) |
| Launcher-side | [Main.reportEnv()](../zk-preview-launcher/src/main/java/org/zkoss/zkpreview/Main.java#L65-L78) → [ErrorPageRenderer.reportBody()](../zk-preview-launcher/src/main/java/org/zkoss/zkpreview/ErrorPageRenderer.java#L127-L144) | **The actual "page failed to render" case** — parse / compose errors |

Both emit exactly four lines:

```
Plugin: ZKIdea <version>
IDE:    IntelliJ IDEA 2024.3 (IU-243.x)
OS:     <os.name> <os.version>
JDK:    <java.version>
```

…plus the `.zul` source and the stack trace. Everything about *how the page was set up to render*
is absent.

## 2. Your hypothesis: right instinct, wrong variable

> "it depends on how the ZK jar is loaded"

The instinct is correct — jar loading is the dominant failure axis. But **"Maven vs Gradle" is a
weak proxy for it**, because the plugin never touches the build tool:

- The preview explicitly **does not read `pom.xml` / `build.gradle`** and never runs `mvn`/`gradle`
  (documented in [zul-preview-feature.md §Requirements 3](../doc/zul-preview-feature.md)). It reads
  only IntelliJ's already-resolved model:
  `OrderEnumerator.orderEntries(module).recursively().runtimeOnly().withoutSdk()`
  ([ZulPreviewServerService:186-190](../src/main/java/org/zkoss/zkidea/preview/ZulPreviewServerService.java#L186-L190)).
- So a Maven project and a Gradle project with the same resolved dependencies take **byte-for-byte
  identical code paths**. Knowing "Gradle" tells me nothing the resolved jar list wouldn't tell me
  better.

The build tool only matters **one level upstream** — in whether IntelliJ's model is complete and
correct. That is real (see §4), but it is second-order.

## 3. What actually varies, and is invisible today

Ranked by diagnostic value for a render failure:

### (a) The ZK jars actually handed to the launcher — **highest value**
This is the direct answer to "how was the ZK jar loaded". It captures ZK version, CE vs EE, and
missing transitives. The single documented render failure in the troubleshooting table —
`NoClassDefFoundError: …zkex…CometServerPush` from an unresolved `zkex` — is *exactly* a
"wrong jar set" failure, and today the report shows none of it. Currently the classpath is computed
into `PreviewTarget.launcherClasspath` and thrown away report-wise.

### (b) The inferred docroot kind — **high value**
[DocrootResolver](../src/main/java/org/zkoss/zkidea/preview/DocrootResolver.java) picks one of three
branches (WAR `webapp`/`WEB-INF` ancestor · Spring-Boot classpath `web` root · nearest-content-root
fallback) and returns **only a `Path`, discarding which branch it took**. That branch explains most
"page not found" / broken-`<include>` / `~./`-not-resolving reports.

Note this also subsumes the genuinely useful part of your question: **Spring Boot jar vs WAR is a
packaging difference, not a build-tool difference** — and it surfaces as a *docroot* difference.
Capturing the layout kind is strictly more informative than capturing "Maven".

### (c) Servlet variant detected (javax vs jakarta) — **medium**
Auto-detected in the launcher. A mis-detection is a plausible bug class and currently unobservable
from a report.

### (d) Resource roots put on the render classpath — **medium**
Directly explains the documented `~./page.zul` "Page not found" case.

### (e) Build system (Maven / Gradle / other) — **worth adding, but secondary**
Its real value is not the code path, it's:
- **Reproduction** — I need to know what kind of skeleton project to build to reproduce.
- **Detecting the un-imported / hand-configured project**, which is a distinct support category.
- **Importer-specific model bugs** — Maven's and Gradle's IntelliJ importers genuinely differ in
  how they expose runtime scope, provided scope, and WAR overlays. When the jar list looks wrong,
  the build tool is the next question I would ask.

So: include it, one line, but don't mistake it for the fix.

## 4. Recommendation

Replace the four-line env block with a **"Render target"** block. Suggested shape:

```
Plugin: ZKIdea 1.0.0
IDE:    IntelliJ IDEA 2024.3 (IU-243.x)
OS:     Mac OS X 14.6   JDK: 17.0.11
Build:  Maven            (or: Gradle / none)
Layout: WAR webapp       (or: Spring Boot classpath web / fallback content root)
Servlet: jakarta
ZK jars: zk-10.0.0.jar, zul-10.0.0.jar, zkbind-10.0.0.jar, zcommon-10.0.0.jar,
         zweb-10.0.0.jar, zkmax-10.0.0.jar  (+3 more)
```

### Implementation notes

1. **Do the launcher side first — it is where render failures are reported, and it is nearly free.**
   The launcher already receives the full jar list via `--classpath` and the docroot via `--webapp`.
   It can derive the ZK-jar line and the servlet variant **with no plugin change at all**, purely
   inside `Main.reportEnv()`. That is the cheapest, highest-value slice of this whole change.
2. **Docroot *kind* and build system must come from the plugin** as new `--report-*` args (the
   launcher only sees an absolute path, not which branch produced it). `DocrootResolver.resolve`
   would need to return the branch alongside the path.
3. **Build system lookup**: `ExternalSystemModulePropertyManager.getInstance(module)
   .getExternalSystemId()` returns `GRADLE`; Maven modules are detectable via the Maven plugin API
   (already a declared dependency) or the same external-system id. Fall back to `none`.
4. **Watch the length budget.** `MAX_BODY_CHARS = 6000` / `MAX_URL_CHARS = 8000`. A 15-jar list is
   ~600 chars and will push more reports into the truncation (plugin-side) / clipboard hand-off
   (launcher-side) paths. Mitigate by emitting **jar file names only** (never full paths — they are
   long *and* leak the user's home directory) and capping the list with a `(+N more)` tail.
5. **Privacy**: prefer the docroot *kind* over the absolute docroot path for the same reason.
6. **Keep the two builders in sync** — they are separate code with duplicated intent today, which is
   how the env block silently stayed thin in both. Consider making the plugin pass one prebuilt
   env block instead of two `--report-*` fragments the launcher re-assembles.

## 5. Verdict

- **Necessary?** Something like it, yes — but the field you want is **the resolved ZK jar list + the
  inferred docroot kind**, not the build tool. Those two would have made the two hardest documented
  failure modes (`zkex` missing, `~./` not found) self-diagnosing from the report alone.
- **Build tool**: add it too — one cheap line, genuinely useful for reproduction and for the
  un-imported-project category — but it is a supporting detail, not the primary signal.

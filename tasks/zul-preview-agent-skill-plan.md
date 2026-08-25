# Plan — ZUL Preview for the `zul-writer` Agent Skill

## Context

`zul-writer` (agent skill) writes ZUL and validates it **statically** — XML well-formedness, XSD, attribute
placement, version compatibility. It never sees what the page looks like. Meanwhile this repo already contains a
complete, IDE-free ZUL rendering core (`zk-preview-launcher`), but it ships only inside the IntelliJ plugin ZIP.

Goal: connect the two, so that after the AI writes a `.zul` it renders it to a PNG, **looks at the image**, and
fixes the markup if it's wrong. This closes the loop that static validation cannot.

Three questions the design must answer (from `tasks/zul-preview-for-agent-skill.md`), and the answers:

| Question | Answer |
|---|---|
| How does the user obtain the launcher? | Downloaded on first use from a **zkidea GitHub Release**, SHA-256 pinned, cached in `~/.cache/zul-writer/`. ~500 KB, one time. |
| How is it invoked, and how does an image come out? | A new `scripts/preview-zul.py` in the skill: resolve classpath → start the jar → drive headless Chrome via Playwright → PNG. |
| How does the launcher get the ZK SDK classpath? | The script resolves it: Maven project → Gradle project → stock-ZK probe POM fallback. The launcher itself stays dumb (`--classpath` verbatim). |

**Two repos are involved.**
- **A** = `/Users/hawk/Documents/workspace/PLUGIN/zkidea` — publishes the launcher jar.
- **B** = `/Users/hawk/Documents/workspace/AI/agent-skill` — canonical source of the skill.
- `hawk-marketplace` is **generated output** (`build.sh` does `rm -rf plugins/`). Never hand-edit it.

**First implementation step:** copy this file to `tasks/zul-preview-agent-skill-plan.md` in repo A per the
project's task-tracking convention.

---

## Architecture

```
preview-zul.py  (repo B, the only new moving part)
  1. find a JDK 17+            → probe & verify versions; JAVA_HOME is unreliable
  2. get zk-preview-launcher-<v>.jar  → cached, else download from GitHub Release, verify SHA-256
  3. resolve ZK classpath      → --classpath | Maven | Gradle | probe POM     (cached, keyed on build files)
  4. resolve docroot           → port of DocrootResolver's 4 rules
  5. java -jar launcher --classpath … --webapp … --port 0   → read "PREVIEW_PORT=<n>"
  6. Playwright + system Chrome → GET http://127.0.0.1:<n>/<rel>.zul → wait for ZK mount → screenshot
  7. kill the JVM (3 layers), print STATUS:/SCREENSHOT:/NEXT:
```

Nothing in the launcher's Java source changes. It already does exactly what's needed.

---

## Phase A — repo A: publish the launcher jar

### A0. BLOCKER — the recorded cause of the blocked release is wrong

`tasks/todo.md` (in commit `1ea2613`) blames *"workflow scope may be required"* and prescribes
`gh auth refresh -s workflow`. **That will not fix it.** Verified live:

- Active `gh` account is `hawkhero`, which **already has** the `workflow` scope.
- `gh api repos/zkoss/zkidea --jq .permissions` → `push: false` (read-only).
- `hawkhero` is not a member of the `zkoss` org; `hawkchen` is the org account.
- Pushes and tags succeeded only because the remote is **SSH**, which bypasses the `gh` OAuth token entirely.

Fix: either `gh auth switch --hostname github.com --user hawkchen`, or — preferred — publish from GitHub Actions
using `github.token`, so no local credential is involved. Correct the note in `tasks/todo.md` while here.

Also confirmed: no zkidea release has **ever** carried an asset (latest is `v0.1.23`, `assets: []`), and repo A has
no CI workflows at all. This will be its first.

### A1. Give the launcher an independent version line

`zk-preview-launcher/build.gradle` already declares `version '0.1.0'`, currently dead (the `jar` task hard-codes
`archiveFileName`). Bump to `1.0.0` and start using it.

Independent, **not** coupled to the plugin version, because: the skill pins an exact version + SHA, and the plugin
version moves for IDE reasons that leave the launcher bytes identical; and it decouples this work from the stuck
plugin v1.0.0 release. Tag prefix `launcher-v` so it can never collide with plugin `v*` tags.

### A2. `zk-preview-launcher/build.gradle` — additive only (~30 lines)

**Do not rename the `jar` output.** Three consumers depend on the fixed name:
`ZulPreviewServerService.LAUNCHER_JAR_NAME`, the root `prepareSandbox` wiring, and
`IsolationChildProcessTest` (hard-codes `build/libs/zk-preview-launcher.jar`).

1. Inside the existing `jar { }` block add `preserveFileTimestamps = false` and `reproducibleFileOrder = true`
   — required so a rebuild from the same tag reproduces the pinned SHA-256. Do this **before** the first release,
   since it changes the bytes once.
2. Add three tasks that emit a *versioned copy* into `build/release/`:
   - `releaseJar` (Copy, `rename { "zk-preview-launcher-${version}.jar" }`)
   - `releaseChecksum` — writes `<hex>  <filename>\n`, i.e. `shasum -a 256 -c` compatible
   - `releaseLauncher` — lifecycle task depending on the above

`prepareSandbox` is untouched. `build/` is already gitignored.

### A3. New `.github/workflows/release-launcher.yml`

Triggers on `push: tags: ['launcher-v*']` plus `workflow_dispatch`. `permissions: contents: write`.
Steps: checkout → setup-java 17 (temurin) → setup-gradle → derive `version` from the tag →
`./gradlew --no-daemon :zk-preview-launcher:releaseLauncher -x test` → **assert the expected versioned filename
exists** (catches tag/`build.gradle` drift) → `gh release create … --latest=false` with both assets and the
SHA-256 in the notes.

`-x test` is deliberate: the launcher's tests spawn `mvn` subprocesses and Playwright browsers — a pre-tag local
gate, not a release-path gate.

Two first-run unknowns to watch: whether the `org.jetbrains.intellij` plugin drags in the ~1 GB IDE dependency for
a `:zk-preview-launcher` task (it should be lazy in 1.x; `setup-gradle` caches it either way), and whether the
`zkoss` org allows the explicit `contents: write` elevation.

Document the manual `gh` runbook in the README as the fallback (`gh auth switch --user hawkchen` first).

### A4. Bootstrap release — decoupled from the stuck plugin release

Land A2 + A3 on `master`, push over SSH, then `git tag -a launcher-v1.0.0 && git push origin launcher-v1.0.0`.
Actions publishes exactly two assets. The plugin's own blocked `v1.0.0` GitHub release stays a separate chore —
do not entangle them.

Resulting immutable pin for the skill:
```
https://github.com/zkoss/zkidea/releases/download/launcher-v1.0.0/zk-preview-launcher-1.0.0.jar
```

### A5. New `zk-preview-launcher/README.md`

The CLI contract stops being an internal detail once an external consumer pins the jar. Today it exists only as a
Javadoc comment on `Main.java`. Cover: what it is and who consumes it (same artifact as the plugin bundles);
**Java 17+** (an older JVM dies with `UnsupportedClassVersionError` *before* printing a port — the single most
likely integration failure); the stable surface (`--classpath`, `--webapp`, `--port`, cosmetic `--report-*`,
`PREVIEW_PORT=<n>` on stdout, then blocks); the three endpoints; what is **deliberately** unsupported (stdin,
`--help`, any exit-code contract); how to choose `--webapp` (the four docroot rules) and why the request path is
the `.zul` relativized against it; a pointer to the L-1…L-14 limitations rather than duplicating them;
javax/jakarta auto-detection; build/release commands and the version policy; `shasum -a 256 -c` verification.

---

## Phase B — repo B: `skills/zul-writer/scripts/preview-zul.py`

House style is set by the sibling `scripts/validate-zul.py`: PEP 723 header, single self-contained file, `uv run`,
`DO_NOT_TRACK`/`TRACK_URL` telemetry block (copy `track_usage_async()` verbatim, same `zul_writer` event).
Dependencies: `["playwright>=1.44"]`.

### B1. CLI and the output contract

```
uv run preview-zul.py [options] <file.zul>
  -o/--out PNG        default: <stem>.png beside the .zul
  --width/--height    default 1280x900     --full-page
  --classpath CP      skip all build-tool resolution
  --webapp DIR        skip docroot resolution
  --project DIR       pom/build.gradle root + docroot boundary
  --zk-version V      probe-POM fallback version (default 10.2.1; `-jakarta` suffix picks jakarta servlet)
  --java PATH  --launcher-jar PATH  --timeout S  --refresh
```

Env: `ZUL_WRITER_LAUNCHER_JAR`, `ZUL_WRITER_CACHE_DIR` (offline / proxied sites).

**Exit codes — the load-bearing part of the whole design.** The skill must be able to tell "your ZUL is broken"
from "I couldn't set up a preview" as one deterministic branch, or the agent will start "fixing" working markup:

| Code | Meaning | stdout |
|---|---|---|
| 0 | rendered | `STATUS: ok` + `SCREENSHOT: <abs path>` + docroot/classpath/warnings |
| 1 | **render error** — a real ZUL bug | `STATUS: render-error`, phase, message, `file:line`, `NEXT:` |
| 2 | **degraded, no preview possible** | exactly one `PREVIEW_SKIPPED: <reason>` line |
| 3 | usage error | argparse |

Exit-2 reasons: no ZK on the classpath, no project detected, no JDK 17+, no Chrome/Edge, launcher download failed,
`.zul` outside the docroot. Every non-zero path prints a `NEXT:` line — that is the actionability contract.

### B2. Classpath resolution (precedence order)

Rules ported from `src/main/java/org/zkoss/zkidea/preview/ZkClasspathFilter.java` — **read it first**:
- Pass **every** jar, not just ZK-named ones (narrowing is a documented shipped crash:
  `NoClassDefFoundError: org.slf4j.LoggerFactory`).
- ~~**Exclude the project's own class-output directories** — this is the isolation guarantee; user classes must
  never load. Dropping every non-file entry achieves this.~~
  **SUPERSEDED by `da45ffc` (#67)**, landed on master after this plan was approved: that exclusion was both
  redundant (the `UiFactory` hook is the real boundary) and harmful (a page whose `<zscript>` / `use="…"` / custom
  EL named a project class failed to render at all). **Include** the compiled-output roots, from a
  production-only enumeration so `target/test-classes` stays off. See `ZulPreviewServerService.launcherClasspath`.
- **Include** `src/main/resources`-style roots so ZK's `~./` `ClassWebResource` pages resolve.
- Order: jars, then compiled-output roots, then resource roots.

1. `--classpath` verbatim.
2. **Maven** — nearest `pom.xml` walking up. Proven recipe from
   `zk-preview-launcher/src/test/java/.../testutil/ZkClasspathResolver.java`:
   `mvn -f <pom> dependency:build-classpath -Dmdep.outputFile=<tmp> -q`.
   Prefer `./mvnw`. **Do not** override `JAVA_HOME` for this call — Maven must run on the project's JDK.
3. **Gradle** — no recipe exists; write one. A **Groovy** init script (DSL-agnostic, so it covers Kotlin DSL)
   cached at `<cache>/gradle/zk-preview-init.gradle`, registering `zkPreviewClasspath` on `allprojects`, printing
   `ZKCP\t<kind>\t<projectDir>\t<path>` lines for kinds `JAR` (union of `runtimeClasspath`, `providedRuntime`,
   `providedCompile`, `compileClasspath` — the war plugin keeps servlet-api *out* of runtimeClasspath), `RES`,
   `OUT`, and `WEBAPP` (the war plugin knows the real docroot, so we don't have to guess).
   Invoke `<gradlew|gradle> -p <dir> -I <init> -q --console=plain zkPreviewClasspath`.
   Multi-project: keep the lines whose tagged `projectDir` is the longest ancestor of the `.zul`.
   Retry once with `--no-configuration-cache` if the configuration cache rejects it. Do **not** pass `--no-daemon`
   (a warm daemon turns 60 s into ~3 s). Any failure → warn on stderr and fall through to (4).
4. **Stock-ZK probe POM** — generalize `ZkClasspathResolver.JAVAX_PROBE_POM`: `org.zkoss.zk:zkbind:<v>` (pulls the
   whole CE stack transitively) + the matching servlet API, repo `https://mavensync.zkoss.org/maven2`.

Report which source was used in the success output (`CLASSPATH: maven, 47 jars …` / `probe (gradle failed: …)`)
so the agent knows whether it saw the project's ZK or stock ZK.

**Presence gate caveat (spec L-12):** the plugin's `ZK_ARTIFACT_PREFIXES` list misses `calendar-*`, `ckez-*`,
`pivottable-*`. Gate only on the `zk-*` **core** being present; never use that list to filter what gets passed.

### B3. Caching (`~/.cache/zul-writer/`)

```
launcher/<version>/zk-preview-launcher.jar
gradle/zk-preview-init.gradle
classpath/<key>.json          ← jar list, resource roots, webapp hint, project root, resolver kind
java.json
```
Key = sha256 of resolver-kind + schema-version + (path, size, mtime_ns, content-hash) of the nearest build file
**and every ancestor `pom.xml`** (parent-POM `dependencyManagement` edits), plus `settings.gradle*`,
`gradle.properties`, `libs.versions.toml`, `gradle-wrapper.properties` for Gradle.
Invalidate on: key change, schema bump, **any cached jar path no longer existing** (a `Path.is_file()` sweep on
load — catches a wiped `~/.m2`), a 7-day TTL (catches `-SNAPSHOT` drift no hash can see), or `--refresh`.
Write atomically (`tmp` + `os.replace`) — two agent runs can race.

Launcher download: stream into `<target>.part.<pid>` feeding `hashlib.sha256`, verify, then `os.replace`. Never
leave a partial at the real path. Distinguish offline / HTTP 404 (stale pin) / checksum mismatch in the message.

### B4. Docroot + server lifecycle

Port `src/main/java/org/zkoss/zkidea/preview/DocrootResolver.java`'s four rules (WAR webapp → Spring-Boot
classpath `web` → content root → file parent). Boundary roots come from `--project`, else the build-file dir, else
`git rev-parse --show-toplevel`, else unbounded. A Gradle `WEBAPP` line short-circuits rule 1.
**Python hazard:** `Path("/").parent == Path("/")` where Java's `getParent()` returns `null` — every walk-up loop
needs `if c.parent == c: break` or it spins forever.

**The `.zul` must be inside the docroot** — enforced server-side (`MockServletContextCore` rejects escapes), not
just a URL nicety. A `../` path cannot work. This is the *most likely* agent case (a file written to a scratch
dir), so exit 2 with a message naming `--webapp <dir>` as the one-flag fix.
Also reject non-`.zul` extensions up front: `PreviewHttpServer.handle` only renders `path.endsWith(".zul")` and
returns a bare `text/plain` 404 otherwise.

Lifecycle, mirroring `ManagedPreviewServer`:
- `Popen` with `start_new_session=True` (`CREATE_NEW_PROCESS_GROUP` on Windows) so the whole group is killable.
- **Pump both pipes on daemon threads.** Draining stderr is not optional — a chatty ZK bootstrap can fill the
  64 KB pipe buffer and wedge the JVM before it prints the port, which looks exactly like a startup timeout.
- Handshake loop on `PREVIEW_PORT=(\d+)`; on `proc.poll() is not None` before a port, report the **stderr tail**
  (bounded deque, 200 lines). The commonest cause is no ZK core jar → `VariantDetector` throws in `main`.
- Kill guarantee in three layers: `with` block, `atexit`, and SIGINT/SIGTERM handlers that `sys.exit` so the
  `with` unwinds. Kill the process *group*, SIGTERM (the launcher has a shutdown hook) then SIGKILL.

### B5. Screenshot and error scraping

Browser: `pw.chromium.launch(channel="chrome")` → fall back `"msedge"` → bundled chromium → exit 2 telling the
user to install Chrome or run `python -m playwright install chromium`. `uv` supplies the Python package only,
never a browser — which is exactly why `channel=` is the right call.

**ZK-ready wait**, verified against ZK 10.2.1's `web/js/zk/index.src.js` (`zk.booted = true; zk.mounting = false`
at the end of `mount_.mtBL1`); these flags are stable client API back to ZK 5, so the javax/ZK-9 variant works too:

```js
() => { const z = window.zk;
        return !!z && z.booted === true && z.mounting !== true && !z.loading && z.processing !== true; }
```
Two-stage: first `wait_for_function("typeof window.zk !== 'undefined'", 5s)` — if that times out we're on the
error page or a pure-HTML page, and we fall through and capture anyway. Then the ready predicate, then
`networkidle` (5 s, suppressed) and `document.fonts.ready`. Screenshot with `animations="disabled"`,
`caret="hide"` so repeat captures are comparable if the agent diffs before/after.

The launcher-test recipes (`BrowserEquivalentTest` waits on `.z-window`, `SyntaxCorpusBrowserSampleTest` on a text
marker) are **fixture-specific and not reusable** — do not copy them.

Collect `pageerror`, console `error`, and `response.status >= 400` on `/zkau/web/*`. A 404 on a ZK asset produces a
*visually plausible but wrong* screenshot (missing add-on jar) — surface it as a warning even on success.

HTTP 500 → scrape `ErrorPageRenderer`'s `.phase`, `pre.msg`, `.loc`, `details.trace pre`. `ErrorPageRendererTest`
locks the structure but **not** the class names, so fall back to `page.inner_text("body")[:1500]` — free
robustness, since we're driving a real browser. **Still save the PNG** (the agent will look where it was told),
but label it `[ERROR PAGE — this is not your UI]` and exit 1.

### B6. Java detection — probe, don't trust

`JAVA_HOME` and `PATH` are demonstrably wrong on this machine (both Java 11, while five JDK 17+ installs exist).
Candidates in order: `--java` → `ZUL_WRITER_JAVA` → `/usr/libexec/java_home -v 17+` (macOS) → `JAVA_HOME` → globs
(`/Library/Java/JavaVirtualMachines/*`, `/usr/lib/jvm/*`, `~/.sdkman/candidates/java/*`, `~/.gradle/jdks/*`,
`C:/Program Files/*/*`) → `which java`. **Verify each by running `java -version`** (it writes to *stderr*; map
`1.8.0_x` → 8). Newest `>= 17` wins; cache in `java.json` and revalidate cheaply.
Scope the discovered JDK to the launcher `Popen` **only** — never export it to `mvn`/`gradlew`, which need the
project's own JDK.

Absent-JDK message must name what it *did* find, plus install commands and the `--java` escape hatch.

### B7. Implementation sequencing

Skeleton + reporter + `--launcher-jar` → Java detection + `Launcher` handshake/kill + `--classpath`/`--webapp`
(first end-to-end render) → Playwright + 500 scraping → docroot port → Maven + filter + cache → Gradle + fallback
→ probe POM → download + SHA pin (**gated on Phase A**).

Single file, one class (`Launcher`), four resolver *functions* behind one `resolve_classpath()`. No strategy
pattern, no plugin abstraction.

---

## Phase C — repo B: SKILL.md Step 5

### C1. Portability rule (verified against `hawk-marketplace/patch-portability.py`)

The rewrite pairs are `~/.claude/skills/zul-writer/` → `${CLAUDE_PLUGIN_ROOT}/skills/zul-writer/` and require the
**trailing slash**. A bare `` `~/.claude/skills/zul-writer` `` survives untouched and would ship a wrong path to
plugin users. Write every path with the trailing slash.

### C2. Edits to `skills/zul-writer/SKILL.md`

- Frontmatter `description:` — currently sells a "4-step workflow" and never mentions rendering, so
  "screenshot this zul" won't trigger the skill. Make it 5-step and put the **ZUL → image** direction in *both*
  the capability sentence and the trigger sentence (it's now a standalone entry point, not only a tail step).
- `version: "1.0.0"` → `"1.1.0"`. **`scripts/validate-zul.py` hard-codes `"skill_version": "1.0.0"`** in its
  tracking payload with nothing binding the two — bump it together or telemetry reports a dead version.
- Workflow Overview: `4-step` → `5-step` (lines 19 and 26), add item 5.
- New **Step 5: Preview & Self-Review** after Step 4, structured like Step 3 (invocation block, prerequisites,
  then guidance), containing:
  - the `uv run <skill-base-dir>/scripts/preview-zul.py --out <png> <zul>` invocation, the one-time ~500 KB jar
    download, and the **Java 17+ / Chrome or Edge** prerequisites;
  - **read the PNG**, compare against the Step 1 answers — and against the original mockup if the user started
    from an image;
  - **the skip branch**: exit 2 / `PREVIEW_SKIPPED:` is *not* a defect in the ZUL. Report it in one line and
    finish normally. *Never describe a screenshot you did not see; never let a skipped preview stand in for a
    passed one.*
  - **What to fix** — structure, not pixels and not data: error page (exit 1); "unknown component" (missing
    add-on jar — ask, don't delete); missing/extra sections; wrong region placement (tab content outside its
    `<tabpanel>`, sidebar under the content); wrong component kind; clipped/overflowing/overlapping layout, an
    `hflex`/`vflex` that visibly didn't take; raw unstyled HTML where a ZK component was intended.
  - **What you cannot judge** — the real guardrail. Every item maps to a documented limitation in
    `doc/zul_preview_spec.md` §4, and an agent that doesn't know them will "fix" correct markup:

    | Spec | Told to the agent as |
    |---|---|
    | **L-2** | Bound values render as dimmed expression text; bound `<grid>`/`<listbox>`/`<tree>` show placeholder rows; anything a Composer/ViewModel would populate is absent. **The single most important one.** |
    | **L-1** | First paint only — no clicks, paging, sorting, tree expansion, selection, event-opened windows. Client-side `w:` handlers *do* run. |
    | **L-7** | Docroot is inferred; assets outside it 404 in the preview but load fine on a real server. |
    | **L-3** | (on the *fix* side) `<zscript>` does run — a zscript failure is a genuine page error. |
    | **L-13** | (in the reference file) hierarchy `UiException`s carry no line/column — a missing line number is not a clue worth chasing. |

    Plus: theme-dependent colour/spacing when the theme jar isn't a dependency; exact spacing, fonts, sub-pixel
    alignment, near-miss colours; sample data differing from the mockup — compare the *shape*, not the values.
    L-4, L-6, L-8…L-11, L-14 are IDE-specific or invisible in a CLI screenshot — **excluded on purpose**, so the
    list the agent must retain stays short.
  - **How many rounds** — at most **two fix rounds, three renders**. Re-run Step 3 validation before each
    re-render (a Step 5 edit can break well-formedness). "Good enough" is a checklist, not a judgement: every
    Step 1 requirement visibly present, in the right region, in the right kind of component, nothing clipped or
    overlapping. Never edit for a cosmetic difference alone. If a defect survives both rounds, stop and tell the
    user what it is and what was tried. Always report the final image path.

### C3. New `references/preview-guidelines.md`

Split on *when the agent needs it*. The judging guidance stays **inline** in SKILL.md — it's needed while looking
at the image, and if it lived in a reference the agent might never open it (silent, expensive failure: "fixing" a
correct placeholder into a hard-coded literal). ~35 lines against a 186-line file is affordable, and Step 3
already keeps its layer list inline.

The reference takes what's only needed **when something goes wrong**: how the classpath is resolved and how to
supply one by hand; the four docroot rules and the request-path derivation (what to check when the page renders
but `~./` resources 404); every `PREVIEW_SKIPPED:` reason with its remedy; reading an error page (phase,
`file:line`, the L-13 caveat); previewing a `.zul` outside any project; the `ZUL_WRITER_*` overrides for offline
and proxied environments; pointers to `doc/zul-preview-feature.md` and `doc/zul_preview_spec.md` §4.

### C4. CI smoke test in `.github/workflows/validate-zul.yml`

`scripts/**` is already inside the path filter, so `preview-zul.py` **will** trigger the job — and it will pass
while testing nothing, because `test/run-regression.py` only shells out to `validate-zul.py`. That's a green light
with no signal.

Do **not** attempt a real render in CI (JDK + ZK jars from mavensync + a GitHub download + a headless browser =
minutes and several flaky network dependencies; everyone learns to ignore the workflow). Test the ~3-second paths
that need no Java and no network — `--help` parses, and a no-ZK project degrades **cleanly**:
exit 2, a `PREVIEW_SKIPPED:` line, and **no PNG written**. That also pins the ordering "check for ZK before
downloading anything", which is worth locking. `DO_NOT_TRACK: "1"` is already set at job level.

---

## Phase D — regenerate the marketplace mirror

Run `/Users/hawk/Documents/workspace/AI/hawk-marketplace/build.sh`. Never hand-edit
`hawk-marketplace/plugins/zk-framework/skills/zul-writer/`. Afterwards confirm the generated copy shows
`${CLAUDE_PLUGIN_ROOT}/skills/zul-writer/scripts/preview-zul.py` and that **no bare `~/.claude/...` survived** in
the new Step 5 text.

---

## Verification

**Phase A**
1. `./gradlew :zk-preview-launcher:releaseLauncher` → `build/release/` holds the versioned jar + `.sha256`.
2. Run it twice from clean; the SHA must match (reproducibility).
3. After tagging: `curl -sL <release URL> -o /tmp/l.jar && shasum -a 256 /tmp/l.jar` matches the published sum.
4. `./gradlew build` and the plugin's Layout Preview still work — proves `prepareSandbox` is unaffected.

**Phase B** — against the in-repo sample projects, which cover both layouts and both servlet variants:
5. `manual-test/` (Maven, ZK 10.1.0-**jakarta**, WAR layout): preview `src/main/webapp/preview/*.zul` → exit 0,
   a PNG showing real widgets.
6. `manual-test-springboot/` (Spring Boot, `src/main/resources/web/`): exercises docroot rule 2.
7. A `.zul` in an empty scratch dir with no project → probe-POM path renders stock ZK; then delete `mvn` from
   `PATH` → clean exit 2 with a `PREVIEW_SKIPPED:` reason and **no PNG**.
8. A deliberately broken `.zul` (unknown component) → exit 1, phase/message/`file:line`, error-page PNG saved and
   labelled.
9. A `.zul` outside its docroot → exit 2 naming `--webapp`.
10. `ps` / Activity Monitor after each run **and after a Ctrl-C mid-render** → no orphaned JVM.
11. Second run on the same project is materially faster (classpath cache hit); `touch pom.xml` re-resolves.
12. Point `--java` at a JDK 11 → the `no-jdk17` message, not an `UnsupportedClassVersionError` stack trace.
13. Create a Gradle war/java project (there is none in-repo — this path has no existing coverage and is the
    highest-risk piece); verify the init script resolves, then break it deliberately and confirm the fall-through
    to the probe POM says so in the output.

**Phase C/D**
14. End-to-end through the installed plugin: ask the skill to build a page, and confirm it renders, reads the
    image, and either reports "good enough" or makes exactly one structural fix — and that it does **not**
    "fix" a dimmed `vm.*` placeholder or bound-model placeholder rows (the L-2 trap).
15. `build.sh` then grep the generated copy for `~/.claude` — must be zero hits in the new text.

---

## Risks

| Risk | Handling |
|---|---|
| **`gh` account, not token scope** (A0) — the recorded fix is wrong and will waste a cycle | Publish from Actions with `github.token`; correct `tasks/todo.md` |
| Repo A's first-ever CI workflow: org token policy, possible 1 GB IDE download | Explicit `permissions: contents: write`; `setup-gradle` caching; watch the first run |
| **Gradle classpath resolution is entirely new** — no in-repo precedent, and no Gradle sample project to test against | Verified fall-through to the probe POM with the reason stated in the output; test 13 |
| Playwright `channel="chrome"` — user may have neither Chrome nor Edge | Cascade to bundled chromium, then a clean exit 2 with the `playwright install` command |
| ZK-ready predicate drifts in a future ZK | Bounded wait, then capture anyway with a warning — never a hard failure |
| Error-page scraping breaks (class names aren't test-locked) | `inner_text("body")` fallback |
| Agent "fixes" correct MVVM placeholders (L-2) | The *What you cannot judge* list is inline in SKILL.md, not in a reference; verification 14 targets it |
| Skill can't ship before the release exists | `--launcher-jar` / `ZUL_WRITER_LAUNCHER_JAR` works from day one; the pin is two constants to edit |

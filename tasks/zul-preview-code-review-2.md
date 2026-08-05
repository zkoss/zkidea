# ZUL Preview — code review #2 (all preview classes)

## L1 · Executive summary

Second full review of the ZUL Preview feature — all 10 plugin-side classes
(`org.zkoss.zkidea.preview`) and all ~35 launcher classes (`org.zkoss.zkpreview`), done by
three parallel reviewers (plugin integration / launcher runtime+HTTP+engine / servlet mocks+cores),
then the Critical/Major findings were re-verified by hand against the source.

**Verdict:** the feature is well-built and unusually well-documented about its own past bugs
(review #1's U1–U3, D4, M2/M3). The recent Bridge (mocks) and Template Method (engines) refactors are
clean, and **jakarta/javax behavioural parity is now perfect** (verified: 0 semantic diff across all 6
mock pairs and both engine subclasses). What remains is not a pile of broken code — it is a cluster of
**diagnosability** failures: three ways the preview can fail while telling the developer nothing, or
telling them something actively misleading.

| Severity | Count | Findings |
|---|---|---|
| **Critical** | 3 ✅ all fixed | R2-CRIT1 unsynchronised `ScopedZkClassLoader` · R2-CRIT2 `resource()` swallows all errors · R2-CRIT3 missing `.onError` (pane stuck loading) |
| **Major** | 3 | R2-MAJ1 unbounded docroot scan · R2-MAJ2 `getMimeType` always null *(unconfirmed)* · R2-MAJ3 external-link `userGesture` bypass |
| **Minor** | 10 | R2-MIN1 … R2-MIN10 (see L2) |

### About the IDs

Findings are **`R2-<LEVEL><n>`**, where the level is `CRIT` / `MAJ` / `MIN` and `n` is the finding's
rank *within* that level by end-user impact — so `R2-CRIT1` is the single most damaging finding in the
review and the whole set reads top-down as the fix order.

Two things to know about the scheme:

- **The `R2-` prefix is deliberate.** Review #1's findings are already cited as bare `M1`/`M2`/`M3` in
  this document, in source comments (`ZulPreviewFileEditor.java`), and in three commit messages
  (`… (review M1)`, `… (review M2)`, `… (review M3)`). Unprefixed `M1` here would silently collide with
  those in `git log`. Likewise `CRIT`/`MAJ`/`MIN` rather than `C`/`M`/`m`: a scheme where major and
  minor differ only by letter case is a trap in commit messages and case-insensitive greps.
- **Levels were re-derived from user impact**, so severity and rank now agree. Previously the two
  disagreed visibly — a Minor (`m7`) outranked three Majors. Five findings moved; see the mapping below.

### Old → new ID mapping

Earlier drafts of this review, and the discussion around it, used the reviewers' original IDs.

| New ID | Was | Level change | Why it moved |
|---|---|---|---|
| **R2-CRIT1** | C2 | — | |
| **R2-CRIT2** | J5 | Major → **Critical** | Permanently unstyled preview with *zero* diagnostics anywhere; also the multiplier that hides every other asset failure |
| **R2-CRIT3** | J2 | Major → **Critical** | Total dead end: no message, no retry, no recovery, nothing to report |
| **R2-MAJ1** | J4 | — | |
| **R2-MAJ2** | m7 | Minor → **Major** *(provisional)* | If the assumption behind it is wrong it is a Critical; unconfirmed either way, so it cannot sit in Minor |
| **R2-MAJ3** | J1 | — | |
| **R2-MIN1** | J3 | Major → **Minor** | Cosmetic — an error badge; nothing actually malfunctions |
| **R2-MIN2** | m2 | — | |
| **R2-MIN3** | m1 | — | |
| **R2-MIN4** | J6 | Major → **Minor** | Cannot fire today; purely latent |
| **R2-MIN5** | m3 | — | |
| **R2-MIN6** | m4 | — | |
| **R2-MIN7** | C1 | **Critical → Minor** | Only threat model is an untrusted project, where `<zscript>` already grants RCE in the launcher JVM — the symlink escape is a strict downgrade from what the attacker has. Defence-in-depth, not a vulnerability |
| **R2-MIN8** | m5 | — | |
| **R2-MIN9** | m6 | — | |
| **R2-MIN10** | m8 | — | |

**How to read the *User impact* lines:** every finding in L2 carries one, written for the audience
that actually hits it — a Java developer building a ZK app, previewing `.zul` files inside IDEA, who
has no visibility into the launcher JVM and will attribute anything odd to their own code, their ZK
version, or their Maven/Gradle setup.

## L1b · Ranked by end-user impact

Ranked by **expected harm to a ZK developer** — roughly
`how often it fires × how bad the moment is × how hard it is to recover from or diagnose`.

| # | ID | Finding | How often | What the developer experiences | Recover? | Diagnose? |
|---|---|---|---|---|---|---|
| 1 | **R2-CRIT1** | classloader race | Occasional→common (every cold preview is a dice roll; worse on many-core) | Preview fails at random with `attempted duplicate class definition … org/zkoss/zul/Grid` | Yes — save/refresh | **Actively misleading** — reads as a duplicate ZK jar; sends them into `mvn dependency:tree` and `~/.m2` |
| 2 | **R2-CRIT2** | `resource()` swallows errors | Whenever *any* asset fetch fails — the multiplier that makes other failures invisible | Page renders as an unstyled skeleton; no widgets, no ZK CSS/JS | No | **No.** No log, no stderr, no distinguishing status, no error card → the Report link never even appears |
| 3 | **R2-CRIT3** | missing `.onError` | Rare (needs a `resolveTarget` throw) but unbounded | Pane reads *"Starting ZK preview server…"* forever | No — only closing/reopening the tab, undiscoverable | **No.** Nothing to read, search, or paste into a report |
| 4 | **R2-MAJ1** | unbounded docroot scan | Occasional — module-less `.zul`: project still importing, sample file dragged in, folder not marked as a root | Every `<include>`, CSS and image broken on a layout they know is correct | Self-heals once the module imports | No — docroot never shown; classic "works for my colleague" |
| 5 | **R2-MAJ2** | `getMimeType` → null | **Unknown — confirm before closing** | If ZK does fall back to it: assets served with no `Content-Type`, browser refuses them | No | No — identical symptom to R2-CRIT2 |
| 6 | **R2-MAJ3** | `userGesture` bypass | Occasional — `index.zul` bouncing to SSO, `<meta refresh>`, redirecting third-party script | The editor tab becomes a web browser showing an Okta/Azure login page | Save the `.zul` again, or reopen the tab — neither discoverable (no toolbar, no Back) | Visible, but reads as "the plugin is broken" |
| 7 | **R2-MIN1** | disposal race | Occasional — Ctrl+S then immediately Ctrl+W | Red **IDE Internal Error** badge naming ZKIdea | N/A — nothing actually broke | Cosmetic only, but reads as instability |
| 8 | **R2-MIN2** | unescaped ``` fence | Rare — needs a ``` ``` ``` in the `.zul` *and* a click on Report | Their GitHub issue renders as garbage; environment block swallowed | Edit the issue by hand | Obvious to them, costly for the maintainer |
| 9 | **R2-MIN3** | first-not-nearest root | Rare — nested content roots (Maven aggregator with a child WAR inside the parent root) | Same broken-resources symptom as R2-MAJ1 | No | No |
| 10 | **R2-MIN4** | hardcoded UTF-8 | Never today (nothing sets a non-UTF-8 encoding) | Latent: 亂碼 in previews for legacy Big5 / Shift_JIS apps — a real ZK constituency | — | — |
| 11 | **R2-MIN5** | `pathSeparator` join | Effectively never (Maven/Gradle paths have no `:`) | `NoClassDefFoundError` for a ZK class visibly present in their `pom.xml` | — | Maximally confusing if it ever fires |
| 12 | **R2-MIN6** | `resourceFile(null)` NPE | Not observed | Error page with an NPE instead of a clean 404 | — | — |
| 13 | **R2-MIN7** | symlink escape | **No impact on legitimate users** | Nothing. Threat model already dominated by `<zscript>` RCE | — | — |
| 14 | **R2-MIN8** | `setDateHeader` no-op | No impact — headers aren't forwarded today | Nothing | — | — |
| 15 | **R2-MIN9** | engine never closed | No impact — each server is a separate JVM, killed on project close | Nothing | — | — |
| 16 | **R2-MIN10** | "Jakarta" javadoc casing | No impact — contributor-facing only | Nothing | — | — |

**Recommended fix order:** straight down the IDs — **R2-CRIT1 → CRIT2 → CRIT3 → MAJ1 → MAJ2 → MAJ3**,
except that **R2-MAJ2 should be *confirmed* first**: a positive answer promotes it to Critical and
changes what R2-CRIT2's logging needs to surface. Then the Minors as cleanup; R2-MIN6 … R2-MIN10 are
debt, not user-facing. Note the three Criticals are all *diagnosability* failures as much as functional
ones — in each case the developer's worst problem is not that something broke, but that nothing tells
them what. Fixing them as one batch closes the entire "preview is broken and I can't tell why" class of
report.

**A note on the ranking metric:** R2-CRIT3 is the worst single *moment* (a total dead end) but fires
rarely, so R2-CRIT1 and R2-CRIT2 outrank it on aggregate harm. Conversely R2-MIN7 is a genuine
security-shaped defect that no real user will ever feel. Each finding is small and independently
testable; suggest TDD (failing test first) per the usual rule.

## L2 · Findings

### Critical

**R2-CRIT1 · `ScopedZkClassLoader.loadClass` drops the per-class-name lock → concurrent duplicate `defineClass`** *(verified — ✅ FIXED)*
`ScopedZkClassLoader.java:28-49`.
For `org.zkoss.*` names the override does `findLoadedClass` → `findClass` (→ `defineClass`) with **no**
`synchronized (getClassLoadingLock(name))`, unlike the JDK's own `ClassLoader.loadClass`. ZK loads
component/util classes lazily on the request thread via the thread-context classloader (the single
shared `zkLoader`), and `PreviewHttpServer` dispatches concurrent requests on a fixed thread pool
(`PreviewHttpServer.java:54`) — a single page load alone fans out into many parallel `/zkau/web/*` GETs
against the same engine. Two threads that are both first to touch the same not-yet-defined class both
call `defineClass` → the JVM throws `LinkageError: attempted duplicate class definition`, surfacing
(via `Method.invoke`) as an intermittent, unreproducible render failure on whichever request loses the
race. Note the `catch (ClassNotFoundException ignored)` does **not** absorb it — `LinkageError` is an
`Error` and propagates.

> **User impact — high, and actively misleading.** The preview fails *sometimes*: the dev opens a
> `.zul`, gets an error card or a half-rendered page, hits save, and the second attempt works. Nothing
> in their code changed, so the feature reads as flaky. Worse, the message they see says
> `attempted duplicate class definition for name: "org/zkoss/zul/Grid"` — which to a Java developer
> looks exactly like a **corrupt jar or a duplicate ZK version on the classpath**. The predictable
> reaction is to go re-import Maven, run `mvn dependency:tree`, wipe `~/.m2`, or file a bug against ZK
> itself — hours spent on a problem that is entirely inside the plugin. Most likely on a *cold* first
> preview (nothing cached yet, maximum parallel class loading) and on many-core machines, i.e. exactly
> the first-impression path and the developer machines most likely to belong to senior users.

*Fix:* wrap the method body in `synchronized (getClassLoadingLock(name)) { ... }`, mirroring the JDK.

✅ **Fixed.** Child-first branch wrapped in `synchronized (getClassLoadingLock(name))`, plus
`registerAsParallelCapable()` in a static initialiser so the lock stays per class name rather than
degrading to per loader (otherwise the fix would serialise the whole `/zkau/web/*` burst).
Test `ScopedZkClassLoaderConcurrencyTest` — 8 threads (matching `HANDLER_THREADS`) barrier-released
into a first touch of the same class, over every `org.zkoss.*` class in the launcher's own output,
4 rounds each. **Red: 1074 of 1280 calls threw the predicted `LinkageError`. Green: 0.**

**R2-CRIT2 · `resource()` collapses every failure into a blank, unlogged 404** *(verified — promoted from Major; ✅ FIXED, one half deferred)*
`AbstractRenderEngine.java:83-99` (`resource`).
Both `if (status >= 400) return ResourceResult.notFound();` and the outer `catch (Exception e) { return
ResourceResult.notFound(); }` produce the identical empty 404 a plain typo'd path would get. Unlike
`renderZul` (which unwraps `InvocationTargetException`, classifies via `ErrorMapper`, and renders a
diagnostic page), a broken JS/CSS/asset fetch leaves zero trace — no log line, no distinguishing
status.

> **User impact — this is the "my preview looks unstyled / like raw HTML" report, and it is
> undebuggable by design.** The `.zul` itself renders fine, so no error card appears; what fails is the
> `/zkau/web/*` fetch of ZK's own CSS and JS. The dev sees an unstyled skeleton of their page and has
> **nowhere to look**: nothing in `idea.log`, nothing on the launcher's stderr, no distinguishing HTTP
> status, and no error card — so the "Report this issue on GitHub" flow never triggers either. Compare
> with a real deployment, where a missing asset is one glance at the browser's Network tab; here the
> browser is embedded and the dev has no devtools habit for it. This is also the finding with the
> highest *support* cost, not just user cost: every report it generates arrives with zero diagnostics
> attached, so it can't be triaged remotely.

*Fix:* log the exception (and the real status/body on ≥400) to stderr before returning `notFound()`,
and/or propagate the real status instead of always synthesising 404.

✅ **Fixed (launcher half).** The two outcome branches of `resource()` are now platform-free
package-visible seams — `resourceOutcome(pathInfo, status, contentType, body)` and
`resourceFailure(pathInfo, cause)` — that emit a `[zk-preview]` stderr line naming the path plus the
real status and body snippet, or the real exception with `InvocationTargetException` unwrapped the way
`renderZul` already does. The *served* outcome is deliberately unchanged (still a 404): a failed asset
must not paint a ZK error body into a `<script>`/`<link>` slot. Test
`ResourceFailureDiagnosticsTest` (4 cases, incl. "a healthy fetch logs nothing").

⚠️ **Deferred — the "nothing in `idea.log`" half.** `ManagedPreviewServer` captures helper-JVM stderr
into a bounded `stderrTail` but only surfaces it when the process dies before reporting a port, so for
a *running* server these lines still never reach the developer. Forwarding all helper stderr to
`idea.log` is a separate call: ZK bootstraps through `java.util.logging` → stderr, so it would be
chatty (the `STDERR_TAIL_LIMIT` cap exists for exactly that reason). Needs a decision on level and
filtering; not taken unilaterally.

**R2-CRIT3 · `preparePreview` wires no `.onError` → pane stuck on "Starting…" forever** *(verified — promoted from Major; ✅ FIXED)*
`ZulPreviewServerService.java:79-82`.
`ReadAction.nonBlocking(() -> resolveTarget(zulFile)).expireWith(this).submit(...).onSuccess(...)` has
no error handler. If `resolveTarget` throws — any `RuntimeException`, e.g. a non-local `VirtualFile`
(a `.zul` opened from inside a jar), a `docroot.relativize(zulPath)` mismatch, or an already-disposed
module during a concurrent Gradle/Maven re-import — the promise rejects silently, `onReady` never
fires, and the editor sits on `CARD_LOADING` with no error card / Report link / retry — the same U2
"stuck loading" mode `startGuarded` protects against one step later.

> **User impact — the single worst failure mode in the feature, because it's a dead end.** The pane
> shows *"Starting ZK preview server…"* forever. Every other failure path in this code was carefully
> built to end in an explanatory card plus a "Report this issue on GitHub" link; this one ends in
> nothing. The dev gets no message to read, no text to search for, nothing to paste into a bug report,
> and no retry — closing and reopening the tab is the only recovery, and there is no hint that it would
> help. For a Java developer the natural conclusion is "the preview server can't start / this plugin
> doesn't work", and the most likely next action is uninstalling the feature rather than reporting it.
> Two lines of `.onError` convert a dead end into the diagnosable path the rest of the class already
> provides.

*Fix:* add `.onError(ex -> deliverResult(PreviewResult.error(rootMessage(ex)), onReady))`.

✅ **Fixed.** Both outcomes now go through a package-visible `wireResolveOutcome` seam — the same
platform-free shape as the existing `startGuarded` seam for U2 — with `.onError` delivering
`PreviewResult.error(rootMessage(ex))`, so a `resolveTarget` throw ends in the explanatory card plus
Report link the rest of the class already provides. Cancellation needs no special case: the promise is
`expireWith`'d on this project-level service, so it can only be cancelled at project close, by which
point the editor is disposed and `onReady` is already a no-op. Test `ResolveFailureDeliveryTest`
(3 cases); red verified by stripping `.onError` back off the seam, not just by the missing symbol.

### Major

**R2-MAJ1 · Empty `boundaryRoots` makes the docroot scan unbounded to filesystem root** *(verified)*
`DocrootResolver.java:87-97` (`withinBoundary`) + `ZulPreviewServerService.java:208`.
`resolveTarget` passes `contentRoots = List.of()` whenever the `.zul` has no owning module;
`withinBoundary` then returns `true` for every ancestor, so the WEB-INF/`webapp` scan walks from the
file's parent all the way to `/`. A module-less `.zul` whose distant ancestor is named `webapp` (or
contains a `WEB-INF`) — e.g. a `~/webapp/...` checkout folder — gets that far-away directory as the
docroot, producing a wrong `--webapp` argument. (The javadoc documents the unbounded case as
back-compat, but it defeats the class's stated anti-hijack guarantee for module-less files.)

> **User impact — "it previews fine for my colleague but not for me."** Hits `.zul` files with no
> owning module, which is a *first-five-minutes* situation more often than it sounds: a project still
> importing, a sample `.zul` dragged in from a downloaded ZK demo, a file under a folder never marked
> as a source/content root, or a file opened via Recent Files from a different project. The launcher
> gets the wrong `--webapp`, so the page either 404s or renders with every `<include>`, CSS and image
> broken — a layout the dev knows is correct appears mangled. The giveaway (a bizarre docroot) is never
> shown in the UI, so it's unattributable, and it fixes itself once the module imports — the classic
> shape of a bug that gets reported as "flaky" and closed as unreproducible. Secondary: the scan does a
> `Files.isDirectory` on every ancestor up to `/`, which is a visible stall on a network-mounted home
> directory.

*Fix:* when `boundaryRoots` is empty, skip the unbounded scan — go straight to the classpath-web-root
check and the `parent` fallback.

**R2-MAJ2 · `getMimeType` always returns null** *(UNCONFIRMED — level is provisional; promoted from Minor)*
`MockServletContextCore.java:125-127`.
Probably benign: ZK's `DHtmlUpdateServlet` sets content types itself. But nobody has confirmed that ZK
never falls back to `ServletContext.getMimeType` for asset content-typing, and the consequence if it
does is severe enough that it cannot sit in Minor while unanswered. **This is a question to resolve,
not a fix to schedule** — resolve it before touching R2-CRIT2, since a positive answer changes what
that logging has to surface.

> **User impact — none if the assumption holds; Critical if it doesn't.** Assets served with no
> `Content-Type` are refused by the browser's strict MIME checks, so the dev gets the same permanently
> unstyled preview as R2-CRIT2 — except deterministically, for every project, rather than
> intermittently. Same total absence of diagnostics.

*Fix:* first confirm (trace ZK's asset-serving path); then either close as not-applicable, or implement
`getMimeType` from a small extension→type table and re-level to Critical.

**R2-MAJ3 · External-link handler's `userGesture` clause lets non-gesture navigations escape in-pane** *(verified — follow-up to review #1's M3)*
`ZulPreviewFileEditor.java:188` (`onBeforeBrowse`).
`if (userGesture && http/https && !isLoopbackPreviewUrl(url))` — a script-driven `location.href=`,
`<meta http-equiv="refresh">`, or an HTTP redirect (all `userGesture == false`) to an external URL
falls through to `return false` and loads **inside** the JCEF pane, replacing the render — exactly what
the handler exists to prevent. Loopback URLs already return `false` regardless of gesture, so the
`userGesture` clause has no upside, only this bypass.

> **User impact — the preview tab silently turns into a web browser.** The realistic trigger is
> mundane, not adversarial: an `index.zul` that bounces to a corporate SSO page, a `<meta refresh>`
> landing page, or any third-party widget script that redirects. Instead of their layout, the dev is
> staring at an Okta/Azure login form *inside the IDE editor tab*. There is **no toolbar, no back
> button and no reload action** on the preview editor (`ZulPreviewFileEditor` builds only card panels),
> so the only ways out are: save the `.zul` again (the refresh listener re-issues `loadURL(previewUrl)`)
> or close and reopen the tab. Neither is discoverable. Secondary concern worth naming for an
> enterprise audience: the IDE just made an un-gestured request to an external host from inside the
> editor — some corporate security teams will care about that on its own.

*Fix:* drop `userGesture`; bounce/cancel any navigation to a non-loopback http(s) URL (also consider `isRedirect`).

### Minor

**R2-MIN1 · Debounced reload isn't guarded by `disposed` → `loadURL` on a disposed browser** *(verified — demoted from Major)*
`ZulPreviewFileEditor.java:236-241` (`installRefreshListener`).
The reload lambda guards only `browser != null && previewUrl != null`; its sibling `startPreview`
callback (line 124) correctly also checks the `volatile boolean disposed` flag. A save fired as the tab
closes can have its `invokeLater` runnable already dispatched before `dispose()` runs, so it executes
afterward and calls `browser.loadURL(...)` on an already-disposed `JBCefBrowser` (the field is never
nulled) → logged platform exception / no-op.

> **User impact — low, cosmetic, but it looks like a plugin crash.** Nothing the dev was doing breaks;
> the tab is closing anyway. The cost is that IntelliJ surfaces logged platform exceptions as the red
> "IDE Internal Error" badge in the status bar, with a *Report to JetBrains* dialog naming ZKIdea in
> the stack trace. The trigger is an ordinary habit — hitting **Ctrl+S then immediately Ctrl+W** — so
> a fast-moving developer can see it repeatedly and reasonably conclude the plugin is unstable, even
> though nothing actually malfunctioned. One-word fix, worth taking purely for perceived quality.

*Fix:* guard the lambda body with `if (!disposed && browser != null && previewUrl != null)`.

**R2-MIN4 · Response body capture hardcodes UTF-8, ignoring `characterEncoding`** *(verified — demoted from Major)*
`mockcore/MockHttpServletResponseCore.java:44-57` (+ `setCharacterEncoding` at 87-89).
`getContentBytes()`/`getContent()` fall back to `StandardCharsets.UTF_8` and never consult the
`characterEncoding` field. A resource written via the writer path with a non-UTF-8 charset (e.g. a
legacy ZK deployment doing `setCharacterEncoding("Big5")`) is then paired by `resource()` with a
`getContentType()` that may report `charset=Big5` — declared charset vs. actual bytes mismatch →
mojibake. Demoted because nothing in the current preview path can set a non-UTF-8 encoding: it is
latent, not live.

> **User impact — near-zero today, but the users it *would* hit are a core ZK constituency.** If it
> ever triggers, the symptom is garbled Chinese/Japanese text (`亂碼`) in the preview of a page that
> displays correctly in the real container — and ZK's installed base includes a lot of long-lived
> Big5 / Shift_JIS deployments in Taiwan and Japan, exactly the developers most likely to hit it and
> least able to explain it. Cheap to fix now; annoying to diagnose from a screenshot later.

*Fix:* `Charset.forName(characterEncoding)` in both fallback branches.

**R2-MIN7 · Symlink defeats the docroot-containment guard** *(verified — demoted from Critical)*
`mockcore/MockServletContextCore.java:198-209` (`resourceFile`); the same lexical pattern is mirrored
in `PreviewHttpServer.readZulSource:126-141`.
The guard is purely lexical: `root.resolve(relative).normalize()` then `resolved.startsWith(root)`.
`normalize()` collapses `..` but never touches the filesystem, so it does not resolve symlinks. Plain
`../` traversal and absolute-path injection *are* correctly blocked (confirmed). But a symlink inside
the webapp dir — `webappDir/evil -> /Users/<user>/.ssh` — makes a ZUL reference like
`<include src="/evil/id_rsa"/>` resolve lexically to `root/evil/id_rsa` (passes the check) while the
file actually opened is `~/.ssh/id_rsa`.
**Why not Critical:** the only threat model is previewing an untrusted project, and there a hostile
`.zul` already gets arbitrary code execution — `<zscript>` is evaluated by ZK in the launcher JVM
against the project's own classpath (`PreviewHttpServer.java:30` even accounts for runaway zscript
loops). Reading one file through a symlink is a strict downgrade from what the attacker already has,
so this is defence-in-depth against path confusion, not a vulnerability.

> **User impact — none for legitimate users; the *fix* is where the user-facing risk lives.** No ZK
> developer previewing their own project is affected today. But note the preview docroot is the **dev
> source tree** (`src/main/webapp`), not a built WAR: symlinked asset folders do occur there —
> monorepo shared resources, a themes directory linked in from a sibling repo, `ln -s` into
> `node_modules`. A strict `toRealPath()` ancestry check would silently 404 those, turning a
> non-problem into a real "my images/CSS vanished" bug, and it adds a filesystem syscall to every
> asset request. So: fix it, but scope the check to *"the real path must stay within the project's
> content roots"* rather than *"within the docroot"*.

*Fix:* compare real paths (`toRealPath()`, falling back to the resolved parent's real path for
not-yet-existing targets), with the containment target widened to the content roots as above.

#### Remaining Minors

- **R2-MIN2 · `PreviewIssueReporter` inlines `.zul` in a ```` ``` ```` fence without escaping** *(verified)* —
  `PreviewIssueReporter.java:89-98`; a source containing a literal ```` ``` ```` closes the fence early and
  corrupts the GitHub issue body. *Fix:* use a longer fence than any run in the content (or escape it).
  **User impact:** only when a dev actually clicks "Report this issue on GitHub" and their `.zul`
  contains a triple backtick (realistic inside a `<script>` block or a markdown-bearing label). The
  issue they file renders as garbage on GitHub — embarrassing for them, and the environment block after
  the fence gets swallowed, so the maintainer loses the diagnostics. Costs a round-trip on exactly the
  users who took the trouble to report something.
- **R2-MIN3 · `DocrootResolver` fallback returns *first*, not *nearest*, boundary root** *(verified)* —
  `DocrootResolver.java:56-60`; contradicts the javadoc's "nearest of boundaryRoots". Only bites with
  nested roots (uncommon today). *Fix:* pick the longest matching path.
  **User impact:** same symptom as R2-MAJ1 (wrong docroot → broken `<include>`/CSS/images), but confined
  to multi-module projects with nested content roots — e.g. a Maven aggregator whose child WAR module
  sits inside the parent's content root. Rare, and self-inflicted-looking to the dev.
- **R2-MIN5 · `joinClasspath` joins with `File.pathSeparator`** *(verified)* — `ZulPreviewServerService.java:241`;
  a jar path containing a literal `:` (legal on Unix) is mis-split by the launcher. Very low likelihood.
  *Fix:* pass one `--classpath-entry` per jar.
  **User impact:** if it ever fires, the launcher gets a truncated classpath and the dev sees a
  `NoClassDefFoundError` naming a ZK class that is demonstrably in their `pom.xml` — maximally
  confusing. But Maven/Gradle cache paths don't contain `:`, so effectively nobody hits it.
- **R2-MIN6 · `resourceFile(null)` throws NPE instead of returning null** *(verified)* —
  `MockServletContextCore.java:198-199`; `getResource`/`getResourceAsStream`/`getRealPath` all funnel
  here; a container tolerates a null path. *Fix:* `if (path == null) return null;` at the top.
  **User impact:** none observed. Latent: if any ZK code path passes null, the dev gets a render error
  page with an NPE stack trace instead of the clean "not found" a real container would produce.
- **R2-MIN8 · `setDateHeader`/`addDateHeader` are silent no-ops** *(verified)* —
  `MockHttpServletResponseCore.java:111-115`; unlike every sibling setter, they drop the value, so
  `containsHeader`/`getHeader` report it was never set. Latent (headers not forwarded today). *Fix:*
  format via `DateTimeFormatter.RFC_1123_DATE_TIME` and store like the others.
  **User impact:** none — response headers aren't forwarded to the JCEF pane today. Would only matter
  if caching/`Last-Modified` handling were ever wired up.
- **R2-MIN9 · Engine `close()` / `contextDestroyed` never called** *(reviewer-reported, plausible)* —
  `PreviewHttpServer.stop()` closes the http server + executor but not the `RenderEngine`; `close()`
  itself only closes the URLClassLoader and never fires `contextDestroyed` to match the constructor's
  `contextInitialized`. Harmless today (each server is its own process, killed on shutdown) but a leak
  for the "independently callable / embedded" use `RenderEngineFactory` advertises. *Fix:* close the
  engine in `stop()`; fire `contextDestroyed` in `close()`.
  **User impact:** none. Each preview server is a separate JVM killed on project close, so the leak
  never accumulates in the developer's IDE. Purely a correctness debt for future embedding.
- **R2-MIN10 · javax mock javadocs still say "Jakarta"** *(verified — doc nit)* — the mock refactor's
  `sed 's/jakarta/javax/g'` was lowercase-only, so the capitalised "Jakarta" in the class javadocs
  survived in 5 javax files (`javax/mock/MockServletContext.java:25`, `MockHttpServletRequest.java:28`,
  `MockHttpServletResponse.java:10`, `MockHttpSession.java:10`, `MockServletConfig.java:11`). Zero
  runtime impact; misleads a reader about the namespace. *Fix:* one-line `s/Jakarta/Javax/` on those.
  **User impact:** none — invisible to plugin users. Contributor-facing only.

## L3 · Notes

### jakarta/javax parity — clean
Diffing all 6 mock pairs (after mechanically substituting `jakarta`→`javax` on the jakarta originals)
and both engine subclasses yielded **0 semantic divergence** — the Bridge + Template Method refactors
achieved their goal. The only residue is the R2-MIN10 javadoc-casing nit. No Bridge regressions: the
shared `ByteArrayOutputStream` wiring (`MockHttpServletResponseCore.byteBuffer()` →
`MockServletOutputStream`) is a single shared instance in both namespaces; no method is misplaced
between core and adapter.

### What checked out clean
`JcefAvailability`, `LayoutPreviewHint`, `PreviewResult`, `ZkClasspathFilter`,
`ZulPreviewFileEditorProvider`, and `ManagedPreviewServer` (port-parse / kill / stderr-tail is
correctly synchronised and idempotent). Engine TCCL save/restore is `finally`-guarded on every path;
session-per-render correctly rules out desktop accumulation; the loopback bind and the `readZulSource`
lexical traversal guard are otherwise correct (modulo the R2-MIN7 symlink gap).

### Method
Static review only — no gradle run, no server started, no port bound (8080 is the user's live app).
Findings marked *(verified)* were re-checked by hand against the source this session; *(reviewer-
reported / low confidence / UNCONFIRMED)* were not independently re-traced.

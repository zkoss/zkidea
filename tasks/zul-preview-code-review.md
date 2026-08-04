# ZUL Preview — code review (pre-1.0.0)

Three focused reviewers over the whole feature (91 Java files, ~10.5k lines): plugin-side
integration (`org.zkoss.zkidea.preview.*`), launcher isolation/rendering, launcher HTTP/error.
Findings de-duplicated and re-verified against the actual code by the main agent. Severity is for a
**public 1.0.0 marketplace release**. Nothing here is a regression from the docs/version commit — all
of it is pre-existing in the `preview` branch.

Legend: **CONFIRMED** = defect proven from the code · **PLAUSIBLE** = defect real, full impact depends
on ZK-internal behavior not in scope.

---

## Must-fix before publishing 1.0.0

### S1 · Path traversal in the mock servlet context — CONFIRMED (exploit PLAUSIBLE)
`zk-preview-launcher/.../{jakarta,javax}/mock/MockServletContext.java:343-346`

`resourceFile()` does `webappDir.resolve(relative).toFile()` with **no `normalize()` and no
containment check** — yet the very same package's `PreviewHttpServer.readZulSource` (lines 107-109)
does it correctly (`root.resolve(rel).normalize()` + `!f.startsWith(root)`). It backs `getResource`,
`getResourceAsStream`, `getRealPath`. `PreviewHttpServer.handle` passes the request path through
unsanitized (`/zkau/*` → `engine.resource(pathInfo)`, and `*.zul` → `engine.renderZul(path)`).

Failure scenario: the previewed page's own client JS runs live in the JCEF pane (`w:` handlers). A
booby-trapped `.zul` (from a shared branch / third-party repo) can `fetch('http://127.0.0.1:<port>/zkau/web/../../../../etc/passwd')`
and exfiltrate any file the IDE process can read — the moment the developer opens the file in preview.
Loopback-only mitigates remote attackers but not this local-JS vector.

Fix: mirror `readZulSource` — `normalize()` + `startsWith(webappDir)` in `resourceFile`, and/or reject
`..` at the `handle()` boundary.

### C1 · DocrootResolver WEB-INF/`webapp` walk is unbounded — CONFIRMED
`src/main/java/org/zkoss/zkidea/preview/DocrootResolver.java` (first loop)

The WEB-INF/`webapp` ancestor scan walks to filesystem root with **no boundary check** (the content
roots are consulted only in the 3rd fallback loop; the classpath-web-root loop is safe only because
it's gated by exact `resourceRoots.contains(parent)`).

Failure scenario: a Spring-Boot-jar project (the headline 1.0.0 case) checked out under any path
segment literally named `webapp` — e.g. `~/webapp/my-app/src/main/resources/web/index.zul` — matches
`isNamedWebapp` on that ancestor and returns it as docroot, **bypassing** the classpath-web-root rule.
The launcher gets `--webapp ~/webapp` and the page fails to resolve. Silently breaks the documented
SB-jar support for anyone with such a parent folder.

Fix: clip the first loop to the module's content roots (don't scan above `boundaryRoots`).

### C2 · Placeholder dimming is skipped on any styled component — CONFIRMED
`zk-preview-launcher/src/hooks/java/.../hooks/PlaceholderInjector.java:230-241`

`dim()` sets `DIM_STYLE` only when the component has **no** inline style; it never merges. The
placeholder *text* is written unconditionally, so a styled bound component shows undimmed literal text.

Failure scenario: `<label value="@load(vm.name)" style="font-weight:bold"/>` renders the literal
`vm.name` in bold — indistinguishable from real content, defeating M-1 (the headline "placeholders"
promise) for the very common case of a styled label/button.

Fix: append — `hc.setStyle(existing == null || existing.isEmpty() ? DIM_STYLE : existing + ";" + DIM_STYLE)`.

---

## Should-fix soon (quality / robustness)

### U1 · Helper JVM fork/exec runs on the EDT — CONFIRMED
`ZulPreviewServerService.startServer` (called from `onTargetResolved`, delivered on EDT via
`finishOnUiThread`) → `new ManagedPreviewServer(commandLine)` → `KillableProcessHandler(commandLine)`
constructor fork/execs a JVM synchronously. Opening the first `.zul` in a module freezes the IDE for
the process-creation time (tens of ms typically, worse under AV/slow disk/Windows) — on the feature's
most common path. Fix: start the process on the background executor, hop to EDT only for UI state.

### U2 · Uncaught throw before the try → pane stuck "loading" forever — CONFIRMED (low-probability trigger)
`ZulPreviewServerService.startServer:104-113` builds the command line — incl. `resolveLauncherJar()`
(throws `IllegalStateException` if the plugin descriptor is null) and `PreviewIssueReporter.*` —
**outside** the `try` at line 114, inside a `compute()` lambda on the EDT. An escape never reaches
`onReady`, so the card stays on `CARD_LOADING` with no error and no Report link (every other failure
path surfaces one). Fix: widen the guard so every path reaches `onReady`.

### U3 · "No ZK jars" message is wrong for a stale classpath — CONFIRMED
`hasZkJars = filterZkJars(...)` matches by **filename only** (no `isFile()`), while `libraryJars` uses
`filterLibraryJars` (requires `isFile()`). If ZK is declared but the local repo cache is wiped (dangling
paths), `hasZkJars` is true but `libraryJars` is empty → `PreviewResult.noZkJars()` tells the user to
"add a ZK dependency" they already have. Fix: distinguish "no ZK-named entry" from "present but not on
disk" and word accordingly ("re-sync/re-import").

### L1 · Shared `MockHttpSession` never reset → Desktop accumulation — CONFIRMED reuse (heap impact PLAUSIBLE)
`{jakarta,javax}RenderEngine` create one `MockHttpSession` in the constructor and reuse it for every
render for the JVM's whole life (kept alive per project session). Nothing invalidates the session or
evicts desktops, so each save retains a new `Desktop`/component-tree in the one session — monotonic
heap growth over a long editing session (and possible ZK desktop-cap errors). Also a fidelity issue:
separate preview tabs share one session (would be separate on a real server). Fix: fresh session (or
reset) per render.

### L2 · `stderrTail` StringBuilder grows unbounded — CONFIRMED
`ManagedPreviewServer:54-58` appends every stderr chunk for the process's whole life; the 2000-char
cap is applied only lazily in `tail()` at read time. Steady non-fatal stderr chatter over a workday
accumulates in memory. Fix: trim on append.

### I1 · Production isolation rests entirely on classpath narrowness — CONFIRMED framing; resource-root leak PLAUSIBLE
Two reviewers, same theme:
- `ForbiddenLoadTracker` is a **no-op in production**: `Main` calls the 2-arg `RenderEngineFactory.create`
  which passes `null`, so `ScopedZkClassLoader`'s blocklist never runs. The real guarantee is that user
  compiled output is off the classloader (`filterLibraryJars` excludes the output dir) + the `UiFactory`
  no-op hook. Nothing in the isolation code would *catch* a regression that let a user jar onto the list.
- `filterResourceRoots` puts the **whole** `src/main/resources` (not just `web/`) on the render
  classpath, so a user's `metainfo/zk/config.xml` could be scanned by ZK. Mitigation: a user `<listener>`
  *class* lives in the excluded output dir → `ClassNotFoundException`, not silent execution. So bounded,
  but worth (a) verifying what the launcher's ZK bootstrap actually scans, and (b) narrowing the
  classpath entry to the `web/` subdir if feasible.

### M5 · HTTP server is single-threaded with no request timeout — CONFIRMED
`PreviewHttpServer` never calls `setExecutor(...)`, so `com.sun.net.httpserver` dispatches serially on
one thread (verified empirically). A hanging render (infinite `<zscript>` loop, pathological
include/apply cycle) occupies that thread forever, freezing **every** preview tab sharing the JVM, with
no self-recovery. Note: if this is fixed by adding a thread pool, L1's shared session + the unsynchronized
`MockHttpSession`/`MockHttpServletRequest` maps become live data races — fix them together.

---

## Minor / maintainability

- **M1 · jakarta/javax duplication** — the two `RenderEngine`s and all six mock servlet classes are
  byte-identical apart from package/import names. No drift today, but nothing enforces parity (no shared
  base, no cross-check test) — a one-file fix will silently diverge. Consider a package-agnostic base.
- **M2 · Issue-report URL length** — `PreviewIssueReporter`'s 6000/3500-char caps are measured
  *pre*-URL-encoding; dense ZUL markup (`<`,`>`,`"`,`=`,newlines) can expand ~3×, past OS URL limits
  (Windows `ShellExecute` ~2047) so "Report on GitHub" silently fails to open. The existing test uses
  `"x".repeat(...)` (zero-encoding filler), so it never exercises this. Cap on encoded length + test with
  real markup.
- **M3 · `installExternalLinkHandler` localhost check** (`ZulPreviewFileEditor:188-189`) — `http://localhost`
  prefix match with no trailing boundary treats `http://localhost.evil.example` as trusted in-pane. Use
  equality / require a following `:` or `/`.
- **M4 · Response headers dropped** — `PreviewHttpServer.send` forwards only `Content-Type`; a ZK
  redirect (`Location`) or conditional-GET (`ETag`/`Last-Modified`) for a resource is silently lost.
- **Lower**: `Main.parseArgs` silently drops a trailing dangling `--flag` (mask a truncated invocation);
  `Main` has no failure-code differentiation (plugin must string-match stderr); AU `POST /zkau` never
  drains the request body (kept-alive nit); `RenderResult.failure` no null check; already-open tabs don't
  detect a mid-session helper-JVM crash (next save shows a generic browser error, not a plugin message);
  `RenderEngine.close()`/`zkLoader.close()` is dead in the CLI path (matters only if embedded);
  `VariantDetector` fallback reintroduces ordering risk if no canonical `zk-*.jar` name exists; the AU
  empty-envelope magic string `{"rid":0,"rs":[]}` has no regression test.

---

## Verified clean (called out by reviewers)

Loopback-only bind; response-stream try-with-resources + `exchange.close()` in finally; `ErrorPageRenderer`
HTML-escaping incl. the `</script>`-breakout guard for the clipboard `<script>` block; `RenderError`/`ErrorMapper`
null/empty invariants; `PreviewUiFactory` intercepts both `apply=` and auto-MVVM `BindComposer` paths (no user
composer/VM instantiation path found); `ScopedZkClassLoader`'s `org.zkoss.` prefix uses a trailing dot (no
`startsWith` boundary bug); variant misdetection fails loud (`IllegalStateException`), not silent; process
teardown (`destroy()` soft-then-hard kill, `dispose()` kills all — no orphan JVMs), backed by a test;
`serversByKey.compute` de-dups concurrent starts correctly; `filterLibraryJars` excludes directories/output
dir; editor disposal wiring (`Disposer`, `MergingUpdateQueue`, `disposed` guard) is correct; no GitHub URL
injection (title/body URL-encoded).

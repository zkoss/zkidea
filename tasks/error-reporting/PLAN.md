# Error page v2 — full stack trace + Report-to-GitHub

Two user-requested enhancements to the L-10 formatted error page (built in
`tasks/stage2-error-pane/`). Decisions confirmed with the user:
- **Stack trace:** collapsible, **collapsed by default** (keep the clean summary; power
  users expand for the full trace on the unexpected cases).
- **GitHub report:** prefilled with **error details + environment** (phase, message, stack
  trace, plugin/IDE/OS/JDK), user reviews & submits; **no ZUL source** included.

Target repo: `github.com/zkoss/zkidea/issues` (per the user; note the existing
Help ▸ Report Bug points at the `tracker.zkoss.org` JIRA — this preview report is separate
and GitHub-specific). Mechanism reuses the plugin's existing `BrowserUtil.browse(...)`
pattern (the `feedback` actions).

## Phase 1 — full stack trace (collapsible)  [launcher only]
- `RenderError`: add nullable `stackTrace`; keep the 5-arg constructor (delegates with
  `null`) + add a 6-arg one; `toJson()` gains `"stackTrace"`.
- `ErrorMapper.map()`: capture `t.printStackTrace(pw)` (full chain incl. causes).
- `ErrorPageRenderer`: when present, render `<details><summary>Show full stack
  trace</summary><pre>…</pre></details>` — **collapsed** (no `open`), HTML-escaped.
- Tests: `ErrorPageRendererTest` (details present+collapsed when trace set; absent when
  null); one assertion in `StructuredFailureTest` that a real render captures a trace.

## Phase 2 — Report to GitHub
"Can't display preview" spans render errors (HTML page) **and** the Swing message cards
(no-ZK-jars / server-failed / JCEF-unavailable). Coverage, easiest-first:

### 2a — Swing message cards  [plugin only, fully testable]
- `PreviewIssueReporter` (new, pure): builds a `github.com/zkoss/zkidea/issues/new?title=
  …&body=…` URL from (kind, detail, env); URL-encoded; **body length-capped** (~6 KB) so
  browsers/GitHub accept it (trace truncated with a "…(truncated)" marker).
- Env gathered from `ApplicationInfo` (IDE build) + `PluginManagerCore` (plugin version) +
  `System` props (OS, JDK).
- `ZulPreviewFileEditor.showMessage(...)` → render the message panel with a
  "Report this issue on GitHub" link (`BrowserUtil.browse`).
- Tests: `PreviewIssueReporterTest` — URL host/path, title & body carry the phase/message,
  encoding correct, over-long trace truncated under the cap.

### 2b — Render-error HTML page  [launcher + plugin; JCEF wiring is runIde-only]
- Plugin passes `--report-plugin <v>` / `--report-ide <build>` when spawning the launcher
  (`ZulPreviewServerService`/`ManagedPreviewServer`); OS/JDK/ZK-version the launcher
  already knows.
- Launcher threads that env into `ErrorPageRenderer`, which builds the same GitHub URL
  (shared builder logic with 2a where practical) and renders a "Report on GitHub" link.
- `ZulPreviewFileEditor`: install a JBCef request handler so external `http(s)` links
  (github.com) open in the **system browser** (`BrowserUtil.browse`) instead of hijacking
  the preview pane. (Also generally correct for any external link in a rendered ZUL.)
- Tests: launcher builder unit-tested; the JCEF external-link routing is **runIde-only**
  (no JCEF headless — lesson #1).

## Out of scope
- Auto-submitting issues / GitHub API auth (user always reviews + clicks submit).
- A "Preview Problems" aggregation panel (broader stage 2).

## Update — include the .zul source in the report (user, reversing the earlier "no source")
The user asked for the source in the report ("so we can debug in the future"), accepting
that it lands in a public issue. A prefilled GitHub URL **can't attach a file** and its
body is length-limited (URL-encoding inflates ~2–3×), so — per the user's choice — the
source is **inlined, budgeted** (`SOURCE_BUDGET` chars, fenced ```xml, truncated with a
marker for large files):
- Launcher: `PreviewHttpServer` reads the failing `.zul` from the docroot (path-guarded)
  and passes it to `ErrorPageRenderer.render(error, reportEnv, zulSource)`.
- Plugin (message cards): `ZulPreviewFileEditor` reads the document text and passes it to
  `PreviewIssueReporter.report(title, context, zulSource)` → `body(context, env, source)`.
- Tests: `ErrorPageRendererTest` (source carried + truncated), `PreviewIssueReporterTest`
  (fenced source block + truncation). Verified via curl: the render-error report href
  carries the file's own content (`ZUL+source`, `mismatched+open`).

## Success criteria
- RED→GREEN per phase; full suite green.
- Manual (runIde): expand the stack trace; click Report on both a render error and a
  no-ZK-jars state → system browser opens a prefilled GitHub issue with the right context.

## Status

- **Phase 1 (stack trace) — DONE & verified.** `RenderError.stackTrace` (6-arg ctor;
  5-arg delegates) + `toJson`; `ErrorMapper` captures `printStackTrace`; `ErrorPageRenderer`
  renders a collapsed `<details>`. Tests: `ErrorPageRendererTest` (+3), `StructuredFailureTest`
  (+capture assertion). **Verified via curl** against the launcher: the error page shows
  `<details class="trace">` (collapsed), "Show full stack trace", real frames.
- **Phase 2a (Swing report link) — DONE.** `PreviewIssueReporter` (pure `issueUrl`/`body`
  + runtime `environment`/`report`); message cards show a "Report this issue on GitHub"
  `ActionLink`. Tests: `PreviewIssueReporterTest` (encoding + length cap + body). Visual is
  runIde-only. Covers the setup-failure cases (no-ZK-jars / server-failed / JCEF-unavailable).
- **Phase 2b (render-error page report link) — DONE.** `ErrorPageRenderer.render(error,
  reportEnv)` builds the prefilled GitHub link in the error HTML (phase, message,
  file:line, stack trace, env — body-capped, URL-encoded, href HTML-escaped); the launcher
  owns this small builder (no plugin dep). `Main` parses `--report-plugin`/`--report-ide`
  and fills OS/JDK itself; `ZulPreviewServerService` passes the plugin/IDE identity at
  spawn (reusing `PreviewIssueReporter.pluginVersion()`/`ideDescription()`).
  `ZulPreviewFileEditor` installs a JBCef `onBeforeBrowse` handler that routes external
  `http(s)` links to the system browser (also fixes external links in any rendered ZUL);
  localhost preview/`/zkau` URLs still load in-pane.
  - **Verified via curl:** the error page carries a `github.com/zkoss/zkidea/issues/new`
    link with the phase in the title and the env (`ZKIdea 0.8.0`, `IU-243…`) encoded in
    the body. Tests: `ErrorPageRendererTest` (+2: link + env, link without env).
  - **runIde-only:** the JCEF external-link routing and the `--report-*` spawn wiring
    (no JCEF headless — lesson #1).

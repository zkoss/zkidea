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
- Including ZUL source (declined for privacy).
- A "Preview Problems" aggregation panel (broader stage 2).

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
- **Phase 2b (render-error page report link) — NOT STARTED.** Chosen approach: launcher
  builds the prefilled GitHub link in the error HTML (its own small URL builder — the
  module has no plugin dep; ~15 lines duplicated with `PreviewIssueReporter` by design),
  plugin passes `--report-plugin`/`--report-ide` at spawn, and `ZulPreviewFileEditor`
  installs a JBCef request handler so external `http(s)` links open in the system browser.
  The JCEF routing + CLI wiring are **runIde-only** (no JCEF headless — lesson #1).

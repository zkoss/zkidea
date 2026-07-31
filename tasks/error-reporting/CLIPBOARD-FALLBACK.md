# Clipboard fallback for over-long reports (render-error page)

## Problem
The render-error report body (source + env + full stack trace) can exceed a safe GitHub
URL length (~8 KB — see URL-LENGTH.md). Today we truncate the content to fit. The user
wants: instead of truncating, detect the over-length case and switch to a clipboard hand-off
that carries the **complete** information.

## Scope
Only the **render-error page** (`ErrorPageRenderer`, launcher). The Swing setup-failure
cards (`PreviewIssueReporter`) carry no stack trace and their body can't overflow ~4 KB, so
they keep the direct prefilled link (no speculative fallback).

## Behaviour
`ErrorPageRenderer.reportSection(error, env, source)` builds the full (untruncated) body and
the prefilled URL, then:
- **URL ≤ MAX_URL_LENGTH (8000):** a one-click prefilled `<a>` link (as today).
- **URL > MAX_URL_LENGTH:** the same-looking **"Report this issue on GitHub ↗"** link, but a
  click first copies the full body to the clipboard (`execCommand('copy')` on a temp textarea,
  async Clipboard API as a best-effort enhancement) and then lets the anchor navigate to a
  new-issue URL whose **body is the short paste instruction** (`CLIPBOARD_NOTE`) — the error
  info rides on the clipboard, not the URL. The plugin's existing JCEF handler routes that
  github.com navigation to the system browser.
  - **User feedback:** the "too large / paste" guidance is **not** shown in the IDE pane
    (a non-reporter shouldn't have to read it). It appears only in the opened issue's
    description, seen by someone who actually clicked to file.

Truncation (`cap`/`SOURCE_BUDGET`/`TRACE_BUDGET`) is removed — the content is always
delivered in full, either prefilled (small) or via the clipboard (large).

## Tests
- `ErrorPageRendererTest`: small report -> direct link, no button; over-long report ->
  clipboard button + instruction + full (untruncated) source + title-only URL.
- The actual clipboard write + system-browser open is JCEF runtime behaviour -> manual test.

## Manual test
- Fixture `manual-test/.../preview/errors/err-large-report.zul`: a large (~28 KB) ZUL that
  also fails to render (missing class), so the report reliably exceeds the URL limit.
- Steps in `MANUAL-clipboard-fallback.md`.

## Status — DONE (headless), manual pass pending
- `ErrorPageRenderer`: `MAX_URL_LENGTH = 8000`; `reportSection(...)` picks direct link vs
  clipboard hand-off; `reportBody(...)` no longer truncates; `CLIPBOARD_NOTE` (the issue-body
  paste instruction) + `clipboardReportSection(...)` + `jsString(...)` added;
  `MAX_BODY_CHARS`/`SOURCE_BUDGET`/`cap`/`truncate` removed.
- Tests: `ErrorPageRendererTest` — small → direct prefilled link; over-long → copy-to-clipboard
  link, **no** paragraph in the pane, the paste instruction pre-filled into the issue **body**,
  and the full (untruncated) source in the clipboard payload. Full suite green.
- Verified via a probe: the pane shows only the report link (no `report-note`); the opened
  issue's decoded body is exactly `CLIPBOARD_NOTE`; the error source is NOT in the URL (it is
  in the JS `REPORT` clipboard payload); `jsString` safely escapes `<`/`>`/`&`.
- **Pending:** the JCEF clipboard write + system-browser hand-off — `MANUAL-clipboard-fallback.md`
  (runIde-only, lesson #1).

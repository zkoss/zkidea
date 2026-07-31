# Report-URL length — limits, failure modes, detection

## Which URL?
Two URLs exist in the preview feature; only one has a length concern.

- **Preview URL** — `http://localhost:<port>/preview/foo.zul`. Just a path; a few dozen
  chars. Never a concern.
- **GitHub report URL** — `https://github.com/zkoss/zkidea/issues/new?title=…&body=…`.
  Carries the `.zul` source + environment + stack trace in the `body` query param. This is
  the one that can grow.

## Maximum length (measured, not estimated)
The body is capped **before** the URL is built: `MAX_BODY_CHARS = 6000` chars (pre-encoding),
in both `ErrorPageRenderer` (render-error page) and `PreviewIssueReporter` (Swing cards).

Probe with an oversized, realistic input (12 KB ZUL + 30 KB ZK trace):

| Metric | Value |
|---|---|
| Input source | 12,065 chars |
| Input trace | 30,073 chars |
| Decoded body after cap | 6,061 chars |
| Encoded body | 8,087 chars |
| **Full report URL** | **8,210 chars** |
| Encoding inflation | 1.33× |

So the report URL is **hard-bounded at ~8.2 KB**, no matter how large the ZUL or trace.
(Inflation is only ~1.33× because ZK traces / XML are mostly alphanumeric, and
`URLEncoder` encodes spaces as `+`, not `%20`.)

## How that compares to real limits
- **Browsers:** Chrome/Edge ~2 MB, Firefox ~65 KB+, Safari (macOS default) ~80 KB.
  8.2 KB is comfortably under all of them.
- **macOS `open` / ARG_MAX:** ~256 KB. Fine.
- **Receiving server (GitHub):** the binding factor, and undocumented. Classic web-server
  request-line defaults are ~8,192 bytes (nginx `large_client_header_buffers`,
  Apache `LimitRequestLine`). Our worst case (8,210) sits *right at* that line. GitHub
  accepts these prefill URLs in practice (confirmed by the user's own successful test), but
  the margin is thin in the absolute worst case (source **and** trace both maxing the cap).

## What happens if it were exceeded
- **Render-error page (`ErrorPageRenderer`) — resolved.** We measure the full prefilled URL
  and, above `MAX_URL_LENGTH = 8000`, the same-looking report link instead **copies the
  complete report to the clipboard** and opens an issue whose **body carries only a short
  paste instruction** (the error info rides on the clipboard, not the URL). Nothing is
  truncated. (See CLIPBOARD-FALLBACK.md / MANUAL-clipboard-fallback.md.)
- **Swing setup-failure cards (`PreviewIssueReporter`).** Still cap the body at 6,000 chars
  (with a `…(truncated)` marker), but their content carries no stack trace and can't realistically
  exceed ~4 KB, so this never triggers.

If an *uncapped* URL were ever sent past a limit, the failure would be downstream: GitHub
returns **414 URI Too Long**, or the issue form loads with an empty/truncated body — which is
exactly what the render-error page's clipboard hand-off now avoids.

## Can we detect it?
- **Proactively: yes** — we know the body length before building the URL; that is exactly
  what the cap uses. We can also compare the final encoded length to a threshold.
- **Reactively: no** — `BrowserUtil.browse(url)` hands the URL to the OS/system browser and
  returns nothing. We never learn whether GitHub accepted, truncated, or 414'd it.

## Is a huge ZUL + long trace a real problem?
- **The URL can't be exceeded** — structurally bounded at ~8.2 KB (proved above).
- **The report content will be truncated** — this is the real trade-off, and it is realistic:
  ZK `UiEngine` traces are commonly 5–20 KB, and a large ZUL easily exceeds the 3,500-char
  source budget. In those cases the GitHub issue carries a partial trace + the marker; the
  full data lives in the pane.

## Options if we want to remove the truncation trade-off
1. **Clipboard fallback (robust) — IMPLEMENTED for the render-error page.** When the prefilled
   URL would exceed `MAX_URL_LENGTH`, copy the *complete* report to the clipboard and open a
   title-only new-issue page → the user pastes the full source + trace. No truncation, no
   URL-length risk. (A prefilled URL fundamentally can't attach a file; the clipboard is the
   only single-click way to carry the whole thing.) See CLIPBOARD-FALLBACK.md.
2. **Safety margin (cheap):** not needed for the render-error page any more (the clipboard
   path covers the large case). Still applicable in spirit to the Swing cards, but they can't
   overflow.

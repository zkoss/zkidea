# Stage 2 / L-10 — formatted render-error page (not raw JSON)

Source: `doc/zul_preview_product_positioning.md` §3 **P0 / L-10** — a broken ZUL is the
most frequent touchpoint (files are broken half the time while editing); dumping the raw
HTTP-500 JSON into the pane destroys trust. The structured `RenderError{phase, message,
zulFile, line, column}` contract already exists (AC-6, since E1). This builds the
**formatted error pane that consumes it** — the cheapest trust win.

Approach chosen (user): **server-rendered HTML error page**. The launcher turns the
`RenderError` into a clean, self-contained, theme-aware HTML page that the existing JCEF
browser displays in place of the raw JSON. Entirely in `zk-preview-launcher`; **zero
plugin-side changes**; no double-render; no JSON parser on the plugin side.

## Key facts (verified)
- No test asserts the HTTP-level failure body is JSON. `StructuredFailureTest` asserts
  `RenderResult.toJson()` at the **object** level, not the wire — so changing the wire
  format to HTML keeps the structured `RenderError`/`toJson()` contract fully intact.
- `PreviewHttpServer.handle()` `.zul` branch currently: `!isSuccess()` → `send(500,
  "application/json", r.toJson())`. That JSON is what the browser paints verbatim (L-10).
- CEF renders a 500 response body (proven: the raw JSON currently shows), so a 500
  `text/html` full document will render our page. Status **500 retained** (semantically
  correct; browser still renders the body).

## Changes
1. **`ErrorPageRenderer`** (new, launcher `org.zkoss.zkpreview`) — pure, dependency-free:
   `render(RenderError) -> String` producing a self-contained HTML doc (phase badge,
   HTML-escaped message, `zulFile[:line[:column]]`, and an M-3-consistent note that the
   ViewModel isn't executed here / fix + save to re-render). Theme-aware via
   `prefers-color-scheme`. All interpolated fields HTML-escaped.
2. **`PreviewHttpServer`** failure branch → `send(500, "text/html;charset=UTF-8",
   ErrorPageRenderer.render(r.getError()))`. Update the stage-2 pointer comment.
3. **`stage2-hook.md`** — record that the browser endpoint now serves formatted HTML; the
   structured `RenderError` is still produced (object-level), and a future programmatic
   sink should use a server-side `Consumer<RenderError>` (option 1), not the wire body.
4. **`doc/feature_overview.md`** §10 — update the `PreviewHttpServer` description
   (failure → formatted HTML error page, not JSON).

## Tests (TDD, RED first)
- **`ErrorPageRendererTest`** (pure, fast): output is HTML not JSON (no leading `{`,
  starts `<`); contains phase, the message text, the zulFile; **HTML-escapes** a message
  containing `<`/`&` (no raw `<script>`); shows the line when present; carries the
  "ViewModel"/"not executed" framing.
- **`RenderErrorPageHttpTest`** (HTTP wire, `Variants.both()`, mirrors `AuStubTest`):
  `GET /zscript-missing-class.zul` via a real `PreviewHttpServer` → assert `Content-Type`
  is `text/html` (NOT `application/json`), body does **not** start with `{` (L-10
  regression guard), body is HTML and contains the phase + the missing FQCN.

## Out of scope
- A "Preview Problems" aggregation panel, gutter markers, persistence (broader stage 2).
- Client-side JCEF interception / native Swing panel (the alternative approach, declined).
- `error.line`/`column` accuracy improvements (structural ZK limit; unchanged).

## Success criteria
- RED→GREEN on both new tests; full suite (root + launcher) green.
- Manual (runIde): open a broken `.zul`, confirm the pane shows the formatted error, not
  raw JSON; fix + save re-renders.

## Review — DONE (automated slice) / manual check pending

- **RED confirmed:** `ErrorPageRendererTest` failed to compile before `ErrorPageRenderer`
  existed. `RenderErrorPageHttpTest` then failed *green-render* (a real bug in the test,
  not the code): without a `ForbiddenLoadTracker`, the canary class loads from the parent
  classloader and `/zscript-missing-class.zul` renders a **200 page** — no error to show.
  Fixed the test to forbid the canary prefix (mirrors `StructuredFailureTest`), which is
  the correct way to simulate a missing user class → render failure. → new **lesson #11**.
- **GREEN confirmed:** all 7 new tests pass (5 renderer + both HTTP variants; phase
  `COMPOSE` observed on the wire, missing FQCN surfaced, `Content-Type: text/html`, body
  is HTML not JSON, no leading `{`).
- **No regressions:** full suite (root + `zk-preview-launcher`) BUILD SUCCESSFUL.
- **Files:** `ErrorPageRenderer` (new); `PreviewHttpServer` failure branch → HTML;
  `stage2-hook.md` + `feature_overview.md` §10 updated; tests `ErrorPageRendererTest`,
  `RenderErrorPageHttpTest`.
- **Still pending — manual runIde:** the actual pane showing the formatted page (vs the
  old raw JSON) is a JCEF render only visible in-IDE.

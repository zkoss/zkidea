# Stage 2 hook: Fail-Render reporting

> v1 (E1–E4) ships **no** reporting feature. This file documents where and how a future
> stage-2 consumer plugs in, per PLAN.md §1 ("Stage 2 (out of v1 scope, design hook
> only): Fail-Render reporting") and the E4 brief (W3). No UI, persistence, or network
> reporting is built here — this is a design note plus one pointer comment in the code.

## Shape decision: documentation, not a listener interface

Two shapes were considered for the E4 hook stub:

1. A `ZulPreviewFailureListener` Java interface in `org.zkoss.zkidea.preview`, with a
   no-op default wiring point in the plugin where a FAILURE response is received.
2. Documenting the existing JSON contract as the hook, with a pointer comment at the
   one place it is produced.

**Chosen: (2), documentation.** Reason: on the plugin side, `ZulPreviewFileEditor`
never parses the launcher's HTTP response at all — it hands `previewUrl` straight to
`JBCefBrowser`'s constructor (see
`src/main/java/org/zkoss/zkidea/preview/ZulPreviewFileEditor.java`, `startPreview()`),
and the embedded browser itself fetches and renders whatever comes back (200 HTML, or
500 JSON rendered as plain text). There is **no existing code path on the plugin side**
that would ever call a `ZulPreviewFailureListener` today. Adding one now would mean
either:

- the plugin duplicates a second HTTP GET + hand-rolled JSON parse purely to feed a
  listener with zero real consumers (dead code, plus a parsing dependency this codebase
  doesn't otherwise have), or
- wiring a `CefLoadHandler`/`CefRequestHandler` on `JBCefBrowser` to intercept the HTTP
  status code asynchronously — a real feature-shaped design decision (which callback,
  which thread, how to re-fetch the body JCEF already consumed) that stage 2 should
  make once it exists, not something E4 should pre-commit to as a "stub."

Documenting the contract avoids speculative code with no caller (the brief's own
escape hatch for this work item) while still giving stage 2 everything it needs: the
exact JSON shape, field semantics, and the single authoritative place it is produced.

## Where stage 2 plugs in

The structured failure is produced once, server-side, before JCEF or the plugin ever
see it:

- `zk-preview-launcher/src/main/java/org/zkoss/zkpreview/ErrorMapper.java` — maps a
  raw render-time exception to a `RenderError` (`phase`, `message`, `zulFile`, `line`,
  `column`).
- `zk-preview-launcher/src/main/java/org/zkoss/zkpreview/RenderResult.java` —
  `toJson()` wraps it as `{"status":"FAILURE","error":{...}}`.
- `zk-preview-launcher/src/main/java/org/zkoss/zkpreview/PreviewHttpServer.java`,
  `handle()` — the `.zul` GET branch: on `!r.isSuccess()`, writes `r.toJson()` as the
  HTTP 500 response body (`Content-Type: application/json`). **This is the single
  wire-level point stage 2 should intercept** (a pointer comment is left here). Two
  concrete integration options for stage 2, neither built now:
  1. **Server-side**: add a second, opt-in sink inside `PreviewHttpServer` (e.g. a
     `Consumer<RenderError>` passed in at construction) that fires alongside the HTTP
     response — no JCEF/browser involvement, works even before the browser finishes
     loading.
  2. **Client-side**: attach a `CefLoadHandler.onLoadError`/status-code check on the
     `JBCefBrowser` in `ZulPreviewFileEditor`, re-fetch `previewUrl` with a plain HTTP
     client when a non-2xx load is observed, and parse the JSON body.

  Option 1 is simpler and doesn't need a JSON parser dependency on the plugin side
  (the launcher already serializes; stage 2's plugin-side consumer would need one only
  under option 2).

## JSON schema (already shipped, stable since E1 — AC-6)

Success:
```json
{"status": "SUCCESS"}
```
(The rendered HTML itself is the HTTP 200 body already fetched by the caller; it is
not duplicated into this JSON envelope.)

Failure:
```json
{
  "status": "FAILURE",
  "error": {
    "phase": "COMPOSE",
    "message": "...",
    "zulFile": "/scope-var-completion.zul",
    "line": null,
    "column": null
  }
}
```

Field semantics:

| Field | Type | Semantics |
|-------|------|-----------|
| `status` | `"SUCCESS"` \| `"FAILURE"` | Top-level outcome of rendering one `.zul` request. |
| `error.phase` | enum string | Which render stage failed. See `RenderPhase` below. |
| `error.message` | string, non-empty | Human-readable summary of the exception chain (up to 4 levels, `ClassName: message <- ClassName: message ...`); guaranteed to contain the missing FQCN when the failure is a missing-class error. |
| `error.zulFile` | string \| `null` | The request path of the `.zul` being rendered when the failure occurred (not necessarily where the fault originates, e.g. an `<apply>`'d template). |
| `error.line` | integer \| `null` | Best-effort source line, when the failing layer's exception message carries one (e.g. BeanShell/zscript failures — `RenderFidelityTest`/`StructuredFailureTest` fixture (f)). `null` when no layer in the exception chain reports a position — this is a structural fact about the exception object, not a mapper defect (see the E4-evidence.md W5 investigation finding). |
| `error.column` | integer \| `null` | Same as `line`; currently only ever populated together with `line` when a `"line: N, column: M"`-shaped message is found (`ErrorMapper.LINE_COL`) — no fixture in this repo produces a non-null column today. |

`RenderPhase` values (`zk-preview-launcher/src/main/java/org/zkoss/zkpreview/RenderPhase.java`):

| Value | When `ErrorMapper` assigns it |
|-------|-------------------------------|
| `COMPOSE` | A `java.lang.ClassNotFoundException` in the cause chain, OR a BeanShell/zscript "Class: X not found in namespace" message, OR (added E4, W5) any `org.zkoss.zk.ui.UiException`/subclass with neither of the above — i.e. the document parsed successfully and ZK failed while building the component tree (e.g. "Unsupported parent for row"). |
| `PARSE` | Chain contains a throwable whose class name or message mentions parse/SAX/XML — no fixture in this repo currently exercises this path. |
| `UNKNOWN` | None of the above matched. After E4's W5 fix this is now a strictly smaller bucket than before (see E4-evidence.md). |
| `CLASSPATH` | Reserved in the enum; `ErrorMapper` never assigns it today — no fixture/scenario in this repo currently produces a classpath-stage failure distinct from `COMPOSE`'s `ClassNotFoundException` case. Stage 2 should not assume this value is ever observed in v1's JSON output. |
| `RESOURCE` | Reserved in the enum; `ErrorMapper` never assigns it today (resource-serving failures currently just 404, see `PreviewHttpServer.handle()`'s `/zkau/` branch — they never reach `ErrorMapper` at all). |

## What stage 2 still needs to design (out of scope here)

- Where captured failures are stored/aggregated (persistence) and for how long.
- Any UI surfacing (e.g. a "Preview Problems" panel, gutter markers).
- Whether/how failures are ever transmitted off-machine (the brief explicitly rules
  out network reporting for this stub).
- Deciding between the server-side vs. client-side integration point above once an
  actual consumer exists to make that call meaningfully.

# Layout Preview — render-error test cases (L-10)

Deliberately-broken ZULs for manually checking the formatted error page
(`ErrorPageRenderer`, `tasks/stage2-error-pane/`). Each should show a clean error card
in the preview pane — **never** the raw HTTP-500 JSON.

## How to view

- **In the IDE:** `./gradlew runIde`, then open any file below. The right (Layout
  Preview) pane should show the formatted error card.
- **Standalone launcher:** `GET /preview/errors/<file>` on a launcher started against
  this webapp. **Note:** a launcher started before this feature serves the old raw-JSON
  body — restart it with the freshly built `zk-preview-launcher.jar` (or use `runIde`).

## Cases (all verified: HTTP 500, `Content-Type: text/html`, formatted card)

| File | Real-world mistake | Phase | What the card shows |
|---|---|---|---|
| `err-malformed-xml.zul` | Mismatched tags mid-edit (`<vbox>`…`</hbox>`) | **PARSE** | `SAXParseException` with the `lineNumber`/`columnNumber` of the bad tag |
| `err-zscript-missing-class.zul` | Inline `<zscript>` uses a class not on the classpath | **COMPOSE** | `Missing class: com.example.reporting.ReportBuilder …`, line 7 |
| `err-zscript-runtime.zul` | `<zscript>` throws at runtime (logic bug, not a missing class) | **UNKNOWN** | `java.lang.IllegalStateException: simulated failure from zscript` |
| `err-unsupported-parent.zul` | `<row>` under `<window>` (no `<grid><rows>`) | **COMPOSE** | `Unsupported parent for row: <Window …>` |
| `err-unknown-component.zul` | Typo'd component tag (`<labell/>`) | **COMPOSE** | `DefinitionNotFoundException … Component definition not found: labell`, line 5:53 |

Every card also carries the note *"Binding values are not evaluated and your ViewModel is
not executed in the Layout Preview. Fix the ZUL (or its classpath) and save to
re-render."* — and the message is HTML-escaped (e.g. `<Window>` → `&lt;Window&gt;`), so a
message containing markup can't inject anything.

## Coverage note

`PARSE` and `UNKNOWN` were previously untested phases (see `stage2-hook.md`); these
fixtures exercise them end-to-end. The three `COMPOSE` cases each use a **different**
message shape (missing-class / hierarchy / definition-not-found) so the card is checked
against real ZK diagnostics, not one canned string.

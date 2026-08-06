# Layout Preview — special ZUML syntax cases

Ten hand-picked ZUL fixtures for manually checking that the Layout Preview renders the
**less-common / more distinctive ZUML syntax** correctly. They are distilled from the
automated corpus (`ZulSyntaxCorpusTest` in `zk-preview-launcher`) — the groups a developer
is most likely to be unsure about: **directives** (`<?page?>`, `<?component?>`, `<?taglib?>`, `<?xel-method?>`), the
**native** and **client** namespaces, `forEach` comma-lists, inline `zscript`, `variables`,
the MVVM binding **placeholder**, and every **EL implicit object**.

Each fixture is self-documenting: it renders the construct plus a gray note stating exactly
what a pass looks like in the preview pane.

## How to view

- **In the IDE (main path):** `./gradlew runIde` from the repo root, open this
  `manual-test/` folder in the sandbox IDE, then open each file below and read the right
  (Layout Preview) pane against the "Look for" column.
- **Standalone render (optional):** `GET /preview/syntax/<file>` on a launcher started
  against this webapp, or `mvn jetty:run` here and browse `/plugin-test/preview/syntax/<file>`.

All ten were verified to render on the jakarta variant (ZK 10.1.0 — the runIde/Jetty
runtime) through the production launcher render path before being committed.

## Cases

| # | File | ZUML feature (why it's special) | Look for in the preview |
|---|------|----------------------------------|-------------------------|
| 1 | `directive-page-title.zul` | **Directive** `<?page?>` — sets document metadata before the root element | Page renders; the document/tab title reads "ZUML Directive …" |
| 2 | `directive-component.zul` | **Directive** `<?component?>` — defines a new component (`pricetag`) inline, then uses it | The `pricetag` line renders even though it isn't a built-in tag |
| 3 | `directive-taglib.zul` | **Tag Library** `<?taglib?>` — loads the core TLD so its static methods become EL functions | Two strings concatenated (`c:cat`), then the same text uppercased (`c:toUpperCase`) |
| 4 | `directive-xel-method.zul` | **Directive** `<?xel-method?>` — turns one static method (`java.lang.Math#max`) into an EL function | Line ends with `42`, computed by the `m:max` EL function |
| 5 | `foreach-comma-list.zul` | `forEach` over a **comma-separated list** — iteration with no Java/model | Exactly three lines: Mercury, Venus, Earth |
| 6 | `native-html-passthrough.zul` | **Native namespace** (`n:`) — elements pass straight through as raw HTML | A plain bordered HTML `<table>` (browser default styling, not a ZK widget) |
| 7 | `client-side-listener.zul` | **Client namespace** (`w:`) — a client-side JS handler, no server round-trip | Click the button → its label changes to "label changed on the client" |
| 8 | `zscript-inline.zul` | Inline `<zscript>` — Java runs at compose time and builds a component | A label reading "This label was created by zscript …" that exists only because the script ran |
| 9 | `mvvm-placeholder.zul` | **MVVM placeholder** — the ViewModel is *not* executed in preview | The first line shows the literal text `vm.name` (not an evaluated value); the static sibling renders normally |
| 10 | `variables-composite-list.zul` | `<variables composite="list">` — a comma string declared as a list, read by index in EL | `planets[0]` → Mercury, `planets[2]` → Earth |
| 11 | `el-implicit-objects.zul` | **EL implicit objects** — every predefined variable from the ZUML Reference (`desktop`, `page`, `execution`, `param`, the scope maps, …) | Every line reads `true` **except** `event = false`; `desktop` shows a live id starting with `z_` |

## Note on the two interactive / behavioral cases

- **#7 (client listener)** is the one case that needs a *click*: the label change proves the
  `w:` client-side JavaScript survived into the preview and runs in the browser with no live
  ZK desktop behind it. (Server-side listeners can't fire in the preview — only client-side
  `w:` ones can.)
- **#9 (MVVM placeholder)** is the signature preview behavior: seeing `vm.name` as literal
  text — rather than a rendered value or an error — is *correct*. It confirms the preview
  shows layout without running your ViewModel. The same page under a real server would show
  the evaluated name.
- **#11 (EL implicit objects)** is a characterization survey, not an interactive case: 24 of
  the 25 predefined variables resolve to *live* objects (the preview renders through a real ZK
  `Execution`/`Desktop`/`Page`), which is why it differs from the MVVM placeholder in #9. The
  lone `event = false` is *correct* — the reference says `event` exists only inside an event
  listener, and none is dispatched at render time. Request-derived maps (`param`, `header`, …)
  and `zk` (browser info) resolve but read empty here because the headless render has no query
  string or browser user-agent; a real server would populate them.

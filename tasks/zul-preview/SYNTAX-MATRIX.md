# ZUL Syntax Corpus — Results Matrix (P3)

> Plan: [PLAN-P3-syntax-corpus.md](PLAN-P3-syntax-corpus.md). Corpus test:
> `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/ZulSyntaxCorpusTest.java`.
> Fixtures: `zk-preview-launcher/src/test/resources/fixtures/syntax/*.zul`.
> **Run:** `withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.ZulSyntaxCorpusTest"`

## Summary

- **48 cases × 2 servlet variants (javax ZK 9.6.0.2 · jakarta ZK 10.1.0) = 96 runs — all PASS, 0 skipped, 0 failures.**
- **16 groups** (15 ZUML syntax groups + a `smoke` scaffold group). Every ZUML group has **≥3 cases**.
- Full launcher suite green with the corpus added: **182 tests, 0 failures** across 18 classes.
- **No render gaps found.** Every hypothesized outcome matched the observed outcome on the first run,
  on both variants — including every discovery case (see [Discovery notes](#discovery-notes)).
- Outcomes: **RENDERS** = success + `present` marker in HTML · **PLACEHOLDER** = success, dynamic value
  intentionally absent (`absent` marker not in HTML; for MVVM, the binding expression renders as
  placeholder text via `present`) · **GRACEFUL_FAIL** = structured `RenderError` whose message contains
  `present`, not a raw stack.
- `javax` and `jakarta` columns are both **✅** for every row below (identical outcome on both), so they
  are collapsed into one **Result** column; any divergence would be called out explicitly.

## Matrix

| Group | Case (`syntax/…`) | Facet exercised | Outcome | present / absent | Result |
|---|---|---|---|---|---|
| smoke | smoke-renders.zul | scaffold: success + marker | RENDERS | present=SMOKERENDERS | ✅ |
| smoke | smoke-hidden.zul | scaffold: absent-marker branch | PLACEHOLDER | absent=SMOKEHIDDEN | ✅ |
| smoke | smoke-missing-class.zul | scaffold: structured-failure branch | GRACEFUL_FAIL | msg∋NoSuchClassXyz | ✅ |
| elements | elem-basic.zul | element + string attribute | RENDERS | present=ELEMBASIC | ✅ |
| elements | elem-nested.zul | nested components (vbox>hbox>label) | RENDERS | present=ELEMNESTED | ✅ |
| elements | elem-attrs.zul | multiple attrs + boolean/size coercion | RENDERS | present=ELEMATTRS | ✅ |
| attribute | attr-text.zul | `<attribute name="value">` long form | RENDERS | present=ATTRLONGFORM | ✅ |
| attribute | attr-multiline.zul | multiline `<attribute>` body (2nd line captured) | RENDERS | present=LINETWO | ✅ |
| attribute | attr-event.zul | long-form sets `label` + declares `onClick` | RENDERS | present=ATTREVENTLABEL | ✅ |
| el | el-arith.zul | `${6*7}` embedded in a string | RENDERS | present=EL42END | ✅ |
| el | el-empty.zul | `${empty foo}` operator on undefined var | RENDERS | present=ELEMPTYtrue | ✅ |
| el | el-if.zul | EL `${2 gt 1}` in an `if` attribute | RENDERS | present=ELIFSHOWN | ✅ |
| if-unless | if-true.zul | `if="true"` renders | RENDERS | present=IFTRUE | ✅ |
| if-unless | if-false.zul | `if="false"` omits the element | PLACEHOLDER | present=alwayshere, absent=IFFALSE | ✅ |
| if-unless | unless-true.zul | `unless="true"` omits the element | PLACEHOLDER | present=alwayshere, absent=UNLESSGONE | ✅ |
| foreach | foreach-commalist.zul | `forEach="AA, BB, CC"` comma list | RENDERS | present=ITEMCC | ✅ |
| foreach | foreach-status.zul | `forEachStatus.index` | RENDERS | present=IDX1 | ✅ |
| foreach | foreach-zscript.zul | `forEach="${items}"` over a zscript array | RENDERS | present=ITERFEB | ✅ |
| zk-container | zk-group.zul | `<zk>` groups siblings, no component | RENDERS | present=ZKGROUPTWO | ✅ |
| zk-container | zk-if.zul | `if` on a `<zk>` group omits children | PLACEHOLDER | present=alwayshere, absent=ZKHIDDEN | ✅ |
| zk-container | zk-foreach.zul | `forEach` on a `<zk>` group | RENDERS | present=ZKQ | ✅ |
| namespaces | ns-default.zul | default `zul` namespace | RENDERS | present=NSDEFAULT | ✅ |
| namespaces | ns-zk.zul | `zk` prefix resolves `if` (negative proof: `zk:if="false"` hides) | PLACEHOLDER | present=alwayshere, absent=NSZKHIDDEN | ✅ |
| namespaces | ns-shortcut.zul | shortcut namespace name (`xmlns:n="native"`) | RENDERS | present=NSSHORTCUT | ✅ |
| native | native-table.zul | native `n:table/n:tr/n:td` passthrough | RENDERS | present=NATIVECELL | ✅ |
| native | native-with-zk-child.zul | ZK widget inside native `n:fieldset` (no ZUL fallback) | RENDERS | present=NATIVECHILD | ✅ |
| native | native-attr.zul | native HTML attribute passthrough | RENDERS | present=NATIVEATTRVAL | ✅ |
| client | client-onfocus.zul | `w:onFocus` listener emitted (marker in handler body) | RENDERS | present=CLIENTFOCUSMARK | ✅ |
| client | client-attribute.zul | `client/attribute` DOM attr namespace | RENDERS | present=CLIENTATTRVAL | ✅ |
| client | client-shortcut.zul | `w:onClick` via `client` shortcut ns (marker in handler body) | RENDERS | present=CLIENTSHORTCUTMARK | ✅ |
| zscript | zscript-inline.zul | inline Java creates a ZK widget | RENDERS | present=ZSCRIPTLABEL | ✅ |
| zscript | zscript-guarded.zul | `if="false"` skips the zscript | PLACEHOLDER | present=alwayshere, absent=ZSGUARDED | ✅ |
| zscript | zscript-external-missing.zul | missing `src` external script | GRACEFUL_FAIL | msg∋nosuchscript | ✅ |
| pi | pi-page-title.zul | `<?page title?>` → `<title>` | RENDERS | present=PITITLE | ✅ |
| pi | pi-component.zul | `<?component name extends?>` define+use | RENDERS | present=PICOMPONENT | ✅ |
| pi | pi-missing.zul | `<?variable-resolver?>` missing class | GRACEFUL_FAIL | msg∋NoSuchResolver | ✅ |
| custom-attributes | ca-component.zul | component-scope attr, read via EL | RENDERS | present=CAVALUE | ✅ |
| custom-attributes | ca-page-scope.zul | `scope="page"` attr, read via `page` | RENDERS | present=CAPAGE | ✅ |
| custom-attributes | ca-composite-map.zul | `composite="map"` + EL map index | RENDERS | present=CAMAPVAL | ✅ |
| variables | var-basic.zul | `<variables>` read via `${var}` | RENDERS | present=VARBASIC | ✅ |
| variables | var-local.zul | `local="true"` | RENDERS | present=VARLOCAL | ✅ |
| variables | var-composite-list.zul | `composite="list"` + EL index | RENDERS | present=VARONE | ✅ |
| mvvm | mvvm-load.zul | `@load(vm.greeting)` → placeholder | PLACEHOLDER | present=vm.greeting, absent=LOADED | ✅ |
| mvvm | mvvm-bind.zul | `@bind(vm.name)` → placeholder | PLACEHOLDER | present=vm.name, absent=LOADED | ✅ |
| mvvm | mvvm-init-command.zul | `@init` + `onClick=@command` | PLACEHOLDER | present=vm.greeting, absent=LOADED | ✅ |
| annotations-template | tmpl-model.zul | `<template name="model">` → placeholder rows | PLACEHOLDER | present=each.name, absent=LOADED | ✅ |
| annotations-template | tmpl-page-level.zul | bare `<template>` is inert | PLACEHOLDER | present=alwayshere, absent=TMPLINERT | ✅ |
| annotations-template | annot-namespace.zul | `xmlns:z="zul"` makes `@x()` a literal, not annotation | RENDERS | present=thisIsValueNotAnnot | ✅ |

## Coverage — cases per group

| Group | Cases | ≥3? |
|---|---|---|
| smoke (scaffold) | 3 | — |
| elements | 3 | ✅ |
| attribute | 3 | ✅ |
| el | 3 | ✅ |
| if-unless | 3 | ✅ |
| foreach | 3 | ✅ |
| zk-container | 3 | ✅ |
| namespaces | 3 | ✅ |
| native | 3 | ✅ |
| client | 3 | ✅ |
| zscript | 3 | ✅ |
| pi | 3 | ✅ |
| custom-attributes | 3 | ✅ |
| variables | 3 | ✅ |
| mvvm | 3 | ✅ |
| annotations-template | 3 | ✅ |

<a id="discovery-notes"></a>
## Discovery notes (cases whose outcome was genuinely unknown before the run)

All resolved on the first run, on both variants — none produced a defect/gap:

- **pi-component** — `<?component name="myloclabel" extends="label"?>` then `<myloclabel>` renders → RENDERS.
- **pi-missing** — `<?variable-resolver class="com.example.NoSuchResolver"?>` fails at page evaluation with a
  structured error naming the class → GRACEFUL_FAIL (not silently ignored).
- **zscript-external-missing** — `<zscript src="/syntax/nosuchscript.zs"/>` fails gracefully naming the path.
- **custom-attributes / variables reads** — EL method invocation (`d.getAttribute('x')`, `page.getAttribute`),
  map indexing (`['k1']`) and list indexing (`[0]`) all resolve in rendered EL → RENDERS.
- **annot-namespace** — the `xmlns:z="zul"` disambiguation renders `@thisIsValueNotAnnot()` as a literal
  value (not interpreted as an annotation) → RENDERS.

## Verification layers applied (per PLAN §P3.4)

1. **Per-case, all 48 × 2 (server HTML markers):** `ZulSyntaxCorpusTest` — ✅ 96/96.
2. **Live-DOM (Playwright headless Chromium), representative sample:** `SyntaxCorpusBrowserSampleTest` —
   `elem-basic`, `native-table`, `foreach-commalist`, `zscript-inline` — ✅ 4/4 (real browser built the
   marker in the DOM). The Playwright path ran live in this environment (`E1-G2 path: PLAYWRIGHT`).
3. **Real-container equivalence (Jetty) & visual (runIde JCEF):** not run in this pass — deferred to a
   manual spot-check; the launcher render path IS production, and layer 2 confirms client-side build.

## Verifier sweep (P3.4)

An independent adversarial audit (subagent) checked the corpus for *honesty* — markers decoupled from the
construct under test, coverage claims, fixture↔descriptor alignment, and whether the MVVM guard is real.

- **Confirmed honest:** 16×3 coverage is a clean 1:1 bijection with the 48 fixtures; no hyphenated
  value-markers; the MVVM `present` (placeholder expression text) is genuinely load-bearing — the auditor
  traced `PreviewUiFactory`/`PlaceholderInjector` and confirmed the ViewModel-load path is blocked
  architecturally (not merely by the tracker), so `absent="LOADED"` is correctly documented as
  defense-in-depth; `annot-namespace`, `native-attr`, `client-attribute` markers live in attribute values
  distinct from visible text and cannot pass by coincidence.
- **Weak markers found and FIXED** (marker was an unrelated attribute → now load-bearing):
  - `client-onfocus` / `client-shortcut` — marker moved INTO the `w:` listener body (`CLIENTFOCUSMARK` /
    `CLIENTSHORTCUTMARK`): the run confirms the listener body is emitted to the client, so the token only
    appears if the client-namespace attribute was actually processed.
  - `attr-event` — the button `label` is now SET BY a long-form `<attribute name="label">` (`ATTREVENTLABEL`);
    the marker appears only if the `<attribute>` long form applied.
  - `native-with-zk-child` — wrapper changed to `n:fieldset` (no ZUL-component equivalent), so a broken
    native namespace hard-fails on the unknown element instead of silently falling back to a ZK `Div`.
  - `ns-zk` — now a negative proof (`zk:if="false"` must hide `NSZKHIDDEN`); a dropped `zk:` prefix would leak it.
  - `attr-multiline` — marker moved to the 2nd line (`LINETWO`), proving the multiline body was captured.
- All fixes re-run green: **96/96, 0 skipped**, both variants.

## Notes on assertion strength

- The MVVM `absent="LOADED"` guard is **defense-in-depth**: the real bound value is never computed in the
  preview, so the load-bearing assertion for those rows is `present` (the placeholder expression text
  actually rendering, proving the M-1 pass ran and the annotation did not crash the render). The strong
  isolation proof (no user class loaded, via `ForbiddenLoadTracker`) already lives in `RenderFidelityTest`
  and is cross-referenced rather than duplicated here.

## Addendum — EL implicit objects (ZUML Reference "Implicit Objects / Predefined Variables")

> Separate test (not part of the 48-case corpus above):
> `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/ImplicitObjectsElTest.java`,
> fixture `syntax/el-implicit-objects.zul`.
> **Run:** `withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.ImplicitObjectsElTest"`

All **25** implicit objects from the reference table were exercised via `${obj != null}` (plus `each`/
`forEachStatus` inside a `forEach`, and a live-`desktop.id` proof). **Identical result on javax (ZK 9.6)
and jakarta (ZK 10) — 2/2 runs PASS.**

- **24 resolve to a live object:** `self`, `page`, `desktop`, `execution`, `session`, `pageContext`,
  `spaceOwner`; the 7 scope maps (`application/session/desktop/request/page/component/space` Scope);
  `param`, `paramValues`, `cookie`, `header`, `headerValues`; `labels`, `zk`, `arg`; and `each` /
  `forEachStatus`. `desktop.id` renders a real `z_`-prefixed id — these are genuine runtime objects,
  **not** MVVM-style placeholders (the preview renders through a real `Execution`/`Desktop`/`Page`).
- **1 resolves to null — `event` — and that is correct:** the reference scopes `event` to "the event
  listener only"; page-evaluation render dispatches no event, so `${event != null}` is `false`.
- **Headless-sensible values** (resolve, but read empty because there is no HTTP query string / browser
  UA in the launcher render): `param.size()`=0, `execution.contextPath`=empty, `zk.safari`=empty. A real
  server populates these from the request.
- **EL constraint discovered:** `${x.class...}` aborts the whole page on ZK 10 — its EL (`zel`) rejects
  `class` as an identifier (EL spec §1.19). Liveness is therefore proven via `desktop.id`, not `.class`.

# E3-G1c — Corpus Cross-Reference: Validator vs Preview Renderer

> QA/corpus-test pass. Collects evidence and classifies only — no code changes, no fixes,
> no commits. Rule under test: **any ZUL the validator says is VALID must preview without
> error.**

## Tools & versions

- JDK: `withjdk.sh 17` → Zulu 17.0.4.1 (OpenJDK 17.0.4.1+1-LTS)
- Launcher jar: `zk-preview-launcher/build/libs/zk-preview-launcher.jar` (rebuilt via
  `withjdk.sh 17 ./gradlew :zk-preview-launcher:jar` — all tasks reported `UP-TO-DATE`,
  i.e. the jar on disk already matched current sources)
- Classpath: `withjdk.sh 17 mvn -f manual-test/pom.xml dependency:build-classpath
  -Dmdep.outputFile=<scratch>/e3-cp.txt -q` → 29-entry classpath, ZK `10.1.0-jakarta`
  (zkmax/zkex/zkbind/zul/zk/zweb/zcommon/zel/zuti/zhtml + transitive deps)
- Launcher invocation:
  ```
  withjdk.sh 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
    --classpath "$(cat <scratch>/e3-cp.txt)" \
    --webapp manual-test/src/main/webapp --port 0
  ```
  Bound to `PREVIEW_PORT=61580`, PID 72450 (verified via `lsof -iTCP:61580 -sTCP:LISTEN`).
  A separate, unrelated `zk-preview-launcher.jar` process (PID 34527, from
  `.sandbox/plugins/zkidea/lib/`, serving a different webapp under
  `SUPPORT/plugin-test/src/main/webapp`) was already running before this session started;
  it was left untouched throughout and is not part of this gate's evidence.
- Validator: `/Users/hawk/Documents/workspace/AI/agent-skill/skills/zul-writer/scripts/validate-zul.py`,
  run as `DO_NOT_TRACK=1 python3 validate-zul.py <relative-path>` from
  `manual-test/src/main/webapp/` (relative paths make validator output match the
  preview's webapp-relative URLs). Default XSD:
  `/Users/hawk/Documents/workspace/AI/agent-skill/skills/zul-writer/assets/zul.xsd`,
  `--zk-version 10` default. Exit code 0 = all 4 layers pass ("VALID"), exit code 1 = at
  least one layer failed ("INVALID"). lxml 6.1.1 auto-installed by the script on first run.

## The matrix (20 files)

| # | File | A: Validator verdict | B: Preview result | C: Classification |
|---|------|----------------------|--------------------|--------------------|
| 1 | binding-property-nav.zul | **INVALID** — Layer2/XSD: line 43 `<listbox>` missing child element(s) | SUCCESS (200, `zkmx(` present) | **LENIENT-OK** |
| 2 | command-binding-nav.zul | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 3 | command-name-completion.zul | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 4 | command.zul | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 5 | generic-inheritance-nav.zul | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 6 | index.zul | **INVALID** — Layer2/XSD: 7× `<a>` element, "Character content other than whitespace is not allowed because the content type is 'element-only'" (lines 15,22,29,36,43,50,53) | SUCCESS (200, `zkmx(`) | **LENIENT-OK** |
| 7 | missing-vm-nav.zul | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 8 | model.zul | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 9 | preview/broken.zul | VALID (all 4 layers pass) | FAILURE — 500, JSON, `phase=COMPOSE`, message names `org.example.definitely.NoSuchClassAtAll` | **EXPECTED-FIXTURE** |
| 10 | preview/button.zul | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 11 | preview/separate-wpd.zul | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 12 | scope-var-completion.zul | **INVALID** — Layer2/XSD: `<grid>` missing children (4×) + `<apply>` attrs `ctx`/`if`/`unless`/`forEach*` not allowed (lines 105-141); Layer3/attribute-placement: same 6 `<apply>` attrs flagged unsupported | FAILURE — 500, JSON, `phase=UNKNOWN`, `"org.zkoss.zk.ui.UiException: Unsupported parent for row: <Window hFcB0>"` | CONSISTENT |
| 13 | template-uri-nav.zul | **VALID** (all 4 layers pass) | **FAILURE** — 500, JSON, `phase=UNKNOWN`, `"org.zkoss.zk.ui.UiException: Page not found: /@load('/WEB-INF/template/"` | **DEFECT** |
| 14 | test-attribute.zul | **INVALID** — Layer2/XSD: line 2 `<if>` missing child element(s) | SUCCESS (200, `zkmx(`) | **LENIENT-OK** |
| 15 | test.zul | **INVALID** — Layer1/XML well-formedness: "not well-formed (invalid token): line 6, column 9" (stray `<` inside `<listbox>`) | FAILURE — 500, JSON, `phase=PARSE`, SAXParseException at line 6 col 10, "content of elements must consist of well-formed character data or markup" | CONSISTENT |
| 16 | viewmodel-id-nav.zul | **INVALID** — Layer2/XSD: line 46 `<grid>` missing children; Layer3/attribute-placement: lines 54/58 `name`/`value` not supported on `<attribute>` | FAILURE — 500, JSON, `phase=UNKNOWN`, `"org.zkoss.zk.ui.metainfo.PropertyNotFoundException: Method setVm not found for class org.zkoss.zul.Window"` | CONSISTENT |
| 17 | vm-property-completion.zul | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 18 | WEB-INF/template/grid.zul (template) | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 19 | WEB-INF/template/item.zul (template) | VALID | SUCCESS (200, `zkmx(`) | PASS |
| 20 | WEB-INF/template/row.zul (template) | VALID | SUCCESS (200, `zkmx(`) | PASS |

**Column B methodology**: `curl -s -w "%{http_code}"` against
`http://127.0.0.1:61580/<path>`. All 15 "SUCCESS" rows returned HTTP 200 with `zkmx(`
verified present in the body (checked programmatically, not just eyeballed) and body
sizes 1187–4103 bytes (real rendered HTML, not empty/truncated). All 5 "FAILURE" rows
returned HTTP 500 with a well-formed `{"status":"FAILURE","error":{"phase":...,
"message":...,"zulFile":...,"line":...,"column":...}}` body — no "OTHER" cases (no empty
responses, connection errors, HTML stack traces, or non-JSON 500s) occurred anywhere in
the corpus.

## Counts

- PASS: 12
- DEFECT: 1
- CONSISTENT: 3
- LENIENT-OK: 3
- EXPECTED-FIXTURE: 1

(12 + 1 + 3 + 3 + 1 = 20)

Validator totals: 14 VALID, 6 INVALID. Preview totals: 15 SUCCESS, 5 FAILURE (all
structured JSON — zero "OTHER").

## Defect detail: `template-uri-nav.zul`

**Gate breach**: validator says VALID (all 4 layers pass, verified twice, same
`validate-zul.py` run against the file both from the repo root context and from
`manual-test/src/main/webapp/`), but the preview renderer returns a structured 500.

Full validator output:
```
Validating: template-uri-nav.zul
--------------------------------------------------
Layer 1: XML Well-formedness... ✓ PASS
Layer 2: XSD Schema Validation... ✓ PASS
Layer 3: Attribute Placement... ✓ PASS
Layer 4: ZK 10 Compatibility... ✓ PASS
--------------------------------------------------
Result: ✓ All validations passed
EXIT_CODE=0
```

Full preview response (`curl -s -w "\nHTTP_STATUS=%{http_code}\n" http://127.0.0.1:61580/template-uri-nav.zul`):
```
{"status":"FAILURE","error":{"phase":"UNKNOWN","message":"org.zkoss.zk.ui.UiException: Page not found: /@load('/WEB-INF/template/","zulFile":"/template-uri-nav.zul","line":null,"column":null}}
HTTP_STATUS=500
```

**Reproducibility (flakiness check)**: called 3 additional times after the first
observation — twice back-to-back, then a third time interleaved with a render of
`command.zul` (200 OK) in between to rule out server-side lock/contention artifacts. All
3 extra calls returned byte-for-byte the same JSON body and HTTP 500. **Fully
deterministic, not flaky.**

**State-pollution check**: rendered `template-uri-nav.zul` itself twice in a row (same
result both times) and rendered a different file (`command.zul`, succeeded 200) in
between two `template-uri-nav.zul` calls with no change in outcome. **No state
pollution observed** — the failure is intrinsic to this file's content, not carried
over from a prior request.

**Launcher-side console output**: launcher stdout/stderr captured continuously
throughout the run. No stderr was ever emitted. The only stdout log line for the whole
session (`ERROR org.zkoss.zk.ui.metainfo.Property - Failed to assign [vm=] to <Window
uWCY0> / Method setVm not found for class org.zkoss.zul.Window`) corresponds to
`viewmodel-id-nav.zul`'s render (matches that file's JSON message), not to
`template-uri-nav.zul` — its failure produced no extra console noise, only the
structured JSON.

**Root-cause hypothesis (not a fix — for the planner)**: The file is a navigation/
completion fixture for `template-uri-navigation.feature`. Its own header comment
documents 9 test cases (T01-T09), most exercising IDE-only behavior (Ctrl+Click
navigation, autocomplete). T05, at line 49:
```xml
<!-- T05: Partial path for completion testing. -->
<apply templateURI="@load('/WEB-INF/template/"/>
```
is a **deliberately incomplete** annotation value — it exists solely so the IDE's
completion provider has a half-typed path to offer completions for; it was never meant
to be a functioning `@load(...)` call. The validator's four layers (XML
well-formedness, XSD, attribute-placement, ZK10-compat) only check *syntactic* ZUL
structure — a bare string in a `templateURI` attribute is syntactically legal no matter
what it says, so none of the 4 layers can detect "this annotation is semantically
incomplete." But the preview renderer actually composes the page: `<apply>`
concretely tries to resolve `templateURI` at render time, and — because the value
doesn't parse as a recognized `@load(...)`/`@init(...)` annotation — ZK appears to fall
back to treating the raw string as a literal page path, hence
`Page not found: /@load('/WEB-INF/template/`. **Hypothesis**: this is a fixture-design
mismatch (a file authored for completion-provider testing, not for full-page rendering)
rather than a renderer bug in the general sense — but per the user's stated rule (VALID
+ FAILURE = DEFECT, no exceptions carved out for "the fixture wasn't meant to render"),
it is recorded as a DEFECT since E3-G1c's rule is binary. The planner should weigh
whether the fix belongs in (a) the renderer (make `<apply>` failures on
unrecognized/incomplete annotations non-fatal or clearly attributed), or (b) the
fixture (split T05's incomplete-annotation case into a file/element that the preview
skips, e.g. wrapped so it's never composed), or (c) accept it as a documented,
permanent LENIENT/DEFECT exception for IDE-only completion fixtures.

## CONSISTENT detail (both reject, structured JSON — recorded per gate spec)

- **scope-var-completion.zul**: validator flags `<apply>` JSTL-style attributes
  (`ctx`, `if`, `unless`, `forEach*`) as invalid per the XSD/attribute-placement layers;
  preview fails at runtime with `Unsupported parent for row: <Window hFcB0>` — a
  different-looking but consistent rejection (both reject the file, though for
  proximately different stated reasons — validator catches the `<apply>` attribute
  misuse; the runtime failure surfaces from a nested `<row>`/`<grid>` structural issue
  it walks into before ever reaching the `<apply>` line). Preview failure is a clean
  structured JSON, not a stack-trace dump.
- **test.zul**: validator's Layer 1 (XML well-formedness) catches a literal stray `<`
  inside `<listbox>` at line 6, column 9 — preview's `phase=PARSE` failure reports the
  identical line/column-adjacent SAXParseException. Directly consistent, same root
  cause, both graceful.
- **viewmodel-id-nav.zul**: validator's Layer 3 (attribute-placement) flags `name`/
  `value` as unsupported on `<attribute>`; preview fails with
  `PropertyNotFoundException: Method setVm not found for class org.zkoss.zul.Window`
  — again a different proximate error message but both reject the file; preview
  failure is graceful structured JSON.

## LENIENT-OK detail (validator stricter than renderer — not a defect, recorded per spec)

- **binding-property-nav.zul**: validator's XSD layer objects to `<listbox>` "missing
  child element(s)" at line 43 (expects the listbox to have at least one of
  `attribute/custom-attributes/variables/template/zk/auxhead/listitem/listgroup/
  listgroupfoot` as a child) — likely because the listbox's rows come entirely from a
  `<template>`-driven MVVM binding the strict XSD doesn't special-case as "has
  children" the way ZK's real parser does. Renderer renders fine (200, `zkmx(`).
- **index.zul**: validator's XSD layer rejects text content directly inside 7 `<a>`
  elements (ZK's real `<a>` widget accepts a text label as content; the local/repo XSD
  used by the validator models `<a>` as element-only). Renderer renders fine.
- **test-attribute.zul**: validator's XSD layer rejects `<if>` for "missing child
  element(s)" at line 2 (likely an EL-only/empty-bodied `<if>` used purely to test
  attribute placement, which the XSD doesn't recognize as valid on its own). Renderer
  renders fine.

## EXPECTED-FIXTURE detail: `preview/broken.zul`

Validator: VALID (all 4 layers pass — the file is syntactically valid ZUL; the intended
failure is a runtime zscript class-resolution error, which no static-analysis layer can
catch).

Preview (verified twice, both calls returned byte-for-byte identical output):
```
{"status":"FAILURE","error":{"phase":"COMPOSE","message":"Missing class: org.example.definitely.NoSuchClassAtAll (org.zkoss.zk.ui.UiException: ... Class: org.example.definitely.NoSuchClassAtAll not found in namespace ...)","zulFile":"/preview/broken.zul","line":7,"column":null}}
HTTP_STATUS=500
```
Matches the expected fixture behavior exactly: `phase=COMPOSE`, message names
`org.example.definitely.NoSuchClassAtAll`. Marked **EXPECTED-FIXTURE** per the task's
known-case carve-out, not counted as a DEFECT despite being VALID+FAILURE.

## Template-context note

`WEB-INF/template/grid.zul`, `item.zul`, and `row.zul` are include-context templates
(normally only ever reached via `<apply templateURI="...">`/`@load` from another page,
never opened directly by a browser). All three were still requested directly by URL per
the gate's instruction ("if any is VALID + FAILURE, it is still a DEFECT... but record
that it's a template"). All three rendered standalone with HTTP 200 and `zkmx(` present
— no special-casing was needed, they are not part of the DEFECT list.

## Launcher teardown

```
$ kill 72450
$ sleep 1
$ ps -ef | grep zk-preview-launcher | grep -v grep
  501 34527 ...  java -jar .../.sandbox/plugins/zkidea/lib/zk-preview-launcher.jar ...   <- pre-existing, unrelated, untouched
$ lsof -iTCP:61580 -sTCP:LISTEN   -> (no output, port released)
```
The launcher process started for this gate (PID 72450, port 61580) is confirmed killed
with no orphan. The unrelated pre-existing launcher process (PID 34527, a different
webapp under `SUPPORT/plugin-test`) was already running before this task started and
was intentionally left untouched — it is outside this gate's scope.

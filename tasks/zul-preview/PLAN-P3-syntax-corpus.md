# P3 — ZUL Syntax Corpus (ZUML Reference) — PLAN

> Follows the **plan-spec** 3-tier structure (L1 summary / L2 phase breakdown / L3 technical appendix).
> Parent: [PLAN-followups.md](PLAN-followups.md) (P3 is the next open follow-up; P1/P2 DONE).
> Reference source: `/Users/hawk/Documents/workspace/DOC/zkdoc/zuml_ref` (135 md files) + `zk-doc` MCP.

---

## L1 — Executive Summary

**Core goal.** Prove the ZUL preview renders (or fails gracefully on) every ZUML syntax construct, by
building a **data-driven launcher corpus**: for each ZUML *syntax group*, ≥3 *cases*, each a `.zul`
*fixture* with one asserted *outcome*. The launcher render path IS the production render path, so this
is real coverage — headless, run on both servlet variants (javax ZK 9.6 + jakarta ZK 10).

**Vocabulary (fixed — used identically in L2/L3).**
- **syntax group** — one ZUML construct family (e.g. `forEach`, `<zscript>`, processing instructions). 15 groups.
- **case / fixture** — one `.zul` file exercising one facet of a group (1 case = 1 fixture).
- **outcome** — the asserted result, exactly one of: **RENDERS** (success + marker present) ·
  **PLACEHOLDER** (success, dynamic/bound value intentionally absent — no crash) · **GRACEFUL_FAIL**
  (structured `RenderError`, not a raw stack).
- **descriptor** — the Java record binding a fixture to its expected outcome + marker.
- **matrix** — `SYNTAX-MATRIX.md`, the committed results table (group × case × outcome × variant).

**Milestones.**

| Phase | Deliverable | % |
|---|---|---|
| **P3.0** | Harness scaffold: `ZulSyntaxCorpusTest` (cartesian *cases* × *variants*) + `Outcome` enum + descriptor list, wired to the P2 harness | ✅ 100% |
| **P3.1** | Core syntax groups (elements/attributes, `<attribute>`, EL, `if`/`unless`, `forEach`, `<zk>`) | ✅ 100% |
| **P3.2** | Namespaces & passthrough (namespaces, native HTML, client `w:`, `<zscript>`) | ✅ 100% |
| **P3.3** | Directives & binding (processing instructions, `<custom-attributes>`, `<variables>`, MVVM annotations, `<template>`) | ✅ 100% |
| **P3.4** | `SYNTAX-MATRIX.md` + Verifier sweep + found-gap disposition (fix or accept-with-rationale) | ✅ 100% |

**Overall P3 progress: ✅ 100% (headless).** 48 cases × 2 variants (96 runs) all PASS, 0 render gaps; full
launcher suite **186 tests / 0 failures**; Playwright live-DOM sample 4/4. Verifier sweep confirmed coverage
+ the MVVM guard, and flagged 5 decoupled/weak markers — all **strengthened to load-bearing** and re-run
green. Results: [SYNTAX-MATRIX.md](SYNTAX-MATRIX.md). Remaining (optional): runIde/Jetty visual spot-check;
user-directed commit.

**Non-goals.** `<apply>`/`<include>` (done in P2 — referenced, not re-authored); non-ZK JSP/servlet
includes; the plugin/JCEF Swing side (runIde-only, out of the launcher's headless scope); P4 (Spring Boot jar).

**Key risk & method.** A corpus is partly *discovery*: some outcomes can't be known until the first run
(esp. directives referencing external classes, the `xml` namespace, `composite`/client-property rows).
MVVM annotations are **not** discovery — the M-1 placeholder pass is established behavior (see P3.3).
Method =
**hypothesize → run → lock**: author each case with a hypothesized outcome, run, then lock the *observed*
outcome. An observed result that is a legitimate graceful behavior updates the descriptor; an observed
result that is a genuine defect (crash where Jetty/a real browser renders) is a **found gap** → filed as
a sub-item and either fixed (new RED→GREEN) or logged as explicitly accepted. See [L2 §Method](#method).

---

## L2 — Phase Breakdown

### Method (applies to every P3.x phase) {#method}

Standard TDD is "RED then GREEN". A discovery corpus refines this per case:

1. Author the fixture + a descriptor row with the **hypothesized** outcome/marker.
2. Run the corpus test → observe the **actual** result for that case, on **both** variants.
3. **Match** → locked (GREEN).
4. **Mismatch, actual is legitimate** (renders-with-placeholder where we guessed renders, or a clean
   structured failure where we guessed placeholder) → update the descriptor to the observed outcome; the
   corpus documents reality. Note the surprise in the matrix.
5. **Mismatch, actual is a defect** (uncaught exception / raw stack / crash where Jetty renders) → **found
   gap**: file as a P3 sub-item; either fix it (its own RED→GREEN) or log it in the matrix as *accepted*
   with a one-line rationale. No silent truncation — every gap is visible in the matrix.

Every marker asserted via `html.contains(...)` must be **alphanumeric, no `-`** (ZK 10 JS-escapes `-`→`\-`
in rendered widget values; a hyphenated marker false-negatives on jakarta only). Fixture *file names* may
use hyphens; only rendered *values* are affected.

### P3.0 — Harness scaffold

- **Goal.** One data-driven test that runs every case on both servlet variants with a uniform assertion switch.
- **Input.** The P2 harness (see [L3 §1](#l3-1)): `RenderEngineFactory.create`, `RenderResult`,
  `RenderError`/`RenderPhase`, `Variants.both()`, `ZkClasspathResolver`.
- **Output.** `ZulSyntaxCorpusTest.java` + `Outcome` enum + `SyntaxCase` descriptor + an initially-tiny
  `CASES` list (1–2 smoke cases) proving the cartesian `@MethodSource` and the assertion switch work.
- **Acceptance gate.** The smoke cases pass on both variants (or skip cleanly when jars unresolvable);
  the test reports per-case, per-variant rows in the JUnit output.

### P3.1 — Core syntax groups

- **Goal.** Cover the constructs that need no binder/classpath/external resource — pure loader semantics.
- **Groups (each ≥3 cases).** elements & attributes · `<attribute>` long form · EL `${…}` · `if`/`unless` · `forEach` · `<zk>` container.
- **Input/Output.** New fixtures under `fixtures/syntax/`; new descriptor rows in `CASES`.
- **Acceptance gate.** Every group has ≥3 cases, each locked to an observed outcome on both variants; suite green.

### P3.2 — Namespaces & passthrough

- **Goal.** Cover namespace selection and client/native passthrough, plus `<zscript>` (incl. its graceful-fail case).
- **Groups (each ≥3 cases).** namespaces (default/`zk`/native/client/`xml`) · native HTML passthrough ·
  client attributes (`w:` and `client/attribute`) · `<zscript>` (inline-renders / guarded / missing-class GRACEFUL_FAIL).
- **Acceptance gate.** Same as P3.1; the `<zscript>` missing-class case asserts a structured `RenderError`
  whose message names the offending class (mirrors `StructuredFailureTest`).

### P3.3 — Directives & binding

- **Goal.** Cover page directives and the attribute/variable/binding constructs — the highest-discovery groups.
- **Groups (each ≥3 cases).** processing instructions (`<?page?>` / `<?component?>` / a missing-external
  directive) · `<custom-attributes>` · `<variables>` · MVVM annotations (`@load`/`@bind`/`@init`+`@command`,
  expected **PLACEHOLDER** — pinned by run) · annotations/`<template>`.
- **Acceptance gate.** Same as P3.1; MVVM cases assert **both** sides of the placeholder contract —
  the `@load/@bind` **expression text renders as placeholder** (`present` = e.g. `vm.greeting`) **and the
  real bound value does not leak** (`absent` = e.g. `LOADED`), mirroring
  [RenderFidelityTest.java:44-60](../../zk-preview-launcher/src/test/java/org/zkoss/zkpreview/RenderFidelityTest.java#L44-L60).
  This is **established behavior, not discovery** — the M-1 placeholder pass already renders binding
  expressions as text. The deeper isolation proof (`ForbiddenLoadTracker` + a canary VM class asserting the
  hook intercepts before ZK loads the class) already lives in `RenderFidelityTest`; P3 **cross-references
  it, does not duplicate** the canary. Any genuine surprise is still recorded per [Method](#method).

### P3.4 — Matrix + Verifier sweep + deeper-oracle sampling

- **Goal.** Publish results, sample the stronger oracles, and independently confirm the corpus is honest.
- **Verification layers** (the launcher's per-case oracle is server-HTML markers; the layers below are the
  deeper confirmations, reserved for a representative sample — not all ~45 cases):
  1. **Per-case (all cases):** `RenderResult` success/`RenderError` + captured-not-guessed HTML markers.
  2. **Client-side DOM (sample):** extend `BrowserEquivalentTest`'s **Playwright (headless Chromium)** path
     to a handful of representative corpus fixtures — assert the *live DOM* actually built the widgets
     (`.z-*` classes + text), not just that the server emitted them. Falls back to the HTTP
     content-signature check when Playwright can't run (documented, skips cleanly).
  3. **Real-container equivalence (sample, manual):** for a few high-value cases, eyeball the SAME `.zul`
     under the real ZK servlet (Jetty / `manual-test`) — the preview's job is to reproduce it. **Read-only;
     port 8080 is the user's app — never bind/kill/probe it.**
  4. **Visual (sample, runIde):** JCEF-pane display spot-check — the only oracle for pixels/theme/layout,
     which headless tests are blind to (cf. the black-background bug that passed a green suite).
- **Output.** `SYNTAX-MATRIX.md` (group × case × outcome × javax-result × jakarta-result × notes);
  a **deeper-oracle sample** section listing which cases got Playwright/Jetty/runIde confirmation;
  every found gap dispositioned.
- **Acceptance gate.** (i) every one of the 15 groups has ≥3 asserted cases; (ii) matrix committed and
  matches the green suite; (iii) each found gap is either fixed or logged as explicitly accepted with a
  rationale; (iv) a Verifier pass (subagent, per [PLAN-followups §7.4](PLAN-followups.md)) confirms no case
  asserts a marker that trivially can't fail, and no group was silently skipped; (v) the deeper-oracle
  sample (layer 2 Playwright at minimum) ran green for its representative cases.

### Sequencing & size

P3.0 first (unblocks all). P3.1→P3.2→P3.3 are independent group batches (parallelizable but reviewed per
batch). P3.4 last. ~15 groups × ~3 = **~45 fixtures**; batched into 3 reviewable phases to stay within the
medium workflow-size guideline. Recommendation from the parent plan: light direct TDD for authoring,
reserve one **Verifier subagent** pass for P3.4.

---

## L3 — Technical Appendix

<a id="l3-1"></a>
### 1. P2 harness API (grounded, verbatim signatures)

All under `zk-preview-launcher`; test package `org.zkoss.zkpreview`, utils `org.zkoss.zkpreview.testutil`.

**Fixtures.** Live in `src/test/resources/fixtures/` (Gradle copies to `build/resources/test/fixtures/`).
Every render test declares `private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");`
(relative — Gradle `test` runs with the module dir as CWD, `build.gradle:59`). A `.zul` is addressed by
servlet path with leading slash: `engine.renderZul("/plain.zul")`; a subdir fixture as `/sub/x.zul`. **P3
fixtures go in `src/test/resources/fixtures/syntax/`, addressed as `/syntax/<name>.zul`.**

**RenderEngine / factory / result:**
```java
// RenderEngine (Closeable)
RenderResult renderZul(String zulPath);        // zulPath = "/syntax/foo.zul"
// RenderEngineFactory — transparently selects Javax/Jakarta from the jar list
static RenderEngine create(List<File> zkJars, Path webappDir) throws IOException;
static RenderEngine create(List<File> zkJars, Path webappDir, ForbiddenLoadTracker t);
// RenderResult
boolean isSuccess();  String getHtml();  RenderError getError();  String toJson();
// RenderError
RenderPhase getPhase();  String getMessage();  String getZulFile();
Integer getLine();  Integer getColumn();  String getStackTrace();  String toJson();
// RenderPhase enum: CLASSPATH, PARSE, COMPOSE, RESOURCE, UNKNOWN
```

**Dual-variant driver.** `testutil/Variants.both()` → `Stream<Variants.Named>`; `Named.resolve()` →
`ZkClasspathResolver.Resolution` with public fields `List<File> jars` (null on failure) + `String
skipReason`. Clean-skip: `Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);`.
`@ParameterizedTest` + `@MethodSource` is used across ~10 test classes; `junit-jupiter-params:5.10.2` is on
the test classpath (`build.gradle:44-47`), so multi-arg / cartesian `@MethodSource` is available (currently
unused — new ground for the cartesian corpus source).

### 2. Corpus test design (data-driven, cartesian cases × variants)

```java
enum Outcome { RENDERS, PLACEHOLDER, GRACEFUL_FAIL }

// group: for the matrix; fixture: under fixtures/syntax/; markers: alphanumeric, no '-'.
// present/absent are both optional (null = not checked); the pair lets one record express
// binding-placeholder correctness (expression text present AND real value absent) as
// RenderFidelityTest does (contains("vm.x") && !contains("LOADED")).
//   RENDERS / PLACEHOLDER (success path):
//     success && (present==null || html.contains(present)) && (absent==null || !html.contains(absent))
//     RENDERS vs PLACEHOLDER is documentary (matrix intent): RENDERS asserts real output via `present`;
//     PLACEHOLDER asserts a value intentionally NOT the real data (usually via `absent`, or `present`
//     = the placeholder expression text for MVVM).
//   GRACEFUL_FAIL (failure path):
//     !success && error.getMessage().contains(present) && (phase==null || error.getPhase()==phase)
record SyntaxCase(String group, String fixture, Outcome outcome,
                  String present, String absent, RenderPhase phase) {}

static final List<SyntaxCase> CASES = List.of( /* filled per phase P3.1–P3.3 */ );

static Stream<Arguments> corpus() {              // cartesian product: every case × both variants
    return CASES.stream().flatMap(c ->
        Variants.both().map(v -> Arguments.of(c, v)));
}

@ParameterizedTest(name = "{0} [{1}]")
@MethodSource("corpus")
void case_(SyntaxCase c, Variants.Named variant) throws Exception {
    ZkClasspathResolver.Resolution res = variant.resolve();
    Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);
    try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
        RenderResult r = engine.renderZul("/syntax/" + c.fixture());
        switch (c.outcome()) {
            case RENDERS, PLACEHOLDER -> {           // shared success-path assertion
                assertTrue(r.isSuccess(), () -> r.getError().toJson());
                if (c.present() != null) assertTrue(r.getHtml().contains(c.present()), r::getHtml);
                if (c.absent() != null)  assertFalse(r.getHtml().contains(c.absent()), r::getHtml);
            }
            case GRACEFUL_FAIL -> {
                assertFalse(r.isSuccess(), r::getHtml);
                assertTrue(r.getError().getMessage().contains(c.present()), () -> r.getError().toJson());
                if (c.phase() != null) assertEquals(c.phase(), r.getError().getPhase());
            }
        }
    }
}
```
`SyntaxCase.toString()` should return `group/fixture` so JUnit rows read `foreach/foreach-commalist.zul [jakarta ...]`.

### 3. Per-group case matrix (hypothesized outcomes — to be pinned by first run)

Markers are alphanumeric (no `-`). "H:" = hypothesis; the run locks the actual.

| # | Group | Case (fixture under `syntax/`) | H: outcome | Marker / note |
|---|---|---|---|---|
| 1 | elements & attributes | `elem-basic.zul` (label w/ value) | RENDERS | `ELEMBASIC` |
| 1 | | `elem-nested.zul` (window>vbox>label) | RENDERS | `zul.wgt.Label` structural |
| 1 | | `elem-el-attr.zul` (`value="${1+2}"`) | RENDERS | `3` |
| 2 | `<attribute>` long form | `attr-text.zul` (`<attribute name="value">…`) | RENDERS | `ATTRLONGFORM` |
| 2 | | `attr-multiline.zul` | RENDERS | `LINEONE` |
| 2 | | `attr-event.zul` (`<attribute name="onClick">`) | RENDERS | widget renders (no click in preview) |
| 3 | EL `${…}` | `el-arith.zul` (`${6*7}`) | RENDERS | `42` |
| 3 | | `el-empty.zul` (`${empty x}`) | RENDERS | `true` |
| 3 | | `el-if.zul` (`if="${1 gt 0}"`) | RENDERS | `ELIFSHOWN` |
| 4 | `if`/`unless` | `if-true.zul` | RENDERS | `IFTRUE` |
| 4 | | `if-false.zul` | PLACEHOLDER | `IFFALSE` (must NOT appear) |
| 4 | | `unless-true.zul` | PLACEHOLDER | `UNLESSGONE` (must NOT appear) |
| 5 | `forEach` | `foreach-commalist.zul` (`forEach="AA, BB, CC"`) | RENDERS | `AA` & `BB` & `CC` |
| 5 | | `foreach-zscript.zul` (collection from zscript) | RENDERS | `ITEMONE` |
| 5 | | `foreach-status.zul` (`forEachStatus.index`) | RENDERS | index text |
| 6 | `<zk>` container | `zk-group.zul` (siblings under `<zk>`) | RENDERS | both children |
| 6 | | `zk-if.zul` (`<zk if="false">`) | PLACEHOLDER | `ZKHIDDEN` (must NOT appear) |
| 6 | | `zk-foreach.zul` | RENDERS | repeated marker |
| 7 | namespaces | `ns-default.zul` (zul only) | RENDERS | `NSDEFAULT` |
| 7 | | `ns-zk.zul` (`xmlns:zk`, `zk:if`) | RENDERS | `NSZK` |
| 7 | | `ns-xml.zul` (`xmlns="…/2007/xml"` passthrough) | RENDERS/PLACEHOLDER | pin by run |
| 8 | native HTML passthrough | `native-table.zul` (`n:table/n:tr/n:td`) | RENDERS | `NATIVECELL` |
| 8 | | `native-with-zk-child.zul` (`<n:div><button/>`) | RENDERS | `zul.wgt.Button` |
| 8 | | `native-attr.zul` (native attr passthrough) | RENDERS | attr in HTML |
| 9 | client attributes | `client-onfocus.zul` (`w:onFocus`) | RENDERS | widget renders |
| 9 | | `client-attribute.zul` (`ca:` DOM attr) | RENDERS | DOM attr in HTML |
| 9 | | `client-prop.zul` (client property) | RENDERS | pin by run |
| 10 | `<zscript>` | `zscript-inline.zul` (new Label in Java) | RENDERS | `ZSCRIPTLABEL` |
| 10 | | `zscript-guarded.zul` (`if="false"` → skipped) | PLACEHOLDER | `ZSGUARDED` (must NOT appear) |
| 10 | | `zscript-missing-class.zul` (refs absent FQCN) | GRACEFUL_FAIL | FQCN substring; phase pin by run |
| 11 | processing instructions | `pi-page-title.zul` (`<?page title="PITITLE"?>`) | RENDERS | `PITITLE` in `<title>` |
| 11 | | `pi-component.zul` (`<?component?>` define+use) | RENDERS | `PICOMPONENT` |
| 11 | | `pi-missing.zul` (`<?taglib?>`/`<?variable-resolver?>` absent) | GRACEFUL_FAIL | pin by run |
| 12 | `<custom-attributes>` | `ca-component.zul` (read via `${self.attr}`/onClick) | RENDERS | `CAVALUE` |
| 12 | | `ca-page-scope.zul` (`scope="page"`) | RENDERS | `CAPAGE` |
| 12 | | `ca-composite-map.zul` (`composite="map"`) | RENDERS | pin by run |
| 13 | `<variables>` | `var-basic.zul` (define + `${v}`) | RENDERS | `VARBASIC` |
| 13 | | `var-local.zul` (`local="true"`) | RENDERS | `VARLOCAL` |
| 13 | | `var-composite-list.zul` (`composite="list"`) | RENDERS | pin by run |
| 14 | MVVM annotations | `mvvm-load.zul` (`@load(vm.greeting)` no VM) | PLACEHOLDER | present=`vm.greeting`, absent=`LOADED` |
| 14 | | `mvvm-bind.zul` (`@bind(vm.name)` on textbox) | PLACEHOLDER | present=`vm.name`, absent=`LOADED` |
| 14 | | `mvvm-init-command.zul` (`@init`+`onClick=@command`) | PLACEHOLDER | present=`vm.` expr text, absent=`LOADED` |
| 15 | annotations/`<template>` | `tmpl-model.zul` (`<template name="model">` + model comp) | RENDERS | template content |
| 15 | | `tmpl-page-level.zul` (bare `<template>` inert) | PLACEHOLDER | `TMPLINERT` (must NOT appear) |
| 15 | | `tmpl-if.zul` (`<template if="false">`) | PLACEHOLDER | pin by run |

**Discovery-heavy cells** (outcome genuinely unknown until run): MVVM group (14) entirely; PI missing/`xml`
namespace/`composite`/client-property rows marked "pin by run". These are where [Method](#method) steps 4–5 apply.

### 4. Grounding notes (from the ZUML reference)

- **Processing instructions are effective on direct visit and ignored under include/apply**
  (`processing_instructions.md`). The preview visits pages directly → `<?page title?>` SHOULD reach `<title>`.
- **`forEach` comma-list form** `forEach="a, b, c"` iterates without any Java collection
  (`foreach.md`) → self-contained RENDERS case with no zscript dependency.
- **MVVM `@load/@bind/@init/@command`** require a binder/ViewModel documented in `zk_dev_ref`, not
  `zuml_ref`. The preview isolation provides no VM, and the M-1 placeholder pass (already shipped) renders
  the binding **expression as literal text** while the real value never leaks — **established behavior**,
  proven in `RenderFidelityTest`. So MVVM outcome = PLACEHOLDER with `present`=expression text +
  `absent`=`LOADED`; this is a cross-reference of existing behavior, NOT discovery.
- **`<zscript>` runs at load** (`zscript.md`); inline JDK/ZK code renders, a reference to a missing class is
  the GRACEFUL_FAIL case (ties to lesson #11 / `StructuredFailureTest`'s canary).
- **native vs client vs client/attribute** (`native.md`, `client.md`, `namespaces.md`): native = raw HTML
  tag passthrough (no component); client `w:` = widget-side listeners/props; client/attribute = DOM attrs.
  The reference lists more namespaces than the parent plan enumerated (`shadow`, `xml`,
  `client/attribute-prefix`) — folded into groups 7/9; `shadow` optional (advanced, may defer).

### 5. Deliverables & file map

- `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/ZulSyntaxCorpusTest.java` (new)
- `zk-preview-launcher/src/test/resources/fixtures/syntax/*.zul` (~45 new)
- `tasks/zul-preview/SYNTAX-MATRIX.md` (new — results)
- Optional launcher fixes for any found gap (own RED→GREEN), each locked by a targeted test.
- `tasks/lessons.md` — append any correction-driven lesson.

**Standing constraints (unchanged).** `withjdk.sh 17 …` for all Gradle; launcher tests via
`withjdk.sh 17 ./gradlew :zk-preview-launcher:test [--tests "…ZulSyntaxCorpusTest"]`; never touch port 8080;
surgical, user-directed, not-pushed commits excluding the pre-existing untracked drafts; validate every new
`.zul` with `xmllint --noout` (lesson #12); markers hyphen-free (lesson #14).

### 6. Open decisions (recommendations)

1. **`shadow` namespace** — include a 4th namespace/own group, or defer as advanced? **Recommend: defer**
   (not core ZUML syntax; note in matrix as not-covered-by-design).
2. **One corpus test class vs per-batch classes** — **Recommend: one `ZulSyntaxCorpusTest`** with the
   cartesian source; the `group` field already segments the matrix.
3. **Verifier depth for P3.4** — full fable-commander Maker/Verifier loop, or a single Verifier sweep?
   **Recommend: single Verifier sweep** (authoring is mechanical; the value is an honesty check on markers/coverage).

### 7. Change Log

| ver | date | change |
|---|---|---|
| v0 | 2026-08-04 | Initial P3 plan; harness grounded against P2 tests; 15 groups × ≥3 cases hypothesized; cartesian data-driven design |
| v0.1 | 2026-08-04 | Descriptor gains `present`/`absent` marker pair (binding-placeholder = expr-text present + real-value absent); MVVM reclassified established-not-discovery (M-1 placeholder pass, cross-refs `RenderFidelityTest`); P3.4 adds deeper-oracle sampling (Playwright DOM / Jetty equivalence / runIde visual) |
| v1 | 2026-08-04 | **P3.0–P3.3 IMPLEMENTED.** 48 fixtures under `fixtures/syntax/` + `ZulSyntaxCorpusTest` (16 groups, cartesian × 2 variants) — 96 runs all PASS, 0 gaps; every hypothesis matched first-run on both variants. Full launcher suite 182/0. `SyntaxCorpusBrowserSampleTest` (Playwright live-DOM, 4 cases) PASS. `SYNTAX-MATRIX.md` published. P3.4 Verifier sweep in flight |
| v1.1 | 2026-08-04 | **P3.4 DONE.** Verifier sweep confirmed coverage + MVVM guard architecturally real; flagged 5 decoupled/weak markers (client-onfocus, client-shortcut, attr-event, native-with-zk-child, ns-zk) — all strengthened to load-bearing (marker in listener body / long-form-set property / native `fieldset` hard-fail net / `zk:if="false"` negative proof) + attr-multiline→LINETWO. Re-run 96/0; full suite **186/0**. P3 complete (headless) |

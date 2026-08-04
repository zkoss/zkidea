# ZUL Preview — Follow-ups PLAN (state file)

> Source: [tasks.md](tasks.md) (the four open issues). Builds on the shipped v1 in [PLAN.md](PLAN.md).
> Related: [../error-reporting/PLAN.md](../error-reporting/PLAN.md).

- **Version**: v5
- **Status**: P1 DONE (headless + manual verified); P2 DONE (headless) incl. a `~./` classpath-resource
  fix (runIde re-test pending); **P3 DONE (headless)** — see
  [PLAN-P3-syntax-corpus.md](PLAN-P3-syntax-corpus.md) + [SYNTAX-MATRIX.md](SYNTAX-MATRIX.md) (48 cases ×
  2 variants, 0 gaps; Playwright live-DOM sample); **P4 DONE (headless)** — Spring-Boot-jar docroot rule
  in `DocrootResolver` (`<resourceRoot>/web` classpath web root) + `DocrootResolverTest` (9 green) +
  minimal `manual-test-springboot/` sample; see
  [PLAN-P4-springboot-jar.md](PLAN-P4-springboot-jar.md) + [MANUAL-springboot-jar.md](MANUAL-springboot-jar.md)
  (runIde verification pending)
- **Scope**: robustness (P1), render-path coverage (P2, P3), new environment support (P4)

---

## 1. Source & scope

From [tasks.md](tasks.md), four issues, regrouped by type:

| # | Issue | Type |
|---|---|---|
| **P1** | Handle JCEF unavailable — detect the *reason*; if the boot JDK has no JCEF, guide setup; **or** offer an external-browser link | Implementation |
| **P2** | `<apply>` / `<include>` work in the preview | Coverage (+ likely launcher fix for `<include>`) |
| **P3** | ZUL syntax per the ZUML reference — ≥3 cases per syntax group, verify each renders (or fails gracefully) | Coverage |
| **P4** | Preview works inside a Spring Boot **jar**-packaging project | Implementation + new fixture |

## 2. Standing constraints (do not violate)

- **JVM/Gradle**: run everything through `withjdk.sh 17 …` (default JDK is 11).
- **Port 8080 is the user's app** — never bind, kill, or probe it. The preview server picks its own ephemeral port; any sample project must set `server.port` ≠ 8080.
- **Surgical commits**: exclude pre-existing untracked files (`.vscode/`, `GEMINI.md`, `doc/potential-ideas.md`, `doc/zul_preview_product_positioning.md`, `prompts/`). Commits are user-directed and not pushed.
- **TDD**: failing test(s) first (RED → GREEN), then implement.
- **Headless vs runIde**: the launcher renders ZUL headlessly → that *is* the production render path, so launcher tests are real coverage. JCEF display + Swing wiring are runIde-only (lesson #1) → manual test docs.

## 3. Current state (grounded)

**P1 — JCEF.**
- Split editor is offered regardless of JCEF: [ZulPreviewFileEditorProvider.java:39-43](../../src/main/java/org/zkoss/zkidea/preview/ZulPreviewFileEditorProvider.java#L39-L43).
- Single availability check: [ZulPreviewFileEditor.java:106](../../src/main/java/org/zkoss/zkidea/preview/ZulPreviewFileEditor.java#L106) `if (!JBCefApp.isSupported())` → shows one **generic** message that lumps both causes together, then **`return`s before starting the server** ([ZulPreviewFileEditor.java:106-112](../../src/main/java/org/zkoss/zkidea/preview/ZulPreviewFileEditor.java#L106-L112)). No reason detection, no external-browser path.
- Outbound-link → external browser already exists for links *clicked inside* the pane (`installExternalLinkHandler`, [ZulPreviewFileEditor.java:147-161](../../src/main/java/org/zkoss/zkidea/preview/ZulPreviewFileEditor.java#L147-L161)) via `BrowserUtil.browse`. There is **no** "reopen the whole preview externally" action.

**P2 — apply / include.**
- `<apply>`: handled by the isolation hook `PreviewUiFactory` (`newComposer`/`getPageDefinition`) and covered by `ApplyTemplateUriTest` (javax + jakarta), fixtures `apply-templateuri-annotation.zul` / `apply-templateuri-missing.zul`. Tests assert *doesn't crash*, not *applied content actually renders*.
- `<include>`: **zero coverage anywhere** — no fixtures, no tests, no hook handling. Highest-risk unknown (ZK `<include>` uses `RequestDispatcher.include()`; the launcher's mock servlet env may not support it).

**P3 — syntax corpus.** Fixtures today are ad-hoc render cases under [manual-test/src/main/webapp/preview/cases/](../../manual-test/src/main/webapp/preview/cases/); no systematic per-syntax-group matrix.

**P4 — Spring Boot jar.** No Spring Boot project exists in the repo (grep clean). `ZulPreviewServerService` resolves docroot/classpath assuming a Maven **WAR / `src/main/webapp`** layout; a SB jar keeps ZUL on the classpath (`src/main/resources/web/…`) with no webapp dir — resolution almost certainly needs work.

## 4. Work items × gates

| Phase | Deliverable | Gate (objective) | Headless? |
|---|---|---|---|
| **P1** | Reason diagnosis + external-browser fallback | Pure diagnosis unit-tested; runIde manual shows targeted msg + working external link | mixed |
| **P2** | apply render-assert + full include coverage (fix launcher if broken) | Launcher tests green on javax + jakarta | yes |
| **P3** | Per-syntax-group fixture corpus + data-driven launcher test + results matrix | Every group has ≥3 cases with an asserted expected outcome | mostly |
| **P4** | Minimal ZK+SpringBoot-jar sample + docroot/classpath resolution for the resources layout | Resolution unit-tested; runIde manual renders a SB-jar ZUL | mixed |

## 5. Phase detail

### P1 — JCEF unavailable: diagnose + external-browser fallback

**Goal.** (1) When JCEF is unavailable, tell the user *why* and how to fix it; (2) still let them see the preview by opening the localhost render URL in the system browser.

**Approach.**
- **Diagnosis (pure, testable).** Extract a small pure helper `JcefAvailability.diagnose(...)` taking probe inputs → `{reason, guidance}`:
  - registry `ide.browser.jcef.enabled` is off (`Registry.is(...)`) → "JCEF is disabled in this IDE; enable `ide.browser.jcef.enabled` and restart."
  - boot runtime is not a JetBrains Runtime with JCEF (`java.runtime.name` / `SystemInfo`) → "The IDE is running on a JDK without JCEF; switch the Boot Java Runtime (Find Action ▸ *Choose Boot Java Runtime for the IDE*) to a JetBrains Runtime."
  - otherwise → generic "bundled JCEF incompatible" message.
  - First confirm whether the platform (233–251) exposes a reason API on `JBCefApp`; if yes, prefer it; if not, use the environment probes above. **Investigate, don't assume the API.**
- **External-browser fallback (the key value).** Restructure `startPreview()` so the JCEF-unavailable branch **does not early-return**: start the preview server as normal, and on `READY`, instead of `new JBCefBrowser(url)`, show a message card with an **"Open preview in external browser ↗"** `ActionLink` → `BrowserUtil.browse(previewUrl)`. The server/`/zkau` keep working for the external browser; lifecycle/disposal unchanged.

**Deliverables.** pure `JcefAvailability` + JUnit5 tests (RED first); `ZulPreviewFileEditor` restructure; manual doc `MANUAL-jcef-fallback.md` (force off via `-Dide.browser.jcef.enabled=false` in `runIde`).

**Acceptance.**
- Registry-off case names that cause + fix; JDK-without-JCEF case tells user to switch boot runtime.
- In *every* unavailable case, an "Open in external browser" link renders the ZUL in the system browser.
- Diagnosis logic fully headless-tested.

**Status — headless DONE, manual pending.**
- `JcefAvailability` (pure `diagnose(jcefEnabledInRegistry, javaVendor)` → `{reason, explanation}`;
  boot-JDK check first so it wins over registry) + `JcefAvailabilityTest` (7 cases) — green.
  Investigation: JCEF ships with the JBR, not the IDE jars (no `JBCefApp` in any of the 336 platform
  jars), so there is no portable reason API — the environment-probe function is the right call.
- `ZulPreviewFileEditor`: constructor no longer early-returns on `!isSupported()` (stores the
  diagnosis); `startPreview()` READY branch shows `CARD_EXTERNAL` (diagnosis text + "Open preview in
  external browser" → `BrowserUtil.browse` + GitHub report) when JCEF is unavailable.
- Full plugin suite green (316). Manual runIde: [MANUAL-jcef-fallback.md](MANUAL-jcef-fallback.md) — verified by
  user 2026-08-03 (registry-disabled variant; screenshot `doc/jcef-unavailable.png`).

### P2 — apply / include coverage

**Goal.** Prove `<apply>` renders the applied template's content (not just "no crash"), and establish `<include>` from zero.

**Approach (TDD — fixtures + failing launcher tests first).**
- **apply**: strengthen assertions to check applied-template output appears in the rendered HTML.
- **include**: new fixtures — `include-static.zul` (+ included sibling), `include-annotation.zul` (`@load` dynamic `src` → neutralized/empty, like apply), `include-missing.zul` (missing `src` → graceful structured failure). Mirror `ApplyTemplateUriTest` across javax + jakarta.
- **If include fails** in the mock servlet env (likely — `RequestDispatcher.include`), that failing test is the RED for a launcher fix: teach the mock servlet context/dispatcher to resolve includes against the preview docroot. New tests gate the fix.

**Deliverables.** launcher fixtures + tests; matching manual-test fixtures under [manual-test/src/main/webapp/preview/](../../manual-test/src/main/webapp/preview/); optional launcher dispatcher fix.

**Acceptance.** apply + all include cases green on both variants; missing-src yields the structured error page, not a raw stack.

**Status — DONE (headless), no launcher fix needed.**
- **apply**: new `apply-static.zul` + `apply-static-template.zul`; new render-assert in
  `ApplyTemplateUriTest` (`staticTemplateUri_appliesTemplateContentIntoTheHost`) proving the applied
  template's *content* (its label text + its button widget) appears in the rendered HTML — not just
  "no crash". The pre-existing annotation/missing cases are unchanged.
- **include**: new `IncludeTest` (javax + jakarta) over three fixtures — `include-static.zul`
  (+ `include-static-fragment.zul`), `include-annotation.zul`, `include-missing.zul`. All green.
- **Key finding — the predicted `RequestDispatcher.include` fix was NOT needed.** Both mock
  `getRequestDispatcher(...)` still return `null`, yet a static `.zul` include renders its content.
  ZK resolves a `.zul` `<include>` via **instant include** (loads the page definition and builds the
  child components inline within the same execution — file-based, exactly like `<apply>`), so it
  never routes a `.zul` through `RequestDispatcher.include()`. Missing-src include produces a
  structured failure naming the path (same as apply-missing). RequestDispatcher would only matter for
  non-ZK includes (JSP/servlet), which are out of scope for the ZUL preview.
- manual-test display spot-checks added under `preview/cases/`: `apply-static.zul`,
  `include-static.zul` (+ their targets). Full launcher + plugin suite green.
- **Path-form coverage** (`PathResolutionTest`, javax + jakarta): both `<apply>` and `<include>`
  resolve their target across all three ZK path forms, each a different mechanism —
  **absolute** `/foo.zul` (docroot), **relative** `../foo.zul` (resolved vs the including page's
  URI, page under `/sub/` → target at root), and **`~./foo.zul`** (ZK `ClassWebResource`,
  classpath `/web/`, not the docroot). The `~./` fixture lives at `src/test/resources/web/`
  (outside the docroot `fixtures/`), so its content appearing proves genuine classpath resolution.
  All 8 green — no launcher fix needed.
  - *Production finding (`~./`) — bug FOUND and FIXED.* The user hit `Page not found:
    ~./classweb-fragment.zul` in runIde (jakarta/ZK 10) while the same page renders under Jetty —
    proving the path is correct and the launcher classpath was the cause. Root cause: the launcher
    classpath was **jars-only** (`ZkClasspathFilter.filterLibraryJars` keeps only `file.isFile()`),
    so a user's own `~./` resource — which lives in a resource **directory** — was never passed.
    **Fix:** `resolveTarget` now also hands the module's **resource roots** (`filterResourceRoots` →
    `getSourceRoots(RESOURCE)`, directories) to the launcher classpath — but **not** the module
    *output* dir (AC-4(i) still excludes that; a resource root holds no compiled user classes, so
    the `UiFactory`-hook isolation is unaffected). Headless proof: `ClasspathResourceResolutionTest`
    (javax + jakarta) — `~./` resolves iff its resource root is on the render classpath — and
    `ZkClasspathFilterTest.filterResourceRoots…`. runIde end-to-end confirmation: cases 5/6 in
    [MANUAL-apply-include.md](MANUAL-apply-include.md) (__PENDING__ user re-test).
    - Incidental: ZK 10 (jakarta) JS-escapes `-` as `\-` in rendered widget values — test markers
      asserted against rendered HTML must be hyphen-free (lesson #14).

### P3 — ZUL syntax corpus (ZUML reference)

**Goal.** For each ZUML syntax group, ≥3 cases, each with an asserted expected outcome (renders / renders-with-placeholder / fails-gracefully).

**Syntax groups** (from the ZUML reference; ≥3 cases each): namespaces (default/`zk`/native `n:`/client/`zml`) · elements & attributes · `<attribute>` long form · `<zscript>` · EL `${…}` + MVVM `@load/@bind/@init/@save/@command` · `<zk>` container · `if`/`unless` · `forEach` · `<apply>` (→ P2) · `<include>` (→ P2) · `<custom-attributes>` · `<variables>` · native-namespace HTML passthrough · client-namespace attributes (`w:`) · processing instructions (`<?page?>`,`<?component?>`,`<?taglib?>`,`<?init?>`,`<?variable-resolver?>`, …) · annotations / `<template>`.

**Approach.** Because the launcher renders headlessly, make this **data-driven launcher tests**, not manual: a fixture per group + a parameterized JUnit5 test that renders each and checks against an expected-outcome table. Manual runIde only for a display spot-check sample. Reference the syntax via the `zk-doc` MCP / `dev-ref-7.0` while authoring cases.

**Deliverables.** fixture corpus under `zk-preview-launcher/src/test/resources/fixtures/syntax/`; parameterized test + expected-outcome table; results matrix `SYNTAX-MATRIX.md`; any newly-found render gaps filed as sub-items.

**Acceptance.** every group has ≥3 asserted cases; matrix committed; any gap is either fixed or explicitly logged as accepted.

### P4 — Spring Boot jar-packaging project

**Goal.** Preview renders for a ZK-on-Spring-Boot **jar** project (ZUL on the classpath, no `webapp` dir).

**Approach.**
1. Build a minimal committed sample (sibling to `manual-test/`, e.g. `manual-test-springboot/`): ZK starter, `packaging=jar`, ZUL under `src/main/resources/web/`, `DHtmlLayoutServlet` via `ServletRegistrationBean`, `server.port` ≠ 8080.
2. **Investigate** `ZulPreviewServerService` docroot/classpath resolution (assumes `src/main/webapp`) → add detection of the SB resources layout (`src/main/resources/web`) + classpath docroot. Keep resolution logic pure → unit tests (RED first).
3. runIde manual: open a SB-jar ZUL → preview renders.

**Deliverables.** the sample project; resolution change + unit tests; manual doc `MANUAL-springboot-jar.md`.

**Acceptance.** docroot/classpath resolution for the SB layout unit-tested; manual pass renders a SB-jar ZUL; WAR/manual-test path unaffected (regression check).

## 6. Sequencing & dependencies

1. **P1** first — self-contained, high user value, mostly independent.
2. **P2** — establishes the launcher render-assert harness that **P3** reuses (P3 cross-references apply/include).
3. **P3** — broad corpus on P2's harness.
4. **P4** — needs a new sample project (setup cost); independent of P1–P3.

P2 and P3 share the launcher-test harness; P1 and P4 touch the plugin side and can proceed in parallel with P2/P3 if desired.

## 7. Decisions needed (recommendations)

1. **P1 scope** — do both diagnosis *and* the external-browser link? **Recommend: both** (the link is the real workaround; diagnosis is the guidance). The `or` in tasks.md reads as complementary, not exclusive.
2. **P4 sample** — commit a minimal SB-jar sample into the repo (like `manual-test/`), or keep it external/throwaway? **Recommend: commit a minimal one** so the resolution fix stays regression-testable.
3. **P3 execution** — automated launcher corpus vs a manual matrix? **Recommend: automated launcher tests** (render is headless), manual only as a display spot-check.
4. **Process** — run these as the heavy fable-commander loop (Maker/Verifier subagents, per-round verdicts) like v1, or a lighter direct TDD loop? **Recommend: lighter direct TDD** for P1/P2/P4; reserve a Verifier pass for P3's corpus.

## 8. Revision log

| ver | date | change |
|---|---|---|
| v0 | 2026-07-31 | Initial draft from tasks.md (P1–P4), grounded against current code |
| v1 | 2026-07-31 | P1 implemented (headless): `JcefAvailability` + 7 tests; editor external-browser fallback; MANUAL-jcef-fallback.md. Manual runIde pending |
| v2 | 2026-08-03 | P1 manual verified (user). P2 DONE (headless): apply render-assert + `IncludeTest` (static/annotation/missing) on javax+jakarta; finding — `<include>` needs no `RequestDispatcher` fix (ZK instant-include for `.zul`); manual-test spot-checks added |
| v3 | 2026-08-04 | P2 path-form coverage (`PathResolutionTest`) + `~./` classpath-resource **bug fix**: user hit `Page not found` on a user-project `~./` page (works under Jetty); `resolveTarget` now passes module resource roots to the launcher classpath (`filterResourceRoots`, not output dir); `ClasspathResourceResolutionTest` locks the mechanism. runIde re-test pending |
| v4 | 2026-08-04 | P3 DONE (headless): syntax corpus + manual fixtures + EL implicit-objects coverage committed (`a6ea674`) |
| v5 | 2026-08-04 | **P4 DONE (headless):** Spring-Boot-jar docroot rule — `DocrootResolver` now treats `<resourceRoot>/web` (ZK classpath web root) as the docroot so an SB-jar page previews at its production url (`/index.zul`), not `/src/main/resources/web/index.zul`. 3-arg `resolve(zul, boundaryRoots, resourceRoots)` overload (2-arg preserved → WAR untouched); `resolveTarget` wires the module RESOURCE roots through. `DocrootResolverTest` 9 green (2 SB + negative guard + WAR-wins ordering); full plugin suite green (321). Minimal `manual-test-springboot/` sample added. runIde verification pending — [MANUAL-springboot-jar.md](MANUAL-springboot-jar.md) |

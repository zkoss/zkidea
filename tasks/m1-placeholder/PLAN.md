# M-1 — Binding-expression placeholder rendering

> Implements mitigation **M-1** from `doc/zul_preview_product_positioning.md §2`:
> render MVVM binding expressions as visible, dimmed placeholder text (e.g. a label
> bound to `@load(vm.name)` shows `vm.name`) instead of blank — turning the biggest
> "perceived-as-bug" (empty bound values, limitation **L-2**) into a wireframe-style
> feature, **without breaking the isolation guarantee** (user classes never load).

Branch: `preview`. Module: `zk-preview-launcher`.

---

## 1. Problem

The preview's isolation seam (`PreviewUiFactory` → no-op `PreviewComposer`) blocks the
MVVM `BindComposer` from ever running, so no `Binder` resolves `@load/@bind/@save`
expressions. Result: every bound property renders **empty**. A user opening a
data-heavy MVVM page sees blank labels/fields and reads it as broken (positioning §2).

## 2. Approach (decision)

Add a **`UiLifeCycle` listener** (`PlaceholderInjector`) registered in the launcher's
bundled `zk.xml`. On `afterComponentAttached(comp, page)` it reads the component's
compile-time binding **annotations** (pure metadata parsed from the ZUL — no class
loading) and writes the *expression text* into the matching display property.

Why a `UiLifeCycle` listener rather than folding it into `PreviewComposer`:
- **Uniform coverage** independent of whether a composer is applied to a subtree.
- **Single responsibility**: `PreviewComposer` stays a pure no-op (isolation only);
  placeholder rendering is an independent, separately-toggleable concern.
- Same `hooks` sourceSet (compiled against `zk:9.6.0.2`, javax), uses only stable
  zk-core API present unchanged through `10.1.0-jakarta`.

### Verified facts (via `javap` / disassembly of the real ZK jars)
- `org.zkoss.zk.ui.sys.ComponentCtrl`: `List<String> getAnnotatedProperties()`,
  `Collection<Annotation> getAnnotations(String prop)`. (every Component is a `ComponentCtrl`)
- `org.zkoss.zk.ui.metainfo.Annotation`: `String getName()`, `String getAttribute(String)`.
- ZK's own `org.zkoss.bind.impl.AnnotateBinderHelper` reads the binding expression via
  `annotation.getAttribute("value")` — so `@load(vm.name)` ⇒ name=`load`, `getAttribute("value")`=`vm.name`.
- Binding annotation names ZK emits: `id, init, load, save, bind, ref, converter,
  validator, command, global-command, template`. Value-display bindings = **`{load, save, bind}`**.
- `org.zkoss.zk.ui.util.UiLifeCycle.afterComponentAttached(Component, Page)` (7 methods total).
- `org.zkoss.zk.ui.HtmlBasedComponent.setStyle(String)` for the dim effect.

## 3. Implementation

### 3.1 New file — `zk-preview-launcher/src/hooks/java/org/zkoss/zkpreview/hooks/PlaceholderInjector.java`
Implements `UiLifeCycle` (6 no-op methods + `afterComponentAttached`):
1. If isolation disabled (`-Dzkpreview.isolation=false`, same `zkpreview.isolation`
   literal as `PreviewUiFactory`), **return** — canary mode lets real bindings resolve.
2. `if (!(comp instanceof ComponentCtrl)) return;`
3. For each `prop` in `getAnnotatedProperties()`, for each `Annotation ann` in
   `getAnnotations(prop)`:
   - if `ann.getName()` ∈ `{load, save, bind}`:
     - `String expr = ann.getAttribute("value");` (fallback: first value in
       `getAttributes()` if `"value"` absent); skip if blank.
     - reflectively invoke `set<Cap(prop)>(String)` on `comp` with `expr`
       (`value`→`setValue`, `label`→`setLabel`, `title`→`setTitle`, …). `NoSuchMethod`
       ⇒ **skip** (this is the guard that keeps non-text bindings like `model`/`checked`
       untouched — scopes M-1 to textual display properties only).
     - best-effort dim: if `comp instanceof HtmlBasedComponent` and its style is
       empty, `setStyle("color:#9aa0a6;font-style:italic")`.
     - break to next `prop` after the first match.
   - Never throw — wrap reflection in try/catch; a placeholder failure must not break render.

### 3.2 Register in `zk-preview-launcher/src/main/resources/preview/zk.xml`
Add, inside `<zk>`:
```xml
<listener>
    <listener-class>org.zkoss.zkpreview.hooks.PlaceholderInjector</listener-class>
</listener>
```
(`processResources` rebuilds `zkpreview-hooks.jar`, so the new class + zk.xml ship automatically.)

## 4. Tests (TDD — RED first)

Oracle = rendered HTML through the real ZK engine (`RenderFidelityTest`), both servlet
variants. Tests skip cleanly (`Assumptions`) if no ZK classpath resolves; the jakarta
variant resolves from cached `~/.m2` via `manual-test/pom.xml` and should run.

- **New fixture** `src/test/resources/fixtures/binding-placeholders.zul`: a `<window
  title="@load(vm.pageTitle)" viewModel="@id('vm') @init('…CanaryViewModel')">` with
  `<label value="@load(vm.greeting)"/>`, `<textbox value="@bind(vm.name)"/>`, a static
  `<label value="static"/>`, and `<grid model="@load(vm.rows)"/>`.
- **RED assertions** (fail before §3, pass after):
  - `RenderFidelityTest.fixtureB` — add `assertTrue(html.contains("vm.greeting"))`.
  - New `fixtureF_bindingExpressionsRenderAsPlaceholders`: html contains `vm.greeting`,
    `vm.name`, `vm.pageTitle`, `static`; **not** `LOADED`/`CANARY`; `tracker` empty
    (isolation intact); **not** `vm.rows` (model = non-text binding, scoped out).
- **Guard (already green, prevents over-reach)**: `fixtureC` — add
  `assertFalse(html.contains("missing.prop"))` (plain EL `${…}` is not an annotation → untouched).

## 5. Verification / success criteria
1. `withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "*RenderFidelityTest*"` — all green.
2. Full `:zk-preview-launcher:test` — no regressions; `IsolationTest`,
   `IsolationChildProcessTest`, `RealWorldSmokeTest`, fixtures A/D/E still green.
3. Isolation adversarially re-confirmed: `ForbiddenLoadTracker` empty; canary mode
   (`isolation=false`) still leaks the real value (placeholder gated off).

## 6. Out of scope (v1)
Typed/sample-value placeholders (positioning §6), EL `${…}` rendering, converters/formatting,
per-component style theming. M-1 is the fidelity **ceiling** under the isolation guarantee.

---

## 7. Review — results (executed via workflow `m1-placeholder`, TDD RED→GREEN→VERIFY)

**Status: DONE & verified.** Independent clean re-run
(`cleanTest :zk-preview-launcher:test`): **38 tests, 0 failures, 0 errors, 0 skipped,
BUILD SUCCESSFUL**, both servlet variants ([1] javax 9.6.0.2, [2] jakarta 10.1.0).

- **RED** — 4 assertions failed as designed (empty bound values): `fixtureB`/`fixtureF`
  ×2 variants. Guard fixtures A/C/D/E stayed green (EL not hijacked, no over-reach).
- **GREEN** — first cycle; `PlaceholderInjector.java` created verbatim from the design,
  `zk.xml` `<listener>` added. 12/12 fidelity tests pass.
- **VERIFY** — full suite green; isolation intact (`ForbiddenLoadTracker` empty,
  `CoreIndependenceTest`/jdeps clean), canary mode preserved (isolation-off still
  ClassNotFound on the canary), EL untouched (`fixtureC`), model-binding scoped out
  (`vm.rows` absent).

**Files:** new `PlaceholderInjector.java` (hooks), new fixture `binding-placeholders.zul`;
modified `zk.xml` (+3), `RenderFidelityTest.java` (+29). An unrelated `.gitignore` edit
the workflow introduced was reverted (kept the change surgical). **Not committed** —
awaiting your go-ahead.

**Known minor (non-blocking) refinement:** the whitelist is on the annotation *name*
(`load/save/bind`), so any String-typed property carrying such a binding gets the
expression — including non-display String props like `width`/`sclass`. Harmless
(invalid CSS is ignored; isolation/render unaffected) and consistent with the wireframe
intent, but if we want to be strict we could restrict targets to a display-property set
(`value`/`label`/`title`/`content`/`tooltiptext`). Left as a deliberate, easily-tightened
choice; no test covers it yet.

# Grid / listbox preview coverage & baseline

Fixture set exercising the common ZK data-component usages (patterns catalogued from
~835 grid/listbox ZULs across `/Users/hawk/Documents/workspace/SUPPORT`, richest in
`zk8support`/`zk9support`/`zk7support`/`zk10support`). Each is rendered two ways:

- **Jetty** (real app, real data) — the baseline: `mvn -f manual-test/pom.xml jetty:run`
  → `http://localhost:8080/plugin-test/preview/cases/<name>.zul`
- **Preview** (isolated launcher, placeholder data) →
  `java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar --classpath <zk jars>
  --webapp manual-test/src/main/webapp --port 9123` → `/preview/cases/<name>.zul`

Backing data: `com.example.plugin.test.preview.PreviewCasesVM` (+ `Product`) — loaded by
Jetty, **never** by the preview (isolation; the FQCN is just annotation text).

## Cases (`manual-test/src/main/webapp/preview/cases/`)

| Fixture | Pattern | Jetty | Preview | Preview shows |
|---|---|---|---|---|
| `grid-mvvm` | G1 MVVM `<rows><template>`, paging, sort, mixed cells | 200 | 200 | 3 rows, `item.*` placeholders + real columns |
| `grid-static-form` | G2 static label/value form grid | 200 | 200 | identical (no bindings) |
| `grid-grouping` | G4 `GroupsModel` group/groupfoot | 200 | 200 | 3 **plain** rows (no group headers — see limits) |
| `grid-detail` | G5 inline row `<detail>` (collapsed) | 200 | 200 | 3 rows, collapsed detail present |
| `grid-frozen` | G6 frozen columns | 200 | 200 | 3 rows, frozen column |
| `grid-classic-zscript` | G3 classic `${}` model from `<zscript>` | 200 | 200 | **real** Apple/Banana/Cherry (zscript executes) |
| `listbox-mvvm` | L1 template, listhead, selection, checkmark, paging | 200 | 200 | 3 items, `item.*` placeholders |
| `listbox-static` | L2 static listitems | 200 | 200 | identical |
| `listbox-grouping` | L4 `GroupsModel` | 200 | 200 | 3 plain items (no group headers) |
| `listbox-select-mold` | L6 `mold="select"` + model | 200 | 200 | 3 placeholder options |
| `combobox` | C1 static + C2 model (with/without template) | 200 | 200 | placeholder comboitems (`vm.tags[i]`, `p.name`) |
| `selectbox` | C3 `selectbox` + model | 200 | 200 | 3 placeholder options |
| `tree-mvvm` | T1 MVVM tree + model + template | 200 | 200 | 3 top-level nodes, `node.*` placeholder cells |
| `tree-static` | T2 static nested treeitems | 200 | 200 | identical (nested) |

Verified: on MVVM cases Jetty shows real data (`Keyboard`, `Displays`…) while the preview
shows placeholders (`item.name`) and the real value never leaks into the preview
(isolation clean). Static/zscript cases are byte-comparable in shape.

## Findings

1. **Bug caught & fixed (rows timing).** The initial model-placeholder impl injected the
   synthetic model at `afterComponentAttached`, i.e. *before* a grid's explicit `<rows>`
   had composed, so ZK auto-created a second rows → `"Only one rows child is allowed"`
   (500). The `<rows><template>` idiom is common (217 files use `<rows>`), so this mattered.
   Fix: inject at `PreviewComposer.doAfterCompose` (post-composition, mirroring the real
   binder). Locked by `RenderFidelityTest.fixtureH`.
2. **zscript executes in the preview.** `${}` models built in `<zscript>` render **real**
   rows (BeanShell runs), matching Jetty exactly — including reproducing a real authoring
   error (`<rows>` + compose-time model → "Only one rows child") verbatim.
3. **No-template fallback is safe.** Grid/listbox with a model but no `<template>` render
   via ZK's default per-item rendering (no error).

## Known limitations (by design / deferred)

- **Grouping (G4/L4)** renders as plain placeholder rows — a synthetic `ListModelList`
  isn't a `GroupsModel`, so `model:group`/`model:groupfoot` templates don't fire (no group
  headers/footers). Acceptable v1 degradation.
- **Tree** is supported (synthetic `DefaultTreeModel`: root → 3 branches → 2 leaves).
  Top-level nodes render at first paint (child nodes on expand), matching ZK. Authoring
  note: with a `DefaultTreeModel` the template var is the `DefaultTreeNode`, so cells bind
  `@load(node.data.x)` (via `.data`), **not** `@load(node.x)` — the latter renders empty
  and errors in the browser (this is what the `tree-mvvm` fixtures use).
- **Java `rowRenderer`/`itemRenderer`** (no template) — nothing to draw without running the
  class; stays empty (cannot preview by design).

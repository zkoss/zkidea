# M-2 — Name & frame the feature as "Layout Preview"

Source: `doc/zul_preview_product_positioning.md` §2, mitigation **M-2** —
*"在 UI、文件與 marketplace 文案中命名並框定為「Layout Preview」——絕不用「live app preview」."*
Cost: near-zero. Effect: set expectations **before** the first render, so a
placeholder/first-paint-only layout doesn't read as a broken live app.

## Canonical name
**"Layout Preview"** (proper noun). Never "live preview" / "live app preview" /
"real-time preview" in user-facing copy. Internal/technical prose may still say
"the preview server" / "render" (that names the helper process, not the product).

## Changes (surgical — user-facing naming only)

### UI strings
1. `ZulPreviewFileEditorProvider.java` — split editor name `"ZUL Preview"` → `"Layout Preview"`.
2. `ZulPreviewFileEditor.java#getName()` — `"Preview"` → `"Layout Preview"`.
3. `ZulPreviewFileEditor.java` — JCEF-missing message `"Preview unavailable…"` → `"Layout Preview unavailable…"`.
4. `PreviewResult.java#noZkJars()` — `"…enable the live preview."` → `"…enable the Layout Preview."` (drops the forbidden "live" framing).

### Docs
5. `doc/feature_overview.md` §10 — heading `## 10. ZUL Preview` → `## 10. Layout Preview`; intro drops "live", frames as a layout/first-paint preview with placeholder binding values.
6. `README.md` — `### ZUL Preview` → `### Layout Preview`; bullet label `**Live Preview**` → `**Layout Preview**`, reworded off "live".

### Marketplace copy
7. `plugin.xml <description>` — add a **Layout Preview** feature block (layout/wireframe, binding values shown as placeholders, ViewModel/Composer never runs in the IDE, first-paint only). This is the expectation-setter the positioning doc calls for.

### Out of scope (deliberately untouched)
- "Starting ZK preview server…" loading label and first-run latency framing → **M-3**.
- Internal task/spec files (`tasks/**`, `doc/zul_preview_spec.md`) — not user/marketplace docs.
- The `EDITOR_TYPE_ID` (`"zkidea-zul-preview"`) — a persisted id, not user-facing; changing it would orphan saved editor state.

## Success criteria (verify)
- RED first: naming tests fail against current strings.
- GREEN: `PreviewResultNamingTest` + `ZulPreviewFileEditorProviderTest` naming assertions pass; no "live preview" survives in user-facing strings/docs.
- Full plugin test suite: no regressions.

## Review — DONE & verified

All 7 changes applied (§ above). Verification:
- **RED confirmed:** `PreviewResultNamingTest` failed on the "Layout Preview" assertion against the old `"live preview"` string.
- **GREEN confirmed:** both naming tests pass — `PreviewResultNamingTest` (pure unit) and the new `ZulPreviewFileEditorProviderTest#testCreateEditor_namesFeatureLayoutPreview` (asserts split-editor **and** preview-pane `getName()` == "Layout Preview").
- **No regressions:** full suite (root + `zk-preview-launcher`) BUILD SUCCESSFUL.
- **Residual sweep:** no user-facing "ZUL Preview"/"live preview" remains in `src/main`, `README.md`, or `feature_overview.md §10`. The only surviving hits are internal (a `LOG.warn` string + `ZulPreviewServerService` javadoc/architecture prose naming the helper *process*, and the "never call it *live preview*" note itself) — deliberately out of scope.

Tests added: `PreviewResultNamingTest`; `ZulPreviewFileEditorProviderTest#testCreateEditor_namesFeatureLayoutPreview`.

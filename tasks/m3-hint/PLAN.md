# M-3 — In-pane "these are placeholders" hint

Source: `doc/zul_preview_product_positioning.md` §2, mitigation **M-3** —
*窗格內的說明提示（小小的 ℹ︎／首次執行橫幅）：「綁定值以 placeholder 呈現——你的
ViewModel 不會在此執行。」* Cost: 低. Effect: 攔截那些跳過文件的使用者（也就是所有人）.

Translation: an in-pane info hint — a small ℹ / first-run banner reading *"Binding
values are shown as placeholders — your ViewModel doesn't run here."* — that catches the
users who never read the docs (i.e. everyone). Complements **M-1** (placeholders are
rendered) and **M-2** ("Layout Preview" naming) by explaining the placeholders in-context.

## Design

A dismissible **`EditorNotificationPanel`** (`Status.Info`) pinned to the **top of the
render** (`CARD_BROWSER` only — the hint is about binding values, so it is meaningless on
the no-ZK / error / no-JCEF message cards). Idiomatic IntelliJ banner; a **"Got it"**
action label dismisses it and persists the choice **application-wide** (`PropertiesComponent`),
so it behaves as a genuine first-run banner: shown until dismissed once, then never again.

- `LayoutPreviewHint` (new) — canonical `TEXT` constant + `isDismissed()`/`dismiss()`
  over app-level `PropertiesComponent`. Keeps the reviewable copy and the flag key in one
  small, testable place.
- `ZulPreviewFileEditor` — on `READY`, wrap `browser.getComponent()` in a `BorderLayout`
  panel with the banner at `NORTH` (skipped entirely when already dismissed).

## Out of scope
- Message cards (no-ZK / error / JCEF-missing) — those already explain themselves (R5/R7).
- The loading label wording — untouched.
- Any "learn more" link / docs deep-link — text-only hint, per the positioning doc.

## Success criteria (verify)
- RED first: hint-text test fails before the constant exists.
- GREEN: `LayoutPreviewHintTest` locks that the copy names *placeholders*, the *ViewModel*,
  and that it *doesn't run* here.
- Full plugin test suite: no regressions.
- **Manual (runIde):** open a bound `.zul`, confirm the banner shows above the render,
  "Got it" hides it, and it stays hidden after reopening (lesson #1: the JCEF banner seam
  can't be exercised headlessly — no JCEF in test mode — so this is a manual check).

## Review — DONE (automated slice) / manual check pending

- **RED confirmed:** `LayoutPreviewHintTest` failed to compile (`LayoutPreviewHint` unresolved) before the class existed.
- **GREEN confirmed:** `LayoutPreviewHintTest` passes — locks that the copy names *placeholders*, the *ViewModel*, and that it *doesn't run* here. Main compiled against the 2023.3 SDK, so the `EditorNotificationPanel(Status.Info)` + `text()` + `createActionLabel("Got it", …)` banner API is valid.
- **No regressions:** full suite (root + `zk-preview-launcher`) BUILD SUCCESSFUL.
- **Files:** `LayoutPreviewHint` (new); `ZulPreviewFileEditor` wraps the browser via `wrapWithHint(...)` (banner at NORTH, skipped when dismissed); `feature_overview.md` §10 updated (row + `ZulPreviewFileEditor` note); test `LayoutPreviewHintTest`.
- **Still pending — manual runIde:** the actual banner display/dismiss-persistence can only be seen in-IDE (no JCEF headless). Per lesson #1 this is the seam that matters; not yet eyeballed.

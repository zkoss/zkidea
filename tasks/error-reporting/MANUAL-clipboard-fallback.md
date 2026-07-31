# Manual test — clipboard fallback for over-long reports

Verifies the runtime seam that can't be tested headlessly: when a render-error report is too
large to pre-fill into a GitHub URL, the error page offers a **copy-to-clipboard** button
that copies the **complete** report and opens a title-only new issue to paste into.
(The size-based decision + HTML are unit-tested in `ErrorPageRendererTest`; the actual JCEF
clipboard write + system-browser hand-off are what this manual test covers.)

## Setup
1. Rebuild so the launcher jar is current:
   `withjdk.sh 17 ./gradlew buildPlugin` (or `runIde`).
2. Launch the sandbox IDE: `withjdk.sh 17 ./gradlew runIde`.
3. In the sandbox, open the `manual-test` project so the Layout Preview can render its
   webapp, then open the two fixtures below in the editor.

## Case A — large report → clipboard hand-off
Open **`manual-test/src/main/webapp/preview/errors/err-large-report.zul`**.

1. The right **Layout Preview** pane shows the formatted render-error card
   (**COMPOSE** — missing class `com.example.reporting.HugeReportBuilder`, with the stack
   trace disclosure).
2. At the bottom, expect a normal-looking **"Report this issue on GitHub ↗"** link — the
   pane stays clean (no "too large / paste" paragraph; that guidance lives in the issue).
3. Click the link. Expect:
   - a small confirmation appears next to it: **"✓ report copied — paste it into the
     description"** (if it says *"copy failed…"*, see the note at the bottom);
   - the **system browser** opens
     `github.com/zkoss/zkidea/issues/new?title=[Layout Preview] COMPOSE error rendering …`
     with the **title pre-filled** and a **body pre-filled with the paste instruction**:
     *"⚠️ This Layout Preview error report was too large to pre-fill automatically. The full
     details … have been copied to your clipboard. Please paste them below (⌘V / Ctrl+V)…"*.
4. Click into the issue **description** (below the instruction) and paste (⌘V / Ctrl+V).
   Expect the **complete** report: the full `.zul` source (all ~170 rows), the environment
   block, and the complete stack trace — **nothing truncated**, no `…(truncated)` marker.

✅ Pass: the pane shows only the report link; clicking copies the full report to the
clipboard and opens an issue whose body tells the user to paste; pasting yields the entire
untruncated report.

## Case B — small report → direct pre-filled link (contrast)
Open **`err-zscript-missing-class.zul`** (small).

1. The card's bottom shows the same **"Report this issue on GitHub ↗"** link — but clicking
   it does **not** show the "✓ report copied" confirmation (nothing is copied).
2. It opens the system browser with title **and** the full report body already pre-filled
   (no paste step needed).

✅ Pass: the small case pre-fills everything into the URL — confirming the switch is
size-driven (the clipboard hand-off only kicks in for the large case).

## If the copy fails
If the confirmation shows *"— copy failed; please copy the stack trace above manually"*, the
JCEF runtime blocked both `execCommand('copy')` and the async Clipboard API. Note the IDE
build and report back — the fallback would then need the plugin to do the copy natively (via
a JCEF→Java bridge) instead of in-page JS.

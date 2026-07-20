# AC-5 Manual QA Script — ZUL Preview UI (E3)

> Run this against `./gradlew runIde` (do NOT run it non-interactively — it blocks and
> opens a real sandboxed IDE window). This script covers everything the automated
> tests in `src/test/java/org/zkoss/zkidea/preview/` cannot: JCEF is unavailable in
> headless test mode, so the actual rendered browser content, the save-triggers-refresh
> behaviour, and full-process teardown on project close all require a human at a real
> IDE.

## Prerequisites

1. `withjdk.sh 17 ./gradlew runIde` — wait for the sandboxed IDE to open.
2. In the sandboxed IDE, open the `manual-test` Maven project
   (`manual-test/pom.xml` in this repo — ZK 10.1.0-jakarta, matches
   `tasks/zul-preview/manual-qa/E2-manual-verify.md`'s corpus) and let Maven finish
   importing (so the module has a resolved ZK classpath and a project SDK).

## Steps

| # | Action | Expected result |
|---|--------|------------------|
| 1 | Open `manual-test/src/main/webapp/preview/button.zul` (double-click in the project tree). | A **split editor** opens: normal XML text editor on one side, a "Preview" panel on the other (per `TextEditorWithPreview`, `HIDE_DEFAULT_EDITOR` policy — no separate plain-XML tab should also open). |
| 2 | Wait a few seconds for the preview panel. | It first shows "Starting ZK preview server…", then a rendered ZK page with a themed **button** labeled "submit" (proves the helper JVM started, the classpath/docroot were resolved, and JCEF loaded `http://localhost:<port>/preview/button.zul`). |
| 3 | In the text editor half, change `label="submit"` to `label="submit-changed"`, then save (Cmd/Ctrl+S). | Within ~1s (debounced 300ms) the preview panel **reloads** and shows the button now labeled "submit-changed" — proves the VFS-content-change listener + `MergingUpdateQueue` debounce + `browser.loadURL()` refresh path. |
| 4 | Open `manual-test/src/main/webapp/model.zul` (an MVVM page: `<listbox model="@load(vm.list)"/>`). | Renders without error (listbox structure visible, no crash / no error dialog). Per the E1 isolation design, no user ViewModel class is ever loaded, so bound values are **empty** — this is by design, not a bug (see `PLAN.md` §3 "Known v1 fidelity ceiling"). |
| 5 | Open `manual-test/src/main/webapp/preview/broken.zul` (a `<zscript>` referencing a nonexistent class — added for this script). | The preview panel loads the URL and shows the raw JSON structured-failure body from the launcher instead of a rendered page: `{"status":"FAILURE","error":{"phase":"COMPOSE","message":"Missing class: org.example.definitely.NoSuchClassAtAll (...)","zulFile":"/preview/broken.zul","line":7,...}}` — the key checks are `"phase":"COMPOSE"` and the message naming the missing FQCN. Pretty-printing this JSON into a friendlier panel is Stage 2 ("Fail-Render reporting", explicitly out of v1 scope per `PLAN.md` §1) — seeing the structured JSON is the correct v1 result, not a bug. |
| 6 | Open a **non-ZK** project (or a module with no ZK jars on its classpath), then open any `.zul` file in it. | The preview panel shows the R7 message: *"No ZK framework jars (zk, zul, ...) were found on this file's module classpath. Add a ZK dependency to the module to enable the live preview."* — no exception, no crash. |
| 7 | Close the `manual-test` project (File → Close Project), or close the whole sandboxed IDE window. | The IDE closes cleanly with no error dialogs. |
| 8 | From a terminal (outside the sandboxed IDE): `ps aux \| grep zk-preview-launcher \| grep -v grep` | **Empty output** — no orphan `zk-preview-launcher.jar` JVM left running (E3-G2). If a helper JVM is still listed, note its command line (classpath/docroot) and report as a defect. |

## Optional / secondary checks

- **Multiple tabs, same webapp**: open `button.zul` and `model.zul` at the same time (both under `manual-test/src/main/webapp`). Only **one** `zk-preview-launcher` process should appear in `ps aux` while both tabs are open — proves the "one server per (docroot, classpath-signature)" reuse policy in `ZulPreviewServerService`.
- **JCEF-unavailable fallback**: if you have access to an IDE build/JDK combination without JCEF (or can temporarily set the registry key `ide.browser.jcef.enabled=false`), open any `.zul` file and confirm the preview panel shows the "Preview unavailable: the embedded browser (JCEF) is not supported…" message instead of throwing.
- **javax variant**: repeat steps 1–3 against a ZK 9.x (javax) project such as `/Users/hawk/Documents/workspace/SUPPORT/zk9support` if available, to confirm variant auto-detection also works from the plugin side (not just the launcher's own test suite).

## What was already smoke-verified headlessly (do not re-verify by hand)

Per the E3 evidence file (`tasks/zul-preview/E3-evidence.md`), these were checked without an interactive IDE and are safe to treat as given:

- `./gradlew prepareSandbox` places `zk-preview-launcher.jar` at
  `.sandbox/plugins/zkidea/lib/zk-preview-launcher.jar`.
- `./gradlew buildPlugin`'s zip (`build/distributions/zkidea-<version>.zip`) contains
  `zkidea/lib/zk-preview-launcher.jar`.
- The registration-level tests in `ZulPreviewFileEditorProviderTest` (provider offered
  for `.zul`, not for `zk.xml`/`lang-addon.xml`/plain `.xml`; `createEditor()` builds a
  working split editor with a fallback preview panel headlessly).
- `ManagedPreviewServerTeardownTest` proves the underlying `destroy()` → OS-process-kill
  primitive works, using a short-lived stand-in process (not the real launcher jar).
- `DocrootResolverTest` / `ZkClasspathFilterTest` cover the docroot-resolution and
  ZK-jar-filtering rules in isolation.

What those do **not** cover, and this script does: real JCEF rendering, save-triggers-
refresh, per-page structured-failure display, and full end-to-end teardown after an
actual project close.

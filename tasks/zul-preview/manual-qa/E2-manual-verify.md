# E2 Manual Verification Guide — ZUL Preview rendering core

> Written 2026-07-06 at the E2 checkpoint (E1 gates all PASS, approach D). The preview UI (E3) does not exist yet — what you're verifying is the standalone rendering core that will sit behind it.

## 0. A server is already running for you

I left one running: **http://localhost:8123** serving `manual-test/src/main/webapp` (jakarta variant, ZK 10.1.0 from `manual-test/pom.xml`).

Open in your browser:

| URL | What you should see |
|-----|---------------------|
| http://localhost:8123/preview/button.zul | A ZK button, themed (proves page + JS/CSS resource pipeline) |
| http://localhost:8123/model.zul | MVVM page: listbox structure renders; **bound values are empty by design** (no ViewModel is ever loaded) |
| http://localhost:8123/command.zul | MVVM commands page: buttons visible but inert (`@command` unwired — v1 fidelity ceiling) |
| http://localhost:8123/test.zul | Structured JSON failure (this file is genuinely malformed XML at line 6 — AC-6 error path) |

Stop it when done: `lsof -ti:8123 | xargs kill`

## 1. Start it yourself (jakarta / ZK 10)

```bash
cd /Users/hawk/Documents/workspace/PLUGIN/zkidea
withjdk.sh 17 ./gradlew :zk-preview-launcher:jar
withjdk.sh 17 mvn -f manual-test/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -q
withjdk.sh 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
  --classpath "$(cat /tmp/cp.txt)" --webapp manual-test/src/main/webapp --port 8123
```

It prints `PREVIEW_PORT=8123` when ready. `--port 0` picks an ephemeral port (printed the same way). Ctrl+C stops it.

## 2. javax variant (ZK 9) against zk9support

```bash
withjdk.sh 17 mvn -f /Users/hawk/Documents/workspace/SUPPORT/zk9support/pom.xml \
  dependency:build-classpath -Dmdep.outputFile=/tmp/cp9.txt -q
withjdk.sh 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
  --classpath "$(cat /tmp/cp9.txt)" --webapp /Users/hawk/Documents/workspace/SUPPORT/zk9support/src/main/webapp --port 8124
```

Then browse http://localhost:8124/index.zul or any of the 749 ZULs there. Variant (javax vs jakarta) is auto-detected from the ZK jars.

## 3. What "correct" looks like (v1 semantics)

- Plain components/layouts: render with real ZK theme CSS — should look like the real app.
- `viewModel=` pages: structure renders, `@load/@bind` values are **empty**, no user VM class is ever loaded (that's the isolation guarantee, not a bug).
- `apply=` composer pages: render without composer side effects (wiring, data loading absent).
- `${unknownVar}` EL: renders as empty string.
- `<zscript>` referencing a missing class: structured JSON failure naming the FQCN.
- Interactions (clicks that trigger server events) do nothing — the AU channel is stubbed. Preview is first-paint only.

## 4. Optional deeper checks

- Isolation canary (hooks off): add `-Dzkpreview.isolation=false` to the java command, open an MVVM page → structured failure with `ClassNotFoundException` for the VM class. Proves the class genuinely isn't reachable.
- Full test suite: `withjdk.sh 17 ./gradlew :zk-preview-launcher:test` (28 tests; BrowserEquivalentTest launches headless Chromium via Playwright).

## 5. Key artifacts to read (comprehension checkpoint)

The loop's success metric is your adoption, and that requires you actually reading the load-bearing pieces — the agents' reports are no substitute:

- `tasks/zul-preview/E1-verdict-round1.md` — verifier's reproduced evidence + non-blocking suggestions
- `zk-preview-launcher/src/main/java/org/zkoss/zkpreview/RenderEngine.java` and `ScopedZkClassLoader.java` — the rendering + isolation heart
- `zk-preview-launcher/src/hooks/java/.../PreviewUiFactory.java` — the single-hook isolation (note: PLAN's original two-hook recipe was impossible; `BindComposer.initViewModel` is private — verifier confirmed)
- `settings.gradle` — the only pre-existing file modified

## 6. When satisfied

Tell the commander to proceed to E3 (IntelliJ preview tab: FileEditorProvider + JBCefBrowser + helper-JVM service), or report anything that looks wrong — it goes back to the maker as an E1 round-2 defect.

# M-1 — Manual test guide

Two ways to see M-1 (binding expressions rendering as dimmed placeholder text). Path A
(headless launcher) is fast and reliable and needs no IDE/JCEF; Path B is the real
in-IDE UX. Both use the in-repo `manual-test` project (ZK 10.1.0-jakarta) and the demo
page `manual-test/src/main/webapp/preview/placeholders.zul`.

`WJ=/Users/hawk/Documents/workspace/toolbox/withjdk.sh` (JDK 17 wrapper).

---

## Path A — headless launcher + browser/curl (recommended, verified)

```bash
cd /Users/hawk/Documents/workspace/PLUGIN/zkidea
WJ=/Users/hawk/Documents/workspace/toolbox/withjdk.sh

# 1. build the launcher jar (bundles the hooks jar with PlaceholderInjector + zk.xml)
$WJ 17 ./gradlew :zk-preview-launcher:jar

# 2. resolve the project's ZK classpath (cached in ~/.m2)
$WJ 17 mvn -f manual-test/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -q

# 3. run the render server against the manual-test webapp (Ctrl+C to stop)
$WJ 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
  --classpath "$(cat /tmp/cp.txt)" --webapp manual-test/src/main/webapp --port 8123
# prints: PREVIEW_PORT=8123
```

Then open **http://localhost:8123/preview/placeholders.zul** in a browser.

**What you should see (this is the M-1 change):**
- The window title, and the `@load`/`@bind`/`@save` labels & textbox, show the **bound
  path itself in dimmed italic** — `vm.pageTitle`, `vm.greeting`, `vm.userName`, `vm.note`.
  *Before M-1 these were blank.*
- `User name:` and `plain static text` render normally (literals, not dimmed).
- The listbox stays empty — its `model="@load(vm.rows)"` is a non-text binding, so M-1
  deliberately leaves it alone (you won't see `vm.rows`).
- No `LOADED`/real data anywhere — the ViewModel class is never loaded (isolation intact).

Quick scriptable check (no browser):
```bash
curl -s http://localhost:8123/preview/placeholders.zul | grep -oE 'vm\.[a-zA-Z.]+' | sort -u
# → vm.greeting  vm.note  vm.pageTitle  vm.userName   (NOT vm.rows)
```

Also try the richer real fixture **http://localhost:8123/binding-property-nav.zul**
(`vm.name`, `vm.crew.name`, `vm.list` on labels/textbox become placeholders; the
`@init(...)` value-binding and the checkbox `checked`/listbox `model` bindings stay blank
— only `@load`/`@save`/`@bind` on text properties are placeholdered).

Contrast: `master`/pre-M-1 shows all of these blank (see the "bound values are empty by
design" note in `tasks/zul-preview/manual-qa/E2-manual-verify.md §3`).

---

## Path B — full IDE (`runIde`), the real preview UX

```bash
cd /Users/hawk/Documents/workspace/PLUGIN/zkidea
$WJ 17 ./gradlew runIde
```

In the sandbox IntelliJ that opens:
1. **File ▸ Open** → select `manual-test/pom.xml` → *Open as Project*, and let the Maven
   import finish (this puts the ZK jars on the module classpath — required; the preview
   shows `NO_ZK_JARS` otherwise).
2. Open `src/main/webapp/preview/placeholders.zul`. The editor is a split view: ZUL text
   on the left, live preview on the right.
3. First open spawns the helper JVM (a brief "starting" moment), then the right pane
   renders the same dimmed placeholders as Path A. Edit + save to see it refresh.

Requires JCEF (bundled JBR supports it). If the pane says preview is unavailable, JCEF
isn't supported in that runtime — use Path A.

---

## Toggle check (optional) — prove it's isolation-gated
Add `-Dzkpreview.isolation=false` to the Path A `java` command and reopen an MVVM page:
the injector stands down and ZK tries to load the real ViewModel → structured
`ClassNotFoundException` for the VM FQCN (proving M-1 only runs under the isolation seam).

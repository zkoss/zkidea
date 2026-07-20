# E3 round 3 — maker evidence (D3 + D4)

> Maker: Sonnet. Scope: two blocking defects found by E3-G1c corpus gate
> ([E3-corpus-check.md](E3-corpus-check.md)) and the live-launcher classpath
> observation recorded in [PLAN.md](PLAN.md) §8. TDD throughout: failing test
> first, then fix, both runs recorded below.

## D3 — annotation-valued shadow-element attributes render as literal paths

### Root-cause investigation (verify-before-fix, per instructions)

Decompiled the real ZK 10.1.0-jakarta jars (`zk-10.1.0-jakarta.jar`,
`zuti-10.1.0-jakarta.jar`, via IntelliJ's bundled Fernflower engine, since no
sources jar was cached locally) rather than guessing:

- `org.zkoss.zuti.zul.Apply.compose(Component host)`:
  ```java
  String templateURI = this._templateURI;
  if (templateURI == null) {
      templateURI = ((ShadowDefinitionImpl) this.getDefinition()).getTemplateURI();
  }
  if (templateURI != null) {
      ...
      exec.createComponents(uri, host, this.getNextInsertionComponentIfAny(), null, arg);
  }
  ```
  `_templateURI` is a **private field**, only ever written by `setTemplateURI(String)`
  (also confirmed via the class's own `PropertyAccess` registration, which calls
  `((Apply) cmp).setTemplateURI(value)` — a normal virtual dispatch, so this is the
  only path in).
- `Execution.getPageDefinition(uri)` (`ExecutionImpl.java`) is what `createComponents`
  ultimately calls: `if (pagedef == null) throw new UiException("Page not found: " + uri)`.
  `pagedef` comes from `((WebAppCtrl) webApp).getUiFactory().getPageDefinition(ri, uri)`
  — i.e. **our own already-registered `PreviewUiFactory`** (`SimpleUiFactory` subclass,
  wired via `zk.xml`'s `<ui-factory-class>`).
- `TemplateBasedShadowElement`/`HtmlShadowElement` bytecode: a shadow element's
  `compose()` is only invoked from `afterCompose()` when `isEffective()` is true, and
  `Apply.isEffective()` is:
  ```java
  if (!this.getAnnotatedProperties().isEmpty() && !this.isBindingReady()) return false;
  return hasTemplate || this._templateURI != null || defn.getTemplateURI() != null;
  ```
  `isBindingReady()` only flips true when a real `Binder` fires an `onBindingReady`
  event (`addBindingListener()`), which never happens under the preview's no-op
  composer.

**Empirical confirmation (this is what settled the exact defect class, since the
bytecode alone left one open question — see below):** I built fixture (g) with BOTH
a well-formed annotation (`@load(vm.templatePath)`, complete/balanced) and the
half-typed one (`@load('/WEB-INF/template/`, missing the closing paren) in the same
page and ran it before any fix. The observed failure message named only the
**half-typed** value, byte-for-byte matching the corpus-check's `template-uri-nav.zul`
symptom — proving the well-formed annotation on its own already causes **no** failure
today (it is registered as a binding annotation, `getAnnotatedProperties()` is
non-empty for it, `isBindingReady()` never becomes true under the no-op composer, so
`isEffective()` is false and `compose()` is never even called — that already matches
"contributes nothing"). Only the **malformed** annotation reaches `setTemplateURI`
as a raw literal (ZK's compiler evidently never recognizes it as annotation syntax at
all when it can't find a balanced `)`, so it falls through the normal literal-property
path instead of being deferred as an annotation) — that literal then flows straight
into `Execution.getPageDefinition` as a bogus path.

So the concrete defect class is narrower than the corpus-check's hypothesis
("annotation values are never interpreted"): well-formed annotations were *already*
safe; only unrecognized/half-typed ones leak through. The required semantics (task
brief) are still correct either way — a value that never resolves to a real page
must be treated as "absent", not "literal" — so the fix intercepts both shapes
uniformly rather than special-casing which one currently misbehaves (defensive
against future/other malformed-annotation edge cases, e.g. mismatched quotes).

### Chosen fix and why

**`PreviewUiFactory.getPageDefinition(RequestInfo ri, String path)`** (new override,
`zk-preview-launcher/src/hooks/java/org/zkoss/zkpreview/hooks/PreviewUiFactory.java`):
if `path` matches `(^|/)@\w+\(` (an unresolved annotation-attempt shape, complete or
half-typed), return a **synthesized empty page** (`getPageDefinitionDirectly(ri,
"<zk/>", "zul")`, ZK's own real ZUML parser fed an in-memory empty document) instead
of delegating to the default file lookup. `Execution.createComponents` then inserts
zero components from that empty page — exactly "the `<apply>` contributes nothing" —
instead of `getPageDefinition` returning null and the caller throwing "Page not
found". A path with no leading `@name(` is unaffected and still resolves (or still
fails "Page not found") exactly as before.

**Why this shape and not the others considered:**

1. **Subclass `org.zkoss.zuti.zul.Apply` directly, override `setTemplateURI`,
   register it as the `<apply>` tag's implementation class via a
   `WebAppInit`-driven `ComponentDefinition.setImplementationClass` swap** (the
   approach I built and validated architecturally first — the "later lang-addon /
   `<extends>` override" mechanism ZK itself uses is real and confirmed via
   `DefinitionLoaders.java` decompilation). **Abandoned**: `zuti` is a **ZK EE-only
   artifact** — not published on `mavensync.zkoss.org/maven2` (the free CE repo this
   module's `hooks` sourceSet compiles against; verified with a live `curl` 404).
   Adding it as a `hooksCompileOnly` dependency broke the build for anyone without
   EE repo access, which is unacceptable for a build that must work for arbitrary
   contributors/CI. (Files were built, verified to fail the build for exactly this
   reason, then deleted — not left behind.)
2. **`UiLifeCycle.afterShadowAttached` + reflection** (no compile dependency needed,
   since it identifies the component by class name string at runtime). **Abandoned
   after empirical testing**: traced `AbstractUiFactory.newComponent(Page, Component,
   ShadowInfo, Component)` and confirmed `setShadowHost` (which fires
   `afterShadowAttached`) runs **before** `compInfo.applyProperties(comp)` (where
   `setTemplateURI` is actually invoked) — so the hook fires too early to see the bad
   value; there is no later `UiLifeCycle` callback between property-assignment and
   `afterCompose()`/`compose()` to catch it instead. Confirmed by running the RED test
   against this implementation: identical failure, unchanged.
3. **ZUL text pre-transform** (regex-blank `templateURI="@\w+\(...` before ZK ever
   parses the file), the task's other suggested candidate. **Not pursued past a
   feasibility probe**: instrumented `MockServletContext.getResource`/
   `getResourceAsStream`/`getRealPath` with temporary debug prints and confirmed ZK
   reads the *target* page via `ServletContext.getRealPath()` + a direct
   `java.io.File`/reader (bytes never pass through our overridable stream methods for
   the main request), so intercepting text would require redirecting `getRealPath` to
   a generated temp file per request (extra I/O, cleanup, staleness/caching edge
   cases) — clearly more invasive than the chosen fix.

The winning fix reuses an extension point **already in the architecture**
(`PreviewUiFactory`, the existing isolation hook class) and needs zero new
registration, zero new compile dependency, and zero text rewriting — the literal
"least invasive" option once the first two were ruled out by concrete facts rather
than assumption.

### TDD: red → green

New fixtures (`zk-preview-launcher/src/test/resources/fixtures/`):
- `apply-templateuri-annotation.zul` — (g1) `<apply templateURI="@load(vm.templatePath)"/>`,
  (g2) `<apply templateURI="@load('/WEB-INF/template/"/>`, plus `<label value="apply marker label"/>`.
- `apply-templateuri-missing.zul` — `<apply templateURI="/no/such/file.zul"/>` (genuinely
  missing literal, no annotation syntax) plus a label, to prove the fix doesn't over-suppress.

New test: `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/ApplyTemplateUriTest.java`
(parametrized over both servlet-API variants, same pattern as `RenderFidelityTest`).

**RED** (`withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests
"org.zkoss.zkpreview.ApplyTemplateUriTest"`, before the `PreviewUiFactory` change):
```
ApplyTemplateUriTest > fixtureG_annotationValuedTemplateUriDoesNotLeakAsLiteralPath(Named)[1] FAILED
    org.opentest4j.AssertionFailedError: expected SUCCESS, got:
    {"phase":"UNKNOWN","message":"org.zkoss.zk.ui.UiException: Page not found: /@load('/WEB-INF/template/",
     "zulFile":"/apply-templateuri-annotation.zul","line":null,"column":null} ==> expected: <true> but was: <false>
ApplyTemplateUriTest > fixtureG_annotationValuedTemplateUriDoesNotLeakAsLiteralPath(Named)[2] FAILED  (same, javax variant)
4 tests completed, 4 failed
```
(The other 2 failures at that point were `fixtureGNeg...` failing on an unrelated bug
in my own fixture's XML comment containing `--`, fixed before evaluating the real
defect — see below.)

**GREEN** (after the `PreviewUiFactory.getPageDefinition` fix):
```
ApplyTemplateUriTest > fixtureG_annotationValuedTemplateUriDoesNotLeakAsLiteralPath(Named)[1] PASSED
ApplyTemplateUriTest > fixtureG_annotationValuedTemplateUriDoesNotLeakAsLiteralPath(Named)[2] PASSED
ApplyTemplateUriTest > fixtureGNeg_genuinelyMissingLiteralPathStillFails(Named)[1] PASSED
ApplyTemplateUriTest > fixtureGNeg_genuinelyMissingLiteralPathStillFails(Named)[2] PASSED
BUILD SUCCESSFUL
```
`fixtureGNeg` (the genuinely-nonexistent literal path, `/no/such/file.zul`) asserts
`r.isSuccess()` is **false** and the structured error message names the literal path —
confirming the fix does not over-suppress real "page not found" failures.

## D4 — plugin hands project-SDK pseudo-entries to the launcher

### Fix

(a) `ZulPreviewServerService.resolveTarget` — added `.withoutSdk()` to both
`OrderEnumerator` branches (module and project fallback), so SDK roots are excluded
at the source.

(b) `ZkClasspathFilter.filterLibraryJars` — changed the directory-exclusion check
from `!file.isDirectory()` to `file.isFile()`. `isFile()` is false for both
directories *and* any non-existent/non-regular-file path — including `!`-containing
JDK module pseudo-entries like
`/Library/Java/JavaVirtualMachines/zulu-24.jdk/Contents/Home!/java.base` (not a real
filesystem path at all) — so this is a strict superset of the old behavior for real
files while additionally dropping the pseudo-entries defensively, independent of (a).

### TDD: red → green

New test in `ZkClasspathFilterTest.java`:
`filterLibraryJarsExcludesSdkPseudoEntriesAndNonexistentPaths` — feeds
`filterLibraryJars` an SDK pseudo-entry, a nonexistent path, and one real jar;
asserts only the real jar survives.

**RED** (`withjdk.sh 17 ./gradlew :test --tests
"org.zkoss.zkidea.preview.ZkClasspathFilterTest"`, before the fix):
```
ZkClasspathFilterTest > filterLibraryJarsExcludesSdkPseudoEntriesAndNonexistentPaths() FAILED
    org.opentest4j.AssertionFailedError at ZkClasspathFilterTest.java:109
10 tests completed, 1 failed
```

**GREEN** (after changing `!file.isDirectory()` → `file.isFile()`):
```
BUILD SUCCESSFUL
```
(all `ZkClasspathFilterTest` tests pass, including the pre-existing
`filterLibraryJarsExcludesDirectories` and `filterLibraryJarsKeepsEveryFileRegardlessOfName`.)

Part (a) (`.withoutSdk()`) has no dedicated automated test: `resolveTarget` is
private and platform-bound (needs a real `Module`/`Project`/SDK), a pre-existing gap
noted in [PLAN.md](PLAN.md) §8 ("seam test doesn't lock the resolveTarget→
filterLibraryJars wiring") that this round did not attempt to close — out of scope
for a two-line defensive change and consistent with the round-3 instructions'
explicit TDD scope ("extend ZkClasspathFilterTest ... both excluded").

## Files changed

- `zk-preview-launcher/src/hooks/java/org/zkoss/zkpreview/hooks/PreviewUiFactory.java`
  — D3 fix: `getPageDefinition` override + javadoc.
- `zk-preview-launcher/src/test/resources/fixtures/apply-templateuri-annotation.zul` (new)
- `zk-preview-launcher/src/test/resources/fixtures/apply-templateuri-missing.zul` (new)
- `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/ApplyTemplateUriTest.java` (new)
- `src/main/java/org/zkoss/zkidea/preview/ZkClasspathFilter.java` — D4(b):
  `filterLibraryJars` now uses `file.isFile()`.
- `src/main/java/org/zkoss/zkidea/preview/ZulPreviewServerService.java` — D4(a):
  `.withoutSdk()` on both `OrderEnumerator` branches.
- `src/test/java/org/zkoss/zkidea/preview/ZkClasspathFilterTest.java` — D4 new test.

No other files retain changes: `build.gradle`, `preview/zk.xml`, and both
`MockServletContext.java` variants were touched during investigation (an abandoned
`zuti` compile dependency; a temporarily-registered `<listener>`; temporary debug
`System.err.println` probes) and explicitly reverted to their round-2 state once
those approaches were ruled out — confirmed by re-reading each file's final content
before finishing.

## Full suite runs

`withjdk.sh 17 ./gradlew build test -x buildSearchableOptions` (root; see caveat
below for the `-x`) then `withjdk.sh 17 ./gradlew :zk-preview-launcher:test`:

- Root project: **299 tests, 0 failures, 0 errors** (298 in round 2 + 1 new:
  `filterLibraryJarsExcludesSdkPseudoEntriesAndNonexistentPaths`).
- `zk-preview-launcher`: **34 tests, 0 failures, 0 errors** (30 in round 2 + 4 new:
  `ApplyTemplateUriTest`'s 2 methods × 2 servlet-API variants).
- Confirmed no regression in the isolation/canary suite (`IsolationTest`,
  `IsolationChildProcessTest`) or fidelity fixtures (`RenderFidelityTest` a–e,
  `StructuredFailureTest` f, `RealWorldSmokeTest`, `BrowserEquivalentTest`,
  `CoreIndependenceTest`, `VariantDetectorTest`) — all still pass alongside the D3/D4
  changes in the same run.

**Caveat on `-x buildSearchableOptions`**: `./gradlew build` alone failed with
`Only one instance of IDEA can be run at a time` — `buildSearchableOptions` tries to
launch its own sandbox IDE instance, which collides with the sandbox IDE already
running from a previous round (PID 33706, `.sandbox/config`; documented in
[PLAN.md](PLAN.md) §8 as intentionally left open, not an orphan to be killed).
`buildSearchableOptions` only generates Settings-search-index metadata and runs no
tests; skipping it does not affect the `test` task or `check`/`build` correctness
signal. `assemble`/`buildPlugin`/`test`/`check`/`build` all completed successfully
with it skipped.

## Corpus re-check (live launcher against manual-test)

Started our own instance (never touching the pre-existing PID 34527 sandbox
launcher, per the round's caution):

```
$ mvn -f manual-test/pom.xml dependency:build-classpath -Dmdep.outputFile=<scratch>/e3r3-cp.txt -q
$ java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
    --classpath "$(cat <scratch>/e3r3-cp.txt)" --webapp manual-test/src/main/webapp --port 0
PREVIEW_PORT=62176   (PID 69582, verified via lsof -iTCP:62176 -sTCP:LISTEN)
```

- `GET /template-uri-nav.zul` → **HTTP 200**, body contains `zkmx(` and
  `zul.wnd.Window` (the page's only widget besides the nine now-inert `<apply>`
  elements — it has no `<label>`/other components in source). No stderr/stdout noise
  from the launcher for this request.
- `GET /preview/broken.zul` → **still HTTP 500**, unchanged structured JSON:
  `phase=COMPOSE`, message names `org.example.definitely.NoSuchClassAtAll` — the D3
  fix does not touch zscript-class-resolution failures.
- Spot-checked 4 more corpus files for regression: `command.zul` (200),
  `preview/button.zul` (200), `scope-var-completion.zul` (500, identical
  `Unsupported parent for row` message), `viewmodel-id-nav.zul` (500, identical
  `PropertyNotFoundException: Method setVm not found` message) — byte-for-byte the
  same outcomes as [E3-corpus-check.md](E3-corpus-check.md)'s original matrix.
- Teardown: `kill 69582`; confirmed via `lsof -iTCP:62176 -sTCP:LISTEN` (no output,
  port released) and `ps -ef | grep zk-preview-launcher` (only the pre-existing,
  untouched PID 34527 remains — its classpath printout incidentally reconfirms D4's
  premise live: it is full of `.../zulu-24.jdk/Contents/Home!/java.base`-style
  entries, from before this round's fix).

## Self-assessment per gate

- **D3 fixed and TDD-proven**: new fixture (g1+g2) red→green; negative case
  (genuinely missing literal path) still fails correctly. Root cause independently
  confirmed via bytecode decompilation *and* empirical isolation of the two
  sub-cases (well-formed vs malformed annotation), not just pattern-matched from the
  symptom.
- **D4 fixed and TDD-proven** for the defensive filter (b); `.withoutSdk()` (a) is a
  source-level fix without a dedicated automated test, consistent with the
  pre-existing, documented gap in testing `resolveTarget`'s private/platform-bound
  wiring, and with the round's explicit TDD scope for D4.
- **No regressions**: full root (299) and launcher (34) suites green, including all
  isolation/canary and fidelity fixtures explicitly called out as must-not-regress.
- **Corpus re-check**: the exact defect file now serves 200; the exact must-not-break
  file (`broken.zul`) still correctly fails; 4 additional spot-checks confirm no
  collateral change elsewhere in the 20-file corpus.
- **No orphan processes**: only the pre-existing PID 34527 (explicitly out of scope)
  remains; the round's own launcher instance (PID 69582) was killed and its port
  verified released.

## Honest caveats

1. The corpus-check's own root-cause hypothesis ("annotation values are never
   interpreted [under the no-op composer]") turned out to be broader than the actual
   defect: well-formed annotations were already safe before this fix (they simply
   never compose, which already matches the desired "contributes nothing"
   semantics); only unrecognized/malformed ones leaked through as literals. The fix
   still treats both shapes uniformly (matches `(^|/)@\w+\(` regardless of whether
   the annotation is well-formed), which is intentionally defensive — a
   differently-malformed annotation (mismatched quote style, extra whitespace before
   `(`, etc.) could plausibly hit the same literal-fallthrough path, and the fix
   covers those too without needing to enumerate every malformed shape ZK's compiler
   might tolerate.
2. `PreviewUiFactory.getPageDefinition`'s regex is a **path-shape heuristic**
   (`(^|/)@\w+\(`), not a semantic annotation parser — matched against
   `toAbsoluteURI`-normalized paths only. A hypothetical real file whose name
   literally starts with `@word(` would be misidentified as an unresolved
   annotation; this is the same class of accepted trade-off as the existing
   `ErrorMapper` regexes in this codebase, not a new risk pattern.
3. `buildSearchableOptions` could not be run this round due to a pre-existing sandbox
   IDE holding the single-instance lock (see caveat above) — `test`/`check`/`build`
   all otherwise passed with it skipped; this is an environment precondition outside
   this round's changes, not a gap in the D3/D4 fixes themselves.
4. Two other fix shapes for D3 (zuti subclassing via `WebAppInit`, and
   `UiLifeCycle`-based reflection) were implemented far enough to be empirically
   ruled out, then fully removed — no half-finished alternative-approach code
   remains in the tree; only the final `PreviewUiFactory.getPageDefinition` fix is
   present.

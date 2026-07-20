# E3 Evidence — Round 2 (manual-gate defect fixes: D1, D2, E3-G1b)

> Maker round 2 (Sonnet). Commands below were run from the repo root
> (`/Users/hawk/Documents/workspace/PLUGIN/zkidea`) with `withjdk.sh 17` unless noted.
> Scratch files referenced below live under
> `/private/tmp/claude-501/-Users-hawk-Documents-workspace-PLUGIN-zkidea/e4d9d29f-8b69-499d-a543-c5d4330ba5ce/scratchpad`.
> Context: round 1 passed all headless gates but the user's manual `runIde` gate FAILED —
> every previewed ZUL crashed identically. This document diagnoses and fixes the two
> recorded defects (PLAN.md §8) and adds the missing seam test (E3-G1b).

## Summary

| Item | Result |
|------|--------|
| D1 root cause | **Confirmed** — plugin's `ZkClasspathFilter` handed the launcher `org.zkoss`-prefixed jars only, dropping `slf4j-api` (and every other non-ZK transitive dep ZK needs at bootstrap) |
| D1 fix | `ZkClasspathFilter.filterLibraryJars` (new) — all non-directory runtime classpath entries; `ZulPreviewServerService` now hands the launcher this list instead of the ZK-only subset |
| D2 root cause | **Investigated exhaustively — NOT reproducible from manual-test's real resolved classpath** with either the old or new filter (3 independent verification methods, see below). Found and fixed a real, adjacent latent defect instead: `VariantDetector.detect` was a first-match-wins scan with no defined precedence, which becomes order-fragile once the handoff classpath is widened by the D1 fix |
| D2 fix | `VariantDetector` now tries canonically-named `zk-<version>.jar` candidates first, full-list scan preserved as a fallback |
| E3-G1b seam test | **Written, red before the D1 fix (reproduced the user's crash), green after** — `ZulPreviewLauncherSeamTest` (plugin test source) |
| D2 regression test | **Written, red before the fix (order-dependent), green after** — `VariantDetectorTest` (launcher test source) |
| broken.zul fixture (AC-5 step 5) | **Fixed** — round-1 fixture had an illegal `--` in its XML comment, failing at PARSE instead of the intended COMPOSE missing-zscript-class failure; now verified against the real launcher to produce `phase: COMPOSE` naming the missing FQCN |
| Root `./gradlew build test` | **PASS** — 298 tests (was 295), 0 failures/errors |
| `:zk-preview-launcher:test` | **PASS** — 30 tests (was 28), 0 failures/errors |
| `prepareSandbox` bundling | **PASS** — unaffected, launcher jar still lands at `.sandbox/plugins/zkidea/lib/zk-preview-launcher.jar` |

---

## D1 — root cause, confirmed and fixed

### Root cause

`src/main/java/org/zkoss/zkidea/preview/ZkClasspathFilter.java`'s `filterZkJars` (used
by `ZulPreviewServerService.resolveTarget`, line 129 pre-fix) keeps **only** jars whose
filename starts with a ZK artifact prefix (`zk-`, `zul-`, `zkbind-`, `zcommon-`, `zweb-`,
`zel-`, `zhtml-`, `zkmax-`, `zkex-`, `zuti-`, `zkplus-`). Every other resolved runtime
dependency — including `slf4j-api`, which ZK's `org.zkoss.zk.ui.http.WebManager`
requires in its static initializer — was silently dropped before the classpath ever
reached the launcher's `--classpath` argument.

E1's own tests never caught this because `IsolationChildProcessTest` (and the other
real-classpath tests) resolve their classpath via
`ZkClasspathResolver`/`mvn dependency:build-classpath` **directly**, never routed through
the plugin's `ZkClasspathFilter` — the plugin-side filtering step was never exercised by
any automated test before this round. That gap is exactly what E3-G1b (below) closes.

### Reproduction (before any fix), using the REAL launcher jar and REAL `org.zkoss`-only filtering

```
$ withjdk.sh 17 mvn -f manual-test/pom.xml dependency:build-classpath \
    -Dmdep.outputFile=<scratch>/cp.txt -q
```

Resolved classpath for `manual-test` (ZK 10.1.0-jakarta) has 30 entries, including
`org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar`. Applying the plugin's **current**
`ZkClasspathFilter.filterZkJars` (via a small driver that reuses the actual production
source file) to those 30 entries keeps exactly 10, in this order — **`slf4j-api` is
gone**:

```
zkmax-10.1.0-jakarta.jar
zkex-10.1.0-jakarta.jar
zkbind-10.1.0-jakarta.jar
zul-10.1.0-jakarta.jar
zk-10.1.0-jakarta.jar
zweb-10.1.0-jakarta.jar
zcommon-10.1.0-jakarta.jar
zel-10.1.0-jakarta.jar
zuti-10.1.0-jakarta.jar
zhtml-10.1.0-jakarta.jar
```

Feeding exactly that 10-jar classpath to the REAL packaged launcher jar:

```
$ withjdk.sh 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
    --classpath "<the 10 jars above>" --webapp manual-test/src/main/webapp --port 0
```

reproduces the user's exact crash, byte-for-byte down to the `HttpSessionListener23`/
`WebManager.<clinit>` frames:

```
Exception in thread "main" java.lang.IllegalStateException: Failed to bootstrap the ZK mock webapp
	at org.zkoss.zkpreview.jakarta.JakartaRenderEngine.<init>(JakartaRenderEngine.java:61)
	at org.zkoss.zkpreview.RenderEngineFactory.create(RenderEngineFactory.java:30)
	at org.zkoss.zkpreview.RenderEngineFactory.create(RenderEngineFactory.java:23)
	at org.zkoss.zkpreview.Main.main(Main.java:32)
Caused by: java.lang.reflect.InvocationTargetException
	...
Caused by: java.lang.NoClassDefFoundError: org/slf4j/LoggerFactory
	at zk-preview-scoped//org.zkoss.zk.ui.http.WebManager.<clinit>(WebManager.java:86)
	at zk-preview-scoped//org.zkoss.zk.ui.http.HttpSessionListener23.contextInitialized(HttpSessionListener23.java:140)
	... 8 more
Caused by: java.lang.ClassNotFoundException: org.slf4j.LoggerFactory
	at java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:445)
	...
	at org.zkoss.zkpreview.ScopedZkClassLoader.loadClass(ScopedZkClassLoader.java:48)
```

Note this run selected **`JakartaRenderEngine`** (correctly, for manual-test's real
jakarta jars) — see the D2 section below for why the user's pasted log instead named
`JavaxRenderEngine`.

### Fix

`ZkClasspathFilter.java` — new method, `isZkJar`/`filterZkJars`/`signature` unchanged:

```java
public static List<File> filterLibraryJars(List<String> classpathEntries) {
    List<File> result = new ArrayList<>();
    for (String entry : classpathEntries) {
        File file = new File(entry);
        if (!file.isDirectory()) {
            result.add(file);
        }
    }
    return result;
}
```

`ZulPreviewServerService.resolveTarget()` — `filterZkJars` is now used **only** as the R7
"does this module have any ZK jars at all" gate; the actual handoff/signature list is the
new, wider `filterLibraryJars` result:

```java
boolean hasZkJars = !ZkClasspathFilter.filterZkJars(classpathEntries).isEmpty();
List<File> libraryJars = hasZkJars ? ZkClasspathFilter.filterLibraryJars(classpathEntries) : List.of();
String signature = ZkClasspathFilter.signature(libraryJars);
...
return new PreviewTarget(docroot, libraryJars, signature, "/" + relative);
```

`PreviewTarget.zkJars` renamed to `libraryJars` (both call sites, `onTargetResolved`'s
`isEmpty()` gate and `startServer`'s `joinClasspath(...)`, updated) — this is a
correctness-clarifying rename directly tied to the fix, not a drive-by refactor.

This directly implements Fable's policy decision (PLAN.md §8): hand the launcher **all**
non-directory runtime classpath entries, exclude **all** directories (module outputs =
user classes). Isolation from user code remains the `UiFactory` hook's job, never
classpath narrowness — unchanged from round 1.

---

## D2 — investigated, not reproducible from manual-test's real classpath; adjacent defect found and fixed

### Investigation

Three independent checks, all against manual-test's real, Maven-resolved ZK
10.1.0-jakarta classpath:

1. **Source-reuse driver** (`Repro.java`, scratch dir): compiled the actual
   `ZkClasspathFilter.java` + `VariantDetector.java` + `ZkVariant.java` source files
   together and ran `VariantDetector.detect(ZkClasspathFilter.filterZkJars(realClasspath))`
   → **`JAKARTA`**, correctly.
2. **Real packaged launcher jar via CLI**, fed the exact 10-jar old-filtered classpath
   (see D1 repro above) → crashed on the (unrelated, D1) slf4j `NoClassDefFoundError`,
   but the exception was thrown from **`JakartaRenderEngine.<init>`**, proving
   `VariantDetector.detect` had already, correctly, returned `JAKARTA` before the crash.
3. **Byte-level audit of all 30 resolved classpath entries**: `unzip -l <jar> | grep
   DHtmlLayoutServlet.class` against every one of manual-test's 30 resolved dependency
   jars — **exactly one** (`zk-10.1.0-jakarta.jar`) contains that class, and its bytes
   contain the literal ASCII substring `jakarta/servlet` at offset 855 and do **not**
   contain `javax/servlet` anywhere. There is no other candidate on the real classpath
   that could plausibly cause a wrong detection.

**Conclusion**: with manual-test's actual dependency-resolved classpath, both the
round-1 (ZK-only) and round-2 (all-library-jars) classpaths deterministically and
correctly resolve to `JAKARTA`. I could not reproduce "`JavaxRenderEngine` selected"
from manual-test's real dependencies with any permutation of today's code. I also
confirmed by direct code reading that:
- `RenderEngineFactory.create` has no hidden catch/fallback — `ZkVariant` has exactly two
  values and `detect()` either returns one of them or throws (propagating up, which would
  never manifest as `JavaxRenderEngine.<init>` on the stack).
- The `HttpSessionListener23` frame in the user's pasted log is a **red herring** with
  respect to javax-vs-jakarta: my own jakarta reproduction above shows the identical
  `HttpSessionListener23` frame. That class name is ZK's own internal
  servlet-**spec-minor-version** dispatch helper (2.3, unrelated to the javax/jakarta
  package split) and exists, identically named, in both variants' `zk` core jar.
- `manual-test/pom.xml`'s stray `javax.servlet-api:4.0.1` (provided scope, likely a
  leftover from before the project's ZK version was bumped to `10.1.0-jakarta`) does not
  affect variant detection: it doesn't contain `DHtmlLayoutServlet.class`, and the
  launcher's own servlet-API types are resolved from its own bundled classpath, not from
  the `--classpath` argument's jars.

I could not access the user's live IntelliJ session state to determine what the real
`OrderEnumerator`-resolved classpath looked like at the moment of that specific crash
(e.g. whether stale libraries, a partially-completed Maven import, or a second javax
project previewed in the same session — the manual script's own "optional" step 3
explicitly suggests trying a javax project in the same sitting — produced the pasted
log). This is recorded as an open/unreproduced item, not swept under the rug.

### Adjacent defect found and fixed

Regardless of the above, `VariantDetector.detect` (launcher,
`zk-preview-launcher/src/main/java/org/zkoss/zkpreview/VariantDetector.java`) scans
`classpathEntries` in **whatever order the caller supplies** and returns on the first
entry containing the exact class path `org/zkoss/zk/ui/http/DHtmlLayoutServlet.class`,
with **no defined precedence** between candidates. This was safe by luck when the
plugin handed it a small, curated, ZK-only list (round-1 behaviour: at most one jar could
ever contain that path). Round 2's D1 fix widens the handed-in list from ~10 curated ZK
jars to the module's **entire** runtime classpath (30 entries for manual-test) — this
does not break detection *today*, but it removes the implicit "at most one candidate"
invariant the original code silently relied on: any future project with a shaded/uber-jar
dependency, or a stale duplicate ZK core jar left on the classpath after a version bump,
could have an *unrelated* entry win the scan purely by list position. This exactly matches
Fable's own note in the task ("filtering-for-handoff and scanning-for-detection are
different concerns; keep them separate if needed").

**Fix**: `VariantDetector` now reorders candidates so filenames matching the ZK core
artifact's canonical naming convention (`zk-<version>.jar`, e.g. `zk-10.1.0-jakarta.jar`,
`zk-9.6.0.2.jar` — distinct from `zkbind-`/`zkmax-`/`zkex-`/etc., which have no hyphen
directly after `zk`) are tried first; the full-list scan is preserved as a fallback for
non-canonically-named jars (e.g. a renamed/relocated build passed directly to the CLI).

### Regression test (red → green)

New file: `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/VariantDetectorTest.java`.
Builds tiny synthetic jars (not real ZK bytecode — `VariantDetector` only byte-scans for
the literal marker string) with a decoy, unrelated-named jar placed *before* the real,
canonically-named ZK core jar, with **opposite** variant bytes.

**Before the fix** (red):

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.VariantDetectorTest"
...
VariantDetectorTest > detectsJakarta_evenWhenAnUnrelatedDecoyJarShadowsTheMarkerClassPathFirst() FAILED
    org.opentest4j.AssertionFailedError: expected: <JAKARTA> but was: <JAVAX>
VariantDetectorTest > detectsJavax_evenWhenAnUnrelatedDecoyJarShadowsTheMarkerClassPathFirst() FAILED
    org.opentest4j.AssertionFailedError: expected: <JAVAX> but was: <JAKARTA>
2 tests completed, 2 failed
```

**After the fix** (green):

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:test --tests "org.zkoss.zkpreview.VariantDetectorTest"
...
VariantDetectorTest > detectsJakarta_evenWhenAnUnrelatedDecoyJarShadowsTheMarkerClassPathFirst() PASSED
VariantDetectorTest > detectsJavax_evenWhenAnUnrelatedDecoyJarShadowsTheMarkerClassPathFirst() PASSED
BUILD SUCCESSFUL in 1s
```

---

## E3-G1b — the missing plugin↔launcher seam test

New file: `src/test/java/org/zkoss/zkidea/preview/ZulPreviewLauncherSeamTest.java`
(plugin test source — chosen over the launcher module because it must call
`ZkClasspathFilter`, a plugin class, directly; putting it in the launcher module would
violate AC-2 core independence). Plain JUnit 5, no `BasePlatformTestCase` needed (mirrors
`ZkClasspathFilterTest`/`ManagedPreviewServerTeardownTest`).

What it does:
1. Resolves manual-test's real classpath via `mvn dependency:build-classpath`
   (memoized per JVM; `Assumptions.assumeTrue` skip with a clear message if `mvn` or
   network access isn't available — network-free on every subsequent run once resolved
   for that JVM instance, and the resolution itself is a one-time local `.m2` lookup
   in this environment, no network needed since the jars were already cached from
   earlier E1/round-2 work).
2. Adds a fake module-output **directory** (containing a fake `.class` file) to the
   entries list, exactly as `OrderEnumerator` would report a previewed module's own
   compiled-classes output.
3. Calls `ZkClasspathFilter.filterLibraryJars(...)` — the exact same production method
   `ZulPreviewServerService` now calls — and asserts (a) the fake directory is excluded,
   (b) `slf4j-api` is included.
4. Spawns the **real, packaged** `zk-preview-launcher.jar` (Gradle: root `test` task now
   `dependsOn ':zk-preview-launcher:jar'`, launcher jar path exposed via the
   `zkpreview.launcherJar` system property) with that classpath, waits for
   `PREVIEW_PORT=<n>`, then does a real HTTP GET of `/preview/button.zul` and asserts
   HTTP 200 + a `zkmx(` bootstrap marker in the body.

### Before the fix (calling `ZkClasspathFilter.filterZkJars`, i.e. today's actual production code path)

```
$ withjdk.sh 17 ./gradlew :test --tests "org.zkoss.zkidea.preview.ZulPreviewLauncherSeamTest"
...
ZulPreviewLauncherSeamTest > realPackagedLauncherServesAPageWithThePluginsRealFilteredClasspath(Path) FAILED
    org.opentest4j.AssertionFailedError: slf4j-api (a non-ZK-prefixed transitive dependency
    ZK's WebManager requires at bootstrap) must be included -- this is exactly what D1 got wrong
    ==> expected: <true> but was: <false>
        at ...ZulPreviewLauncherSeamTest.realPackagedLauncherServesAPageWithThePluginsRealFilteredClasspath(ZulPreviewLauncherSeamTest.java:94)

1 test completed, 1 failed
BUILD FAILED
```

### After the fix (calling `ZkClasspathFilter.filterLibraryJars`, the new production code path)

```
$ withjdk.sh 17 ./gradlew :test --tests "org.zkoss.zkidea.preview.ZulPreviewLauncherSeamTest"
...
BUILD SUCCESSFUL in 5s
```

(`build/test-results/test/TEST-org.zkoss.zkidea.preview.ZulPreviewLauncherSeamTest.xml`:
`tests="1" failures="0" errors="0"`, `time="2.248"` — the real launcher jar booted,
served `/preview/button.zul`, and returned a `zkmx(`-containing 200 response.)

---

## Full suite runs (after all fixes)

```
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:clean
$ withjdk.sh 17 ./gradlew :zk-preview-launcher:build test
...
BUILD SUCCESSFUL in 30s
```

Aggregated `zk-preview-launcher/build/test-results/test/*.xml`: **30 tests, 0
failures, 0 errors** (28 pre-existing + 2 new `VariantDetectorTest` cases).

```
$ withjdk.sh 17 ./gradlew build test
...
BUILD SUCCESSFUL in 42s
```

Aggregated `build/test-results/test/*.xml`: **298 tests, 0 failures, 0 errors** (295
pre-existing + 1 new `ZulPreviewLauncherSeamTest` + 2 new `ZkClasspathFilterTest` cases
for `filterLibraryJars`).

```
$ withjdk.sh 17 ./gradlew prepareSandbox
$ find .sandbox -iname "*zk-preview-launcher*"
.sandbox/plugins/zkidea/lib/zk-preview-launcher.jar
```

Unaffected — bundling still works.

### Additional manual sanity (real launcher, real manual-test classpath, network-free re-run of the resolved classpath)

```
$ withjdk.sh 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
    --classpath "<30-entry manual-test classpath>" --webapp manual-test/src/main/webapp --port 0
PREVIEW_PORT=59309

$ curl -s -o /dev/null -w "HTTP %{http_code}\n" http://127.0.0.1:59309/model.zul
HTTP 200
$ curl -s http://127.0.0.1:59309/preview/button.zul | grep -o 'zkmx(' | head -1
zkmx(
$ curl -s http://127.0.0.1:59309/preview/broken.zul | head -c 200
{"status":"FAILURE","error":{"phase":"PARSE","message":"org.zkoss.lang.SystemException: org.xml.sax.SAXParseException; ...
```

`model.zul` (MVVM) and `preview/button.zul` (plain) both render successfully with the
widened classpath. The `phase: PARSE` result for `preview/broken.zul` visible in the
capture above exposed a fixture bug — fixed below.

### broken.zul fixture fix (round-1 E3 artifact, in scope)

`preview/broken.zul` (created by the round-1 E3 maker for `manual-qa/AC-5.md` step 5)
was supposed to demonstrate a COMPOSE-phase missing-zscript-class structured failure,
but instead failed at `phase: PARSE` with a SAXParseException: its own explanatory XML
comment contained a literal `--` (`"structured failure) -- used by"`), which is illegal
inside an XML comment, so the file failed to *parse* before the zscript ever ran — the
user's manual re-run of step 5 would have (correctly) read that as a defect.
(Notably, the same `--`-in-XML-comment mistake was already found and fixed once during
E1, in the launcher's bundled `zk.xml` — see E1-evidence.md.)

Fix: reworded the comment (`--` → `;`) in
`manual-test/src/main/webapp/preview/broken.zul`. No other change to the fixture.

Verified against the REAL launcher jar with manual-test's full resolved classpath:

```
$ withjdk.sh 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
    --classpath "<30-entry manual-test classpath>" --webapp manual-test/src/main/webapp --port 0
PREVIEW_PORT=59617
$ curl -s http://127.0.0.1:59617/preview/broken.zul
{"status":"FAILURE","error":{"phase":"COMPOSE","message":"Missing class: org.example.definitely.NoSuchClassAtAll
 (org.zkoss.zk.ui.UiException: ... Class: org.example.definitely.NoSuchClassAtAll not found in namespace ...
 <- bsh.EvalError: ...)","zulFile":"/preview/broken.zul","line":7,"column":null}}
```

Exactly the intended shape: `phase: COMPOSE`, message names the missing FQCN
(`org.example.definitely.NoSuchClassAtAll`), `zulFile`/`line` populated.
`manual-qa/AC-5.md` step 5's expected-result text was tightened to match this actual
message shape (explicit `"phase":"COMPOSE"` + `Missing class: <FQCN>`).

---

## Files changed this round

- `src/main/java/org/zkoss/zkidea/preview/ZkClasspathFilter.java` — added `filterLibraryJars`.
- `src/main/java/org/zkoss/zkidea/preview/ZulPreviewServerService.java` — `resolveTarget`
  now uses `filterZkJars` only as the "has ZK jars" gate and `filterLibraryJars` for the
  actual handoff/signature; `PreviewTarget.zkJars` renamed to `libraryJars`.
- `src/test/java/org/zkoss/zkidea/preview/ZkClasspathFilterTest.java` — added
  `filterLibraryJarsKeepsEveryFileRegardlessOfName` and
  `filterLibraryJarsExcludesDirectories`.
- `src/test/java/org/zkoss/zkidea/preview/ZulPreviewLauncherSeamTest.java` — new
  (E3-G1b).
- `zk-preview-launcher/src/main/java/org/zkoss/zkpreview/VariantDetector.java` — added
  `preferCanonicalZkCoreJar` reordering before the byte-scan.
- `zk-preview-launcher/src/test/java/org/zkoss/zkpreview/VariantDetectorTest.java` — new.
- `build.gradle` — root `test` task now `dependsOn ':zk-preview-launcher:jar'` and
  exposes `zkpreview.launcherJar` as a system property (needed by the new seam test).
- `tasks/zul-preview/PLAN.md` — §5 AC-4(i) wording updated; §8 loop state appended with
  this round's result (§8's existing round-1 entries left intact, per instructions).
- `manual-test/src/main/webapp/preview/broken.zul` — fixed illegal `--` inside its XML
  comment (round-1 E3 fixture bug; it failed at PARSE instead of the intended
  COMPOSE-phase missing-zscript-class failure — see the fixture-fix section above).
- `tasks/zul-preview/manual-qa/AC-5.md` — step 5 expected-result text tightened to the
  actual message shape (`"phase":"COMPOSE"`, `Missing class: <FQCN>`).

No changes to: `RenderEngineFactory.java`, `Main.java`, `ScopedZkClassLoader.java`,
`IsolatedRuntime.java`, either `RenderEngine` implementation, any launcher gate fixture
(`zk-preview-launcher/src/test/resources/fixtures/`), or any other E1 test file.

---

## Per-gate self-assessment

- **D1**: FIXED, reproduced before and after with the real launcher jar and the real
  manual-test classpath (both a hand-driven CLI repro and the automated E3-G1b seam
  test). High confidence.
- **D2**: Root cause as literally described ("wrong variant chosen from manual-test's
  real classpath") **not reproduced** despite exhaustive effort (3 independent checks
  against the real dependency-resolved classpath). A real, adjacent, and directly
  plan-flagged robustness gap was found and fixed with a red→green regression test.
  Medium-high confidence this eliminates the *class* of bug Fable was worried about;
  **not proven** to be the literal mechanism behind the user's pasted log line, since
  that live IDE session's exact classpath state is not reconstructible from this sandbox.
- **E3-G1b**: DONE, red before / green after, using the real packaged launcher jar and
  the real plugin-side filtering code (not a hand-rolled substitute).
- **broken.zul fixture (AC-5 step 5)**: FIXED and verified against the real launcher —
  now fails at `phase: COMPOSE` naming the missing FQCN, exactly as the manual script
  (updated to the precise message shape) expects. High confidence.
- **Build gates**: root `./gradlew build test` green (298 tests), `:zk-preview-launcher:test`
  green (30 tests) — both hard constraints satisfied.
- **MANUAL-PENDING for the user's re-run**: everything in round 1's
  `tasks/zul-preview/manual-qa/AC-5.md` is still MANUAL-PENDING (JCEF rendering,
  save-triggers-refresh, full end-to-end teardown) — this round only fixes the
  bootstrap-crash defects blocking step 1 of that script from ever getting that far.
  Additionally flag for the user: if the pasted `JavaxRenderEngine` log line recurs on
  a genuine jakarta project after this fix, please capture the exact
  `OrderEnumerator`-resolved classpath at that moment (e.g. add a temporary log line in
  `ZulPreviewServerService.resolveTarget` or inspect via the debugger) so D2's precise
  live-IDE trigger can finally be pinned down — this round's fix is a solid hardening
  but was not falsifiable against the original failure mode.

# E3 Round 2 — Verifier Verdict (Opus, fresh context)

> Independent re-run of every claim in `E3-evidence-round2.md`. Commands executed from
> repo root with `withjdk.sh 17`. Nothing below is taken from the maker's evidence on
> trust — each row is reproduced. Round 1 failed the user's manual gate: the launcher
> child JVM crashed at bootstrap (`NoClassDefFoundError org/slf4j/LoggerFactory`) because
> the plugin's ZK-only classpath filter starved it of transitive deps (D1); the pasted
> stack also named `JavaxRenderEngine` on a jakarta project (D2).

## Verdict table

| # | Item | Verdict | Evidence (reproduced) |
|---|------|---------|-----------------------|
| V1 | Full clean builds | **PASS** | `./gradlew clean build test` → exit 0; `build/test-results/test/*.xml` = **298 tests, 0 failures, 0 errors, 0 skipped**. `./gradlew :zk-preview-launcher:clean :zk-preview-launcher:test` → exit 0; **30 tests, 0 failures, 0 errors, 0 skipped**. Matches maker's 298/30. |
| V2 | Seam test real & load-bearing (E3-G1b) | **PASS** | `ZulPreviewLauncherSeamTest` satisfies (a) mvn `dependency:build-classpath`, (b) real `ZkClasspathFilter.filterLibraryJars` (production class), (c) fake module-output DIR added then asserted excluded, (d) spawns REAL packaged jar via `zkpreview.launcherJar` sys-prop, (e) asserts `PREVIEW_PORT`+HTTP 200+`zkmx(`. It **ran, not skipped** (`skipped="0"`, `time=1.942s`). Original bug independently reproduced (below). |
| V3 | D1 fix correctness | **PASS** | `filterLibraryJars` = all non-dir entries, excludes dirs; `ZulPreviewServerService.resolveTarget` hands `filterLibraryJars` output to launcher (line 135/106) and uses `filterZkJars` only as R7 "has ZK" gate (line 133); `ZkClasspathFilterTest` new cases use real files/dirs (non-tautological); PLAN.md §5 AC-4(i) amended to all-library-jars/no-directories. |
| V4 | D2 disposition | **PASS** | `VariantDetector` has canonical `zk-<version>.jar` precedence + full-scan fallback; `VariantDetectorTest` green (decoy-first flips pre-fix). Independent detect on manual-test's full 30-entry classpath → **JAKARTA** in as-is/shuffled/reversed orderings. Maker's "not reproducible from manual-test's real classpath" is honest & evidence-based (I couldn't reproduce JavaxRenderEngine either). |
| V5 | broken.zul fixture | **PASS** | `xmllint --noout` exit 0 (well-formed). Real launcher + full classpath → `{"status":"FAILURE","error":{"phase":"COMPOSE","message":"Missing class: org.example.definitely.NoSuchClassAtAll (...)","zulFile":"/preview/broken.zul","line":7,"column":null}}`. `manual-qa/AC-5.md` step 5 expected text matches (phase COMPOSE + `Missing class:`+FQCN + line 7). |
| V6 | Repo integrity | **PASS** | `git diff --stat` = only `build.gradle`, `settings.gradle`, `plugin.xml` (all expected). Preview package/launcher/broken.zul/tasks are untracked new files. No commits (HEAD still `35348ef`). Isolation/canary green within the 30 (IsolationTest 8, IsolationChildProcessTest 1, CoreIndependenceTest 4). |

## Overall gate verdicts

- **E3-G1b (seam test): PASS** — real filter method + fake-dir exclusion + real packaged jar + PREVIEW_PORT/200/zkmx, ran green.
- **D1 (root-cause crash): FIXED** — reproduced before (ZK-only classpath crashes) and after (full classpath serves 200).
- **D2 (wrong variant): HARDENED, honestly dispositioned** — literal symptom not reproducible from manual-test's real deps (independently confirmed); detector precedence fix eliminates the order-fragility class introduced by D1's widening.
- **broken.zul fixture: FIXED** — COMPOSE-phase structured failure verified against real launcher.

## Reproduced evidence highlights

**V1** root: `files=28 tests=298 failures=0 errors=0 skipped=0`; launcher: `files=8 tests=30 failures=0 errors=0 skipped=0`. Both gradle invocations exit 0.

**V2 causal story — ZK-only (narrow) classpath = user's crash:**
```
$ java -jar zk-preview-launcher.jar --classpath "<13 org.zkoss jars, no slf4j>" --webapp manual-test/src/main/webapp --port 0
Exception in thread "main" java.lang.IllegalStateException: Failed to bootstrap the ZK mock webapp
  at org.zkoss.zkpreview.jakarta.JakartaRenderEngine.<init>(JakartaRenderEngine.java:61)
Caused by: java.lang.NoClassDefFoundError: org/slf4j/LoggerFactory
  at zk-preview-scoped//org.zkoss.zk.ui.http.WebManager.<clinit>(WebManager.java:86)
  at zk-preview-scoped//org.zkoss.zk.ui.http.HttpSessionListener23.contextInitialized(HttpSessionListener23.java:140)
Caused by: java.lang.ClassNotFoundException: org.slf4j.LoggerFactory   [exit 1]
```
Byte-for-byte the user's D1 crash — and it selected **JakartaRenderEngine** (correct), confirming D2's "manual-test detects jakarta correctly".

**V2 wide (fixed) classpath — full 29-entry manual-test classpath:**
```
PREVIEW_PORT=59860
/preview/button.zul  -> HTTP 200, body contains zkmx(
/model.zul (MVVM)    -> HTTP 200
/preview/broken.zul  -> {"status":"FAILURE",...,"phase":"COMPOSE",...}
```

**V4 detector, manual-test full classpath (launcher's own compiled `VariantDetector`):**
```
entries=30
as-is order  -> JAKARTA
shuffled(42) -> JAKARTA
reversed     -> JAKARTA
```

## Defects (blocking)

None.

## Suggestions (non-blocking)

1. **Seam test doesn't lock the `resolveTarget`→`filterLibraryJars` wiring.** `ZulPreviewLauncherSeamTest` calls `ZkClasspathFilter.filterLibraryJars` directly (correct per the gate's letter), but `ZulPreviewServerService.resolveTarget` is where the production choice of `filterLibraryJars` vs `filterZkJars` actually lives, and it is not exercised by any automated test (it's private + IntelliJ-platform-bound). A future revert of that one line would not turn the seam test red. Consider a thin extraction/seam so the handoff-classpath decision is unit-testable, or a comment pinning the invariant. (The wiring is currently correct — verified by reading.)
2. **D2 live-session trigger still unproven.** The maker's flag stands: if `JavaxRenderEngine` recurs on a genuine jakarta project after this fix, the exact `OrderEnumerator`-resolved classpath at that moment must be captured. The hardening is sound but was never falsified against the original failure mode. Keep as an open watch item, not a blocker.
3. **ZK-prefix list still misses addon jars** (carried from round-1 suggestions: zkcharts/keikai) — only affects the R7 "has ZK" gate now, not the handoff, so lower severity than before.

## Ready for user manual re-run: YES

The bootstrap crash that killed the round-1 manual gate (D1) is definitively fixed and reproduced both ways; manual-test's real classpath correctly selects the jakarta engine; broken.zul now produces the intended COMPOSE-phase structured failure; the AC-5 script's step-5 expected text matches actual output. The remaining D2 item is an honestly-disclosed, unreproducible-in-sandbox watch item that does not block the manual re-run.

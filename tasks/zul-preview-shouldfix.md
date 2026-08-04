# ZUL Preview — should-fix pass (post-1.0.0 review)

## L1 · Executive summary

Fix the "should-fix soon" findings from [zul-preview-code-review.md](zul-preview-code-review.md).
All are pre-existing on `master` after the 1.0.0 merge; none is a regression. Approach is
test-first (RED→GREEN) for every item that has a non-platform seam.

| Item | What | Risk | Test seam |
|------|------|------|-----------|
| L2 | `stderrTail` grows unbounded → trim on append | trivial | pure helper `appendBounded` |
| L1 | shared `MockHttpSession` reused every render → fresh session per render | low | per-render session counter |
| M5 | single-threaded HTTP server → bounded thread pool (safe only after L1) | medium | stub-engine parallelism test |
| U3 | "no ZK jars" message wrong for a stale/unresolved classpath | low | pure classifier `detectZkPresence` |
| U2 | throw before the try → pane stuck "loading" forever | low | pure guard `startGuarded` |
| U1 | helper JVM fork/exec runs on the EDT | low | (platform threading — no unit seam) |
| I1 | isolation rests on classpath narrowness / resource-root scope | n/a | **won't-fix — see L3** |

Progress: implementing.

## L2 · Phase breakdown

**L2 — bound stderr tail.** `ManagedPreviewServer` appends every stderr chunk to a
`StringBuilder` for the process's whole life; the 2000-char cap is applied only lazily at
read time. Fix: extract `static appendBounded(StringBuilder, String, int)` that trims to the
last `limit` chars on every append; `tail()` then just returns the (already-bounded) buffer.
Gate: a unit test drives many oversized appends and asserts the retained length never exceeds
the limit and the content equals the last `limit` chars.

**L1 — fresh session per render.** Both `JakartaRenderEngine` and `JavaxRenderEngine` create
one `MockHttpSession` in the constructor and reuse it for every `renderZul`/`resource` call,
so ZK desktops accumulate in one session for the JVM's life and separate preview tabs share a
session. Fix: create a fresh `MockHttpSession` per call. Gate: a same-package test renders N
times through one engine and asserts N distinct render sessions were created.

**M5 — bounded thread pool.** `PreviewHttpServer` never sets an executor, so
`com.sun.net.httpserver` dispatches serially: one hung render freezes every preview tab
sharing the JVM. Fix: `httpServer.setExecutor(<small fixed daemon pool>)`, shut the pool down
in `stop()`. Safe only because L1 makes each render use its own session/request/response
(the shared `MockHttpSession` HashMap was the race the review flagged). Gate: a deterministic
test with a stub `RenderEngine` whose one path blocks on a latch — a second request must
still complete while the first is blocked (fails RED single-threaded, passes GREEN pooled);
plus a real concurrent-render smoke.

**U3 — stale-classpath message.** `hasZkJars` matches ZK jars by filename only (no `isFile()`);
`libraryJars` requires `isFile()`. A declared-but-wiped repo cache ⇒ `hasZkJars` true but
`libraryJars` empty ⇒ the "add a ZK dependency you already have" message. Fix: pure
`ZkClasspathFilter.detectZkPresence` → `{NONE, DECLARED_BUT_MISSING, PRESENT}`; map
`DECLARED_BUT_MISSING` to a new `PreviewResult.staleClasspath()` ("re-import/re-sync"). Gate:
classifier unit test over the three cases.

**U2 — no stuck loading.** `startServer` builds the command line (incl. `resolveLauncherJar()`,
which throws when the plugin descriptor is null) *outside* its `try`, on a `compute()` lambda,
so an escape never reaches `onReady` and the card stays on "loading" with no error/report.
Fix: `static startGuarded(Supplier<GeneralCommandLine>)` that routes any throw to
`ManagedPreviewServer.failed(e)`; `startServer` builds the command line *inside* the supplier.
Gate: unit test — a throwing supplier yields a server whose `portFuture` fails (not a throw).

**U1 — off-EDT fork/exec.** `onTargetResolved` (hence `startServer`'s `KillableProcessHandler`
fork/exec) runs on the EDT via `finishOnUiThread`, freezing the IDE for process-creation time
on the feature's most common path. Fix: drop `finishOnUiThread`; run `onTargetResolved` on the
pooled executor via `.onSuccess`, and marshal every `onReady` call to the EDT (the browser/UI
work `onReady` does still runs on the EDT). No unit seam (platform threading); verified by build
+ the existing seam/teardown tests.

## L3 · Technical appendix

### I1 — won't-fix, with rationale
Two sub-points, neither a safe code change:
1. **`ForbiddenLoadTracker` is a no-op in production** — by design. `Main` calls the 2-arg
   `RenderEngineFactory.create` (tracker `null`); the real isolation guarantee is that the
   module *output* dir is off the classpath (`filterLibraryJars`) + the `UiFactory` no-op hook.
   The tracker is a test-only lock on that invariant. Adding a production blocklist would be
   speculative and change the isolation model on a release branch — out of scope for a bug pass.
2. **Resource root put on the render classpath is the whole `src/main/resources`, not `web/`** —
   cannot be narrowed to `web/`. ZK's `ClassWebResource` resolves a `~./foo.zul` page to the
   classpath resource `/web/foo.zul`, so the classpath entry MUST be the directory that
   *contains* `web/` (i.e. the resource root). Narrowing to `web/` would put pages at
   `/foo.zul` and break `~./` resolution — the exact feature P4 added. The residual "a user's
   `metainfo/zk/config.xml` could be scanned" is bounded: any listener/initializer class it
   names lives in the excluded output dir ⇒ `ClassNotFoundException`, not silent execution;
   pure-data config is harmless. Documented, not changed.

### Change log
- **Done.** L2, L1, M5, U3, U2 implemented test-first (RED confirmed → GREEN); U1 is a structural
  threading change (no unit seam) verified by build + full suite. I1 documented, no code change.
  New tests (all ran, 0 skipped/failed): L2 `ManagedPreviewServerStderrTailTest` (2), L1
  `jakarta.JakartaSessionPerRenderTest` (1), M5 `PreviewHttpServerConcurrencyTest` (2 — incl. 6
  real concurrent renders), U3 `ZkClasspathFilterTest.detectZkPresence*` (3), U2
  `ServerStartGuardTest` (1). Full `:test` + `:zk-preview-launcher:test` → BUILD SUCCESSFUL.
- Gotcha (see lessons #22): the pre-existing `ManagedPreviewServerTeardownTest` NPEs when run in
  isolation (`--tests`) because `KillableProcessHandler.destroy()` needs an initialized IntelliJ
  `Application`; it only passes in a full-suite run where an earlier platform test bootstraps the
  global singleton. Dropped a redundant valid-supplier companion test that had the same dependency.

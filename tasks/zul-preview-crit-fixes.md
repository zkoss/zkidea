# ZUL Preview — fixing the three Criticals (R2-CRIT1/2/3)

Source: [zul-preview-code-review-2.md](zul-preview-code-review-2.md). All three are *diagnosability*
failures. TDD: failing test first, then the fix.

## Plan

1. **R2-CRIT1 · `ScopedZkClassLoader` drops the per-class-name lock**
   → test: `ScopedZkClassLoaderConcurrencyTest` — N threads released from a barrier all first-touch
     the same `org.zkoss.*` class through one loader; assert no `LinkageError` and one `Class` identity.
   → fix: `synchronized (getClassLoadingLock(name))` + `registerAsParallelCapable()`.
   → verify: test red before, green after; `:zk-preview-launcher:test` green.

2. **R2-CRIT2 · `resource()` collapses every failure into a blank, unlogged 404**
   → test: `ResourceFailureDiagnosticsTest` — an exception and a ≥400 status each leave a
     stderr line naming the path; a success leaves stderr untouched.
   → fix: extract the two outcome branches of `resource()` into platform-free package-visible
     seams that emit the diagnostic (and unwrap `InvocationTargetException`) before returning.
   → verify: test red before, green after. Served behaviour deliberately unchanged.

3. **R2-CRIT3 · `preparePreview` wires no `.onError`**
   → test: `ResolveFailureDeliveryTest` — a rejected resolve promise must reach the failure
     consumer; a resolved one must still reach the success consumer.
   → fix: route both outcomes through a package-visible `wireResolveOutcome` seam (the same
     shape as the existing `startGuarded` seam for U2) and wire `.onError` to
     `PreviewResult.error(rootMessage(ex))`.
   → verify: test red before, green after; `:test` green.

## Scope note

R2-CRIT2's finding also names *"nothing in `idea.log`"*. The launcher-side half is fixed here.
Forwarding **all** helper-JVM stderr to `idea.log` is a separate decision (ZK bootstraps through
j.u.l → stderr, so it would be chatty; `ManagedPreviewServer` already bounds a `stderrTail` for
exactly that reason) — left out deliberately, raised as a follow-up.

## Review

All three fixed, TDD, each red before green.

| ID | Test (new) | Red evidence | Production change |
|---|---|---|---|
| R2-CRIT1 | `ScopedZkClassLoaderConcurrencyTest` | **1074 / 1280** concurrent `loadClass` calls threw `LinkageError: attempted duplicate … definition` | `ScopedZkClassLoader`: child-first branch under `synchronized (getClassLoadingLock(name))`; `registerAsParallelCapable()` |
| R2-CRIT2 | `ResourceFailureDiagnosticsTest` (4) | seams absent → compile failure; assertions then pin the stderr content | `AbstractRenderEngine`: `resourceOutcome` / `resourceFailure` seams emit `[zk-preview]` diagnostics, unwrap `InvocationTargetException` |
| R2-CRIT3 | `ResolveFailureDeliveryTest` (3) | `.onError` stripped back off the seam → the failure test fails | `ZulPreviewServerService`: `wireResolveOutcome` seam; `.onError` → `PreviewResult.error(rootMessage(ex))`; `rootMessage` package-visible |

Suites: `:zk-preview-launcher:test` **200 passed, 0 failed**; `:test` **335 passed, 0 failed**.

Behaviour deliberately unchanged: R2-CRIT2 still serves 404 for a failed asset (a ZK error body must
not land in a `<script>`/`<link>` slot) — only the silence is fixed.

### Follow-up raised, not taken

R2-CRIT2's finding also names *"nothing in `idea.log`"*. `ManagedPreviewServer` captures helper stderr
into a bounded `stderrTail` but only surfaces it if the process dies before reporting a port, so these
new lines still don't reach a developer whose server is running fine. Forwarding all helper stderr to
`idea.log` needs a decision on level and filtering (ZK logs through j.u.l → stderr, hence the existing
`STDERR_TAIL_LIMIT`); left for the user to call.

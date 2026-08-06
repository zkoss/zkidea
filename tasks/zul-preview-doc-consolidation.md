# Plan — consolidate the ZUL Layout Preview documentation

The feature is implemented and released as 1.0.0. Its documentation is spread over 4 docs plus
7 session records, and three of the docs still describe the 0.8.0 state. Goal: one accurate
two-layer doc set, no session narrative, no dangling links.

Decisions taken by the user:
- **Layering:** guide + refreshed spec (not one mega-doc, not a separate backlog file).
- **Session records:** delete the 6 `tasks/zul-preview-*.md` + `tasks/todo.md`; keep
  `preview-launcher-jar-path-bug.md` (unfinished work) and `lessons.md`.

## Target doc set

| File | Role after this pass |
|---|---|
| `doc/zul-preview-feature.md` | **Canonical user-facing guide.** Enriched with the conclusions a user needs (wrong-artifact install failure, report contents). |
| `doc/zul_preview_spec.md` | **Engineering contract**, refreshed to 1.0.0: FR-1…FR-n, limitations L-1…L-14 with shipped ones marked, open review findings, won't-fix rationales. |
| `doc/feature_overview.md §10` | **Class-by-class map**, refreshed: real class list, Bridge/Template-Method note, formatted error page. |
| `doc/zul_preview_product_positioning.md` | Product view, trimmed: shipped mitigations marked, forward-looking parts kept. |

## Steps

1. **Refresh `zul_preview_spec.md`** → verify: every FR/L claim re-checked against source; no
   `tasks/zul-preview/` reference remains.
   - status header 0.8.0 → 1.0.0; drop the retired-design-record provenance line
   - FR-8 docroot: 4 layout kinds incl. the Spring-Boot classpath rule + boundary clipping
   - FR-14 HTTP: 8-thread pool (was "serial"); FR-17: formatted error page + GitHub report is
     the consumer the 0.8.0 text said did not exist
   - new FRs: JCEF diagnosis + external-browser fallback, stale-classpath status,
     session-per-render, report env block + privacy rules
   - §4: mark L-10 shipped; keep IDs stable (the guide links to them)
   - new §5 open findings (R2-MAJ1/2/3, R2-MIN1…10, review-1 M4 + the "lower" tail), each
     re-verified in code this pass
   - new §6 deliberate non-goals / won't-fix, with rationale (I1 ×2, R2-MIN7 threat model,
     jakarta/javax irreducible remainder, single-jar install, two report assemblers)
2. **Enrich `zul-preview-feature.md`** → verify: reads as a user guide, no engineering IDs.
   - troubleshooting row for the "Unable to access jarfile …/zkidea-1.0.0.jar/lib/…" install
     mistake (currently only recorded in a task file)
3. **Refresh `feature_overview.md §10`** → verify: class tables match `ls` of both source trees.
4. **Trim `zul_preview_product_positioning.md`** → verify: no claim that a shipped item is pending.
5. **Fix dangling links** in `manual-test-springboot/README.md` and
   `manual-test/src/main/webapp/preview/syntax/README.md` → verify: `grep -rn "tasks/zul-preview" --include="*.md"` clean.
6. **Delete** the 7 session records → verify: `git status` shows only intended deletions.

## Step 7 (added on request) — inline the cited facts into the javadoc

55 citations across 28 source files pointed at docs retired in `7c921db` (`PLAN.md` R5/R7/D1-D4/
§5/§8, `RESEARCH.md` U1/U4/U5/U6/U7, `PLAN-followups.md` P1/P2, `PLAN-P3-syntax-corpus.md`,
`E1/E3/E4-evidence.md`, `MANUAL-*.md`, `manual-qa/AC-5.md`, `stage2-hook.md`, `URL-LENGTH.md`,
`stage2-error-pane/PLAN.md`). Recovered every one of those files from git
(`git show 7c921db^:<path>`), extracted the cited item, and rewrote each comment to carry the
fact instead of the pointer. → verify: zero retired-doc citations remain; full build green.

## Review

**Done.** All six steps executed.

| File | Change |
|---|---|
| `doc/zul_preview_spec.md` | Rewritten. 22 FRs (was 18) covering JCEF diagnosis + external-browser fallback, the 3-state ZK-presence classification, the 4 docroot layout kinds, the threaded HTTP server, session-per-render, the formatted error page as the structured contract's consumer, and the report contract (§2.7: label set, flag names, why those fields, budget + privacy rules). §4 keeps L-1…L-14 stable with L-10 marked shipped. New §5 open findings (R2-MAJ1/2/3, R2-MIN1…10, review-1 M4, plus the deferred `idea.log` decision and the low-value tail). New §6 non-goals with rationale. New §7 verification assets. |
| `doc/zul-preview-feature.md` | Placeholder rows for model-bound data components documented; three troubleshooting rows added (stale classpath, the single-jar install failure, plus the existing ones kept); pointer to the open-findings section; "short-lived" JVM contradiction removed. |
| `doc/feature_overview.md §10` | "since 1.0.0"; plugin table gained `JcefAvailability`, `PreviewIssueReporter`, `BuildSystemDetector` and the seam/guard behaviour of the existing classes; launcher table gained `AbstractRenderEngine` (Template Method), `mockcore/*Core` (Bridge), `IsolationMode`/`ForbiddenLoadTracker` (inert in production); `PlaceholderInjector` added to the isolation section; error-page path corrected in "How it works". |
| `doc/zul_preview_product_positioning.md` | Status header; M-1/M-2/M-3 and all three P0 items marked ✅ with what actually shipped; §4 notes which launch recommendations remain. |
| `manual-test*/README.md` ×2 | Retired-doc links repointed to the live docs / `ZulSyntaxCorpusTest`. |
| 7 × `tasks/*.md` | Deleted (unstaged deletions, so reviewable in `git diff`). |
| 28 × `*.java` | Comment-only. 10 files: `tasks/preview-report-environment-analysis.md §3x` → `doc/zul_preview_spec.md §2.7`. 18 more (step 7): every citation of a doc retired in `7c921db` replaced by the fact it cited, recovered from git. |

### Verified
- Every FR/L claim re-checked against source; the open-findings list is **re-verified state**,
  not inherited text — each of R2-MAJ1/2/3 and R2-MIN1…10 was confirmed still present
  (`withinBoundary` empty-list branch, `userGesture` clause, unguarded reload lambda,
  unescaped fence, `File.pathSeparator` join, `getMimeType` → null, `resourceFile` null,
  UTF-8 fallback, date-header no-ops, "Jakarta" javadoc in `javax/`, `send()` forwarding only
  `Content-Type`).
- Items the docs had left ambiguous and are now recorded as **fixed**: review-1 C2 (dim-style
  merge), M2 (encoded-length URL cap), S1/R2-MIN7 lexical containment, R2-CRIT1/2/3.
- `grep -rn "tasks/zul-preview/" --include="*.md"` → only this plan file. FR ids 1…22 unique
  and sequential; L ids 1…14 all present.

### Facts recovered into the code (step 7 highlights)

Where a citation was pure provenance the pointer was dropped; where it carried substance the
substance was inlined. The ones that changed what the code says about itself:

- **`ZkClasspathFilter.filterLibraryJars`** now records *why* the classpath must never be
  narrowed to ZK-named jars: the earlier ZK-only version shipped and died at ZK bootstrap with
  `NoClassDefFoundError: org.slf4j.LoggerFactory` from `WebManager.<clinit>`.
- **`ZulPreviewLauncherSeamTest`** states that same crash as its RED condition, so re-pointing
  the test at `filterZkJars` is a documented way to reproduce it.
- **`PreviewUiFactory`** now explains that the alternative design (a `BindComposer` subclass
  overriding `initViewModel`) is impossible — `javap` on zkbind 9.6.0.2 and 10.1.0 shows that
  method is `private` — which is why this one hook is the entire isolation mechanism.
- **`IsolationMode`** spells out what canary mode proves: hooks off ⇒ a fixture naming a user
  Composer/ViewModel must fail with `ClassNotFoundException` for that exact FQCN.
- **`ErrorPageRenderer`** carries the measurement behind `MAX_URL_LENGTH`: a worst-case report
  URL measured 8,210 chars against the ~8 KB request-line limit.
- **`RenderEngine.auStub`** / **`RenderError`** / **`RenderEngineFactory`** now state the
  underlying facts (first paint needs no AU round-trip but *does* need `/zkau/web/*`; which
  failure shapes structurally carry no line number; javax = ZK ≤9, jakarta = ZK 10+).
- **`JcefAvailability`** records that there is no portable "why unsupported" API to prefer —
  JCEF ships with the JBR, not the IDE jars — and how to force the unavailable path in `runIde`.
- **Two comments were stale, not just dangling, and are now corrected**: `PathResolutionTest`
  claimed a user's own `~./` resources are never passed to the launcher (resource roots have
  been passed since the P4 fix), and `IncludeTest` framed ZK includes as going through
  `RequestDispatcher.include` (ZK uses an *instant* include for `.zul`, which is why no mock
  dispatcher was ever needed).

Full build after the pass: **BUILD SUCCESSFUL** — 355 plugin tests + 208 launcher tests,
0 failures, 0 errors, 0 skipped.

### Flagged, not done
- **`plugin.xml` has no 1.0.0 change-notes entry** — the top entry is 0.7.3, so the Layout
  Preview release has no marketplace release copy. That is outward-facing text; it needs the
  user's wording, not mine.
- `README.md`'s Release Process still has no "install a local build into your IDE" step
  (Part 3 of `preview-launcher-jar-path-bug.md`); the user-facing symptom is now at least
  documented in the feature guide's troubleshooting table.
- ~12 javadoc citations of other retired evidence files (`PLAN.md` D1/E3, `E1-evidence.md`,
  `PLAN-P3-syntax-corpus.md`, `RESEARCH.md` U6, `manual-qa/AC-5.md`) predate this pass and
  were left alone — they are historic provenance inside test comments, and the evidence lives
  in git history.

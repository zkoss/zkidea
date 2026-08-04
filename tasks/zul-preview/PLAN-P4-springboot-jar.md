# P4 — ZUL Preview in a Spring Boot *jar*-packaging project

> Source issue: [tasks.md](tasks.md) — "test within springboot jar packaging project".
> Parent state: [PLAN-followups.md](PLAN-followups.md) (P1–P3 DONE; **P4 NOT STARTED**).

---

## L1 — Executive summary

**Goal.** Make the Layout Preview render a ZUL that lives in a **Spring Boot jar** project —
i.e. on the classpath under `src/main/resources/web/`, with **no** `src/main/webapp` and no
`WEB-INF`. Today `DocrootResolver` only knows the WAR layout (WEB-INF / a dir named `webapp`),
so an SB-jar page falls through to the module-root fallback and is served under a wrong,
non-production request URL (`/src/main/resources/web/…`). Fix the docroot rule so an SB-jar page
is served from its classpath `web` root at its **production** URL (`/…`).

**Why it's small.** The classpath (resource roots on `--classpath`) is *already* correct — that
was the P2 `~./` fix. P4 is essentially one pure-function rule: recognise `<resourceRoot>/web`
as a docroot. The rest is a committed sample project + a manual runIde verification.

**Milestones.**

| Phase | Title | Gate |
|---|---|---|
| **P4.0** | Ground the failure empirically | ✅ Minimal SB-jar sample built; RED confirmed the current resolver returns the module root (docroot) for an SB page ⇒ wrong url `/src/main/resources/web/…`. |
| **P4.1** | RED: failing `DocrootResolver` tests for the SB layout | ✅ 2 SB tests failed against the stub; existing 5 (+2 guards) green. |
| **P4.2** | GREEN: extend `DocrootResolver` + wire it in | ✅ `DocrootResolverTest` 9 green; full plugin suite green (321), no regressions. |
| **P4.3** | Manual runIde verification + docs | ⏳ `MANUAL-springboot-jar.md` written; **runIde verification pending user**. |

**Overall progress: headless DONE; runIde manual PENDING.**

---

## L2 — Phase breakdown

### P4.0 — Ground the failure (investigate, don't assume)
- **Input.** The confirmed SB-jar convention (ZK docs: `src/main/resources/web/**.zul`, `~./`
  prefix, `static/` for `/`-URLs).
- **Output.** A minimal committed sample `manual-test-springboot/` (Maven, `packaging jar`) and a
  one-paragraph record of what the launcher does with it **before** the fix: does it render at
  all, and with what (wrong) request path / page self-URI? This is the objective RED.
- **Acceptance gate.** The current-code behavior is written down (rendered-but-wrong-URL vs
  outright fail), not guessed.

### P4.1 — RED (failing tests first, per TDD)
- **Input.** P4.0's finding + the SB `web`-root rule.
- **Output.** New `DocrootResolverTest` cases (temp-dir file trees, no IntelliJ dependency):
  1. zul at `…/src/main/resources/web/zul/page.zul`, resource-root `…/src/main/resources` →
     docroot = `…/src/main/resources/web`.
  2. top-level zul `…/web/index.zul` → docroot = `…/web` (⇒ request path `/index.zul`).
  3. a `web` dir **not** under any resource root → rule does **not** fire (guards against
     over-broad matching); falls back as today.
  4. WAR layout still wins when both a `webapp/WEB-INF` ancestor and a `resources/web` exist
     (ordering / no regression).
- **Acceptance gate.** New tests fail against current resolver; the existing 5 tests still pass.

### P4.2 — GREEN (implement)
- **Input.** The RED suite.
- **Output.**
  - `DocrootResolver`: add a 3-arg `resolve(zulFile, boundaryRoots, resourceRoots)` that, after the
    existing WEB-INF/`webapp` rule, returns the nearest ancestor named `web` whose parent is one of
    `resourceRoots`. Keep the current 2-arg `resolve(...)` as an overload delegating with
    `List.of()` → **existing WAR behavior and tests untouched** (surgical; regression-safe).
  - `ZulPreviewServerService.resolveTarget`: pass the module's RESOURCE source roots (already
    computed there for the `~./` classpath fix) into the new 3-arg call.
- **Acceptance gate.** Full new + existing `DocrootResolverTest` green; entire plugin suite green;
  the SB sample from P4.0 now renders at the production URL through the launcher.

### P4.3 — Manual verification + docs
- **Input.** GREEN build + the sample.
- **Output.** `MANUAL-springboot-jar.md` (open the sample in the runIde sandbox IDE, open a
  `web/**.zul`, confirm the Layout Preview renders and the URL is `/…` not `/src/main/resources/…`);
  update `PLAN-followups.md` status + revision log.
- **Acceptance gate.** User confirms a SB-jar ZUL previews; WAR/`manual-test` path unaffected.

---

## L3 — Technical appendix

<details>
<summary>Grounding: current resolver + why SB-jar is unhandled</summary>

- [DocrootResolver.java](../../src/main/java/org/zkoss/zkidea/preview/DocrootResolver.java) —
  `resolve(zulFile, boundaryRoots)`: walk parents → first with `WEB-INF/` or named `webapp` →
  docroot; else nearest boundary root ancestor; else the file's parent.
- [ZulPreviewServerService.java:127-178](../../src/main/java/org/zkoss/zkidea/preview/ZulPreviewServerService.java#L127-L178)
  `resolveTarget`: already computes RESOURCE source roots and puts them on `--classpath`
  (the P2 `~./` fix, lines ~156-166), then calls `DocrootResolver.resolve(zulPath, contentRoots)`
  and relativizes the docroot to build the request path.
- **SB-jar today:** no `WEB-INF`, no `webapp` ⇒ WAR rule misses ⇒ falls back to the boundary
  (module content) root ⇒ request path becomes `/src/main/resources/web/…zul` and the page's
  self-URI is likewise non-production. The classpath is already right, so `~./` refs resolve, but
  the top-level page URL is wrong. P4.0 confirms the exact symptom empirically.
</details>

<details>
<summary>Confirmed SB-jar convention (ZK docs)</summary>

From `zkspringboot-demo-jar` (ZK Installation Guide → Spring Boot):
- `src/main/resources/web/` = ZK classpath web-resource path (ZUL pages; e.g. `web/zul/resources.zul`).
- ZK resources incl. zul use the `~./` prefix → `~./zul/mvvm-page1.zul` == `web/zul/mvvm-page1.zul`.
- `src/main/resources/static/` = Spring Boot static dir, referenced with `/`-prefixed URLs.
- `web` is a fixed ZK convention (ClassWebResource `/web`), so matching `<resourceRoot>/web`
  is *convention*, not a heuristic.
</details>

<details>
<summary>Proposed DocrootResolver rule (sketch)</summary>

```
resolve(zulFile, boundaryRoots):                       // preserved overload → WAR behavior
    return resolve(zulFile, boundaryRoots, List.of())

resolve(zulFile, boundaryRoots, resourceRoots):
    for candidate up from parent:
        if hasWebInf(candidate) || isNamedWebapp(candidate): return candidate   // WAR (unchanged, first)
    for candidate up from parent:
        if candidate.name == "web" && resourceRoots.contains(candidate.parent): // SB-jar classpath web root
            return candidate
    ... existing boundary-root fallback ...
    ... existing parent fallback ...
```
WAR rule stays first; the two layouts are disjoint in practice, but ordering makes it explicit.
</details>

<details>
<summary>Sample project layout (minimal)</summary>

`manual-test-springboot/` (Maven, `<packaging>jar</packaging>`, `zkspringboot-starter` dep so the
IDE resolves ZK jars onto the module classpath; `server.port` ≠ 8080 per standing constraint):
- `src/main/resources/web/index.zul` — top-level page (proves request path `/index.zul`).
- `src/main/resources/web/zul/page.zul` — nested page + a `~./` reference (proves classpath +
  nested request path `/zul/page.zul`).
- Minimal `DemoApplication` + `application.properties` for faithfulness (NOT exercised by the
  headless preview render — the launcher renders via ZK jars + resource roots + docroot, not the
  user's Spring context).
</details>

<details>
<summary>Constraints (from PLAN-followups §2)</summary>

- Run everything via `withjdk.sh 17 …`.
- **Port 8080 is the user's app — never bind/kill/probe it.** Sample sets `server.port` ≠ 8080;
  the preview server picks its own ephemeral port anyway.
- Surgical commit; exclude the standing untracked drafts; commits are user-directed, not pushed.
- Validate every new `.zul` with `xmllint --noout`; rendered-value markers hyphen-free (lesson #14).
</details>

<details>
<summary>Change log</summary>

| ver | date | change |
|---|---|---|
| v0 | 2026-08-04 | Initial P4 plan, grounded on DocrootResolver + ZulPreviewServerService and the confirmed SB-jar convention. Not started. |
| v1 | 2026-08-04 | Headless DONE. `DocrootResolver` 3-arg SB-jar rule (`<resourceRoot>/web`) + `DocrootResolverTest` 9 green; `resolveTarget` wired; full plugin suite green (321). Sample `manual-test-springboot/` added. **Sample-pom bug found via the user's runIde attempt** — missing `ZK EE` repo ⇒ `zkmax` resolved but transitive `zkex` did not ⇒ launcher died with `NoClassDefFoundError …CometServerPush$AsyncInfo`; fixed by adding the third repo + `zuti` (mirror `manual-test`). End-to-end proof: real launcher jar + sample's resolved classpath + docroot `web/` served `/index.zul` and `/zul/page.zul` HTTP 200 with live EL (`${desktop.id}`→`z_…`) and the `~./` include; Spring jars on the classpath were a non-issue. See lesson #19. runIde manual pending. |
</details>

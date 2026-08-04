# Manual test — `<apply>` / `<include>` across path forms (P2)

Headless coverage is the real proof (the launcher render path is production): `ApplyTemplateUriTest`,
`IncludeTest`, `PathResolutionTest` — all green on javax + jakarta. These runIde cases are a
**display spot-check** (lesson #1: the JCEF pane is runIde-only) and, for `~./`, an end-to-end
classpath-wiring check the headless tests deliberately cannot make.

## Setup
1. `withjdk.sh 17 ./gradlew runIde`
2. In the sandbox IDE, open the `manual-test` module and preview each `.zul` below (split editor).

## Cases & expected result

All targets live in `manual-test/src/main/webapp/preview/cases/` (relative pages under `.../cases/sub/`).
The applied/included fragments carry unique marker text so a glance confirms resolution.

| # | Open this .zul | Path form | Expect in the preview pane |
|---|---|---|---|
| 1 | `preview/cases/apply-static.zul` | absolute `/…` | `APPLIED TEMPLATE CONTENT` + `applied button` + `apply host marker` |
| 2 | `preview/cases/include-static.zul` | absolute `/…` | `INCLUDED FRAGMENT CONTENT` + `include host marker` |
| 3 | `preview/cases/sub/apply-relative.zul` | relative `../…` | `APPLIED TEMPLATE CONTENT` (same target, reached via `../`) |
| 4 | `preview/cases/sub/include-relative.zul` | relative `../…` | `INCLUDED FRAGMENT CONTENT` (same target, reached via `../`) |
| 5 | `preview/cases/apply-classweb.zul` | `~./` classpath | `CLASSWEB FRAGMENT CONTENT` (after the resource-root fix) |
| 6 | `preview/cases/include-classweb.zul` | `~./` classpath | `CLASSWEB FRAGMENT CONTENT` (after the resource-root fix) |

## Note on the `~./` cases (#5, #6) — root cause & fix

`~./x.zul` resolves via ZK's `ClassWebResource` from the **classpath** `/web/x.zul`, never the docroot.
Originally the plugin handed the launcher a **jars-only** `--classpath`: `ZkClasspathFilter.filterLibraryJars`
keeps only `file.isFile()`, so a user's own `~./` resource — which lives in a resource **directory**
(`src/main/resources/web/…`, copied to `target/classes/web/…`) — was never passed, and ZK reported
`Page not found: ~./classweb-fragment.zul`. (It works under the Jetty plugin because the whole webapp
classpath, incl. `WEB-INF/classes/web/`, is present there — confirming the path itself is correct.)

**Fix (shipped in this change):** `ZulPreviewServerService.resolveTarget` now also passes the module's
**resource roots** (`ZkClasspathFilter.filterResourceRoots` → `getSourceRoots(RESOURCE)`, directories)
on the launcher classpath — but **not** the module *output* directory. A resource root holds resources,
not compiled `.class` files, so class-isolation (AC-4(i), guaranteed by the `UiFactory` hook) is
preserved while ZK's `ClassWebResource` can resolve a user's `~./` page as it does in a real container.

Headless proof: `ClasspathResourceResolutionTest` (javax + jakarta) shows a `~./` page resolves **iff**
its resource root is on the render classpath, and `ZkClasspathFilterTest.filterResourceRoots…` locks the
"directories in, files out" rule. Cases #5/#6 are the **runIde confirmation** that
`resolveTarget` wires the real module's resource root through end-to-end.

## Status
- Headless: `ApplyTemplateUriTest` (6), `IncludeTest` (6), `PathResolutionTest` (8),
  `ClasspathResourceResolutionTest` (2) — green; `ZkClasspathFilterTest` (+1 for `filterResourceRoots`).
- Manual (runIde): **PASS** — verified by user 2026-08-04. All six cases render their applied/included
  content; cases 5/6 confirm the `~./` resource-root fix wires through end-to-end (before the fix they
  showed `Page not found`).

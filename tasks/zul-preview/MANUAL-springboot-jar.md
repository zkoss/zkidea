# Manual test — ZUL Layout Preview in a Spring Boot *jar* project (P4)

Headless coverage is the real proof of the resolution rule: `DocrootResolverTest` (9, incl. the
Spring-Boot-jar cases) — green; full plugin suite green (321). This runIde case is the **display +
classpath-wiring spot-check** (lesson #1: the JCEF pane is runIde-only) that the headless pure test
deliberately cannot make: it exercises the real module's resource roots through `resolveTarget`.

## What P4 fixes
A Spring Boot **jar** project keeps its ZULs on the classpath under `src/main/resources/web/` — no
`src/main/webapp`, no `WEB-INF`. Before the fix, `DocrootResolver` recognised only the WAR layout, so
such a page fell through to the module-root fallback and was served under the **wrong** request path
`/src/main/resources/web/index.zul`. The fix adds the ZK *classpath web root* rule: `<resourceRoot>/web`
is the docroot, so the page previews at its **production** url (`/index.zul`).

## Setup
1. `withjdk.sh 17 ./gradlew runIde`
2. In the sandbox IDE, **open `manual-test-springboot/` as a Maven project** and let Maven import
   (or "Reload All Maven Projects") so the full ZK jar set resolves onto the module classpath. It is
   a `packaging=jar` Spring Boot project.
   - **The pom needs all three ZK repos** (CE + EE-eval + **EE**). `zkmax`/`zkex` are ZK EE artifacts;
     omitting the `ZK EE` repo (`https://maven.zkoss.org/repo/zk/ee`) resolves `zkmax` but not its
     transitive `zkex`, and the launcher then dies with
     `NoClassDefFoundError: org.zkoss.zkex.ui.comet.CometServerPush$AsyncInfo` at ZK's `WebAppInit`.
     (Found and fixed during headless verification; see the pom.)

## Cases & expected result

| # | Open this .zul | Expect in the preview pane |
|---|---|---|
| 1 | `src/main/resources/web/index.zul` | Window "Spring Boot jar preview: index" renders; the nested `~./zul/page.zul` include renders inside it (its "Nested page…" labels + a live `desktop id`). |
| 2 | `src/main/resources/web/zul/page.zul` | Renders standalone: "Nested page…" labels + a live `desktop id = z_…`. |

### The load-bearing check — production URL, not the on-disk path
For case #1, confirm the served page URL is **`/index.zul`**, not `/src/main/resources/web/index.zul`.
(If it were the latter, the docroot fell back to the module root — the pre-fix behavior.) The page's
own relative/`~./` references then resolve exactly as under a real server.

## Regression check (WAR path unaffected)
Open the WAR `manual-test/` project and preview any `src/main/webapp/preview/**.zul` — it still resolves
to the `webapp` docroot and renders as before. (The WAR rule is checked first; the new rule is additive
and gated on a resource-root parent, so it cannot fire for WAR pages.)

## Spring Boot jars on the classpath — verified a non-issue
An SB-jar module has Spring Boot jars on its runtime classpath, which the plugin hands to the launcher
(`filterLibraryJars` is deliberately wide). This was verified fine: driving the real launcher jar with
the sample's full resolved classpath (ZK + `spring-boot-starter` 3.2.7 + transitive) rendered both pages
with no interference. The launcher's `ScopedZkClassLoader` keeps ZK isolated.

## Status
- Headless (pure): `DocrootResolverTest` — 9 green (2 Spring-Boot-jar cases + 1 negative guard + 1
  WAR-wins ordering guard); full plugin suite green (321), no regressions.
- Headless (end-to-end): the real `zk-preview-launcher.jar` driven with the sample's resolved classpath
  and docroot `= src/main/resources/web` served **both** pages `HTTP 200` — `/index.zul` (window + the
  `~./zul/page.zul` include rendered inline) and `/zul/page.zul` (EL live: `${desktop.id}` →
  `z_…`). Confirms the docroot rule + `~./` classpath resolution + live render together.
- Manual (runIde): **PENDING** user verification (the JCEF display pane is runIde-only, lesson #1).

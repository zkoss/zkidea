# zk-preview-launcher

A standalone ZUL rendering core. It boots ZK's real `DHtmlLayoutServlet` over a mock servlet
environment in an isolated classloader and serves the rendered first paint over plain HTTP.
Zero IntelliJ dependencies — it is an ordinary `java -jar` program.

It renders **your** ZK jars: none are bundled, and the ZK version, edition and add-ons all come
from whatever you pass to `--classpath`.

Background reading: [../doc/zul-preview-feature.md](../doc/zul-preview-feature.md) (end-user
behaviour) and [../doc/zul_preview_spec.md](../doc/zul_preview_spec.md) (the engineering contract,
including the full limitation list L-1…L-15).

## Who consumes it

| Consumer | How it gets the jar |
|---|---|
| The ZK IntelliJ plugin (this repo) | Bundled at `<plugin>/lib/zk-preview-launcher.jar`, spawned by `ZulPreviewServerService` |
| The `zul-writer` agent skill ([zkoss-demo/agent-skill](https://github.com/zkoss-demo/agent-skill)) | Downloads a pinned version from this repo's GitHub Releases |

Both use the **same artifact**; the release asset is a versioned copy of the jar the plugin ships.

## Requirements

- **Java 17 or newer.** The jar is compiled to class file version 61. An older JVM dies with
  `UnsupportedClassVersionError` at main-class load — *before* printing a port — which is by far
  the most common integration failure. Check the JVM version before blaming the launcher.
- ZK jars for the page you want to render, supplied via `--classpath`.

## CLI contract

```
java -jar zk-preview-launcher-<version>.jar \
     --classpath <File.pathSeparator-separated ZK jars and resource dirs> \
     --webapp <docroot dir> \
     --port <n>
```

| Flag | Required | Meaning |
|---|---|---|
| `--classpath` | yes | Jars (and resource-root directories) forming the ZK runtime. Separated by the platform path separator: `:` on Unix, `;` on Windows. |
| `--webapp` | yes | The document root. Requested `.zul` paths are resolved against it. |
| `--port` | no | TCP port, `0` (default) for an ephemeral one. Always bound to `127.0.0.1`. |
| `--report-plugin`, `--report-ide`, `--report-build`, `--report-layout`, `--report-zkjars` | no | Cosmetic. They populate an environment block on the generated error page. Omit them all and the block is simply left out — the supported standalone case. |

**stdout handshake.** Once the server is bound, and only then, the process prints exactly:

```
PREVIEW_PORT=<n>
```

then blocks until killed. Wait for that line before issuing a request; parse the port from it
rather than assuming the one you asked for (`--port 0` picks its own). It responds to `SIGTERM`
via a shutdown hook.

**Endpoints:**

| Request | Response |
|---|---|
| `GET /<path>.zul` | `200 text/html` — the rendered first paint. On failure, `500 text/html` carrying a formatted error page (phase, message, `file:line`, collapsible stack trace). |
| `GET /zkau/web/*` | ZK's own JS/CSS, extendlet-processed. |
| `POST /zkau` | A benign stub. The first paint never issues an AU round-trip. |
| anything else | `404 text/plain` — including a non-`.zul` path. |

**Deliberately not supported**, so please don't build on them: stdin, `--help`, and any exit-code
contract beyond "non-zero if startup fails".

## Choosing `--webapp`

The docroot matters twice: the requested URL path is the `.zul` **relativized against it**, and the
server refuses to serve anything resolving outside it. A `../`-style path cannot work.

The IntelliJ plugin picks it with these rules (`DocrootResolver`), in order — reproduce whichever
fits your project, or just pass the `.zul`'s own directory for a self-contained file:

1. **WAR layout** — the nearest ancestor of the `.zul` that contains `WEB-INF/` or is named `webapp`.
2. **Spring Boot classpath layout** — a directory named `web` sitting directly under a resource root
   (`src/main/resources/web`).
3. The nearest content root.
4. The `.zul` file's own parent directory.

## Assembling `--classpath`

Three entry kinds, in this order — the order is part of the contract:

1. **Every jar on the project's runtime classpath, not just the ZK-named ones.** Narrowing to
   `zk-*` was a shipped crash: `WebManager.<clinit>` needs `org.slf4j.LoggerFactory`. Jars go
   first so ZK's own bundled `web/` resources win any name collision.
2. **The compiled-output roots** (`target/classes`, `build/classes/java/main`, …) of the previewed
   module and the modules it depends on, so a page's own `<zscript>`, `use="…"` or custom EL
   function can resolve the project's classes. Take these from a *production-only* enumeration —
   `target/test-classes` must not reach the render. Isolation from ViewModels and Composers comes
   from the launcher's `UiFactory` hook, which never resolves their class name, **not** from
   keeping these roots off the classpath.
3. **Resource roots** such as `src/main/resources`, which is what makes ZK's `~./`
   `ClassWebResource` pages resolve. Last, mirroring a real container where `WEB-INF/classes` *is*
   the compiled output with the resources already copied into it.

The plugin's own assembly of exactly this is `ZulPreviewServerService.launcherClasspath`.

A quick way to produce one for a Maven project:

```bash
mvn -f pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -q
java -jar zk-preview-launcher-1.0.0.jar \
     --classpath "$(cat /tmp/cp.txt)" --webapp src/main/webapp --port 0
```

## Servlet variants

javax vs jakarta is auto-detected by byte-scanning `DHtmlLayoutServlet.class` in the supplied jars
(`VariantDetector`), preferring the canonical `zk-<version>.jar`. If no ZK core jar is present the
process fails loudly on startup and never prints a port — check the stderr tail.

## What renders, and what does not

The launcher produces the **first paint, with no ViewModel and no Composer**. That means bound
values appear as dimmed placeholder text, model-bound `<grid>`/`<listbox>`/`<tree>` show placeholder
rows, and nothing needing a server round-trip (clicks, paging, sorting) happens.

One binding is not placeholdered: a bound `src` (`<include>`, `<image>`, …) is a URI ZK *loads*
rather than shows, so a constant literal is included for real and anything else leaves `src` unset —
an included section can therefore be missing entirely rather than dimmed.

The project's own bytecode *does* execute where the page itself names it — `<zscript>`, custom
components, EL functions, anything in `metainfo/zk/config.xml` — provided you put the compiled-output
roots on `--classpath`. Client-side `w:` handlers also run.

The full list is L-1…L-15 in [../doc/zul_preview_spec.md](../doc/zul_preview_spec.md) §4 — read it
before reporting a rendering difference as a bug. L-15 is the one that bites a long-lived caller:
classes are read once per helper JVM, so a rebuild is invisible until the process restarts.

## Building

```bash
./gradlew :zk-preview-launcher:jar             # build/libs/zk-preview-launcher.jar (what the plugin bundles)
./gradlew :zk-preview-launcher:test            # spawns mvn subprocesses and Playwright browsers
./gradlew :zk-preview-launcher:releaseLauncher # build/release/zk-preview-launcher-<version>.jar + .sha256
```

The `jar` task's output name is fixed (`zk-preview-launcher.jar`) because the plugin looks it up by
name; release assets are versioned *copies* under `build/release/`.

## Releasing

Versioned independently of the IntelliJ plugin, because external consumers pin an exact version and
the plugin's version moves for reasons that leave these bytes identical.

- **major** — a breaking change to the CLI or the stdout handshake
- **minor** — new flags, or newly supported ZK/servlet combinations
- **patch** — render fixes

Tag `launcher-v<version>` (matching `version` in `build.gradle`) and push. The
[release-launcher workflow](../.github/workflows/release-launcher.yml) builds and publishes both
assets using the repository's own token.

```bash
./gradlew clean :zk-preview-launcher:releaseLauncher   # verify locally first
git tag -a launcher-v1.0.0 -m "zk-preview-launcher 1.0.0"
git push origin launcher-v1.0.0
```

The jar is built reproducibly (`preserveFileTimestamps = false`, `reproducibleFileOrder = true`), so
rebuilding a tag yields the same SHA-256 that was published.

**Manual fallback**, if Actions is unavailable. Note the failure mode that stalled earlier releases:
`gh` may be authenticated as an account without push rights on `zkoss/zkidea`, and its error message
misleadingly blames a missing `workflow` scope. Verify the account first — `git push` succeeds
regardless, because the remote is SSH and bypasses the `gh` token entirely.

```bash
gh auth switch --hostname github.com --user <org-account>
gh api repos/zkoss/zkidea --jq .permissions          # expect "push": true
cd zk-preview-launcher/build/release
gh release create launcher-v1.0.0 --repo zkoss/zkidea --latest=false \
  --title "zk-preview-launcher 1.0.0" --notes "..." \
  zk-preview-launcher-1.0.0.jar zk-preview-launcher-1.0.0.jar.sha256
```

## Verifying a downloaded jar

```bash
shasum -a 256 -c zk-preview-launcher-<version>.jar.sha256
```

## License

Apache-2.0, per the repository [LICENSE](../LICENSE). The runnable jar embeds the
`jakarta.servlet-api` and `javax.servlet-api` classes (both Apache-2.0 / EPL-2.0+GPL-2.0-CPE).

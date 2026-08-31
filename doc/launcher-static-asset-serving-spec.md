# Requirements: serve static files from the webapp docroot

**Target component:** `zk-preview-launcher` (the ZK preview render helper).
**Status:** **implemented and settled, 2026-08-31**, for
[zkoss/zkidea#70](https://github.com/zkoss/zkidea/issues/70). R1–R9, S1, S2, S4, S5 and S6 are
covered by `zk-preview-launcher`'s `StaticAssetServingTest` (61 cases) plus the manual-test
fixtures and `manual-test/tools/static-asset-probe.sh`. **S3 was withdrawn** — symlink escape is a
documented non-goal, see its section. No requirement here is open.
**Filing:** moved from `tasks/` to `doc/` on 2026-08-31, once the implementation landed and the
last open requirement (S3) was resolved — it is a reference now, not a work item.
**Security test placement:** the §6 / §8 security rows are verified by `zk-preview-launcher`'s own
automated tests, which build throwaway docroots — not by committed fixtures in `manual-test`
(decided 2026-08-31).
**Written:** 2026-08-31. All measurements in this document were taken on that date against the
published launcher 1.0.2 and are reproducible with the commands given inline.

This document is self-contained. Everything needed to reproduce the defect, judge the design and
verify the fix is written out here; nothing is deferred to another document.

---

## 1. Summary

The preview launcher is handed a webapp docroot and serves `.zul` pages out of it, but it serves
**no other file from that directory** — not images, not stylesheets, not scripts. A page containing
`<image src="/img/logo.png"/>` renders with an empty box even when `img/logo.png` exists at exactly
that path inside the docroot the launcher was given.

The request is to add a static file handler for the docroot, confined to it, with the security
properties spelled out in §6.

## 2. What the launcher is, and how it is invoked

The launcher is a self-contained jar that boots an embedded HTTP server, mounts a ZK runtime built
from a caller-supplied classpath, and renders `.zul` pages so a headless browser can screenshot
them. It is consumed by a command-line tool that spawns it as a child process, drives a browser at
the port it reports, and shuts it down.

Invocation, verbatim, as the consuming tool produces it:

```
<java17+> -jar zk-preview-launcher.jar \
  --classpath <one os-pathsep-joined list of jars, output roots and resource roots> \
  --webapp    <absolute path to the docroot> \
  --port      0 \
  [--isolation off --controller-timeout <seconds>]
```

* `--port 0` asks for an ephemeral port. The launcher prints `PREVIEW_PORT=<n>` on stdout, and the
  caller parses that line to learn where to connect.
* `--isolation off` is appended only when the caller wants Composers and ViewModels to execute;
  it is absent by default.
* The jar's main class is `org.zkoss.zkpreview.Main`. Its HTTP server is
  `org.zkoss.zkpreview.PreviewHttpServer` — a hand-written server, not Jetty or Tomcat, which is
  why there is no `DefaultServlet` to inherit static file behaviour from.

Reference build used for every measurement below:

| Item | Value |
|---|---|
| Launcher | `zk-preview-launcher-1.0.2.jar` |
| SHA-256 | `d451589f8d0e447599a96240fb17cef5b39e1575596bdc71a5bd9ad7b0d3fb7e` |
| Published at | `https://github.com/zkoss/zkidea/releases/download/v1.0.2/zk-preview-launcher-1.0.2.jar` |
| Main class bytecode | class file version 61 (requires Java 17 or newer) |
| JDK used | Azul Zulu 24 |
| ZK on the classpath | 10.3.0.1-Eval (zkmax, zkex, zkbind, zul, zk, zhtml, zuti, plus zkcharts 12.2.0.0-Eval) |
| Docroot used | a Maven `src/main/webapp` directory |

## 3. Current behaviour, measured

Reproduce by starting the launcher on a fixed port against any webapp docroot:

```bash
java -jar zk-preview-launcher.jar \
     --classpath "<cp>" --webapp "<docroot>" --port 18899
```

then placing these files in the docroot under `spec-probe/` — `p.zul` (a two-line ZK page),
`p.zhtml`, `a.css`, `a.js`, `a.png`, `a.json`, `a.txt` — and requesting each with
`curl -s -o /dev/null -w "%{http_code} %{content_type} %{size_download}\n"`.

| Request | Status | Content-Type | Body bytes |
|---|---|---|---|
| `/spec-probe/p.zul` (file exists) | **200** | `text/html;charset=UTF-8` | 1287 |
| `/nope.zul` (file does **not** exist) | **200** | `text/html;charset=UTF-8` | **0** |
| `/spec-probe/p.zhtml` | 404 | `text/plain;charset=UTF-8` | 0 |
| `/spec-probe/a.css` | **404** | `text/plain;charset=UTF-8` | 0 |
| `/spec-probe/a.js` | **404** | `text/plain;charset=UTF-8` | 0 |
| `/spec-probe/a.png` | **404** | `text/plain;charset=UTF-8` | 0 |
| `/spec-probe/a.json` | **404** | `text/plain;charset=UTF-8` | 0 |
| `/spec-probe/a.txt` | **404** | `text/plain;charset=UTF-8` | 0 |
| `/spec-probe/` (a directory) | 404 | `text/plain;charset=UTF-8` | 0 |
| `/` | 404 | `text/plain;charset=UTF-8` | 0 |
| `/zkau/web/zul/less/font/ZK85Icons.woff` | **200** | `font/woff` | 10648 |
| `/zkau/web/js/zk/zk.wpd` | 404 | `text/plain;charset=UTF-8` | 0 |

Two things this table establishes:

1. **Only `.zul` and ZK classpath resources under `/zkau/web/` are served.** The five static files
   sit inside the very directory passed as `--webapp`, at exactly the requested paths, and all five
   return 404. The failure is not path resolution — it is that no handler exists.
2. **Classpath resource serving already works correctly, including MIME typing.** The woff file is
   returned with `font/woff` and the correct byte count, which is a useful precedent for §5.

Response headers on a served `.zul`, for reference:

```
HTTP/1.1 200 OK
X-zk-preview-controllers: skipped
Pragma: no-cache
Date: Mon, 31 Aug 2026 08:12:24 GMT
Content-type: text/html;charset=UTF-8
Content-length: 1287
Cache-control: no-store, no-cache, must-revalidate
```

(The launcher also sets `X-zk-preview-controller-failure` when a Composer or ViewModel threw.)

Path traversal, measured against the current build with `curl --path-as-is`:

| Request | Status |
|---|---|
| `/../../../../etc/passwd` | 404 |
| `/spec-probe/../../../../etc/passwd` | 404 |
| `/%2e%2e/%2e%2e/etc/passwd` | 404 |

These are all safe today only because nothing reads the filesystem for these paths. **Adding a
static handler removes that accidental safety, which is why §6 is a requirement and not advice.**

## 4. Why this matters

The consuming tool renders a page to a PNG and asks an automated reviewer to compare that image
against the design it was built from. Because no docroot asset is ever served, every image on every
page is blank in that PNG. The reviewer therefore cannot use "an image did not draw" as a signal at
all — a real broken path and a correct one look identical.

The tool's own guidance had to compensate with a blanket instruction to ignore missing assets. In a
six-run evaluation of that guidance, that instruction was quoted to close a genuine, one-word markup
bug as unfixable, and one page shipped with every icon on it rendered as an empty box. The blanket
instruction is the direct consequence of this gap: with static serving in place, a blank asset
becomes a real signal and the instruction can be deleted.

Secondary effects, all from the same cause:

* A page's own stylesheet (`<link href="/css/app.css">`) never loads, so the screenshot shows
  unstyled or half-styled output that does not represent the page.
* A page's own script never loads.
* A mistyped asset path is indistinguishable from a correct one.

## 5. Functional requirements

**R1 — Serve regular files from the docroot.** A `GET` for a path that resolves to a regular file
inside the docroot returns `200` with that file's exact bytes and a correct `Content-Length`.

**R2 — Correct `Content-Type`, by extension.** At minimum:

| Extension | Content-Type |
|---|---|
| `.css` | `text/css` |
| `.js`, `.mjs` | `text/javascript` |
| `.json` | `application/json` |
| `.png` | `image/png` |
| `.jpg`, `.jpeg` | `image/jpeg` |
| `.gif` | `image/gif` |
| `.svg` | `image/svg+xml` |
| `.webp` | `image/webp` |
| `.ico` | `image/vnd.microsoft.icon` |
| `.woff` | `font/woff` |
| `.woff2` | `font/woff2` |
| `.ttf` | `font/ttf` |
| `.eot` | `application/vnd.ms-fontobject` |
| `.txt` | `text/plain` |
| `.html`, `.htm` | `text/html` |
| `.map` | `application/json` |
| anything else | `application/octet-stream` |

Text types must carry `;charset=UTF-8`. Binary types must not.

The handler must own this table itself and must **not** delegate to
`ServletContext.getMimeType`: **R2-MAJ2** in `doc/zul_preview_spec.md` records that
`MockServletContextCore.getMimeType` always returns `null`.

**R3 — Handler precedence, existing handlers unchanged.** Resolve in this order, first match wins:
1. the existing `.zul` page handler,
2. the existing `/zkau/**` classpath resource handler,
3. the new static file handler.

The static handler must never see a request the first two would have answered. In particular a
`.zul` file must continue to be rendered as a page, never returned as source text.

**R4 — `HEAD` behaves as `GET` without a body**, returning the same status and `Content-Length`.
Methods other than `GET` and `HEAD` return `405` with an `Allow: GET, HEAD` header.

**R5 — A genuinely missing file returns `404`** with an empty body, matching the current shape.

**R6 — Directories are not served and not listed.** A request resolving to a directory returns
`404`. Do not fall back to `index.html` or `index.zul`, and never emit a directory listing — the
listing would disclose the contents of a developer's working tree to anything that can reach the
port.

**R7 — Never cache.** Static responses carry the same no-cache headers the `.zul` handler already
sends: `Cache-control: no-store, no-cache, must-revalidate` and `Pragma: no-cache`. The caller
re-renders the same URL repeatedly while editing files, and a cached asset would silently show a
previous version. Do not implement `ETag` or `If-Modified-Since` handling; a `304` would produce the
same stale-image failure.

**R8 — Stream large files.** Do not read a whole file into memory before writing it. Assets of tens
of megabytes are normal in a webapp and the launcher runs with default heap.

**R9 — Concurrency.** Asset requests arrive in parallel with, and during, the page request that
triggered them. Serving them must not block or deadlock the page handler.

## 6. Security requirements

The launcher binds to a local port and serves whatever it is pointed at. Today it reads no file for
an arbitrary path, so traversal is impossible by construction; a static handler ends that, and these
requirements replace it.

**S1 — Confinement.** Canonicalise the resolved path (resolving `.`, `..` and symbolic links) and
serve it only if the canonical path is inside the canonical docroot. Compare on path components, not
on string prefixes — a docroot of `/home/u/app` must not admit `/home/u/app-secrets/x`.

**S2 — Decode before validating.** Percent-decode the request path first, then validate, so
`%2e%2e%2f` is rejected on the same code path as `../`. Reject over-long or malformed encodings
rather than repairing them. Reject any path containing a NUL byte. On Windows, reject backslash as
a separator rather than normalising it.

**S3 — Symlink escape: NOT SUPPORTED.** *Withdrawn as a requirement, 2026-08-31.* A symlink
inside the docroot that points outside it **is served**. The containment check in S1 is lexical
(`normalize()` resolves `.` and `..` but not symbolic links), and it is left that way deliberately.

This is a **documented limitation, not an open item.** Three reasons, in the order they weighed:

1. **The strict fix would break real previews.** This is already on record as **R2-MIN7** in
   `doc/zul_preview_spec.md`: "the preview docroot is the dev source tree, where symlinked asset
   folders legitimately occur, so a strict `toRealPath()` check must be scoped to the project's
   content roots, not the docroot, or it will 404 real assets." A docroot-bounded `toRealPath()`
   would trade a theoretical read for pages that visibly stop rendering.
2. **The threat model does not justify it.** Also from R2-MIN7: the only scenario is previewing an
   untrusted project, and previewing one already grants arbitrary code execution in the launcher
   JVM through `<zscript>`. Reading one file through a symlink is a strict downgrade from that.
   Closing it would change nothing an attacker can do.
3. **The correct fix is disproportionate.** Bounding a real-path check by the project's content
   roots — the only version that does not break legitimate previews — requires the plugin to
   discover and pass those roots through a new CLI argument, plus defined behaviour for the
   standalone CLI case where none are supplied. That is more work than all of S1–S6 combined, for
   no change in the attack surface per point 2.

**What this means in practice.** The launcher is a developer tool bound to `127.0.0.1` that is
pointed at the developer's own source tree. It is not a server for untrusted content and must not
be used as one. Everything an escaping symlink could reach is already reachable by the person
running it.

**If this is ever revisited**, the shape is fixed by point 1: resolve real paths, bound the check
by the **content roots**, never by the docroot. Do not implement the docroot-bounded version.

**S4 — `WEB-INF` and `META-INF` are never served.** A request whose resolved path has a
`WEB-INF` or `META-INF` component returns `404`, case-insensitively, at any depth. These directories
hold `web.xml`, `zk.xml`, and in a built webapp the application's own classes and jars.

**S5 — No dotfiles.** Any path component beginning with `.` returns `404`. This keeps `.git/`,
`.env` and editor state out of reach.

**S6 — Bind to loopback only.** Confirm the listening socket is bound to `127.0.0.1` and not to
`0.0.0.0`. With static serving added, a wildcard bind would expose a developer's working tree to the
local network. If the current bind is already loopback, this is a regression test, not a change.

## 7. Out of scope

* Range requests (`Accept-Ranges`, `206`). The consumer screenshots a first paint; partial content
  is never requested.
* Compression (`Content-Encoding`). Everything is local.
* Conditional requests and caching — explicitly excluded by R7.
* Serving `.zhtml` as a rendered page. It currently returns `404` and this document does not ask for
  that to change; note only that `.zhtml` must not accidentally start being served as *source text*
  by the new static handler. Decide deliberately: either keep it `404`, or render it — but not
  "return the raw file".
* Directory indexes, by R6.
* Refusing a symlink that escapes the docroot, by S3 as withdrawn.

## 8. Acceptance criteria

Set up: a docroot containing `assets/logo.png` (a valid PNG), `assets/app.css`, `assets/app.js`,
`page.zul`, `WEB-INF/web.xml`, `.hidden/secret.txt`, and a symlink `assets/escape` pointing at a
file outside the docroot.

| # | Request | Expected |
|---|---|---|
| A1 | `GET /assets/logo.png` | `200`, `image/png`, bytes identical to the file, correct `Content-Length` |
| A2 | `GET /assets/app.css` | `200`, `text/css;charset=UTF-8` |
| A3 | `GET /assets/app.js` | `200`, `text/javascript;charset=UTF-8` |
| A4 | `GET /page.zul` | `200`, `text/html;charset=UTF-8`, rendered page — **not** file source |
| A5 | `GET /assets/missing.png` | `404`, empty body |
| A6 | `GET /assets/` | `404`, and no listing anywhere in the body |
| A7 | `HEAD /assets/logo.png` | `200`, correct `Content-Length`, empty body |
| A8 | `POST /assets/logo.png` | `405` with `Allow: GET, HEAD` |
| A9 | `GET /assets/logo.png` twice | both `200`, both carry `Cache-control: no-store, no-cache, must-revalidate`; never `304` |
| A10 | `GET /../../../../etc/passwd` (`--path-as-is`) | `404` |
| A11 | `GET /assets/../../../../etc/passwd` (`--path-as-is`) | `404` |
| A12 | `GET /%2e%2e/%2e%2e/etc/passwd` | `404` |
| A13 | `GET /WEB-INF/web.xml` | `404` |
| A14 | `GET /web-inf/web.xml` | `404` (case-insensitive) |
| A15 | `GET /.hidden/secret.txt` | `404` |
| A16 | `GET /assets/escape` (symlink out of the docroot) | *not asserted* — S3 is withdrawn; such a symlink is served, by decision |
| A17 | `GET /zkau/web/zul/less/font/ZK85Icons.woff` | `200`, `font/woff`, 10648 bytes — unchanged |
| A18 | listening socket | bound to `127.0.0.1`, not `0.0.0.0` |
| A19 | a 50 MB asset | served correctly with no `OutOfMemoryError` under the default heap |
| A20 | end to end | a page with `<image src="/assets/logo.png"/>` screenshots with the image visible |

## 9. Non-regression

Every row of the §3 measured table must hold afterwards except the five static 404s that this change
converts to 200s. Specifically unchanged: the `.zul` 200 and its body, the `X-zk-preview-controllers`
and `X-zk-preview-controller-failure` headers, the no-cache headers, `PREVIEW_PORT=<n>` on stdout,
the `--classpath` / `--webapp` / `--port` / `--isolation` / `--controller-timeout` argument surface,
and the `/zkau/web/**` behaviour including MIME typing.

## 10. Adjacent observation, not part of this request

`GET /nope.zul` for a `.zul` that does not exist returns **`200` with a zero-byte body**, rather than
`404`. A caller cannot distinguish "page missing" from "page rendered to nothing", and a browser
pointed at it sees a blank document with a success status. This is a separate defect in the page
handler; it is recorded here because it was found during the same measurement, and it should be
filed and fixed on its own rather than folded into this change.

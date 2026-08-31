# Layout Preview — static docroot assets

Three fixtures that answer one question: **does the preview serve a file that sits in the
webapp docroot?** Images, stylesheets and scripts referenced by absolute path (`/preview/...`)
from a page in this project.

Answer: **yes, since the docroot file route was added** ([#70](https://github.com/zkoss/zkidea/issues/70)).
Before it, every docroot file returned `404` — images blank, the page's own stylesheet and script
silently never applied. These fixtures now serve as the regression guard for that route; the
requirements they were written against are in
[`doc/launcher-static-asset-serving-spec.md`](../../../../../../doc/launcher-static-asset-serving-spec.md).

Each fixture pairs the docroot asset with a **control** that is known to work, so a blank
result can only mean one thing. The control in case 1 is a ZK classpath image
(`~./zk/img/zkpowered.png`), which the launcher serves through its `/zkau/web/**` handler.

## The assets

`assets/` holds five real files, all reachable at the exact absolute paths the fixtures use:

| File | What it does when served |
|---|---|
| `docroot-logo.png` | 160×80 magenta block with a black border |
| `docroot-icon.svg` | 80×80 blue square, yellow circle, "SVG" |
| `docroot.css` | defines `.docroot-css-loaded` → white-on-green pill |
| `docroot.js` | sets `window.__docrootJsRan`, then rewrites `#docroot-js-probe` on `zk.afterMount` |
| `docroot.txt` | plain-text probe, used by the script only |

## The cases

| # | File | Mechanism | PASS looks like | A regression looks like |
|---|------|-----------|-----------------|-------------------------|
| 1 | `image-assets.zul` | `<image src="/…png">`, `<image src="/…svg">`, native `<n:img src="/…png">`, plus classpath control | all four images draw | rows 1–3 empty boxes, only row 4 (the control) draws |
| 2 | `stylesheet.zul` | `<?link rel="stylesheet" href="/…css"?>` | first label is a green pill | plain unstyled text, **with no error shown** |
| 3 | `script.zul` | `<?script src="/…js"?>` | probe reads "docroot.js RAN …" in green | probe text unchanged |

Case 1 is deliberately three-way. The ZK `<image>` component and a raw HTML `<img>` take
completely different render paths, and both were confirmed to emit the correct URL into the
page — so the failure is on the server side, not in the markup or the component:

```
['zul.wgt.Image','dZnD4',{src:'/preview/static/assets/docroot-logo.png'},…]
<img src="/preview/static/assets/docroot-logo.png" width="160" height="80"/>
['zul.wgt.Image','dZnDd',{src:'/zkau/web/b18a4621/zk/img/zkpowered.png'},…]
```

Both docroot rows were emitted with the right URL even while the server was returning `404` for
them — which is why "the image is blank" was never evidence about the markup, and why the control
row matters.

## How to check

**In the IDE (the point of these fixtures).** `./gradlew runIde` from the repo root, open this
`manual-test/` folder in the sandbox IDE, open each of the three files and read the Layout
Preview pane against the "PASS looks like" column.

**HTTP level, no IDE, no eyeballs.** [`tools/static-asset-probe.sh`](../../../../../tools/static-asset-probe.sh)
boots the launcher against this docroot and requests all eight URLs:

```bash
cd manual-test
./tools/static-asset-probe.sh                    # or pass a jar path
```

It exits zero when all nine are served correctly:

```
PASS /preview/static/image-assets.zul          got 200  text/html;charset=UTF-8   2687 bytes
PASS /zkau/web/zk/img/zkpowered.png            got 200  image/png                 1841 bytes
PASS /preview/static/assets/docroot-logo.png   got 200  image/png                  205 bytes
PASS /preview/static/assets/docroot.css        got 200  text/css;charset=UTF-8     388 bytes
…
ALL PASS: the launcher serves docroot static files.
```

**Under a real server, for comparison.** `mvn jetty:run` here, then browse
`http://localhost:8080/plugin-test/preview/static/image-assets.zul`. Jetty's `DefaultServlet`
serves the docroot, so all three cases PASS there too. Keeping both paths green is the point: the
preview should agree with the real container, and this is the cheapest way to check that it does.

## Not covered here

The security rows (traversal, `WEB-INF`/`META-INF`, dotfiles, malformed encodings, the loopback
bind) are **not** fixtures in this folder. They need throwaway docroots containing things that do
not belong in a committed webapp opened in an IDE — a `.hidden/` directory, a symlink pointing
outside the repo. They live in `zk-preview-launcher`'s own
`StaticAssetServingTest`, which builds temporary docroots per case. This folder covers only what a
human can see in a preview pane.

One thing is deliberately **not** covered by either, and is a documented non-goal rather than a
gap: a symlink inside the docroot pointing outside it is served. The containment check is lexical
on purpose. Refusing such a link with a docroot-bounded real-path check would 404 legitimate assets
(a preview docroot is a live source tree, where symlinked asset folders are normal), and it would
close nothing — previewing an untrusted project already grants code execution in the launcher JVM
through `<zscript>`. The launcher is a developer tool on loopback pointed at your own source tree,
not a server for untrusted content.

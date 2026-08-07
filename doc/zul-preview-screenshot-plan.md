# Layout Preview — marketplace screenshot plan

Assets for the [plugin page](https://plugins.jetbrains.com/plugin/7855-zk) carousel, which is
also what renders inside IDEA under *Settings ▸ Plugins*.

---

## The one shot that matters (hero)

**A single side-by-side split of the `.zul` editor: your source on the left, the real ZK render
on the right, with a visually identical anchor in both panes.**

The whole job of this image is to make one claim land in under two seconds: *the IDE draws my
ZUL next to my code.* Everything else is subordinate to that.

### Composition

| Element | Decision | Why |
|---|---|---|
| **Subject** | `preview/showcase.zul` (written for this shot — ~38 lines, fits the pane with no scrollbar) | A screenshot with a half-visible file reads as cropped, not as a feature. |
| **Left pane** | Full ZUL source, editor font bumped to **15–16pt** | Marketplace scales the carousel image down. 12pt code is illegible at thumbnail size — this is the single most common mistake in plugin screenshots. |
| **Right pane** | The rendered page: `<window>` with a real ZK title bar, a form grid, a listbox with headers, two buttons | The window chrome and grid header are what make it read as *a ZK page* rather than generic HTML. |
| **Anchor** | The **Submit / Cancel** buttons, visible in both panes | Gives the eye a left↔right correspondence to latch onto. Without an anchor the two panes look like unrelated screenshots pasted together. |
| **Split buttons** | Keep the three editor/split/preview toggles in the editor's top-right | The affordance IntelliJ users already recognize — it says "this is a built-in editor split", not a separate tool window. |
| **Theme** | **Dark IDE + light rendered page** (ZK's default theme) | The contrast draws the boundary between "editor" and "rendered page" for free. Don't fight it. |
| **Project tree** | **Collapsed** (⌘1) | Eats ~20% of the width and adds nothing — the audience already knows it's IntelliJ. |
| **Crop** | The IDE window only, starting at the editor tab bar | No desktop, no menu bar, no dock. |

### Deliberately in the frame

**Two or three MVVM placeholders** — the dimmed `vm.customer` in the textbox and the
`each.product` / `each.qty` / `each.price` cells in the listbox rows.

This is a marketing decision, not just an honest one. The product positioning doc names
expectation mismatch as the feature's top risk (§2: a user opens a data-bound page, sees blanks,
and leaves a two-star review). Showing the placeholders *in the hero image* sets the mental
model before install: the preview reads as a **wireframe labelled with your field names**, which
is exactly what it is. A page rendered with fake real-looking data would sell better and then
disappoint on first run.

### Deliberately out of the frame

- The **first-run "binding values are placeholders" banner** — a banner in a hero image reads as
  a warning. Dismiss it with **Got it** before shooting, and let the caption carry that message.
- Notification balloons, the Git branch widget, absolute paths, other editor tabs.

### The caption problem

The strongest part of the pitch — *no server, no deploy* — is an **absence**, and absences don't
photograph. So bake a one-line caption strip into the bottom of the PNG itself:

> **Layout Preview** — rendered by your project's own ZK jars. No server, no deploy.

Do not rely on surrounding page text for this: the carousel is often the only thing a visitor
looks at. (Worth confirming whether the Marketplace Media tab supports per-image captions before
committing to a baked-in strip.)

---

## Supporting images (optional, in priority order)

2. **The formatted error page.** Break the ZUL (a bad tag or a missing `<zscript>` class) and
   shoot the error pane: failure phase, message, `file:line`, collapsible stack trace, *Report on
   GitHub*. Nothing else in the carousel says "this tool is trustworthy when things go wrong" —
   and a preview is used precisely while the file is half-broken.
3. **The right-click context menu** — *View Rendered HTML* / *Open DevTools*. Already captured as
   [preview-contextmenu.png](preview-contextmenu.png); reuse as-is.

## The asset that would actually outperform all of these

An **8–12 second looping GIF**: type an attribute → ⌘S → the preview updates. The feature's value
*is* the compressed feedback loop, and a still image cannot show a loop. The positioning doc
reaches the same conclusion independently (§4.2: "那支 GIF 就是整個賣點"). Verify GIF support in the
Marketplace Media tab; if unsupported, embed it in the `<description>` HTML via an absolute URL.

---

## Capture environment

| Item | Value | Note |
|---|---|---|
| IDE | `./gradlew runIde` sandbox — **IntelliJ IDEA Community 2023.3** (IC-233) | Pinned by `build.gradle` (`version = '2023.3'`). Usable, but a 2024.x/2025.x frame would look more current on the store page. |
| Project | `SUPPORT/plugin-test` — the sandbox's last-opened project | `zkmax 10.1.0-jakarta`, jars present in `~/.m2`, so the preview renders without setup. |
| Demo page | `plugin-test/src/main/webapp/preview/showcase.zul` | Written for this shot. |
| Display | 1920×1080, non-retina | Cropping to the ~1400×1000 IDE window yields a 1400×1000 PNG — adequate, not crisp. A retina display would give 2× and a noticeably better store image. |
| Tooling | `screencapture -R x,y,w,h` — verified working on this machine | Window geometry restores to `x=260 y=52 1400×1000` per the sandbox's `recentProjects.xml`. |

---

## Produced

**[zul-preview-hero.png](zul-preview-hero.png)** — 1381×946, 193 KB. The full `showcase.zul` source
(`<window>` … `</window>`, all 32 lines) beside its render, Submit/Cancel visible in both panes,
`vm.customer` and the three `each.*` cells showing as placeholders, the three editor/split/preview
toggles in the top-right, caption strip baked on. Staged with the project tree collapsed, the banner
dismissed, and the editor font raised two steps.

**[zul-preview-loop.gif](zul-preview-loop.gif)** — 900×618, 487 KB, 6.4 s, 12 fps, infinite loop.
The feedback loop: `height="120px"` → the digits are edited to `195` → ⌘S → the listbox grows to
reveal its third placeholder row and pushes the buttons down. Same caption strip. Beat structure:
0.6 s hold on the start state, the edit, then **1.9 s hold on the payoff** — the payoff hold is the
one to protect if this gets re-cut, since a loop that restarts too fast reads as a glitch rather
than a result.

Demo page: `manual-test/src/main/webapp/preview/showcase.zul` (module `plugin-test`, zk
10.1.0-jakarta). It is written to fit the pane exactly — re-check the line count if the font
or window size changes. `grid width="410px"` inside a `480px` window is deliberate: it leaves the
grid's right border visible instead of flush against the window edge.

### Notes for re-shooting

- The three split toggles are painted **only while the pointer is over the preview pane**. Park the
  mouse there before capturing. `screencapture` omits the cursor itself unless `-C` is passed.
- The GIF was spliced from one take to cut a pause mid-edit (`ffmpeg` `trim` + `concat`). The cut is
  invisible because the on-screen text is identical at both ends of it — a useful trick, since it
  means a take only needs the *start* and *end* states to be clean, not the timing in between.

## What automation can and cannot do here

With Accessibility granted, these worked reliably:

- **Window geometry + raise** via System Events, addressing the process by **`unix id`**. Necessary:
  the sandbox IDE and a normal IDEA install are *both* named `idea` to AppleScript — actually the
  sandbox appears as **`java`** — so targeting by name hits the wrong instance.
- **Menu-item clicks** (`View ▸ Tool Windows ▸ Project`, `View ▸ Increase Font Size in All Editors`).
  Far more reliable than keystrokes for staging.
- **`screencapture -R x,y,w,h`** for stills and **`-V <secs>`** for region video.

These did not:

- **Blind `keystroke` sequences are unsafe on a machine in use.** Focus moved mid-sequence during
  the GIF attempt and the keystrokes landed in another application. Nothing was corrupted, but the
  hazard is real: only send keystrokes when the machine is idle, and re-assert frontmost immediately
  before each batch.
- **Writing the `.zul` from outside the IDE does not refresh the preview** (verified: 0 changed
  pixels in the preview pane 5 s after an external write). IntelliJ only syncs the VFS on frame
  activation, and the refresh hangs off the editor save. So a GIF of the edit→save→refresh loop
  **requires real typing inside the IDE** — it cannot be faked with a disk write.
- Clicks inside the JCEF preview pane (needed for the context-menu shot) — no `cliclick` installed
  and System Events `click at` does not reach the embedded browser.

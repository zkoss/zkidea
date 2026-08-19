# Manual test — ZUL Layout Preview in a Spring Boot *jar* project

A minimal Spring Boot **jar**-packaging project used to manually verify that the ZKIdea
Layout Preview renders a ZUL that lives on the **classpath** (ZK's ClassWebResource path),
the way a real ZK-on-Spring-Boot jar app is laid out.

## Why it's different from `manual-test/`

`manual-test/` is a **WAR** with pages under `src/main/webapp/` (a `WEB-INF` docroot). A
Spring Boot **jar** has no `webapp` and no `WEB-INF` — the ZULs live under
`src/main/resources/web/` and are served from the classpath (`~./…`). That layout is what
`DocrootResolver`'s Spring-Boot-jar rule handles.

## Layout

```
src/main/resources/web/index.zul         # top-level page  -> production url /index.zul
src/main/resources/web/zul/page.zul      # nested page     -> /zul/page.zul, also ~./zul/page.zul
src/main/resources/web/include-binding.zul   # <include> with a data-bound src (issue #69)
src/main/resources/web/zul/pop-listbox.zul   # included by the above, as ~./zul/pop-listbox.zul
src/main/java/com/example/springbootjar/DemoApplication.java    # @SpringBootApplication (not used by preview)
src/main/java/com/example/springbootjar/IncludeViewModel.java   # ViewModel behind include-binding.zul
src/main/resources/application.properties                       # server.port=8081 (never 8080)
```

## What "pass" looks like

Open the sample as a Maven project in the runIde sandbox IDE, then open
`src/main/resources/web/index.zul` and look at the Layout Preview pane:

- The page renders (window + labels), and the nested `~./zul/page.zul` include renders
  inside it — proving both the docroot resolution and classpath (`~./`) resolution.
- The preview URL is the **production** one, `/index.zul` — **not**
  `/src/main/resources/web/index.zul`. (Before the P4 fix, the docroot fell back to the
  module root and the page was served under that wrong path.)
- Opening `web/zul/page.zul` directly previews it at `/zul/page.zul`; its live
  `desktop id` renders (a real ZK render, not a stub).

## Include with a bound src (issue #69)

Open `src/main/resources/web/include-binding.zul` and look at the Layout Preview pane:

- **Case 1** — `<include src="@load('~./zul/pop-listbox.zul')"/>`: the binding's expression is a
  constant path, so the include must render for real — the "INCLUDED: .../pop-listbox.zul" label
  and its listbox appear.
- **Case 2** — `<include src="@load(vm.popupSrc)"/>`: the path would come from the ViewModel, which
  never runs in the preview, so this include must contribute **nothing** — no error box, no
  literal `vm.popupSrc` text.
- The host page renders in full: both headings, both explanation labels, and the
  "host page marker" label.
- **Failure signature (the bug):** a red box reading
  `Failed to load /'~./zul/pop-listbox.zul'` / `No dispatcher available to include ...` where the
  included page should be. That means the binding's raw text (quotes included) was written into
  `src` instead of being resolved.

See [doc/zul-preview-feature.md](../doc/zul-preview-feature.md) ("Supported project layouts")
for what the Spring-Boot-jar rule guarantees, and
[doc/zul_preview_spec.md](../doc/zul_preview_spec.md) §2.3 for the docroot resolution order.

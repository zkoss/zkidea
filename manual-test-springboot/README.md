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
src/main/resources/web/index.zul        # top-level page  -> production url /index.zul
src/main/resources/web/zul/page.zul      # nested page     -> /zul/page.zul, also ~./zul/page.zul
src/main/java/com/example/springbootjar/DemoApplication.java   # @SpringBootApplication (not used by preview)
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

See [tasks/zul-preview/MANUAL-springboot-jar.md](../tasks/zul-preview/MANUAL-springboot-jar.md)
for the step-by-step checklist.

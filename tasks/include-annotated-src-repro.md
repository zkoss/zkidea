# Repro harness — issue #69 (annotation-valued `<include src>`)

Repro harness built on `master` (9dd2a69); the fix section at the bottom records the same
harness re-run against the fixed launcher.

## Setup

Spring Boot *jar* layout, mirroring the reporter's project:

```
/private/tmp/claude-501/-Users-hawk-Documents-workspace-PLUGIN-zkidea/65db8f65-6d99-4dea-8bc7-69b1c79bfe3c/scratchpad/repro/classes/web/zuls/base/host.zul        <include src="@load('~./zuls/zk/zkPopListbox.zul')"/>   <- bug
/private/tmp/claude-501/-Users-hawk-Documents-workspace-PLUGIN-zkidea/65db8f65-6d99-4dea-8bc7-69b1c79bfe3c/scratchpad/repro/classes/web/zuls/base/host-plain.zul  <include src="~./zuls/zk/zkPopListbox.zul"/>            <- contrast
/private/tmp/claude-501/-Users-hawk-Documents-workspace-PLUGIN-zkidea/65db8f65-6d99-4dea-8bc7-69b1c79bfe3c/scratchpad/repro/classes/web/zuls/zk/zkPopListbox.zul  included page (label + 3-row listbox)
```

## Commands

```bash
withjdk.sh 17 ./gradlew :zk-preview-launcher:jar
withjdk.sh 17 mvn -o -f manual-test-springboot/pom.xml dependency:build-classpath \
  -Dmdep.outputFile=$SP/repro/cp.txt                     # ZK 10.1.0-jakarta, 45 jars, offline
withjdk.sh 17 java -jar zk-preview-launcher/build/libs/zk-preview-launcher.jar \
  --classpath "$(cat $SP/repro/cp.txt):$SP/repro/classes" \
  --webapp $SP/repro/classes/web --port 0                 # prints PREVIEW_PORT=<n>
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu \
  --hide-scrollbars --user-data-dir=$SP/repro/chrome-profile --window-size=900,760 \
  --virtual-time-budget=12000 --screenshot=$SP/repro/preview-host.png \
  "http://127.0.0.1:$PORT/zuls/base/host.zul"
```

Note: run Chrome via Bash `run_in_background` — headless Chrome does not exit on its own
against a live ZK page (the client keeps a connection open), so a foreground call hangs even
though the PNG is already written.

## Result

| page | outcome | screenshot |
|---|---|---|
| `host.zul` (annotated src) | red error box: `Failed to load /zuls/base/'~./zuls/zk/zkPopListbox.zul'` — identical to the reporter's screenshot | [include-annotated-src-error.png](include-annotated-src-error.png) |
| `host-plain.zul` (plain src) | include renders: label + listbox | [include-annotated-src-plain.png](include-annotated-src-plain.png) |

Host page renders in both cases; only the include is affected. Confirms issue #69: the
`~./` form is fine, the annotation-valued `src` is the trigger.

## Fix

Root cause: `PlaceholderInjector.afterComponentAttached` wrote every binding's raw expression
text into the annotated property, `src` included — so `Include.setSrc("'~./zuls/zk/zkPopListbox.zul'")`
made ZK look for a page whose name ends in `.zul'`, which is not an instant ZUL include and
therefore routed through a `RequestDispatcher` the headless preview has none of.

`src` is now handled separately (`applyBoundUri`): a wholly quoted constant literal is unquoted
and loaded for real, any other expression leaves `src` unset (what the real binder yields for an
unresolvable value). Text placeholders on display properties are untouched.

Same harness, same page, launcher rebuilt with the fix:

| page | outcome | screenshot |
|---|---|---|
| `host.zul` (annotated src) | include renders for real: label + 3-row listbox, no error box | [include-annotated-src-fixed.png](include-annotated-src-fixed.png) |

Regression coverage (both ZK variants, jakarta + javax): `IncludeTest`
`annotationValuedSrc_constantLiteral_isIncludedForReal` (new, fixture
`include-annotation-literal.zul`) and `annotationValuedSrc_isNeutralized_hostStillRenders`
(strengthened to assert the error box's *absence* — the blind spot that let this ship).
Manual case: `manual-test-springboot/src/main/resources/web/include-binding.zul`.

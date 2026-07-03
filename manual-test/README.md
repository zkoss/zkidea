# Manual test harness for the ZKIdea plugin

A small ZK web app whose `.zul` files are **fixtures for manually verifying the plugin's
editor features in a live IntelliJ IDE** — completion, Ctrl+Click navigation, error
highlighting, etc. Each fixture maps to a BDD spec under the plugin's
[`src/test/resources/features/`](../src/test/resources/features/).

This is a standalone **Maven** project. It is intentionally *not* part of the plugin's
Gradle build — Gradle ignores it.

## How to use it

There are two distinct ways to exercise it:

1. **Editor features (the main point).** Open this `manual-test/` folder as a project in
   the IntelliJ instance that has the ZKIdea plugin installed (e.g. via `./gradlew runIde`
   from the repo root), then open the `.zul` files below and check completion / navigation /
   highlighting behave as the matching `.feature` spec describes.

2. **Render in a browser (optional sanity check).** Run the app and open the index page:

   ```bash
   cd manual-test
   mvn clean jetty:run
   # then browse http://localhost:8080/plugin-test  (see index.zul)
   ```

Requires JDK 11+ (`pom.xml` compiles to Java 11); ZK `10.1.0-jakarta` is pulled from the ZK
Maven repositories declared in `pom.xml`.

## Fixture → feature map

Authoritative list is [`src/main/webapp/index.zul`](src/main/webapp/index.zul), which links
every fixture in-app. Summary:

| Fixture (`src/main/webapp/`)  | Plugin feature (`src/test/resources/features/`) | Exercises |
|-------------------------------|--------------------------------------------------|-----------|
| `binding-property-nav.zul`    | `binding_property_navigation.feature`            | ViewModel property Ctrl+Click nav (`getXxx`/`isXxx`, nested paths) |
| `command-binding-nav.zul`     | `command-binding-navigation.feature`             | `@command`/`@global-command` nav, before/after guards |
| `command-name-completion.zul` | `command-name-completion.feature`                | command-name completion |
| `vm-property-completion.zul`  | `vm-property-completion.feature`                 | ViewModel property completion |
| `scope-var-completion.zul`    | `scope-var-completion.feature`                   | Ctrl+Space: vm id, template var, `apply` passdown |
| `template-uri-nav.zul`        | `template-uri-navigation.feature`                | `@load`/`@init` path nav, web-root resolution |
| `viewmodel-id-nav.zul`        | `viewmodel-id-navigation.dsl.feature`            | Ctrl+Click on `vm` → ViewModel class |
| `missing-vm-nav.zul`          | `viewmodel-id-navigation.dsl.feature`            | missing class → silent no-op |
| `generic-inheritance-nav.zul` | (PR #62 / #61 regression)                        | resolve/nav/complete `vm.model.name` past an inherited generic getter |
| `test.zul`, `test-attribute.zul` | `zul-code-completion.feature`                 | component/attribute tag completion |

Supporting / exploratory fixtures with no single dedicated feature: `command.zul`,
`model.zul`, and `preview/*.zul`. The remaining feature specs (`mvvm_property_navigation.feature`)
are covered by the property nav/completion fixtures above.

## Backing ViewModels

Java ViewModels the fixtures bind against live in
[`src/main/java/com/example/plugin/test/`](src/main/java/com/example/plugin/test/)
(`MyViewModel`, `UserViewModel`, `CrewVM`/`GenericVM`/`CrewModel` for the generic-inheritance
case, `Outer`/`InnerViewModel`, etc.).

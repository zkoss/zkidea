# Data Binding Navigation and Completion Overview

This document provides a technical overview of the ZK MVVM data binding features in the ZKIdea plugin, including navigation and code completion. It maps high-level features to their implementation classes and BDD test specifications.

---

## Feature overview

### Navigation Features

**MVVM Property Navigation**: Enables developers to jump directly from property references in ZUL binding expressions (like `@load(vm.name)`) to their corresponding Java getter methods in the ViewModel. This feature resolves dotted paths (e.g., `vm.user.name`) by recursively resolving return types of each segment, ensuring that navigation lands on the correct method even in deep object hierarchies. It supports all standard MVVM annotations including `@load`, `@save`, `@bind`, and `@init`.

**ViewModel ID Navigation**: Provides navigation from the ViewModel identifier at the start of a binding chain directly to the ViewModel's Java class. This allows quick access to the ViewModel implementation from any binding point in the ZUL file. The plugin handles multiple ViewModel declarations in nested components, always resolving to the nearest ancestor's defined ID.

**Command Binding Navigation**: Facilitates navigation from `@command` and `@global-command` annotation arguments to the server-side methods annotated with `@Command` or `@GlobalCommand`. It intelligently handles cases where the command name in the ZUL differs from the method name due to explicit annotation values (e.g., `@Command("myCommand")`), ensuring developers land on the actual implementation regardless of naming aliases.

**Template URI Navigation**: Allows navigation to static ZUL template file paths referenced within `@load` or `@init` expressions, such as `templateURI="@load('/WEB-INF/template.zul')"`. It resolves web-context absolute paths by identifying the project's web root (searching for `WEB-INF/web.xml`), enabling seamless transitions between ZUL files in multi-template layouts.

**ZUL Tag Navigation**: Intercepts the default IntelliJ "Go to Declaration" behavior on ZUL element tag names. Instead of navigating to the XSD schema definition (which is rarely useful for ZUL development), it redirects navigation to the matching counterpart tag—clicking an opening `<window>` jumps to the closing `</window>` and vice versa. For self-closing tags, it navigates to the tag itself, effectively acting as a no-op that prevents unwanted jumps to the schema.

### Completion Features

**ViewModel Property Completion**: Provides smart code completion for properties and methods within data binding expressions based on the current ViewModel's type. It suggests getter-backed properties (filtering out "get"/"is" prefixes), public zero-parameter methods (auto-inserting "()"), and correctly propagates types through property chains to offer context-relevant suggestions for nested segments.

**Command Name Completion**: Offers a list of valid `@Command` and `@GlobalCommand` names defined in the active ViewModel when typing inside `@command(...)` or `@global-command(...)` annotations. It prioritizes explicit command values defined in Java annotations over method names and automatically wraps the suggested name in single quotes to maintain correct ZUL syntax.

**Scope Variable Completion**: Intelligent completion at the root of a binding expression that suggests available variables from different ZK scopes. This includes the ViewModel ID, template variables declared via `<template var="...">` (defaulting to `each`), and custom pass-down attributes from enclosing `<apply>` tags, while correctly excluding internal ZK system attributes.


---

## Feature & Implementation Mapping

| Feature Area | Key Implementation Classes | BDD Feature File |
|--------------|----------------------------|------------------|
| **MVVM Property Navigation** | [ZkBindingReferenceProvider](../src/main/java/org/zkoss/zkidea/reference/ZkBindingReferenceProvider.java), [ViewModelPropertyReference](../src/main/java/org/zkoss/zkidea/reference/ViewModelPropertyReference.java) | [binding_property_navigation.feature](../src/test/resources/features/binding_property_navigation.feature) |
| **ViewModel Property Completion** | [MVVMAnnotationCompletionProvider](../src/main/java/org/zkoss/zkidea/completion/MVVMAnnotationCompletionProvider.java), [ZulDomUtil](../src/main/java/org/zkoss/zkidea/dom/ZulDomUtil.java) | [vm-property-completion.feature](../src/test/resources/features/vm-property-completion.feature) |
| **ViewModel ID Navigation** | [ZkBindingReferenceProvider](../src/main/java/org/zkoss/zkidea/reference/ZkBindingReferenceProvider.java), [ViewModelIdReference](../src/main/java/org/zkoss/zkidea/reference/ViewModelIdReference.java) | [viewmodel-id-navigation.dsl.feature](../src/test/resources/features/viewmodel-id-navigation.dsl.feature) |
| **Command Binding Navigation** | [ZkBindingReferenceProvider](../src/main/java/org/zkoss/zkidea/reference/ZkBindingReferenceProvider.java), [ZkCommandReference](../src/main/java/org/zkoss/zkidea/reference/ZkCommandReference.java) | [command-binding-navigation.feature](../src/test/resources/features/command-binding-navigation.feature) |
| **Command Name Completion** | [MVVMAnnotationCompletionProvider](../src/main/java/org/zkoss/zkidea/completion/MVVMAnnotationCompletionProvider.java) | [command-name-completion.feature](../src/test/resources/features/command-name-completion.feature) |
| **Template URI Support** | [ZkTemplateUriReferenceProvider](../src/main/java/org/zkoss/zkidea/reference/ZkTemplateUriReferenceProvider.java), `ZulWebRootResolver` | [template-uri-navigation.feature](../src/test/resources/features/template-uri-navigation.feature) |
| **Scope Variable Completion** | [ZulScopeVarCompletionContributor](../src/main/java/org/zkoss/zkidea/completion/ZulScopeVarCompletionContributor.java) | [scope-var-completion.feature](../src/test/resources/features/scope-var-completion.feature) |
| **ZUL Tag Navigation** | [ZulTagGotoHandler](../src/main/java/org/zkoss/zkidea/actions/ZulTagGotoHandler.java) | N/A |

---

## Technical Details

### Reference Resolution Flow
1. **Detection**: `ZkBindingReferenceContributor` registers providers for `XmlAttributeValue`.
2. **Parsing**: `ZkBindingReferenceProvider` uses regex to extract annotations and property chains.
3. **Context**: `ZulDomUtil` locates the nearest `viewModel` declaration to establish the root class.
4. **Resolution**: `ViewModelPropertyReference` resolves property names to Java `PsiMethod` (getters) using IntelliJ's indexing.

### Deep Type Propagation
When a multi-segment chain is encountered (e.g., `vm.user.name`), the plugin resolves the return type of each segment to provide completion for the next:
*   `vm` → `MyViewModel`
*   `user` → `User` class (via `MyViewModel.getUser()`)
*   `name` → suggestion from `User` class

#### Generic type substitution

Getters are frequently inherited from a generic base class. To resolve the chain past
such a getter, the plugin substitutes type parameters along the inheritance chain rather
than reading the raw declared return type. For example:

```
class CrewModel        { String getName(); }
abstract class GenericVM<T> { T getModel(); }   // getter declared on the generic base
class CrewVM extends GenericVM<CrewModel> { }   // binds T = CrewModel
```

For `vm.model.name`, `getModel()` declares the return type as the type variable `T`.
`ZulDomUtil.substituteReturnType(owner, ownerSubstitutor, method)` maps `T` to the type
argument bound by the concrete ViewModel (`CrewModel`) using
`TypeConversionUtil.getSuperClassSubstitutor`, and `ZulDomUtil.substitutorOf(type)` carries
the resulting substitutor to the next segment so deeper generic chains keep resolving. Both
`ZkBindingReferenceProvider` (navigation/highlighting) and the `ZulDomUtil` chain walkers
(collection element / model type inference) use this path. The substitution falls back to
the raw return type if the hierarchy cannot be analysed, so resolution never regresses.

For more details, see the original implementation notes in the respective feature files.

# Feedback Button

# Overview

The IntelliJ IDEA ZK plugin is a valuable tool that helps users to develop applications more quickly and easily. We can use this plugin to collect user feedback. This proposal outlines the benefits of adding a feedback feature to the ZK plugin and how it would be implemented.

# Difference from Website Feedback

I suppose those developers who download ZK plugin in their IDEA actually develop a ZK app. So collecting their feedback is more valuable than those visitors who just visit our website.

# Benefits

There are several benefits to adding a feedback feature to the ZK plugin. 

First, it would allow users to provide feedback, including bug reports, feature requests, and general feedback. This information would be valuable in improving the plugin or framework and making it more user-friendly.

Second, the feedback feature would make it easier for users to report bugs and request features. Currently, users have to create tickets on Freshdesk or email feedback. This feedback sends to [info@zkoss.org](mailto:info@zkoss.org).

Third, the feedback feature would show users that the developers are committed to listening to their feedback and making the plugin better. This would help to build goodwill with users and encourage them to continue using the plugin.

Encourage user interactivity.

# Existing Similar Examples

There are many existing examples of feedback forms in other products. Here are a few examples:

## Google Chrome

Google Chrome has a feedback form that users can use to report bugs, suggest features, or provide other feedback.

## Notion

A circular **?** button sits in the bottom-right corner of the window. Clicking it opens a menu
whose entries are grouped by weight rather than listed flat:

* **Help & documentation** and **Message support** — the two actions, each with an icon, at the top;
* **Keyboard shortcuts**, **What's new?**, **Join us** — plain text, no icons;
* **Twitter – @NotionHQ**, **Terms & privacy**, **Status** — muted, clearly secondary;
* the build version, the desktop version and "Updated 2 days ago" — dimmest of all, informational.

The shape worth copying is that grouping: one always-available anchor in a corner that never
competes with the content, opening a menu that puts the two things a stuck user needs at the top and
demotes everything else.

# Feedback feature

The feedback feature could be implemented by adding a **?** (question mark icon/button) at the bottom-right corner in a zul file like Notion provides.
For non-zul files, do nothing.
When clicking the icon, it shows items:
* Customer Support
* Documentation
* Feedback 
* What’s New

- Customer Support: open the default browser to visit https://potix.freshdesk.com/
- Documentation: open the default browser to visit zk Developer's Reference website: https://docs.zkoss.org/zk_dev_ref/ 
- Feedback: https://www.zkoss.org/support/about/contact
- What’s New: open the default browser to visit the News page


# UI design evaluation
The evaluation of potential UI placements for the ZK Plugin feedback feature prioritizes strict adherence to JetBrains UX conventions, which emphasize minimalism and contextuality. Placing the action on the **Main Toolbar** was rejected because this area is reserved for high-frequency, critical developer functions (like VCS and Run/Debug), and integrating a low-frequency utility here violates the principle of UI minimalism and contributes to visual clutter. The option of a **Floating Editor Button** was also rejected as it violates the principle of contextuality; floating elements within the IntelliJ editor are strictly reserved for code-related operations such as refactoring or quick fixes that are triggered by a code selection, not for global plugin support.

Consequently, the primary recommendation is to integrate the feedback links as a nested Action Group within the main IDE's **Help Menu**. This placement is the canonical standard for auxiliary features, support links, and documentation, consuming zero persistent visual screen real estate. By using a nested group, the plugin can offer multiple clear options (e.g., "Report Bug," "Suggest Feature") while ensuring high discoverability; any action registered here is automatically indexed and searchable via the IDE’s global "Search Everywhere" function, providing predictable access for the user.

# Design Consideration: Action Implementation Strategy

A common desire when creating multiple similar actions—like opening different URLs—is to write a single, reusable action class and parameterize it from `plugin.xml`. While this approach seems efficient, it is an anti-pattern in the IntelliJ Action System. The chosen implementation of creating separate, stateless action classes for each menu item is a deliberate and critical design decision based on the following platform constraints:

### 1. Action Instantiation from `plugin.xml`

When the IDE loads the plugin, it parses the `<actions>` section in `plugin.xml`. For each `<action>` tag, it instantiates the specified class using its **default, no-argument constructor**. The `plugin.xml` schema provides no mechanism to pass arguments (like a URL string) to the constructor during this process. An attempt to register a reusable `OpenUrlAction` with a `url` field would fail, as the field would never be initialized, leading to a `NullPointerException` when the action is triggered.

### 2. Memory Safety and Statelessness

A core principle of the IntelliJ Action System is that actions must be **stateless**. Action classes should not contain instance fields (non-static variables). The IntelliJ Platform may reuse action instances across different contexts and windows, and holding state can lead to unpredictable behavior and, more importantly, **memory leaks**. An action holding a reference to a URL string or other data prevents that data from being garbage collected, even if the context in which it was created is long gone.

### The Correct, Idiomatic Approach

The correct and recommended pattern is to create a separate, simple class for each action.

-   **Stateless and Safe:** Each action class is completely stateless. The URL is defined as a `private static final String`, which is a compile-time constant and not part of the object's state. This guarantees memory safety.
-   **Clear and Explicit:** The `plugin.xml` file clearly maps a specific user action to a dedicated class whose purpose is self-evident.
-   **Reliable:** This approach is guaranteed to work correctly with the platform's lifecycle and instantiation logic.

While it involves a small amount of code repetition, this pattern ensures stability, safety, and compliance with the fundamental design principles of the IntelliJ Platform.

# Known Limitation: ZK Feedback Icon Not Visible in New UI

## Status

The ZK Feedback action group icon (`/icons/feedback-menu.svg`) renders correctly in IntelliJ's **classic UI** but does **not** render in the **New UI** (default since IntelliJ 2023.1+).

## What Was Attempted

The IntelliJ Platform provides an `iconMapper` extension point (`com.intellij.iconMapper`) that allows plugins to register New UI icon variants via a JSON mapping file. The following was implemented:

1. Registered `<iconMapper mappingFile="ZKIconMappings.json"/>` in `plugin.xml`
2. Created `src/main/resources/ZKIconMappings.json` mapping the old icon path to a New UI variant:
   ```json
   {
     "icons": {
       "newui": {
         "feedback-menu.svg": "icons/feedback-menu.svg"
       }
     }
   }
   ```
3. Created `src/main/resources/icons/newui/feedback-menu.svg` — the New UI icon variant

None of those three exists in the tree today: the attempt did not work and was reverted, leaving
only the classic `src/main/resources/icons/feedback-menu.svg`. The paths above are deliberately left
as they were written — `icons/newui/feedback-menu.svg` was a *second, New-UI-specific file*, not
another way of spelling the classic icon's path, and the mapping only makes sense as that pair.

Despite these changes being structurally consistent with how IntelliJ's own bundled plugins (e.g., `SpellcheckerIconMappings.json`) use this mechanism, the icon still does not appear in the Help menu under the New UI.

## Root Cause (Suspected)

The `iconMapper` mechanism works by installing an `IconPathPatcher` (via `ExperimentalUIImpl.installIconPatcher()`) that intercepts icon loads and redirects old classpath paths to their New UI equivalents. The mapping is keyed on the **old icon path** as used at load time.

The issue likely stems from how the IntelliJ platform resolves the icon path for action groups registered in `plugin.xml`. The `icon` attribute value `"/icons/feedback-menu.svg"` (with leading slash) may be resolved differently from the classpath key `"icons/feedback-menu.svg"` (without leading slash) that `IconMapLoader` builds from the JSON. The `IconPathPatcher` may therefore never match the icon lookup for this action group.

Additionally, the `iconMapper` mechanism appears to be primarily designed for icons loaded programmatically via `IconLoader` calls in code, not for icons declared statically in `plugin.xml` `<group icon="...">` attributes. The icon resolution path for action group icons registered in `plugin.xml` may bypass the patcher entirely.

## Potential Future Fixes

1. **Programmatic icon registration**: Instead of declaring `icon="..."` on the `<group>` element in `plugin.xml`, create a custom `AnAction` subclass for the group and override `getTemplatePresentation().setIcon(...)` at runtime, using `IconLoader.getIcon()` directly — this would go through the `IconPathPatcher` and could be intercepted by the mapper.

2. **Explicit New UI icon loading**: Use `com.intellij.ui.ExperimentalUI.isNewUI()` at runtime to conditionally load either the classic or New UI icon variant, bypassing the mapping mechanism entirely:
   ```java
   Icon icon = ExperimentalUI.isNewUI()
       ? IconLoader.getIcon("/icons/newui/feedback-menu.svg", getClass())
       : IconLoader.getIcon("/icons/feedback-menu.svg", getClass());
   presentation.setIcon(icon);
   ```

3. **JetBrains issue tracker**: File a bug or consult JetBrains support to confirm whether `iconMapper` is expected to work for action group icons declared in `plugin.xml`, and whether there is a supported workaround.
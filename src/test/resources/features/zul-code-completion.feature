Feature: ZUL schema-based code completion for ZK components and attributes
  # The plugin provides schema-driven code completion in .zul files via ZkDomElementDescriptorProvider
  # and ZkDomElementDescriptorHolder, which load the zul.xsd schema and supply element/attribute
  # descriptors to IntelliJ's XML completion infrastructure.
  #
  # "No suggestion" regression: since 0.6.0 the completion shows nothing in ZUL files.
  # Root cause investigation focus: ZkDomElementDescriptorProvider, ZkDomElementDescriptorHolder,
  # and any new CompletionContributor (ZulScopeVarCompletionContributor, MVVMAnnotationCompletionProvider)
  # that may be blocking the default XML schema completion.

  Background:
    Given a .zul file is open in the editor
    And the zul.xsd schema is registered with IntelliJ's ExternalResourceManager

  # ── Tag name completion ───────────────────────────────────────────────────────

  Scenario: Typing inside an element tag triggers ZK component name suggestions
    Given the cursor is positioned after "<"
    When the user invokes code completion
    Then the completion list contains "window"
    And  the completion list contains "grid"
    And  the completion list contains "listbox"
    And  the completion list contains "button"
    And  the completion list contains "label"

  Scenario: Partial tag name narrows the completion list to matching ZK components
    Given the cursor is positioned after "<win"
    When the user invokes code completion
    Then the completion list contains "window"
    And  the completion list does not contain "grid"
    And  the completion list does not contain "button"

  Scenario: Completion works at the root level of a ZUL file (no parent tag)
    Given the ZUL file contains only "<?page?>" and the cursor is on a new line after "<"
    When the user invokes code completion
    Then the completion list is not empty
    And  the completion list contains "window"

  Scenario: Completion works for a nested element inside a <window> tag
    Given the ZUL file has a <window> tag and the cursor is inside it after "<"
    When the user invokes code completion
    Then the completion list contains "button"
    And  the completion list contains "label"
    And  the completion list contains "grid"

  # ── Attribute name completion ─────────────────────────────────────────────────

  Scenario: Completing attributes inside a <window> tag shows schema-defined attributes
    Given the cursor is inside a <window> tag after the tag name and a space
    When the user invokes code completion
    Then the completion list contains "id"
    And  the completion list contains "title"
    And  the completion list contains "width"
    And  the completion list contains "height"
    And  the completion list contains "visible"
    And  the completion list contains "sclass"
    And  the completion list contains "style"

  Scenario: Completing attributes inside a <button> tag shows button-specific attributes
    Given the cursor is inside a <button> tag after the tag name and a space
    When the user invokes code completion
    Then the completion list contains "label"
    And  the completion list contains "onClick"
    And  the completion list contains "disabled"

  Scenario: Partial attribute name narrows suggestions to matching attributes
    Given the cursor is inside a <window> tag and "wid" has been typed
    When the user invokes code completion
    Then the completion list contains "width"

  # ── Completion is not suppressed by MVVM contributors ────────────────────────
  # The MVVMAnnotationCompletionProvider and ZulScopeVarCompletionContributor
  # must NOT block or suppress the native XML schema completion for tags/attributes.

  Scenario: ZUL tag completion works in a file that has no ViewModel
    # MVVMAnnotationCompletionProvider only activates when hasViewModel() is true.
    # It must not interfere when there is no ViewModel.
    Given a ZUL file with no viewModel attribute anywhere
    And the cursor is after "<"
    When the user invokes code completion
    Then the completion list contains "window"
    And  the completion list contains "button"

  Scenario: ZUL attribute completion works in a file that has no ViewModel
    Given a ZUL file with no viewModel attribute anywhere
    And the cursor is inside a <window> tag after the tag name
    When the user invokes code completion
    Then the completion list contains "id"
    And  the completion list contains "title"

  Scenario: ZUL tag completion works in a MVVM-enabled file outside an attribute value
    # In a file with a ViewModel, completion is triggered at tag position (not attribute value).
    # MVVMAnnotationCompletionProvider only activates inside XmlAttributeValue — it must not
    # interfere with tag-name or attribute-name completion positions.
    Given a ZUL file with viewModel="@id('vm') @init('com.example.MyViewModel')"
    And the cursor is after "<" inside the <window> body
    When the user invokes code completion
    Then the completion list contains "button"
    And  the completion list contains "grid"

  Scenario: ZUL attribute completion works in a MVVM-enabled file
    Given a ZUL file with viewModel="@id('vm') @init('com.example.MyViewModel')"
    And the cursor is inside a <button> tag (attribute name position, not value)
    When the user invokes code completion
    Then the completion list contains "label"
    And  the completion list contains "onClick"

  # ── Context-sensitive child completion (parent-aware) ────────────────────────
  # Bug: typing "<" inside <listbox> shows ALL ZK components instead of only the
  # elements permitted by listboxType in zul.xsd:
  #   listitem, listhead, listgroup, listgroupfoot, frozen, auxhead
  #   (plus baseGroup items: attribute, custom-attributes, variables, template, zk)

  Scenario: Child completion inside <listbox> shows only schema-valid children
    Given the ZUL file has a <listbox> tag and the cursor is inside it after "<"
    When the user invokes code completion
    Then the completion list contains "listitem"
    And  the completion list contains "listhead"
    And  the completion list contains "listgroup"
    And  the completion list contains "listgroupfoot"
    And  the completion list contains "frozen"
    And  the completion list contains "auxhead"
    And  the completion list does not contain "window"
    And  the completion list does not contain "button"
    And  the completion list does not contain "grid"
    And  the completion list does not contain "textbox"

  Scenario: Child completion inside <listitem> shows only schema-valid children
    # listitemType allows listcell and baseGroup elements; not other top-level ZK components.
    Given the ZUL file has a <listitem> inside a <listbox> and the cursor is inside <listitem> after "<"
    When the user invokes code completion
    Then the completion list contains "listcell"
    And  the completion list does not contain "listbox"
    And  the completion list does not contain "window"
    And  the completion list does not contain "button"

  # ── Schema descriptor must resolve correctly ─────────────────────────────────

  Scenario: ZkDomElementDescriptorProvider returns a non-null descriptor for a <window> tag
    # If getDescriptor() returns null the whole completion chain falls back to IntelliJ
    # defaults and may show nothing. The provider must return a valid descriptor.
    Given a <window> XmlTag in a .zul file
    When ZkDomElementDescriptorProvider.getDescriptor() is called
    Then the result is not null
    And  the result describes the "window" element according to zul.xsd

  Scenario: ZkDomElementDescriptorProvider returns null for a tag in a plain XML file
    # The provider must be a no-op outside ZK files so it does not interfere.
    Given a <window> XmlTag in a plain .xml file (not a .zul file)
    When ZkDomElementDescriptorProvider.getDescriptor() is called
    Then the result is null

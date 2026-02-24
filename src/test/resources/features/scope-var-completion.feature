Feature: Scope variable completion in ZUL files
  # Unless stated otherwise, each scenario assumes:
  #   - a ZUL file with a ViewModel bound as: @id('vm') @init('com.example.MyViewModel')
  #   - no enclosing <template> or <apply> ancestors (unless stated otherwise)

  # ── ViewModel ID completion ──────────────────────────────────────────────────

  Scenario Outline: Completion suggests the ViewModel ID at the root argument position
    Given the binding expression "<expression>"
    When the user invokes code completion
    Then the completion list contains "vm"

    Examples:
      | expression       |
      | @load(           |
      | @bind(           |
      | @save(           |
      | @init(           |
      | @command(        |
      | @global-command( |

  Scenario: Completion still suggests the ViewModel ID when a partial prefix has already been typed
    Given the binding expression "@load(v"
    When the user invokes code completion
    Then the completion list contains "vm"

  # ── Template variable completion ─────────────────────────────────────────────
  # A <template var="name"> ancestor passes a loop variable into its body.
  # The plugin suggests that variable name at root position inside a binding annotation.

  Scenario: Completion suggests the loop variable from an enclosing <template var="..."> tag
    Given the ZUL structure has an ancestor <template var="member">
    And the binding expression "@load("
    When the user invokes code completion
    Then the completion list contains "member"

  Scenario: Completion suggests loop variables from every enclosing <template> ancestor
    # Nested templates contribute independently; both are offered.
    Given the ZUL structure has ancestors <template var="row"> inside <template var="item">
    And the binding expression "@load("
    When the user invokes code completion
    Then the completion list contains "row"
    And the completion list contains "item"

  Scenario: Completion suggests the default variable "each" when the <template> tag has no var attribute
    # In ZK Framework, a <template> without a var attribute uses "each" as the implicit loop variable.
    Given the ZUL structure has an ancestor <template> with no var attribute
    And the binding expression "@load("
    When the user invokes code completion
    Then the completion list contains "each"

  # ── Apply passdown variable completion ───────────────────────────────────────
  # An <apply> tag passes named attributes down into the included template.
  # User-defined attributes (not ZK system attributes) are offered as variable candidates.

  Scenario: Completion suggests user-defined passdown variable from an enclosing <apply> tag
    # <apply ctx="@load(vm.ctx)" templateURI="/tmpl/row.zul">
    # "ctx" is user-defined → offered; "templateURI" is a ZK system attribute → excluded
    Given the ZUL structure has an ancestor <apply ctx="@load(vm.ctx)" templateURI="/tmpl/row.zul">
    And the binding expression "@load("
    When the user invokes code completion
    Then the completion list contains "ctx"
    And the completion list does not contain "templateURI"

  Scenario: No apply variable suggestion when all <apply> attributes are ZK system attributes
    # <apply templateURI="/row.zul" if="${cond}"> — both are reserved system attributes
    Given the ZUL structure has an ancestor <apply templateURI="/row.zul" if="${cond}">
    And the binding expression "@load("
    When the user invokes code completion
    Then no scope variable suggestions appear from apply ancestors

  Scenario: All ten ZK system attributes on <apply> are excluded from suggestions
    # Reserved names: templateURI, template, if, unless, forEach, forEachBegin, forEachEnd,
    # forEachStep, forEachStatus, forEachIndex
    Given the ZUL structure has an ancestor <apply> with all ten reserved system attributes
    And the binding expression "@load("
    When the user invokes code completion
    Then none of the ten reserved system attribute names appear in the completion list

  # ── The current tag's own attributes are not suggested ───────────────────────
  # The plugin walks ancestors starting from the PARENT of the tag that owns the
  # attribute being edited. This prevents a variable from offering itself as a
  # completion candidate while its own value is being written.

  Scenario: Attributes on the current <apply> tag are not offered while editing its own value
    # The developer is typing the value of "ctx" on the current <apply> tag;
    # "ctx" must not appear in its own completion list (circular reference guard).
    Given the current tag is an <apply> with attribute "ctx" whose value is being edited
    And the binding expression "@load("
    When the user invokes code completion
    Then the completion list does not contain "ctx"

  # ── No completion after a dot (property chain position) ──────────────────────
  # Scope variable completion only triggers at root position (first argument segment).
  # Once a dot is typed, the user is selecting a property — a different contributor handles that.

  Scenario: No scope variable suggestion when the cursor is immediately after a dot
    Given the binding expression "@load(vm."
    When the user invokes code completion
    Then no scope variable suggestions appear

  Scenario: No scope variable suggestion when a property name follows the dot
    Given the binding expression "@load(vm.items"
    When the user invokes code completion
    Then no scope variable suggestions appear

  # ── No completion for class-reference annotations ────────────────────────────
  # @converter and @validator accept class names, not binding expressions,
  # so scope variable suggestions are not shown for them.

  Scenario Outline: No scope variable suggestion inside @converter and @validator
    Given the binding expression "<expression>"
    When the user invokes code completion
    Then no scope variable suggestions appear

    Examples:
      | expression  |
      | @converter( |
      | @validator( |

  # ── No completion when the ZUL has no ViewModel ──────────────────────────────

  Scenario: No scope variable suggestion when no ViewModel is bound in the ZUL file
    Given a ZUL file with no ViewModel bound
    And the binding expression "@load("
    When the user invokes code completion
    Then no scope variable suggestions appear

  # ── No completion in non-ZUL files ───────────────────────────────────────────

  Scenario: No scope variable suggestion in a plain XML file
    Given a plain XML file (not a .zul file) with the binding expression "@load("
    When the user invokes code completion
    Then no scope variable suggestions appear

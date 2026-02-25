Feature: Command name completion in ZUL @command and @global-command annotations
  # Unless stated otherwise, each scenario assumes a ZUL file connected to MyViewModel.
  #
  # Commands available in MyViewModel:
  #   saveItem    @Command
  #   save        effective name from @Command(value="save") on persistItem()
  #   validate    @Command
  #   commit      @Command
  #   hello       @Command
  #   broadcast   @GlobalCommand
  #
  # Non-annotated public methods (must NOT appear in command context):
  #   init()
  #
  # Getter-backed properties (must NOT appear in command context):
  #   list, name, active, crew, selectedItem, value
  #
  # HOW TO INVOKE: Place the cursor at the position marked "|" and press Ctrl+Space.

  # ── @command context — command name suggestions ───────────────────────────────

  Scenario: @Command method names are suggested inside @command()
    Given the binding expression "@command(|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "saveItem"
    And  the completion list contains "validate"
    And  the completion list contains "commit"
    And  the completion list contains "hello"

  Scenario: @Command with explicit value shows annotation value, not method name
    # persistItem() has @Command(value="save") → completion shows "save", not "persistItem"
    Given MyViewModel has "persistItem" annotated @Command(value="save")
    When the user invokes Ctrl+Space inside "@command(|)"
    Then the completion list contains "save"
    And  the completion list does NOT contain "persistItem"

  Scenario: @GlobalCommand method appears in @command() suggestions
    # Both @Command and @GlobalCommand names are offered in @command() context
    Given MyViewModel has "broadcast" annotated @GlobalCommand
    When the user invokes Ctrl+Space inside "@command(|)"
    Then the completion list contains "broadcast"

  Scenario: Non-annotated public method is NOT suggested inside @command()
    # Only @Command / @GlobalCommand methods appear — plain public methods are excluded
    Given MyViewModel has a public method "init()" with no @Command annotation
    When the user invokes Ctrl+Space inside "@command(|)"
    Then "init" does NOT appear in the completion list

  Scenario: Property names are NOT suggested inside @command()
    # Getter-backed properties belong to the property context, not the command context
    When the user invokes Ctrl+Space inside "@command(|)"
    Then the completion list does NOT contain "list"
    And  the completion list does NOT contain "name"
    And  the completion list does NOT contain "active"

  # ── Annotation template suppression ──────────────────────────────────────────
  # When the cursor is already inside @command(...), the annotation-template
  # completion provider (MVVMAnnotationCompletionProvider) must NOT offer top-level
  # annotation names such as @global-command() because those are not valid at that
  # position.

  Scenario: "@global-command" annotation template does NOT appear inside @command()
    # Bug: MVVMAnnotationCompletionProvider was still running inside annotation parens
    # and added "@global-command()" because "@command()" does not start with "@global-command".
    # Fix: isInsideAnnotationBody("@command(") returns true → annotation suggestions suppressed.
    Given the binding expression "@command(|)"
    When the user invokes Ctrl+Space
    Then the completion list does NOT contain "@global-command"
    And  the completion list does NOT contain "@global-command()"

  # ── @global-command context ───────────────────────────────────────────────────

  Scenario: @GlobalCommand method names are suggested inside @global-command()
    Given the binding expression "@global-command(|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "broadcast"

  # ── Insert behaviour ──────────────────────────────────────────────────────────

  Scenario: Selecting a command from @command completion inserts it wrapped in single quotes
    # ZK command syntax requires a string literal: @command('saveItem')
    # The insert handler wraps the selected name so the result is always syntactically correct.
    Given the binding expression "@command(|)"
    When the user selects "saveItem" from the completion list
    Then the editor contains "@command('saveItem')"

  Scenario: Selecting a @GlobalCommand from @command completion inserts it wrapped in single quotes
    Given the binding expression "@command(|)"
    When the user selects "broadcast" from the completion list
    Then the editor contains "@command('broadcast')"

  Scenario: Selecting a command from @global-command completion inserts it wrapped in single quotes
    Given the binding expression "@global-command(|)"
    When the user selects "broadcast" from the completion list
    Then the editor contains "@global-command('broadcast')"

  # ── Incomplete expression (no closing parenthesis) ────────────────────────────
  # When the user is still typing and the closing ')' is absent, IntelliJ injects
  # a dummy identifier at the cursor. Command completion must still work.

  Scenario: Command names are still suggested when closing ) is absent
    # Regression: @command( (no closing ')') previously showed no suggestions.
    # IntelliJ injects a dummy identifier at the cursor; completion must still fire.
    Given the binding expression "@command(|" with no closing parenthesis
    When the user invokes Ctrl+Space
    Then the completion list contains "saveItem"
    And  the completion list contains "validate"

  Scenario: Selecting a command from @command( (no closing paren) inserts with single quotes
    Given the binding expression "@command(|" with no closing parenthesis
    When the user selects "saveItem" from the completion list
    Then the editor contains "@command('saveItem')"

Feature: @command binding navigation in ZUL files
  # Unless stated otherwise, each scenario assumes a ZUL file connected to a ViewModel
  # with the following command methods:
  #   saveItem    @Command
  #   persistItem @Command(value="save")
  #   broadcast   @GlobalCommand
  #   validate    @Command
  #   commit      @Command

  # ── Simple @command ────────────────────────────────────────────────────────

  Scenario: Navigate to a command method — single-quoted string
    Given the binding expression "@command('saveItem')"
    When the user navigates on the command name "saveItem"
    Then the IDE navigates to the @Command method "saveItem"

  Scenario: Navigate to a command method — double-quoted string
    Given the binding expression '@command("saveItem")'
    When the user navigates on the command name "saveItem"
    Then the IDE navigates to the @Command method "saveItem"

  Scenario: Navigate to a method when cursor is on the @command keyword
    Given the binding expression "@command('saveItem')"
    When the user navigates on "@command"
    Then the IDE navigates to the @Command method "saveItem"

  # ── @Command with explicit annotation value ────────────────────────────────

  Scenario: Navigate resolves via the annotation value, not the method name
    # ViewModel has method "persistItem" annotated @Command(value="save")
    Given the binding expression "@command('save')"
    When the user navigates on the command name "save"
    Then the IDE navigates to the method "persistItem" whose @Command value is "save"

  # ── @global-command ────────────────────────────────────────────────────────

  Scenario: Navigate to a global command method
    Given the binding expression "@global-command('broadcast')"
    When the user navigates on the command name "broadcast"
    Then the IDE navigates to the @GlobalCommand method "broadcast"

  Scenario: Navigate to a method when cursor is on the @global-command keyword
    Given the binding expression "@global-command('broadcast')"
    When the user navigates on "@global-command"
    Then he IDE navigates to the @GlobalCommand method "broadcast"

  # ── Both @command and @global-command in one attribute ─────────────────────
  # ZK allows: onClick="@command('saveItem') @global-command('broadcast')"

  Scenario: Navigate the local command in a mixed expression
    Given the binding expression "@command('saveItem') @global-command('broadcast')"
    When the user navigates on the command name "saveItem"
    Then the IDE navigates to the @Command method "saveItem"

  Scenario: Navigate the global command in a mixed expression
    Given the binding expression "@command('saveItem') @global-command('broadcast')"
    When the user navigates on the command name "broadcast"
    Then the IDE navigates to the @GlobalCommand method "broadcast"

  # ── @command with extra key-value arguments ────────────────────────────────
  # ZK allows: @command('showIndex', index=10, keyword='myKeyword')

  Scenario: Navigate to a command when extra key-value arguments are present
    Given the binding expression "@command('saveItem', index=10, keyword='hello')"
    When the user navigates on the command name "saveItem"
    Then the IDE navigates to the @Command method "saveItem"

  Scenario: No navigation when cursor is on an argument key
    Given the binding expression "@command('saveItem', index=10)"
    When the user navigates on the argument key "index"
    Then no navigation occurs

  # ── @save / @bind with before= / after= guard commands ────────────────────

  Scenario: Navigate from a before-guard command
    Given the binding expression "@save(vm.value, before='validate')"
    When the user navigates on the before-guard command name "validate"
    Then the IDE navigates to the @Command method "validate"

  Scenario: Navigate from an after-guard command
    Given the binding expression "@save(vm.value, after='commit')"
    When the user navigates on the after-guard command name "commit"
    Then the IDE navigates to the @Command method "commit"

  Scenario: Navigate from a before-guard in a @bind expression
    Given the binding expression "@bind(vm.value, before='validate')"
    When the user navigates on the before-guard command name "validate"
    Then the IDE navigates to the @Command method "validate"

  Scenario: Navigate each guard independently when both before and after are present
    Given the binding expression "@save(vm.value, before='validate', after='commit')"
    When the user navigates on the before-guard command name "validate"
    Then the IDE navigates to the @Command method "validate"
    When the user navigates on the after-guard command name "commit"
    Then the IDE navigates to the @Command method "commit"

  Scenario: No navigation when cursor is on the "before" or "after" keyword itself
    Given the binding expression "@save(vm.value, before='validate')"
    When the user navigates on the keyword "before"
    Then no navigation occurs


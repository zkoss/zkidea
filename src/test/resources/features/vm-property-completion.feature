Feature: ViewModel property completion (Ctrl+Space) in ZUL binding annotations
  # Unless stated otherwise, each scenario assumes a ZUL file connected to MyViewModel:
  #
  #   Getter-backed properties (property context suggestions):
  #     getList()         → List<String>    "list"
  #     getName()         → String          "name"
  #     isActive()        → boolean         "active"      (boolean getter)
  #     getCrew()         → CrewModel       "crew"
  #     getSelectedItem() → CrewModel       "selectedItem"
  #     getValue()        → String          "value"
  #
  #   Non-getter public methods (also visible in property context):
  #     init()            0 params
  #     saveItem()        0 params  @Command
  #     persistItem()     0 params  @Command(value="save")
  #     broadcast()       0 params  @GlobalCommand
  #     validate()        0 params  @Command
  #     commit()          0 params  @Command
  #     hello()           0 params  @Command
  #     setValue(String)  1 param
  #
  #   Commands (command context suggestions):
  #     saveItem          @Command
  #     save              effective name from @Command(value="save") on persistItem
  #     broadcast         @GlobalCommand
  #     validate          @Command
  #     commit            @Command
  #     hello             @Command
  #
  # HOW TO INVOKE: Place the cursor at the position marked "|" and press Ctrl+Space.

  # ── Getter-backed properties (Pass 1) ───────────────────────────────────────

  Scenario: getXxx getter appears as property name without "get" prefix
    Given the binding expression "@load(vm.|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "list"         # from getList()
    And  the completion list contains "name"         # from getName()
    And  the completion list contains "crew"         # from getCrew()
    And  the completion list contains "selectedItem" # from getSelectedItem()
    And  the completion list contains "value"        # from getValue()

  Scenario: Boolean getter (isXxx) appears as property name without "is" prefix
    Given the binding expression "@load(vm.|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "active"           # from isActive()
    And  the completion list does NOT contain "isActive"

  Scenario: Property suggestion shows return type as type text
    Given the binding expression "@load(vm.|)"
    When the user invokes Ctrl+Space
    Then "name" shows type text "String"
    And  "active" shows type text "boolean"
    And  "list" shows type text "List<String>"

  Scenario: Property suggestion shows containing class name in tail text
    Given the binding expression "@load(vm.|)"
    When the user invokes Ctrl+Space
    Then each property suggestion has "(MyViewModel)" in its tail text

  Scenario: Getter with parameters is excluded from property suggestions
    # Pass 1 requires zero parameters — parameterised "getters" are not properties
    Given MyViewModel has a method "getFiltered(String query)" with 1 parameter
    When the user invokes Ctrl+Space inside "@load(vm.|)"
    Then "filtered" does NOT appear in the completion list

  Scenario: Duplicate property name from two methods appears only once
    # addedNames set prevents the same property from appearing twice
    Given MyViewModel has both "getName()" and another method also producing property "name"
    When the user invokes Ctrl+Space after "vm."
    Then "name" appears exactly once in the completion list

  # ── Non-getter public methods (Pass 2) ──────────────────────────────────────

  Scenario: Non-getter public zero-param method is suggested with method icon
    Given the binding expression "@load(vm.|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "saveItem"
    And  "saveItem" is shown with a method icon

  Scenario: Non-getter zero-param method auto-inserts "()" with caret after
    Given the binding expression "@load(vm.|)"
    When the user selects "saveItem" from the completion list
    Then "saveItem()" is inserted with caret positioned after the closing ")"

  Scenario: Non-getter method with parameters shows "(N params)" in tail text
    Given the binding expression "@load(vm.|)"
    When the user invokes Ctrl+Space
    Then "setValue" shows tail text containing "(1 params)"

  Scenario: Non-getter method with parameters auto-inserts "()" with caret inside
    Given the binding expression "@load(vm.|)"
    When the user selects "setValue" from the completion list
    Then "setValue()" is inserted with caret positioned inside the parentheses

  Scenario: Non-public method is excluded from property completion
    # Only public methods appear — protected/private are not valid binding targets
    Given MyViewModel has a protected method "protectedHelper()"
    When the user invokes Ctrl+Space inside "@load(vm.|)"
    Then "protectedHelper" does NOT appear in the completion list

  Scenario: Constructor is not suggested in property completion
    # isConstructor() guard in Pass 2 prevents constructors appearing
    Given MyViewModel has a public constructor "MyViewModel()"
    When the user invokes Ctrl+Space inside "@load(vm.|)"
    Then "MyViewModel" does NOT appear in the completion list as a method

  # ── Object method filtering ──────────────────────────────────────────────────

  Scenario: Common Object utility methods are filtered out
    Given the binding expression "@load(vm.|)"
    When the user invokes Ctrl+Space
    Then the completion list does NOT contain "toString"
    And  the completion list does NOT contain "hashCode"
    And  the completion list does NOT contain "equals"
    And  the completion list does NOT contain "notify"
    And  the completion list does NOT contain "notifyAll"
    And  the completion list does NOT contain "wait"

  # ── Null / no ViewModel ───────────────────────────────────────────────────────

  Scenario: No completion when ownerClass is null (property context)
    Given no ViewModel is declared in scope
    When the user invokes Ctrl+Space inside a binding annotation after "."
    Then the completion list is empty

  Scenario: No completion when ownerClass is null (command context)
    Given no ViewModel is declared in scope
    When the user invokes Ctrl+Space inside "@command(|)"
    Then the completion list is empty

  # ── Chained property completion (type propagation) ───────────────────────────

  Scenario: Second-segment completion resolves type from first getter
    # vm.crew is of type CrewModel; completion at vm.crew.| shows CrewModel members
    Given the binding expression "@load(vm.crew.|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "name"             # from CrewModel.getName()
    And  the completion list does NOT contain "list"     # MyViewModel-only property
    And  the completion list does NOT contain "active"   # MyViewModel-only property

  Scenario: Completion is empty when intermediate segment cannot be resolved
    Given the binding expression "@load(vm.nonExistent.|)"
    When the user invokes Ctrl+Space
    Then the completion list is empty

  # ── Command context ───────────────────────────────────────────────────────────

  Scenario: Inside @command — @Command method names are suggested
    Given the binding expression "@command(|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "saveItem"
    And  the completion list contains "validate"
    And  the completion list contains "commit"
    And  the completion list contains "hello"

  Scenario: Inside @command — @Command with explicit value shows annotation value
    # persistItem has @Command(value="save") → completion shows "save", not "persistItem"
    Given MyViewModel has "persistItem" annotated @Command(value="save")
    When the user invokes Ctrl+Space inside "@command(|)"
    Then the completion list contains "save"
    And  the completion list does NOT contain "persistItem"

  Scenario: Inside @command — @GlobalCommand method appears in completion
    Given MyViewModel has "broadcast" annotated @GlobalCommand
    When the user invokes Ctrl+Space inside "@command(|)"
    Then the completion list contains "broadcast"

  Scenario: Inside @command — property names are NOT suggested
    When the user invokes Ctrl+Space inside "@command(|)"
    Then the completion list does NOT contain "list"
    And  the completion list does NOT contain "name"
    And  the completion list does NOT contain "active"

  Scenario: Inside @global-command — @GlobalCommand method names are suggested
    Given the binding expression "@global-command(|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "broadcast"

  Scenario: Inside @command — plain (non-annotated) methods are not suggested
    # Only @Command / @GlobalCommand annotated methods appear in command context
    Given MyViewModel has a public method "helperMethod()" with no @Command annotation
    When the user invokes Ctrl+Space inside "@command(|)"
    Then "helperMethod" does NOT appear in the completion list

  # ── Annotation scope ──────────────────────────────────────────────────────────

  Scenario: Property completion works inside @bind
    Given the binding expression "@bind(vm.|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "list"
    And  the completion list contains "name"
    And  the completion list contains "active"

  Scenario: Property completion works inside @save
    Given the binding expression "@save(vm.|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "list"
    And  the completion list contains "name"
    And  the completion list contains "active"

  Scenario: Property completion works inside @init
    Given the binding expression "@init(vm.|)"
    When the user invokes Ctrl+Space
    Then the completion list contains "list"
    And  the completion list contains "name"
    And  the completion list contains "active"

  # ── Incomplete expression (no closing parenthesis) ────────────────────────
  # When the user is still typing and the closing ')' is absent, IntelliJ
  # inserts a dummy identifier at the cursor. Completion must still work.

  Scenario: Property completion works when closing parenthesis is absent
    # Regression: @load(vm.  (no closing ')') previously showed no suggestions.
    # IntelliJ inserts a dummy identifier → effectively "@load(vm.<dummy>".
    # The annotation body must still be extracted and completion offered.
    Given the binding expression "@load(vm.|" with no closing parenthesis
    When the user invokes Ctrl+Space
    Then the completion list contains "list"
    And  the completion list contains "name"
    And  the completion list contains "active"

  Scenario: Chained completion works when closing parenthesis is absent
    Given the binding expression "@load(vm.crew.|" with no closing parenthesis
    When the user invokes Ctrl+Space
    Then the completion list contains "name"
    And  the completion list does NOT contain "list"

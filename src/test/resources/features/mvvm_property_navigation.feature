Feature: MVVM Binding Expression Property Navigation
  As a ZK developer using the MVVM pattern
  I want to Ctrl+Click on a ViewModel property reference (e.g., vm.list) inside a ZUL binding expression
  and navigate directly to the corresponding Java getter method in the ViewModel class
  So that I can quickly jump between my ZUL view and ViewModel implementation

  Background:
    Given a ZK project with the ZKIdea plugin installed
    And a ViewModel class "com.example.MyViewModel" exists with:
      | Method                | Return Type       |
      | getList()             | List<String>      |
      | getName()             | String            |
      | isActive()            | boolean           |
      | getCrew()             | CrewModel         |
    And a CrewModel class "com.example.CrewModel" exists with:
      | Method                | Return Type       |
      | getName()             | String            |
      | getAge()              | int               |
    And a ZUL file contains:
      """
      <window viewModel="@id('vm') @init('com.example.MyViewModel')">
        <grid model="@load(vm.list)">
          <label value="@load(vm.name)"/>
          <label value="@load(vm.active)"/>
          <label value="@load(vm.crew.name)"/>
        </grid>
      </window>
      """

  # --- Simple Property Navigation (getter method) ---

  Scenario: Navigate to getter method from simple property reference
    When I Ctrl+Click on "list" in the expression "@load(vm.list)"
    Then the IDE should navigate to "MyViewModel.getList()" method

  Scenario: Navigate to getter method for String property
    When I Ctrl+Click on "name" in the expression "@load(vm.name)"
    Then the IDE should navigate to "MyViewModel.getName()" method

  Scenario: Navigate to boolean getter (isXxx) from property reference
    When I Ctrl+Click on "active" in the expression "@load(vm.active)"
    Then the IDE should navigate to "MyViewModel.isActive()" method


  # --- Nested Property Navigation ---

  Scenario: Navigate to getter on nested property path (first segment)
    When I Ctrl+Click on "crew" in the expression "@load(vm.crew.name)"
    Then the IDE should navigate to "MyViewModel.getCrew()" method

  Scenario: Navigate to getter on nested property path (second segment)
    When I Ctrl+Click on "name" in the expression "@load(vm.crew.name)"
    Then the plugin should resolve the return type of "MyViewModel.getCrew()" as "CrewModel"
    And navigate to "CrewModel.getName()" method

  # --- Different Binding Annotations ---

  Scenario Outline: Navigate from various MVVM binding annotations
    Given the attribute value is "<annotation>(vm.list)"
    When I Ctrl+Click on "list" in the expression "<annotation>(vm.list)"
    Then the IDE should navigate to "MyViewModel.getList()" method

    Examples:
      | annotation |
      | @load      |
      | @init      |
      | @bind      |
      | @save      |

  # --- Navigation on the ViewModel ID itself ---

  Scenario: Ctrl+Click on the ViewModel ID prefix should not navigate to a property
    When I Ctrl+Click on "vm" in the expression "@load(vm.list)"
    Then the IDE should not navigate to any property getter
    # Navigating to the ViewModel class itself is handled by existing GotoJavaClassHandler

  # --- Edge Cases and Negative Scenarios ---

  Scenario: No navigation when property does not exist on ViewModel
    When I Ctrl+Click on "nonExistent" in the expression "@load(vm.nonExistent)"
    Then the IDE should show "no declarations found"

  Scenario: No navigation outside of binding expression
    Given a ZUL attribute value="plain text with vm.list"
    When I Ctrl+Click on "list" in "plain text with vm.list"
    Then the IDE should show "no declarations found"

  Scenario: No navigation when there is no viewModel declaration in ancestor
    Given a ZUL file without a viewModel attribute:
      """
      <window>
        <label value="@load(vm.list)"/>
      </window>
      """
    When I Ctrl+Click on "list" in the expression "@load(vm.list)"
    Then the IDE should show "no declarations found"

  Scenario: No navigation when ViewModel class cannot be resolved
    Given the ZUL file declares viewModel="@id('vm') @init('com.nonexistent.FakeVM')"
    When I Ctrl+Click on "list" in the expression "@load(vm.list)"
    Then the IDE should show "no declarations found"

  # --- Property in @command context ---

  Scenario: Navigate to property in @command expression parameter
    Given the ViewModel has a method "getSelectedItem()" returning "Item"
    And the attribute is onClick="@command('delete', item=vm.selectedItem)"
    When I Ctrl+Click on "selectedItem" in "vm.selectedItem"
    Then the IDE should navigate to "MyViewModel.getSelectedItem()" method

  # --- Inherited getter methods ---

  Scenario: Navigate to inherited getter method
    Given "com.example.MyViewModel" extends "com.example.BaseViewModel"
    And "BaseViewModel" has a method "getBaseProperty()" returning "String"
    And the expression is "@load(vm.baseProperty)"
    When I Ctrl+Click on "baseProperty" in the expression "@load(vm.baseProperty)"
    Then the IDE should navigate to "BaseViewModel.getBaseProperty()" method

  # --- Getter method priority ---

  Scenario: Prefer getter method over field when both exist
    Given "com.example.MyViewModel" has a field "private String name"
    And "com.example.MyViewModel" has a method "getName()" returning "String"
    When I Ctrl+Click on "name" in the expression "@load(vm.name)"
    Then the IDE should navigate to "MyViewModel.getName()" method
    # ZK calls the getter, so navigation should go to the getter

  # --- Multiple ViewModels in nested components ---

  Scenario: Resolve correct ViewModel in nested viewModel declarations
    Given a ZUL file contains:
      """
      <window viewModel="@id('outer') @init('com.example.OuterVM')">
        <div viewModel="@id('inner') @init('com.example.InnerVM')">
          <label value="@load(inner.name)"/>
        </div>
        <label value="@load(outer.name)"/>
      </window>
      """
    And "OuterVM" has "getName()" returning "String"
    And "InnerVM" has "getName()" returning "String"
    When I Ctrl+Click on "name" in the expression "@load(inner.name)"
    Then the IDE should navigate to "InnerVM.getName()" method
    When I Ctrl+Click on "name" in the expression "@load(outer.name)"
    Then the IDE should navigate to "OuterVM.getName()" method

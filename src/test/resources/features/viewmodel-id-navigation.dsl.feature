Feature: ViewModel ID Reference Navigation
  In a ZUL file, the ViewModel alias (e.g. "vm") at the root position of a binding
  chain is a clickable reference that navigates to the ViewModel Java class.

  Rule: Navigation is only available when the file is a .zul file
    Example: Ctrl+Click on a binding-like expression in a non-ZUL file does not trigger ViewModel navigation
      Given a "zk.xml" file with a viewModel attribute "@id('vm') @init('com.example.UserViewModel')"
      And the file contains the expression "vm.userName"
      When the user Ctrl+Clicks on "vm"
      Then no navigation occurs

  Rule: Navigation is only triggered when the clicked token is the first identifier segment of a dotted binding chain
    Example: Ctrl+Click on a non-root segment does not trigger ViewModel class navigation
      Given a .zul file with an ancestor tag whose viewModel attribute is "@id('vm') @init('com.example.UserViewModel')"
      And the attribute value contains the binding expression "@load(vm.user.name)"
      When the user Ctrl+Clicks on "user" in the binding expression
      Then the editor does not navigate to the class "com.example.UserViewModel"

  Rule: Navigation is only triggered when the first chain segment matches the @id alias
    Example: Ctrl+Click on a template variable root does not trigger ViewModel navigation
      Given a .zul file with an ancestor tag whose viewModel attribute is "@id('vm') @init('com.example.UserViewModel')"
      And a template variable "item" is declared in an enclosing template tag
      And the attribute value contains the binding expression "@load(item.name)"
      When the user Ctrl+Clicks on "item" in the binding expression
      Then no navigation occurs

  Rule: If the ViewModel class is not found on the classpath, no navigation occurs and no error is shown
    Example: Unresolvable ViewModel class results in a silent no-op
      Given a .zul file with an ancestor tag whose viewModel attribute is "@id('vm') @init('com.example.MissingViewModel')"
      And the class "com.example.MissingViewModel" is not on the classpath
      And the attribute value contains the binding expression "@load(vm.userName)"
      When the user Ctrl+Clicks on "vm" in the binding expression
      Then no navigation occurs
      And no error message is shown

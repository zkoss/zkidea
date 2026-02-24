Feature: MVVM Property Navigation — ISA-Level Test Specifications
  # Instruction-Set Architecture level: each scenario maps 1-to-1 to a unit test.
  # Concrete method names, exact string inputs, and computed integer offsets replace
  # every user-behavior phrase ("Ctrl+Click", "IDE navigates to") from the DSL file.
  #
  # Class under test hierarchy:
  #   ZulDomUtil                      — pure static string/PSI utilities
  #   ZkBindingReferenceProvider      — static parsers + getReferencesByElement()
  #   ViewModelPropertyReference      — PsiReferenceBase, resolve() + getVariants()
  #   ViewModelIdReference            — PsiReferenceBase, resolve()
  #
  # TextRange offset convention (established by the zulAttr() helper in existing tests):
  #   attrValue.getTextRange()      = TextRange(0, len(value)+2)   [outer quotes counted]
  #   attrValue.getValueTextRange() = TextRange(1, len(value)+1)   [inner quotes excluded]
  #   valueOffset                   = getValueTextRange().getStartOffset()
  #                                 - getTextRange().getStartOffset()
  #                                 = 1
  #
  # For a reference at chain segment (nameStartInBody, nameLength) inside annotation
  # with bodyStartOffset:
  #   rangeStart = valueOffset + bodyStartOffset + nameStartInBody
  #   rangeEnd   = rangeStart  + nameLength
  #   TextRange  = TextRange(rangeStart, rangeEnd)

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 1  ZulDomUtil.extractViewModelId(String viewModelAttrValue)
  #          DSL refs: Background, all scenarios
  # ═══════════════════════════════════════════════════════════════════════════

  Scenario Outline: extractViewModelId returns the @id alias string
    # Method: ZulDomUtil.extractViewModelId(String)
    # Return type: String (nullable)
    When ZulDomUtil.extractViewModelId("<attrValue>") is called
    Then it returns "<expectedId>"

    Examples:
      | attrValue                                           | expectedId |
      | @id('vm') @init('com.example.MyViewModel')          | vm         |
      | @id('outer') @init('com.example.OuterVM')           | outer      |
      | @id('inner') @init('com.example.InnerVM')           | inner      |

  Scenario: extractViewModelId returns null when @id token is absent
    When ZulDomUtil.extractViewModelId("@init('com.example.MyViewModel')") is called
    Then it returns null

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 2  ZulDomUtil.extractViewModelClassName(String viewModelAttrValue)
  # ═══════════════════════════════════════════════════════════════════════════

  Scenario Outline: extractViewModelClassName returns the @init class name string
    # Method: ZulDomUtil.extractViewModelClassName(String)
    # Return type: String (nullable)
    When ZulDomUtil.extractViewModelClassName("<attrValue>") is called
    Then it returns "<expectedClass>"

    Examples:
      | attrValue                                           | expectedClass               |
      | @id('vm') @init('com.example.MyViewModel')          | com.example.MyViewModel     |
      | @id('vm') @init('com.nonexistent.FakeVM')           | com.nonexistent.FakeVM      |

  Scenario: extractViewModelClassName returns null when @init token is absent
    When ZulDomUtil.extractViewModelClassName("@id('vm')") is called
    Then it returns null

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 3  ZulDomUtil.findGetter(PsiClass, String property)
  #          DSL refs: Navigate to getter method, boolean getter, inherited getter,
  #                    prefer getter over field
  # ═══════════════════════════════════════════════════════════════════════════

  Scenario Outline: findGetter resolves getXxx() from a property name
    # Method: ZulDomUtil.findGetter(PsiClass, String)
    # Return type: PsiMethod (nullable)
    # Setup: mock MyViewModel with getAllMethods() returning a single public no-arg method
    Given a mock MyViewModel whose getAllMethods() includes public no-arg method "<methodName>"
    When ZulDomUtil.findGetter(myViewModel, "<property>") is called
    Then it returns a PsiMethod whose getName() == "<methodName>"

    Examples:
      | property | methodName |
      | list     | getList    |
      | name     | getName    |
      | crew     | getCrew    |

  Scenario: findGetter resolves isXxx() for boolean property name
    # DSL: "Navigate to boolean getter (isXxx) from property reference"
    Given a mock MyViewModel whose getAllMethods() includes public no-arg method "isActive"
    When ZulDomUtil.findGetter(myViewModel, "active") is called
    Then it returns a PsiMethod whose getName() == "isActive"

  Scenario: findGetter searches inherited methods included in getAllMethods()
    # DSL: "Navigate to inherited getter method"
    # getAllMethods() on IntelliJ PsiClass returns ALL methods including inherited ones.
    Given a mock MyViewModel whose getAllMethods() includes inherited public no-arg method "getBaseProperty"
    When ZulDomUtil.findGetter(myViewModel, "baseProperty") is called
    Then it returns a PsiMethod whose getName() == "getBaseProperty"

  Scenario: findGetter returns null when no getXxx() or isXxx() method exists
    # DSL: "No navigation when property does not exist on ViewModel"
    Given a mock MyViewModel whose getAllMethods() returns no method matching "getNonExistent" or "isNonExistent"
    When ZulDomUtil.findGetter(myViewModel, "nonExistent") is called
    Then it returns null

  Scenario: findGetter ignores fields — only methods are scanned
    # DSL: "Prefer getter method over field when both exist"
    # findGetter only inspects PsiMethod[], never PsiField[]. If getName() matches
    # getXxx() in the method list, it wins regardless of any same-named field.
    Given a mock MyViewModel with a field "name" and a public no-arg method "getName"
    When ZulDomUtil.findGetter(myViewModel, "name") is called
    Then it returns a PsiMethod whose getName() == "getName"

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 4  ZkBindingReferenceProvider.findMatchingParen(String text, int openPos)
  # ═══════════════════════════════════════════════════════════════════════════

  Scenario Outline: findMatchingParen returns position of matching close parenthesis
    # Method: ZkBindingReferenceProvider.findMatchingParen(String, int)
    # Return type: int (-1 if not found)
    # openPos points to the '(' character at that index in text.
    When ZkBindingReferenceProvider.findMatchingParen("<text>", <openPos>) is called
    Then it returns <expected>

    Examples:
      | text                                   | openPos | expected |
      | @load(vm.list)                         | 5       | 13       |
      | @load(vm.crew.name)                    | 5       | 18       |
      | @init(vm.list)                         | 5       | 13       |
      | @bind(vm.list)                         | 5       | 13       |
      | @save(vm.list)                         | 5       | 13       |
      | @command('delete', item=vm.selected)   | 8       | 35       |

  Scenario: findMatchingParen skips over quoted strings when counting depth
    # Quoted content that contains ')' must not decrease depth
    When ZkBindingReferenceProvider.findMatchingParen("@command('has(arg)')", 8) is called
    Then it returns 19

  Scenario: findMatchingParen returns -1 when close paren is absent
    When ZkBindingReferenceProvider.findMatchingParen("@load(vm.list", 5) is called
    Then it returns -1

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 5  ZkBindingReferenceProvider.extractAnnotations(String text)
  #          DSL refs: Navigate from various MVVM binding annotations
  # ═══════════════════════════════════════════════════════════════════════════

  Scenario Outline: extractAnnotations parses annotation name, body, and bodyStartOffset
    # Method: ZkBindingReferenceProvider.extractAnnotations(String)
    # Return type: List<AnnotationMatch>
    # AnnotationMatch fields: name (String), body (String), bodyStartOffset (int)
    # bodyStartOffset is the index inside 'text' of the first character after '('.
    When ZkBindingReferenceProvider.extractAnnotations("<text>") is called
    Then it returns exactly 1 AnnotationMatch with:
      | field           | value         |
      | name            | <annotation>  |
      | body            | <body>        |
      | bodyStartOffset | <offset>      |

    Examples:
      | text                 | annotation | body         | offset |
      | @load(vm.list)       | load       | vm.list      | 6      |
      | @load(vm.name)       | load       | vm.name      | 6      |
      | @load(vm.active)     | load       | vm.active    | 6      |
      | @load(vm.crew.name)  | load       | vm.crew.name | 6      |
      | @init(vm.list)       | init       | vm.list      | 6      |
      | @bind(vm.list)       | bind       | vm.list      | 6      |
      | @save(vm.list)       | save       | vm.list      | 6      |

  Scenario: extractAnnotations parses @command with nested arguments
    # DSL: "Navigate to property in @command expression parameter"
    # bodyStartOffset = length("@command(") = 9
    When ZkBindingReferenceProvider.extractAnnotations("@command('delete', item=vm.selectedItem)") is called
    Then it returns exactly 1 AnnotationMatch with:
      | field           | value                           |
      | name            | command                         |
      | body            | 'delete', item=vm.selectedItem  |
      | bodyStartOffset | 9                               |

  Scenario: extractAnnotations returns empty list for plain attribute text
    # DSL: "No navigation outside of binding expression"
    When ZkBindingReferenceProvider.extractAnnotations("plain text with vm.list") is called
    Then it returns an empty list

  Scenario: extractAnnotations returns empty list for unrecognized annotation name
    When ZkBindingReferenceProvider.extractAnnotations("@unknown(vm.list)") is called
    Then it returns an empty list

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 6  ZkBindingReferenceProvider.extractChains(String body)
  # ═══════════════════════════════════════════════════════════════════════════

  Scenario Outline: extractChains produces a single 2-segment chain for simple property body
    # Method: ZkBindingReferenceProvider.extractChains(String)
    # Return type: List<List<ChainSegment>>
    # ChainSegment fields: name(String), isMethodCall(boolean), nameStartInBody(int), nameLength(int)
    When ZkBindingReferenceProvider.extractChains("<body>") is called
    Then it returns exactly 1 chain containing 2 segments:
      | name     | nameStartInBody | nameLength | isMethodCall |
      | vm       | 0               | 2          | false        |
      | <prop>   | 3               | <propLen>  | false        |

    Examples:
      | body      | prop   | propLen |
      | vm.list   | list   | 4       |
      | vm.name   | name   | 4       |
      | vm.active | active | 6       |
      | vm.crew   | crew   | 4       |

  Scenario: extractChains produces a single 3-segment chain for nested property body
    # DSL: "Navigate to getter on nested property path"
    # body = "vm.crew.name"
    #   'vm'  : nameStartInBody=0, nameLength=2
    #   'crew': nameStartInBody=3, nameLength=4
    #   'name': nameStartInBody=8, nameLength=4
    When ZkBindingReferenceProvider.extractChains("vm.crew.name") is called
    Then it returns exactly 1 chain containing 3 segments:
      | name | nameStartInBody | nameLength | isMethodCall |
      | vm   | 0               | 2          | false        |
      | crew | 3               | 4          | false        |
      | name | 8               | 4          | false        |

  Scenario: extractChains skips quoted string literals and produces two separate chains
    # DSL: "Navigate to property in @command expression parameter"
    # body = "'delete', item=vm.selectedItem"
    #   Chain 0: [{item, nameStartInBody=10, nameLength=4, isMethodCall=false}]
    #   Chain 1: [{vm, nameStartInBody=15, nameLength=2, isMethodCall=false},
    #             {selectedItem, nameStartInBody=18, nameLength=12, isMethodCall=false}]
    #
    # Offset derivation for body "'delete', item=vm.selectedItem":
    #   pos 0–7  : 'delete'  (string literal, skipped)
    #   pos 8    : ,
    #   pos 9    : (space)
    #   pos 10   : i — start of "item" (length 4)
    #   pos 14   : = (not '.' or '(', so chain ends here)
    #   pos 15   : v — start of "vm" (length 2)
    #   pos 17   : .
    #   pos 18   : s — start of "selectedItem" (length 12)
    When ZkBindingReferenceProvider.extractChains("'delete', item=vm.selectedItem") is called
    Then it returns exactly 2 chains:
      | chainIndex | name         | nameStartInBody | nameLength | isMethodCall |
      | 0          | item         | 10              | 4          | false        |
      | 1          | vm           | 15              | 2          | false        |
      | 1          | selectedItem | 18              | 12         | false        |

  Scenario: extractChains returns empty list for empty body string
    When ZkBindingReferenceProvider.extractChains("") is called
    Then it returns an empty list

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 7  ViewModelPropertyReference.resolve()
  #          DSL refs: Navigate to getter method (all simple property scenarios)
  # ═══════════════════════════════════════════════════════════════════════════

  Scenario Outline: ViewModelPropertyReference.resolve() delegates to ZulDomUtil.findGetterOrMethod()
    # Constructor: ViewModelPropertyReference(XmlAttributeValue, TextRange, PsiClass, String)
    # resolve() calls: ZulDomUtil.findGetterOrMethod(ownerClass, propertyName)
    # Return type: PsiElement (the matched PsiMethod), or null
    Given a mock PsiClass with public no-arg method "<expectedMethod>"
    And a ViewModelPropertyReference constructed with ownerClass=<mockClass>, propertyName="<property>"
    When resolve() is called
    Then it returns a PsiMethod whose getName() == "<expectedMethod>"

    Examples:
      | property     | expectedMethod  |
      | list         | getList         |
      | name         | getName         |
      | active       | isActive        |
      | crew         | getCrew         |
      | baseProperty | getBaseProperty |
      | selectedItem | getSelectedItem |

  Scenario: ViewModelPropertyReference.resolve() returns null for unknown property
    # DSL: "No navigation when property does not exist on ViewModel"
    Given a mock PsiClass with no method matching "getNonExistent" or "isNonExistent"
    And a ViewModelPropertyReference with ownerClass=<mockClass>, propertyName="nonExistent"
    When resolve() is called
    Then it returns null

  Scenario: ViewModelPropertyReference.resolve() returns null when ownerClass is null
    Given a ViewModelPropertyReference constructed with ownerClass=null, propertyName="list"
    When resolve() is called
    Then it returns null

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 8  ZkBindingReferenceProvider.getReferencesByElement() — TextRange assertions
  #          This group validates full reference assembly: type, TextRange, propertyName.
  #          All scenarios use valueOffset=1 (standard zulAttr() helper convention).
  # ═══════════════════════════════════════════════════════════════════════════

  # Offset derivation for @load(vm.list):
  #   bodyStartOffset=6, valueOffset=1, bodyOffsetInElement = 1+6 = 7
  #   "vm"  : segStart = 7+0 = 7,  TextRange(7,  9)
  #   "list": segStart = 7+3 = 10, TextRange(10, 14)
  Scenario: getReferencesByElement emits ViewModelIdReference + one property ref for "@load(vm.list)"
    # DSL: "Navigate to getter method from simple property reference"
    Given attrValue.getValue() == "@load(vm.list)"
    And valueOffset == 1
    And ZulDomUtil.findViewModelTag returns a tag with viewModel="@id('vm') @init('com.example.MyViewModel')"
    And ZulDomUtil.extractViewModelId returns "vm"
    And ZulDomUtil.resolveViewModelClass returns a non-null mock PsiClass
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then it returns exactly 2 PsiReferences:
      | index | type                       | rangeInElement   | field        | value |
      | 0     | ViewModelIdReference       | TextRange(7, 9)  | —            | —     |
      | 1     | ViewModelPropertyReference | TextRange(10, 14)| propertyName | list  |

  # Offset derivation for @load(vm.name):
  #   body="vm.name", bodyStartOffset=6, bodyOffsetInElement=7
  #   "vm"  : TextRange(7,  9)
  #   "name": segStart=7+3=10, TextRange(10, 14)
  Scenario: getReferencesByElement emits two refs for "@load(vm.name)"
    # DSL: "Navigate to getter method for String property"
    Given attrValue.getValue() == "@load(vm.name)"
    And the same mock setup as the previous scenario with vmId="vm"
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then it returns exactly 2 PsiReferences:
      | index | type                       | rangeInElement   | field        | value |
      | 0     | ViewModelIdReference       | TextRange(7, 9)  | —            | —     |
      | 1     | ViewModelPropertyReference | TextRange(10, 14)| propertyName | name  |

  # Offset derivation for @load(vm.active):
  #   body="vm.active", bodyStartOffset=6, bodyOffsetInElement=7
  #   "active": nameStartInBody=3, nameLength=6 → segStart=7+3=10, TextRange(10,16)
  Scenario: getReferencesByElement emits two refs for "@load(vm.active)" with propertyName="active"
    # DSL: "Navigate to boolean getter (isXxx) from property reference"
    Given attrValue.getValue() == "@load(vm.active)"
    And the same mock setup with vmId="vm"
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then it returns exactly 2 PsiReferences:
      | index | type                       | rangeInElement   | field        | value  |
      | 0     | ViewModelIdReference       | TextRange(7, 9)  | —            | —      |
      | 1     | ViewModelPropertyReference | TextRange(10, 16)| propertyName | active |

  # Offset derivation for @load(vm.crew.name):
  #   body="vm.crew.name", bodyStartOffset=6, bodyOffsetInElement=7
  #   "vm"  : TextRange(7,  9)   (start=7+0, len=2)
  #   "crew": TextRange(10, 14)  (start=7+3, len=4)
  #   "name": TextRange(15, 19)  (start=7+8, len=4)
  Scenario: getReferencesByElement emits three refs for "@load(vm.crew.name)"
    # DSL: "Navigate to getter on nested property path (first/second segment)"
    Given attrValue.getValue() == "@load(vm.crew.name)"
    And ZulDomUtil.resolveViewModelClass returns mockMyViewModelClass
    And processChain resolvePropertyType for "crew" returns mockCrewModelClass
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then it returns exactly 3 PsiReferences:
      | index | type                       | rangeInElement   | field        | value |
      | 0     | ViewModelIdReference       | TextRange(7, 9)  | —            | —     |
      | 1     | ViewModelPropertyReference | TextRange(10, 14)| propertyName | crew  |
      | 2     | ViewModelPropertyReference | TextRange(15, 19)| propertyName | name  |
    And refs[1].ownerClass == mockMyViewModelClass
    And refs[2].ownerClass == mockCrewModelClass

  # Offset derivation for @command('delete', item=vm.selectedItem):
  #   bodyStartOffset=9, valueOffset=1, bodyOffsetInElement=10
  #   body="'delete', item=vm.selectedItem"
  #   chain [{vm,15,2},{selectedItem,18,12}]:
  #     "vm"          : segStart=10+15=25, TextRange(25, 27)
  #     "selectedItem": segStart=10+18=28, nameLength=12, TextRange(28, 40)
  Scenario: getReferencesByElement sets isCommandContext=true for @command body property
    # DSL: "Navigate to property in @command expression parameter"
    Given attrValue.getValue() == "@command('delete', item=vm.selectedItem)"
    And ZulDomUtil.extractViewModelId returns "vm"
    And ZulDomUtil.resolveViewModelClass returns a non-null mock PsiClass
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then among the returned PsiReferences there is a ViewModelPropertyReference at TextRange(28, 40) with:
      | field            | value        |
      | propertyName     | selectedItem |
      | isCommandContext  | true         |
    And there is a ViewModelIdReference at TextRange(25, 27)

  # ── Annotation variant coverage ─────────────────────────────────────────────
  # DSL: "Navigate from various MVVM binding annotations"
  # For all recognized annotations the bodyStartOffset and property offsets are
  # identical for "vm.list" because all annotation names are padded to the same
  # start-of-body offset: @load(=6, @init(=6, @bind(=6, @save(=6.
  Scenario Outline: getReferencesByElement emits property ref for any recognized annotation
    Given attrValue.getValue() == "<annotation>(vm.list)"
    And ZulDomUtil.extractViewModelId returns "vm"
    And ZulDomUtil.resolveViewModelClass returns a non-null mock PsiClass
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then the last PsiReference is a ViewModelPropertyReference with:
      | field        | value |
      | propertyName | list  |

    Examples:
      | annotation |
      | @load      |
      | @init      |
      | @bind      |
      | @save      |

  # ── Negative / guard cases ───────────────────────────────────────────────────

  Scenario: getReferencesByElement returns empty array when no viewModel ancestor tag exists
    # DSL: "No navigation when there is no viewModel declaration in ancestor"
    Given attrValue.getValue() == "@load(vm.list)"
    And ZulDomUtil.isZKFile returns true
    And ZulDomUtil.findViewModelTag returns null
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then it returns PsiReference.EMPTY_ARRAY

  Scenario: getReferencesByElement returns empty array when ViewModel class cannot be resolved
    # DSL: "No navigation when ViewModel class cannot be resolved"
    Given attrValue.getValue() == "@load(vm.list)"
    And ZulDomUtil.isZKFile returns true
    And ZulDomUtil.findViewModelTag returns a tag with viewModel="@id('vm') @init('com.nonexistent.FakeVM')"
    And ZulDomUtil.extractViewModelId returns "vm"
    And ZulDomUtil.resolveViewModelClass returns null
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then it returns PsiReference.EMPTY_ARRAY

  Scenario: getReferencesByElement returns empty array for plain-text attribute value
    # DSL: "No navigation outside of binding expression"
    Given attrValue.getValue() == "plain text with vm.list"
    And ZulDomUtil.isZKFile returns true
    And ZulDomUtil.findViewModelTag returns a non-null mock tag
    And ZulDomUtil.resolveViewModelClass returns a non-null mock PsiClass
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then it returns exactly 0 PsiReferences
    # extractAnnotations("plain text with vm.list") returns [] → chain loop is never entered

  Scenario: getReferencesByElement returns empty array for non-ZUL file
    # DSL: "No navigation outside of binding expression" (file type guard)
    # A PsiFile mock that is NOT an XmlFile causes the real ZulDomUtil.isZKFile check to return false.
    Given the element's containingFile is a plain PsiFile (not XmlFile)
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then it returns PsiReference.EMPTY_ARRAY

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 9  ZulDomUtil.findViewModelTag(PsiElement element)
  #          DSL ref: "Resolve correct ViewModel in nested viewModel declarations"
  # ═══════════════════════════════════════════════════════════════════════════

  Scenario: findViewModelTag returns the nearest ancestor XmlTag with viewModel attribute
    # PSI tree (ancestor chain for the XmlAttributeValue inside inner label):
    #   XmlAttributeValue
    #     └─ XmlAttribute (value attr)
    #          └─ XmlTag[label]
    #               └─ XmlTag[div,  viewModel="@id('inner') @init('com.example.InnerVM')"]  ← nearest
    #                    └─ XmlTag[window, viewModel="@id('outer') @init('com.example.OuterVM')"]
    Given a PSI ancestor chain: XmlAttributeValue → XmlTag[label] → XmlTag[div, viewModel="@id('inner')..."] → XmlTag[window, viewModel="@id('outer')..."]
    When ZulDomUtil.findViewModelTag(attrValueElement) is called
    Then it returns the XmlTag whose getAttributeValue("viewModel") starts with "@id('inner')"

  Scenario: findViewModelTag returns outer ancestor when inner sibling has no viewModel attribute
    # PSI tree (ancestor chain for the XmlAttributeValue of the outer label):
    #   XmlAttributeValue
    #     └─ XmlAttribute
    #          └─ XmlTag[label]                           ← no viewModel
    #               └─ XmlTag[window, viewModel="@id('outer')..."]  ← nearest match
    Given a PSI ancestor chain: XmlAttributeValue → XmlTag[label] → XmlTag[window, viewModel="@id('outer')..."]
    When ZulDomUtil.findViewModelTag(attrValueElement) is called
    Then it returns the XmlTag whose getAttributeValue("viewModel") starts with "@id('outer')"

  Scenario: findViewModelTag returns null when no ancestor has viewModel attribute
    # DSL: "No navigation when there is no viewModel declaration in ancestor"
    Given a PSI ancestor chain: XmlAttributeValue → XmlTag[label] → XmlTag[window]
    # Neither XmlTag has a "viewModel" attribute
    When ZulDomUtil.findViewModelTag(attrValueElement) is called
    Then it returns null

  # ═══════════════════════════════════════════════════════════════════════════
  # GROUP 10  ZkBindingReferenceProvider.processChain() — nested type resolution
  #           Tested indirectly via getReferencesByElement() by verifying ownerClass
  #           on the second property reference in a nested chain.
  #           DSL ref: "Navigate to getter on nested property path (second segment)"
  # ═══════════════════════════════════════════════════════════════════════════

  Scenario: Second segment in nested chain receives CrewModel as ownerClass
    # DSL: "Navigate to getter on nested property path (second segment)"
    # The plugin must: (1) create ViewModelPropertyReference(ownerClass=MyViewModel, "crew"),
    # (2) call resolvePropertyType(MyViewModel, "crew") which calls
    #     ZulDomUtil.findGetterOrMethod(MyViewModel, "crew") → getCrew()
    #     then ZulDomUtil.resolveTypeToClass(getCrew().getReturnType()) → CrewModel PsiClass
    # (3) pass that CrewModel as ownerClass to ViewModelPropertyReference for "name".
    Given attrValue.getValue() == "@load(vm.crew.name)"
    And ZulDomUtil.findGetterOrMethod(myViewModelClass, "crew") returns a PsiMethod whose getReturnType() is "com.example.CrewModel"
    And ZulDomUtil.resolveTypeToClass("com.example.CrewModel", context) returns mockCrewModelClass
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then refs[2] is a ViewModelPropertyReference with:
      | field        | value        |
      | ownerClass   | mockCrewModelClass |
      | propertyName | name         |
    And refs[2].resolve() == ZulDomUtil.findGetterOrMethod(mockCrewModelClass, "name")

  Scenario: Second segment in nested chain gets ownerClass=null when getCrew() cannot be resolved
    # If resolvePropertyType returns null (e.g. return type is primitive or unresolvable),
    # the third reference must still be created with ownerClass=null so that
    # ViewModelPropertyReference.resolve() safely returns null rather than throwing.
    Given attrValue.getValue() == "@load(vm.unknownProp.name)"
    And ZulDomUtil.findGetterOrMethod(myViewModelClass, "unknownProp") returns null
    When ZkBindingReferenceProvider.getReferencesByElement(attrValue, context) is called
    Then refs[2] is a ViewModelPropertyReference with ownerClass == null
    And refs[2].resolve() returns null

Feature: Template URI navigation in ZUL files
  # Unless stated otherwise, each scenario assumes:
  #   - the ZUL file lives inside a web project whose root directory contains WEB-INF/web.xml
  #   - the referenced template file exists at the given web-root-relative path

  # ── @load with absolute path ────────────────────────────────────────────────

  Scenario: Navigate to a template file via @load — single-quoted path
    Given the binding expression "@load('/WEB-INF/template/grid.zul')"
    When the user navigates on the path "/WEB-INF/template/grid.zul"
    Then the IDE navigates to the file "WEB-INF/template/grid.zul" under the web root

  Scenario: Navigate to a template file via @init — single-quoted path
    Given the binding expression "@init('/WEB-INF/template/item.zul')"
    When the user navigates on the path "/WEB-INF/template/item.zul"
    Then the IDE navigates to the file "WEB-INF/template/item.zul" under the web root

  Scenario: Navigate to a template file via @load — double-quoted path
    Given the binding expression "@load(\"/WEB-INF/template/grid.zul\")"
    When the user navigates on the path "/WEB-INF/template/grid.zul"
    Then the IDE navigates to the file "WEB-INF/template/grid.zul" under the web root

  Scenario: Navigate to a template file when optional whitespace follows the opening paren
    # @load( '/path') — space between '(' and the opening quote is allowed
    Given the binding expression "@load( '/WEB-INF/grid.zul')"
    When the user navigates on the path "/WEB-INF/grid.zul"
    Then the IDE navigates to the file "WEB-INF/grid.zul" under the web root

  # ── Navigation and completion are available while typing ─────────────────────
  # The plugin resolves paths even before the closing quote and ')' are typed,
  # so file path completion and navigation work while the expression is incomplete.

  Scenario: File path completion is available while the path is still being typed
    Given the binding expression "@load('/WEB-INF/template/"
    When the user invokes code completion inside the partial path
    Then the IDE offers file name completions for the directory "WEB-INF/template/"

  # ── Web root resolution ──────────────────────────────────────────────────────
  # The web root is the nearest ancestor directory containing WEB-INF/web.xml.
  # The plugin walks up the directory tree until it finds such a directory.

  Scenario: Web root is the ZUL file's immediate parent directory
    # e.g. /webapp/index.zul → web root = /webapp (parent contains WEB-INF/web.xml)
    Given a ZUL file whose parent directory contains WEB-INF/web.xml
    And the binding expression "@load('/WEB-INF/template/grid.zul')"
    When the user navigates on the path "/WEB-INF/template/grid.zul"
    Then the IDE navigates to the file "WEB-INF/template/grid.zul" under the web root

  Scenario: Web root is found by walking up one directory level
    # e.g. ZUL file is inside /webapp/WEB-INF/template/; web root = /webapp
    Given a ZUL file nested one level inside the web root
    And the binding expression "@load('/WEB-INF/template/grid.zul')"
    When the user navigates on the path "/WEB-INF/template/grid.zul"
    Then the IDE navigates to the file "WEB-INF/template/grid.zul" under the web root

  Scenario: Web root resolution skips an ancestor whose WEB-INF directory has no web.xml
    # An ancestor with WEB-INF/ but without WEB-INF/web.xml is not a valid web root;
    # the plugin continues walking up to find the correct ancestor.
    Given a ZUL file with a near ancestor that has WEB-INF/ but no WEB-INF/web.xml
    And the actual web root is a higher ancestor that has a complete WEB-INF/web.xml
    And the binding expression "@load('/WEB-INF/template/grid.zul')"
    When the user navigates on the path "/WEB-INF/template/grid.zul"
    Then the IDE navigates to the file "WEB-INF/template/grid.zul" under the actual web root

  Scenario: No navigation when no ancestor directory contains WEB-INF/web.xml
    Given a ZUL file with no WEB-INF/web.xml in any ancestor directory
    And the binding expression "@load('/WEB-INF/template/grid.zul')"
    When the user navigates on the path "/WEB-INF/template/grid.zul"
    Then no navigation occurs

  # ── No navigation for relative paths ────────────────────────────────────────
  # The plugin only resolves web-context absolute paths that begin with '/'.

  Scenario: No navigation when the path is relative (no leading slash)
    Given the binding expression "@load('template/item.zul')"
    When the user navigates anywhere in the expression
    Then no navigation occurs

  # ── No navigation for unrecognised annotations ───────────────────────────────
  # Only @load and @init are recognised; other ZK binding annotations are not.

  Scenario Outline: No navigation for annotations other than @load and @init
    Given the binding expression "<expression>"
    When the user navigates anywhere in the expression
    Then no navigation occurs

    Examples:
      | expression                                  |
      | @bind('/WEB-INF/template/grid.zul')         |
      | @save('/WEB-INF/template/grid.zul')         |
      | @command('/WEB-INF/template/grid.zul')      |
      | @unknown('/WEB-INF/template/grid.zul')      |

  # ── No navigation in non-ZUL files or when the binding expression is empty ────

  Scenario: No navigation in a non-ZUL XML file
    Given a plain XML file (not a .zul file) with the attribute value "@load('/WEB-INF/template/grid.zul')"
    When the user navigates on the path "/WEB-INF/template/grid.zul"
    Then no navigation occurs

  Scenario: No navigation when the binding expression is empty
    Given an attribute value that is empty
    When the user navigates anywhere in the expression
    Then no navigation occurs

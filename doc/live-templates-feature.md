# Live Templates Feature Spec

## Overview

Ship a "ZK" group of IntelliJ Live Templates with the plugin so every user gets them automatically, without manual import.

## Templates

### `ns` — ZK namespace declarations (XML context)

Inserts the four ZK namespace aliases onto a ZUL tag.

**Abbreviation:** `ns`
**Description:** `zk namespace`
**Context:** XML
**Expansion:**
```
 xmlns:n="native" xmlns:ca="client/attribute" xmlns:w="client" xmlns:x="xhtml"
```

---

### `jspatch` — ZK widget JavaScript patch (OTHER context)

Scaffolds a ZK client-side widget override with version guard and checklist header.

**Abbreviation:** `jspatch`
**Description:** `ZK widget JS patch`
**Context:** OTHER (applies to plain `.js` files)
**Variables:** `$package$`, `$class$`, `$version$`
**Expansion:**
```javascript
/**
 * Purpose:
 * Based on version:
 * Last update:
 * Check List:
 * 1. a patch should be specific to the targeted bug e.g. for specific browser or attribute value
 * 2. ensure copy all required private functions
 */
zk.afterLoad('$package$', function() {
    var exWidget = {};
    zk.override($class$.prototype, exWidget, {
		doClick_: function(e){
			exWidget.doClick_.apply(this, arguments);
		},
    });

});
let patchTargetVersion = '$version$';
if (zk.version != patchTargetVersion) {
    console.warn(`This overridden script was tested for ZK ${patchTargetVersion}. If you are running a different version, please check this script compatibility`);
}
```

---

### `grid` — ZK Grid scaffold (XML context)

Inserts a Grid with two Columns and one Row of Labels.

**Abbreviation:** `grid`
**Description:** `ZK grid`
**Context:** XML_TEXT (between tags)
**Variables:** `$COL1$`, `$COL2$`, `$END$`
**Expansion:**
```xml
<grid>
    <columns>
        <column label="$COL1$" />
        <column label="$COL2$" />
    </columns>
    <rows>
        <row>
            <label value="$END$" />
            <label value="" />
        </row>
    </rows>
</grid>
```

---

### `listbox` — ZK Listbox scaffold (XML context)

Inserts a Listbox with two Listheaders and one Listitem.

**Abbreviation:** `listbox`
**Description:** `ZK listbox`
**Context:** XML_TEXT (between tags)
**Variables:** `$HEAD1$`, `$HEAD2$`, `$END$`
**Expansion:**
```xml
<listbox>
    <listhead>
        <listheader label="$HEAD1$" />
        <listheader label="$HEAD2$" />
    </listhead>
    <listitem>
        <listcell label="$END$" />
        <listcell label="" />
    </listitem>
</listbox>
```

---

### `tree` — ZK Tree scaffold (XML context)

Inserts a Tree with two Treecols and one Treeitem.

**Abbreviation:** `tree`
**Description:** `ZK tree`
**Context:** XML
**Variables:** `$COL1$`, `$COL2$`, `$END$`
**Expansion:**
```xml
<tree>
    <treecols>
        <treecol label="$COL1$" />
        <treecol label="$COL2$" />
    </treecols>
    <treechildren>
        <treeitem>
            <treerow>
                <treecell label="$END$" />
                <treecell label="" />
            </treerow>
        </treeitem>
    </treechildren>
</tree>
```

---

## Runtime Behavior

- **Loaded on every startup** from the plugin JAR classpath — no installation step, no state file written.
- **Merges with user templates**: if a user already has a "ZK" group, IntelliJ merges both. User-defined templates with the same abbreviation shadow the plugin defaults; new abbreviations from the plugin are added alongside.
- **User edits persist**: modifying a default template creates a user copy. A "Reset to default" option appears in Settings → Live Templates to revert.
- **Plugin uninstall**: plugin defaults disappear; any user-modified copies (stored in their IDE config) survive.
- **Plugin update**: users who never touched a template get the updated default; users who modified it keep their version.

## Implementation

| File | Role |
|------|------|
| `src/main/resources/liveTemplates/ZK.xml` | Template definitions (`<templateSet group="ZK">`) |
| `src/main/java/org/zkoss/zkidea/liveTemplates/ZkLiveTemplatesProvider.java` | `DefaultLiveTemplatesProvider` pointing to the XML |
| `src/main/resources/META-INF/plugin.xml` | Registers the provider via `defaultLiveTemplatesProvider` extension |

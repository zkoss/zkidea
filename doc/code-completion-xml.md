# ZKIdea XML Code Completion Architecture

## Overview

The ZKIdea plugin provides comprehensive code completion and validation for three types of XML files in the ZK Framework ecosystem:

- **ZUL files** (`.zul`) - ZK User Interface markup files
- **ZK configuration files** (`zk.xml`) - ZK application configuration
- **Language addon files** (`lang-addon.xml`) - ZK component library definitions

This document describes the architectural patterns, key components, and integration mechanisms that enable IntelliJ IDEA to provide schema-aware completion, validation, and navigation for these file types.

## Architectural Principles

### Dual-Layer Architecture

The code completion system operates on two complementary layers:

1. **Schema Provider Layer** - Explicit namespace-based completion using `StandardResourceProvider`
2. **DOM Descriptor Layer** - Default namespace completion for files without explicit `xmlns` declarations

This dual approach ensures that files work seamlessly whether developers include explicit namespace declarations or rely on filename-based detection.

### IntelliJ Integration Strategy

The architecture leverages IntelliJ Platform's XML infrastructure rather than creating custom completion mechanisms:

- **XSD Schema Integration** - Uses standard XML Schema files for element definitions
- **Standard Extension Points** - Implements IntelliJ's `StandardResourceProvider` and `XmlElementDescriptorProvider` interfaces
- **Built-in Caching** - Relies on IntelliJ's `CachedValue` system for performance optimization
- **Namespace Resolution** - Uses `XmlNSDescriptorImpl` for schema-based validation and completion

## Core Components

### Schema Provider Layer

#### StandardResourceProvider Implementation

Each XML file type has a dedicated schema provider that registers XSD resources:

**ZulSchemaProvider**
- Registers `zul.xsd` for namespace `http://www.zkoss.org/2005/zul`
- Handles multiple ZK-related namespaces (native, client, annotation, shadow)
- Maps HTTP and HTTPS schema URLs to local XSD resources

**ZkConfigSchemaProvider**
- Registers `zk.xsd` for namespace `http://www.zkoss.org/2005/zk/config`
- Provides schema support for ZK application configuration elements

**LangAddonSchemaProvider**
- Registers `lang-addon.xsd` for namespace `http://www.zkoss.org/2005/zk/lang-addon`
- Enables completion for component library and language addon definitions

#### Schema Provider Pattern

All schema providers follow a consistent implementation pattern:

```java
public class [Type]SchemaProvider implements StandardResourceProvider {
    public static final String SCHEMA_URL = "http://www.zkoss.org/...";
    public static final String PROJECT_SCHEMA_URL = "https://www.zkoss.org/.../schema.xsd";
    public static final String PROJECT_SCHEMA_PATH = "org/zkoss/zkidea/lang/resources/schema.xsd";

    public void registerResources(ResourceRegistrar registrar) {
        var path = "/" + PROJECT_SCHEMA_PATH;
        registrar.addStdResource("schema-name", path, getClass());
        registrar.addStdResource(SCHEMA_URL, path, getClass());
        registrar.addStdResource(PROJECT_SCHEMA_URL, path, getClass());
    }
}
```

### DOM Descriptor Layer

#### File Type Detection

**ZulDomUtil** serves as the central utility for identifying ZK-related XML files:

- `isZKFile(PsiFile)` - Detects all ZK-related XML files
- `isZkConfigFile(PsiFile)` - Identifies `zk.xml` files by filename
- `isLangAddonFile(PsiFile)` - Identifies `lang-addon.xml` files by filename

The detection logic uses filename patterns rather than content analysis for performance.

#### Element Descriptor Management

**ZkDomElementDescriptorProvider** implements `XmlElementDescriptorProvider`:
- Entry point for IntelliJ's XML completion system
- Delegates to `ZkDomElementDescriptorHolder` for actual descriptor resolution
- Registered in `plugin.xml` as `xml.elementDescriptorProvider`

**ZkDomElementDescriptorHolder** manages schema descriptors per project:
- Project-level service that caches `XmlNSDescriptorImpl` instances
- Maps file types to appropriate XSD schemas
- Uses `CachedValue` with `PsiModificationTracker` for cache invalidation
- Provides default namespace context for schema-based completion

#### FileKind Enumeration

The system uses an internal `FileKind` enum to map file types to schema URLs:

```java
enum FileKind {
    ZUL_FILE { getSchemaUrl() → ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL },
    ZK_CONFIG_FILE { getSchemaUrl() → ZkConfigSchemaProvider.ZK_CONFIG_PROJECT_SCHEMA_URL },
    LANG_ADDON_FILE { getSchemaUrl() → LangAddonSchemaProvider.LANG_ADDON_PROJECT_SCHEMA_URL }
}
```

## Integration Flow

### Schema-Based Completion (With Namespace)

1. Developer types `<` in an XML file with explicit namespace (`xmlns="http://www.zkoss.org/..."`)
2. IntelliJ Platform's XML parser identifies the namespace
3. `ExternalResourceManager` resolves namespace to local XSD via registered `StandardResourceProvider`
4. IntelliJ provides completion based on XSD schema elements and attributes
5. Real-time validation occurs against the schema

### Default Namespace Completion (Without Namespace)

1. Developer types `<` in a ZK XML file without explicit namespace
2. `ZkDomElementDescriptorProvider.getDescriptor()` is called by IntelliJ
3. `ZulDomUtil` identifies file type by filename pattern
4. `ZkDomElementDescriptorHolder` maps file type to appropriate schema URL
5. Cached `XmlNSDescriptorImpl` provides schema-based completion with default namespace
6. Elements complete as if explicit namespace were present

## Activating Code Completion

For the plugin to provide code completion and validation for `zk.xml` and `lang-addon.xml` files, one of the following two conditions must be met:

1.  **Filename Convention**: The file must be named exactly `zk.xml` or `lang-addon.xml`. The plugin's **DOM Descriptor Layer** automatically detects these filenames and applies the correct schema, even if no XML namespace is declared.

2.  **Explicit XML Namespace**: If you use a custom filename (e.g., `my-zk-config.xml`), you **must** declare the correct XML namespace in the root element. The plugin's **Schema Provider Layer** uses this namespace to associate the file with the proper XSD.

    -   For **ZK configuration files**, the required namespace is:
        ```xml
        <zk xmlns="http://www.zkoss.org/2005/zk/config">
            ...
        </zk>
        ```
    -   For **language addon files**, the required namespace is:
        ```xml
        <language-addon xmlns="http://www.zkoss.org/2005/zk/lang-addon">
            ...
        </language-addon>
        ```

This dual-system ensures flexibility while providing a reliable mechanism for activating the plugin's features.

## Schema Architecture

### XSD Schema Structure

Each XML file type is backed by a comprehensive XSD schema:

**zul.xsd** - ZK UI components
- Root `<zk>` elements and ZK component hierarchy
- Event handlers, data binding attributes
- Layout and styling properties

**zk.xsd** - ZK configuration
- Application-level configuration sections
- Client, desktop, session, and system configuration elements
- Library properties and preference settings

**lang-addon.xsd** - Language addons
- Component definitions and inheritance
- JavaScript and CSS resource declarations
- Custom properties and annotation support

### Schema Location Resolution

Schema resolution follows IntelliJ's standard pattern:

1. **Resource Registration** - Schema providers register local XSD paths
2. **URL Mapping** - External URLs (HTTP/HTTPS) map to local resources
3. **Classpath Loading** - XSD files loaded from plugin JAR resources
4. **Namespace Resolution** - `ExternalResourceManager` handles URL-to-file mapping

## Performance Considerations

### Caching Strategy

- **Project-Level Caching** - One descriptor cache per IntelliJ project
- **Modification Tracking** - Cache invalidation tied to `PsiModificationTracker.MODIFICATION_COUNT`
- **Lazy Loading** - Descriptors created on-demand, not at plugin startup
- **Thread Safety** - Synchronized descriptor creation prevents race conditions

### Resource Management

- **Local Schema Storage** - XSD files bundled in plugin resources
- **Minimal Memory Footprint** - Reuses IntelliJ's existing XML infrastructure
- **No Network Dependency** - All schemas resolved locally

## Plugin Configuration

### Extension Point Registration

The system registers through standard IntelliJ extension points:

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Schema providers for namespace-based completion -->
    <standardResourceProvider implementation="org.zkoss.zkidea.lang.ZulSchemaProvider"/>
    <standardResourceProvider implementation="org.zkoss.zkidea.lang.ZkConfigSchemaProvider"/>
    <standardResourceProvider implementation="org.zkoss.zkidea.lang.LangAddonSchemaProvider"/>

    <!-- File type recognition -->
    <fileType name="XML" extensions="zul"/>
    <fileType name="XML" patterns="zk.xml"/>
    <fileType name="XML" patterns="lang-addon.xml"/>

    <!-- DOM descriptor provider for default namespace support -->
    <xml.elementDescriptorProvider implementation="org.zkoss.zkidea.dom.ZkDomElementDescriptorProvider"/>
    <projectService serviceImplementation="org.zkoss.zkidea.dom.ZkDomElementDescriptorHolder"/>
</extensions>
```

## Extensibility Patterns

### Adding New XML File Types

To add support for additional ZK XML file types:

1. **Create Schema Provider** - Implement `StandardResourceProvider` following existing patterns
2. **Add XSD Schema** - Place schema file in `resources/org/zkoss/zkidea/lang/resources/`
3. **Update File Detection** - Add detection method to `ZulDomUtil`
4. **Extend FileKind Enum** - Add new enum value in `ZkDomElementDescriptorHolder`
5. **Register Extensions** - Update `plugin.xml` with provider and file type registrations

### Schema Evolution

- **Backward Compatibility** - New XSD versions should maintain element compatibility
- **Automatic Updates** - Schema changes automatically flow through to completion
- **Version Management** - Consider schema versioning for breaking changes

## Development Guidelines

### Code Organization

- **Package Structure** - Schema providers in `.lang` package, DOM descriptors in `.dom` package
- **Naming Conventions** - `[Type]SchemaProvider` and clear, descriptive class names
- **Documentation** - Comprehensive JavaDoc explaining integration points

### Testing Considerations

- **Test Files** - Provide sample XML files for manual testing
- **Namespace Variants** - Test both explicit and implicit namespace scenarios
- **Error Conditions** - Verify behavior with malformed or incomplete XML
- **Performance** - Test caching behavior under various modification scenarios

### Maintenance Notes

- **IntelliJ Version Compatibility** - Monitor IntelliJ Platform API changes
- **Schema Updates** - Coordinate XSD updates with ZK Framework releases
- **Error Handling** - Graceful degradation when schemas unavailable
- **Logging** - Appropriate logging for debugging completion issues

This architecture provides a robust, maintainable foundation for XML code completion in ZK Framework development environments while following IntelliJ Platform best practices and patterns.
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ZKIdea is an IntelliJ IDEA plugin for the ZK Framework, providing enhanced development experience for ZUL files and ZK applications. The plugin extends IntelliJ's XML support to provide ZUL-specific features like code completion, syntax checking, and MVVM annotation support.

## Build System & Commands

This project uses Gradle with the IntelliJ Plugin development plugin.

### Common Commands

- `./gradlew build` - Build the plugin
- `./gradlew test` - Run tests
- `./gradlew runIde` - Launch IntelliJ with the plugin installed for testing
- `./gradlew buildPlugin` - Build distributable plugin ZIP
- `./gradlew publishPlugin` - Publish to JetBrains marketplace
- `./gradlew verifyPlugin` - Validate plugin structure and compatibility

### Development Setup

- Java 17 compatibility (source and target)
- IntelliJ Platform version 2023.3
- Supports IntelliJ versions 233.2 to 251.*
- Dependencies: Maven plugin, Java plugin, JSoup library

## Architecture

### Core Components

**Language Support (`lang` package)**
- `ZulLanguage` - Defines ZUL as XML-based language
- `ZulFileType` - File type registration for .zul files
- `ZulSchemaProvider` - Provides XSD schema for validation
- `ZulIconProvider` - Custom icons for ZUL files

**Code Completion (`completion` package)**
- `MVVMAnnotationCompletionProvider` - Auto-completion for ZK MVVM annotations (@init, @load, @bind, @save, @command, etc.)

**DOM Support (`dom` package)**
- `ZulDomElementDescriptorProvider` - XML element descriptors for ZUL elements
- `ZulDomElementDescriptorHolder` - Project-level service holding element descriptors
- `ZulDomUtil` - Utilities for ZK file detection and ViewModel analysis

**Editor Actions (`editorActions` package)**
- `ZulTypedHandler` - Custom typing behavior in ZUL files
- `WebBrowserUrlProvider` - Browser URL generation for ZUL files
- `MavenRunnerPatcher` - Maven integration patches

**Maven Integration (`maven` package)**
- `ZKMavenArchetypesProvider` - ZK Maven archetypes for project creation

**Project Management (`project` package)**
- `ZKProjectsManager` - Post-startup activity for project initialization
- `ZKPathManager` - Path management utilities

**News System (`newsNotification` package)**
- `ZKNews` - Fetches and displays ZK framework news notifications

### Plugin Configuration

The plugin is configured via `plugin.xml` which defines:
- Extension points for XML completion, DOM providers, file types
- Dependencies on Maven and Java plugins  
- Post-startup activities for project management and news
- Supported IntelliJ platform versions

### Testing

Tests use JUnit 5 with Mockito for mocking. The test structure mirrors the main source structure. Key test patterns:
- Mock IntelliJ platform components (Project, Application, etc.)
- Use reflection to test private methods when needed
- Test network operations with mocked connections

### Key Files

- `src/main/resources/org/zkoss/zkidea/lang/resources/zul.xsd` - ZUL schema definition
- `src/main/resources/org/zkoss/zkidea/lang/resources/archetype-catalog.xml` - Maven archetypes catalog
- `src/main/resources/META-INF/plugin.xml` - Plugin configuration

## Development Notes

- ZUL files are treated as XML with custom extensions
- The plugin heavily integrates with IntelliJ's XML infrastructure
- MVVM annotations are a key differentiator from standard XML editing
- Maven archetype support enables quick ZK project creation
- Plugin supports news notifications fetched from ZK website
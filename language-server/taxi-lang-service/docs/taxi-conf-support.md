# Taxi.conf Language Server Support

This document describes the language server support for `taxi.conf` configuration files.

## Features

### 1. Auto-completion (Ctrl+Space)

The language server provides intelligent auto-completion suggestions when editing `taxi.conf` files:

- **Top-level properties**: All properties from `TaxiPackageProject` are suggested with appropriate snippets
- **Nested object properties**: Properties for complex types like `Repository`, `Credentials`, `PluginSettings`
- **Map keys**: Known keys for maps like `linter` (linter rule names) and `plugins` (plugin names)
- **Enum values**: Suggestions for enum types like `Severity` (INFO, WARNING, ERROR)
- **Smart snippets**: Complex objects are inserted with placeholder templates

Example completions:
```hocon
# Type "name" and press Ctrl+Space
name: "${1}"

# Type "plugins" and press Ctrl+Space
plugins: {
  ${1}
}

# Inside linter section, known rules are suggested
linter: {
  no-duplicate-types-on-models: {
    enabled: true
    severity: ${1|INFO,WARNING,ERROR|}
  }
}
```

### 2. Real-time Diagnostics

The language server validates `taxi.conf` files as you type and provides immediate feedback:

#### Error Diagnostics
- **Parse errors**: HOCON syntax errors are highlighted immediately
- **Missing required fields**: `name` and `version` fields are required
- **Type mismatches**: Invalid value types are reported

#### Warning Diagnostics
- **Invalid version format**: Version should match `X.Y.Z` or `X.Y.Z-SNAPSHOT` pattern
- **Invalid project name**: Project name should be in `namespace/project-name` format

Example diagnostics:
```hocon
# ERROR: Missing required fields
sourceRoot: src/

# WARNING: Invalid version format
name: taxi/sample
version: abc

# WARNING: Invalid project name format
name: my-project-without-namespace
version: 1.0.0

# Valid configuration (no diagnostics)
name: org.example/my-project
version: 1.0.0
```

### 3. Reflection-based Schema

The implementation uses Kotlin reflection to extract the configuration schema from the `TaxiPackageProject` data class. This means:

- **Always up-to-date**: As the `TaxiPackageProject` class evolves, the language server support automatically stays current
- **Type-safe**: All property types, nullability, and default values are extracted from the actual class definition
- **No duplication**: No need to maintain a separate schema definition

## Implementation Details

### Architecture

The taxi.conf support is implemented in the following components:

1. **TaxiConfSchemaProvider**: Uses reflection to extract schema information from `TaxiPackageProject`
2. **TaxiConfParser**: Parses and validates taxi.conf files using TypeSafe Config and config4k
3. **TaxiConfCompletionProvider**: Provides context-aware completions based on the schema
4. **TaxiConfService**: Coordinates the above components and manages caching

### Integration Points

The taxi.conf support is integrated into `TaxiTextDocumentService`:

- **didOpen**: Parses the file and publishes initial diagnostics
- **didChange**: Re-parses on every change and updates diagnostics in real-time
- **didSave**: Triggers full workspace reload (existing behavior)
- **didClose**: Cleans up caches
- **completion**: Returns taxi.conf-specific completions

### Configuration Classes

The following classes are supported for nested object completions:

- `Repository`: Package repository configuration
- `Credentials`: Authentication credentials
- `PluginSettings`: Plugin resolution settings
- `TaxiConfLinterRuleConfig`: Linter rule configuration

### Known Linter Rules

The completion provider suggests these common linter rules:
- `no-duplicate-types-on-models`
- `no-primitive-types-on-models`
- `unused-import`
- `type-naming-convention`

### Known Plugins

The completion provider suggests these common plugins:
- `taxi/kotlin`
- `taxi/open-api`
- `taxi/swagger`
- `taxi/protobuf`

## Testing

Tests are located in `TaxiConfServiceTest.kt` and cover:

- Top-level completions
- Diagnostics for various error cases
- Cache management
- Complex configuration validation

## Future Enhancements

Potential improvements for the future:

1. **Go-to-definition**: Jump to plugin definitions or dependency sources
2. **Hover documentation**: Show inline documentation for properties
3. **Code actions**: Quick fixes for common errors
4. **Dynamic linter rules**: Load linter rule names from the compiler
5. **Dynamic plugin discovery**: Load plugin names from repositories
6. **Better position tracking**: More accurate error position reporting in HOCON
7. **Reference validation**: Validate that referenced dependencies exist
8. **Version validation**: Check if specified versions are available in repositories

## Dependencies

The implementation relies on:

- **TypeSafe Config**: For HOCON parsing
- **config4k**: For deserializing Config to Kotlin data classes
- **Kotlin Reflection**: For schema extraction
- **Eclipse LSP4J**: For LSP protocol implementation

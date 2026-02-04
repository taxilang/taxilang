# Taxi.conf Language Server Support

This document describes the language server support for `taxi.conf` configuration files, which is built on top of the [generic HOCON support framework](hocon-support.md).

## Features

### 1. Auto-completion (Ctrl+Space)

The language server provides intelligent auto-completion suggestions when editing `taxi.conf` files:

- **Top-level properties**: All properties from `TaxiPackageProject` are suggested
- **Nested object properties**: Properties for `Repository`, `Credentials`, `PluginSettings`, etc.
- **Known linter rules**: Suggests common linter rule names with configuration snippets
- **Known plugins**: Suggests common plugin names (taxi/kotlin, taxi/open-api, etc.)
- **Enum values**: Suggestions for `Severity` (INFO, WARNING, ERROR)
- **Custom documentation**: Taxi-specific help text for each property

Example completions:
```hocon
# Type "name" and press Ctrl+Space
name: "namespace/project-name"

# Type "plugins" and press Ctrl+Space - suggests known plugins
plugins: {
  "taxi/kotlin": {
    ${1}
  }
}

# Inside linter section - suggests known rules
linter: {
  no-duplicate-types-on-models: {
    enabled: true
    severity: ${1|INFO,WARNING,ERROR|}
  }
}
```

### 2. Real-time Diagnostics

The language server validates `taxi.conf` files as you type:

#### Error Diagnostics
- **Parse errors**: HOCON syntax errors
- **Missing required fields**: `name` and `version` are required
- **Type mismatches**: Reported by config4k during extraction

#### Warning Diagnostics
- **Invalid version format**: Version should match `X.Y.Z` or `X.Y.Z-SNAPSHOT`
- **Invalid project name**: Project name should be `namespace/project-name`

Example diagnostics:
```hocon
# ERROR: Missing required fields
sourceRoot: src/

# WARNING: Invalid version format
name: taxi/sample
version: abc

# WARNING: Invalid project name
name: my-project-without-namespace
version: 1.0.0

# Valid (no diagnostics)
name: org.example/my-project
version: 1.0.0
```

### 3. Reflection-based Schema

The implementation uses Kotlin reflection to extract the configuration schema from the `TaxiPackageProject` data class. This means:

- **Always up-to-date**: As the `TaxiPackageProject` class evolves, the language server support automatically stays current
- **Type-safe**: All property types, nullability, and default values are extracted from the actual class definition
- **No duplication**: No need to maintain a separate schema definition

## Implementation

The taxi.conf support is implemented using three specialized components on top of the generic HOCON framework:

### 1. TaxiConfValidator
Validates taxi-specific rules:
- Required fields (name, version)
- Version format (semantic versioning)
- Project name format (namespace/project)

### 2. TaxiConfCompletionCustomizer
Provides taxi-specific completions:
- **Linter rules**: `no-duplicate-types-on-models`, `no-primitive-types-on-models`, `unused-import`, `type-naming-convention`
- **Plugin names**: `taxi/kotlin`, `taxi/open-api`, `taxi/swagger`, `taxi/protobuf`
- **Custom documentation**: Detailed help text for taxi properties

### 3. TaxiConfService
Coordinates the HOCON framework with taxi-specific customizations:
```kotlin
class TaxiConfService {
   private val hoconService = hoconService<TaxiPackageProject>()
      .ignoreFields("identifier", "dependencyPackages", "packageRootPath", "sourceRootPath")
      .withValidator(TaxiConfValidator())
      .withCustomizer(TaxiConfCompletionCustomizer())
      .withSourceName("taxi.conf")
      .build()
}
```

## Ignored Fields

The following fields in `TaxiPackageProject` are computed/derived and are not shown in completions:
- `identifier` - Computed from name
- `dependencyPackages` - Resolved at runtime
- `packageRootPath` - Derived from file location
- `sourceRootPath` - Derived from sourceRoot + file location
- `taxiConfFile` - Set during loading

## Integration

The taxi.conf support is integrated into `TaxiTextDocumentService`:

- **didOpen**: Parses the file and publishes initial diagnostics
- **didChange**: Re-parses on every change and updates diagnostics in real-time
- **didSave**: Triggers full workspace reload (existing behavior)
- **completion**: Returns taxi.conf-specific completions

## Testing

Tests are located in `TaxiConfServiceTest.kt` and cover:
- Top-level completions
- Ignored fields are not suggested
- Custom documentation
- Diagnostics for various error cases
- Parse error handling
- Complex configuration validation
- Cache management

## Customization Examples

### Adding New Linter Rules

To add support for new linter rules, update `TaxiConfCompletionCustomizer`:

```kotlin
private val KNOWN_LINTER_RULES = listOf(
   "no-duplicate-types-on-models",
   "no-primitive-types-on-models",
   "unused-import",
   "type-naming-convention",
   "new-rule-name"  // Add here
)
```

### Adding New Plugins

To add support for new plugins:

```kotlin
private val KNOWN_PLUGINS = listOf(
   "taxi/kotlin",
   "taxi/open-api",
   "taxi/swagger",
   "taxi/protobuf",
   "taxi/new-plugin"  // Add here
)
```

### Dynamic Completions

For more advanced scenarios, completions can be loaded dynamically:

```kotlin
override fun getMapKeyCompletions(
   mapPropertyPath: String,
   prefix: String,
   schema: HoconSchemaProvider.PropertySchema
): List<CompletionItem>? {
   if (mapPropertyPath == "plugins") {
      // Load from plugin registry
      return pluginRegistry.getAvailablePlugins().map { plugin ->
         CompletionItem(plugin.name).apply {
            detail = plugin.version
            documentation = plugin.description
         }
      }
   }
   return null
}
```

## Example Configuration

See [complete-example.conf](../src/test/resources/taxi-conf-examples/complete-example.conf) for a comprehensive example demonstrating all available configuration options.

## Future Enhancements

Potential improvements:

1. **Dynamic linter rules**: Load rule names from the compiler at runtime
2. **Dynamic plugin discovery**: Query plugin repositories for available plugins
3. **Hover documentation**: Show inline docs when hovering over properties
4. **Go-to-definition**: Jump to plugin or dependency sources
5. **Code actions**: Quick fixes for common configuration errors
6. **Reference validation**: Validate that dependencies exist in repositories
7. **Version suggestions**: Suggest available versions for dependencies

## See Also

- [Generic HOCON Support Framework](hocon-support.md) - Documentation for the underlying framework
- [TaxiPackageProject](../../../../core-types/src/main/java/lang/taxi/packages/TaxiPackageProject.kt) - The data class backing taxi.conf

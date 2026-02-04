# HOCON Language Server Support Framework

This document describes the generic HOCON language server support framework that provides intelligent editing features for any HOCON configuration file backed by a Kotlin data class.

## Overview

The HOCON support framework provides:

1. **Reflection-based schema extraction** - Automatically derives configuration schema from Kotlin data classes
2. **Auto-completion** - Context-aware completion suggestions
3. **Real-time validation** - Parse and validate configurations as you type
4. **Extensibility** - Plugin-based customization system for domain-specific features

## Architecture

### Core Components

#### 1. HoconSchemaProvider<T>
Extracts configuration schema from any Kotlin data class using reflection.

**Features:**
- Supports primitives, collections (List, Map), nested objects, and enums
- Tracks nullability and optional fields
- Caches schema information for performance
- Provides type descriptions for documentation

**Usage:**
```kotlin
val schemaProvider = HoconSchemaProvider(
   klass = MyConfig::class,
   ignoredFields = setOf("computedField", "derivedProperty")
)

val schema = schemaProvider.getSchema()
val propertySchema = schemaProvider.getPropertySchema("nested.field.path")
```

#### 2. HoconParser<T>
Parses and validates HOCON files against a Kotlin type.

**Features:**
- Uses TypeSafe Config for HOCON parsing
- Uses config4k for type extraction
- Generates LSP diagnostics from parse errors
- Supports custom validation via `HoconValidator<T>` interface

**Usage:**
```kotlin
val parser = HoconParser(
   klass = MyConfig::class,
   validator = MyConfigValidator()
)

val result = parser.parse(content, sourceName = "myconfig.conf")
// result contains: parsed value, diagnostics, and raw Config
```

#### 3. HoconCompletionProvider<T>
Provides intelligent completions based on schema and context.

**Features:**
- Context-aware completions (top-level, nested objects, map keys, values)
- Smart snippets for complex types
- Integration with customizers for domain-specific suggestions
- Automatic documentation generation

**Usage:**
```kotlin
val completionProvider = HoconCompletionProvider(
   schemaProvider = schemaProvider,
   customizer = myCustomizer
)

val completions = completionProvider.getCompletions(content, position)
```

#### 4. HoconCompletionCustomizer
Extension point for domain-specific completion logic.

**Interface methods:**
- `getMapKeyCompletions()` - Suggest known map keys
- `getValueCompletions()` - Suggest valid values
- `getObjectPropertyCompletions()` - Customize property suggestions
- `getTopLevelCompletions()` - Customize root-level suggestions
- `getPropertySnippet()` - Custom snippet templates
- `getPropertyDocumentation()` - Custom documentation

#### 5. HoconValidator<T>
Interface for custom validation logic beyond type checking.

**Usage:**
```kotlin
class MyConfigValidator : HoconValidator<MyConfig> {
   override fun validate(value: MyConfig, diagnostics: MutableList<Diagnostic>) {
      if (value.port < 1024) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Port must be >= 1024",
            DiagnosticSeverity.Warning,
            "myconfig.conf"
         ))
      }
   }
}
```

#### 6. HoconService<T>
Coordinates all components and manages caching.

**Features:**
- Combines schema, parsing, completion, and validation
- Caches parse results per document URI
- Builder API for configuration

**Usage:**
```kotlin
val service = hoconService<MyConfig>()
   .ignoreFields("computedField", "derivedProperty")
   .withValidator(MyConfigValidator())
   .withCustomizer(MyConfigCustomizer())
   .withSourceName("myconfig.conf")
   .build()

val completions = service.getCompletions(uri, content, params)
val diagnostics = service.getDiagnostics(uri, content)
```

## Example: taxi.conf Support

The taxi.conf support demonstrates how to use the framework:

### 1. Define the validator
```kotlin
class TaxiConfValidator : HoconValidator<TaxiPackageProject> {
   override fun validate(value: TaxiPackageProject, diagnostics: MutableList<Diagnostic>) {
      // Validate version format
      if (!isValidVersion(value.version)) {
         diagnostics.add(/* version format error */)
      }

      // Validate project name format
      if (!isValidProjectName(value.name)) {
         diagnostics.add(/* name format error */)
      }
   }
}
```

### 2. Define the completion customizer
```kotlin
class TaxiConfCompletionCustomizer : HoconCompletionCustomizer {
   override fun getMapKeyCompletions(
      mapPropertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? {
      return when {
         mapPropertyPath == "linter" -> getLinterRuleCompletions(prefix)
         mapPropertyPath == "plugins" -> getPluginNameCompletions(prefix)
         else -> null
      }
   }

   override fun getPropertyDocumentation(
      property: HoconSchemaProvider.PropertySchema
   ): String? {
      return when (property.name) {
         "name" -> "Project identifier in format: namespace/project-name"
         "version" -> "Project version following semantic versioning"
         else -> null
      }
   }
}
```

### 3. Create the service
```kotlin
class TaxiConfService {
   private val hoconService = hoconService<TaxiPackageProject>()
      .ignoreFields("identifier", "dependencyPackages", "packageRootPath", "sourceRootPath")
      .withValidator(TaxiConfValidator())
      .withCustomizer(TaxiConfCompletionCustomizer())
      .withSourceName("taxi.conf")
      .build()

   fun getCompletions(uri: String, content: String, params: CompletionParams) =
      hoconService.getCompletions(uri, content, params)

   fun getDiagnostics(uri: String, content: String) =
      hoconService.getDiagnostics(uri, content)
}
```

## Creating Support for Other HOCON Files

To add support for another HOCON file type:

### Step 1: Define your configuration data class
```kotlin
data class OrbitalConfig(
   val serviceName: String,
   val port: Int = 8080,
   val endpoints: Map<String, EndpointConfig> = emptyMap(),
   val features: List<String> = emptyList()
)

data class EndpointConfig(
   val path: String,
   val method: String = "GET",
   val timeout: Int = 30
)
```

### Step 2: Create a validator (optional)
```kotlin
class OrbitalConfigValidator : HoconValidator<OrbitalConfig> {
   override fun validate(value: OrbitalConfig, diagnostics: MutableList<Diagnostic>) {
      if (value.port !in 1024..65535) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Port must be between 1024 and 65535",
            DiagnosticSeverity.Warning,
            "orbital.conf"
         ))
      }
   }
}
```

### Step 3: Create a completion customizer (optional)
```kotlin
class OrbitalConfigCustomizer : HoconCompletionCustomizer {
   override fun getMapKeyCompletions(
      mapPropertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? {
      if (mapPropertyPath == "endpoints") {
         // Suggest known endpoint names
         return listOf("api", "health", "metrics").map { name ->
            CompletionItem(name).apply {
               kind = CompletionItemKind.Property
               insertText = "\"$name\": {\n  path: \"/\$1\"\n}"
               insertTextFormat = InsertTextFormat.Snippet
            }
         }
      }
      return null
   }

   override fun getValueCompletions(
      propertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? {
      if (propertyPath.endsWith(".method")) {
         // Suggest HTTP methods
         return listOf("GET", "POST", "PUT", "DELETE", "PATCH").map { method ->
            CompletionItem(method).apply {
               kind = CompletionItemKind.EnumMember
            }
         }
      }
      return null
   }
}
```

### Step 4: Create the service
```kotlin
class OrbitalConfigService {
   private val hoconService = hoconService<OrbitalConfig>()
      .withValidator(OrbitalConfigValidator())
      .withCustomizer(OrbitalConfigCustomizer())
      .withSourceName("orbital.conf")
      .build()

   fun getCompletions(uri: String, content: String, params: CompletionParams) =
      hoconService.getCompletions(uri, content, params)

   fun getDiagnostics(uri: String, content: String) =
      hoconService.getDiagnostics(uri, content)
}
```

### Step 5: Integrate with TaxiTextDocumentService
```kotlin
// In TaxiTextDocumentService.kt
private val orbitalConfigService = services.orbitalConfigService

override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
   return when {
      position.textDocument.uri.endsWith("taxi.conf") -> {
         val content = taxiConfContents[position.textDocument.uri] ?: ""
         val completionList = taxiConfService.getCompletions(position.textDocument.uri, content, position)
         CompletableFuture.completedFuture(Either.forRight(completionList))
      }
      position.textDocument.uri.endsWith("orbital.conf") -> {
         val content = orbitalConfContents[position.textDocument.uri] ?: ""
         val completionList = orbitalConfigService.getCompletions(position.textDocument.uri, content, position)
         CompletableFuture.completedFuture(Either.forRight(completionList))
      }
      else -> {
         // Regular .taxi file completion
         // ...
      }
   }
}
```

## Benefits of This Architecture

1. **Consistency**: Diagnostics match actual parsing behavior since we use the same TypeSafe Config + config4k stack everywhere
2. **Maintainability**: Schema stays in sync with Kotlin types via reflection
3. **Extensibility**: Easy to add new HOCON file types or customize existing ones
4. **Reusability**: Core logic is shared across all HOCON file types
5. **Type Safety**: Strong typing throughout the implementation

## Advanced Features

### Composite Customizers
Combine multiple customizers:
```kotlin
val service = hoconService<MyConfig>()
   .withCustomizer(BaseCustomizer())
   .withCustomizer(AdvancedCustomizer())
   .withCustomizer(ExperimentalCustomizer())
   .build()
```

### Dynamic Completions
Load completion suggestions from external sources:
```kotlin
class DynamicPluginCustomizer(
   private val pluginRegistry: PluginRegistry
) : HoconCompletionCustomizer {
   override fun getMapKeyCompletions(
      mapPropertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? {
      if (mapPropertyPath == "plugins") {
         // Fetch available plugins from registry
         return pluginRegistry.getAvailablePlugins()
            .map { plugin ->
               CompletionItem(plugin.name).apply {
                  detail = plugin.version
                  documentation = plugin.description
               }
            }
      }
      return null
   }
}
```

## Performance Considerations

1. **Schema Caching**: Reflection-based schema extraction is cached per class
2. **Parse Result Caching**: Parse results are cached per document URI
3. **Lazy Evaluation**: Schema is only extracted when first needed
4. **Efficient Context Analysis**: Context detection uses simple heuristics to avoid full parsing

## Future Enhancements

Potential improvements:

1. **Position-Aware Diagnostics**: Extract line/column information from HOCON parse errors
2. **Go-to-Definition**: Navigate to type definitions or referenced resources
3. **Hover Information**: Show detailed documentation on hover
4. **Code Actions**: Quick fixes for common errors
5. **Reference Validation**: Validate cross-references within configuration
6. **Schema Evolution**: Handle schema versioning and migrations
7. **Performance Profiling**: Optimize for large configuration files

## Dependencies

- **TypeSafe Config**: HOCON parsing
- **config4k**: Kotlin data class extraction
- **Kotlin Reflection**: Schema extraction
- **Eclipse LSP4J**: LSP protocol implementation

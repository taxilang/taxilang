# Generic HOCON Language Server Support

This package provides a generic, reflection-based language server framework for HOCON configuration files backed by Kotlin data classes.

## Quick Start

Create language server support for any HOCON file in 3 steps:

### 1. Define your configuration data class
```kotlin
data class MyConfig(
   val serviceName: String,
   val port: Int = 8080,
   val features: Map<String, FeatureConfig> = emptyMap()
)

data class FeatureConfig(
   val enabled: Boolean = true,
   val timeout: Int = 30
)
```

### 2. Create the service
```kotlin
class MyConfigService {
   private val hoconService = hoconService<MyConfig>()
      .withSourceName("myconfig.conf")
      .build()

   fun getCompletions(uri: String, content: String, params: CompletionParams) =
      hoconService.getCompletions(uri, content, params)

   fun getDiagnostics(uri: String, content: String) =
      hoconService.getDiagnostics(uri, content)
}
```

### 3. Integrate with TaxiTextDocumentService
```kotlin
// In TaxiTextDocumentService
override fun completion(position: CompletionParams): CompletableFuture<...> {
   when {
      position.textDocument.uri.endsWith("myconfig.conf") -> {
         val content = configContents[position.textDocument.uri] ?: ""
         return CompletableFuture.completedFuture(
            Either.forRight(myConfigService.getCompletions(...))
         )
      }
      // ... other file types
   }
}
```

That's it! You now have:
- ✅ Auto-completion based on your data class schema
- ✅ Real-time validation and diagnostics
- ✅ Type checking via config4k
- ✅ Automatic schema updates as your data class evolves

## Advanced Features

### Custom Validation
```kotlin
class MyConfigValidator : HoconValidator<MyConfig> {
   override fun validate(value: MyConfig, diagnostics: MutableList<Diagnostic>) {
      if (value.port < 1024) {
         diagnostics.add(Diagnostic(..., "Port must be >= 1024", ...))
      }
   }
}

val service = hoconService<MyConfig>()
   .withValidator(MyConfigValidator())
   .build()
```

### Custom Completions
```kotlin
class MyConfigCustomizer : HoconCompletionCustomizer {
   override fun getMapKeyCompletions(
      mapPropertyPath: String,
      prefix: String,
      schema: HoconSchemaProvider.PropertySchema
   ): List<CompletionItem>? {
      if (mapPropertyPath == "features") {
         return listOf("auth", "logging", "metrics").map { feature ->
            CompletionItem(feature).apply {
               insertText = "\"$feature\": {\n  enabled: true\n}"
               insertTextFormat = InsertTextFormat.Snippet
            }
         }
      }
      return null
   }
}

val service = hoconService<MyConfig>()
   .withCustomizer(MyConfigCustomizer())
   .build()
```

### Ignore Computed Fields
```kotlin
data class MyConfig(
   val name: String,
   val version: String,
   val identifier: String  // Computed from name + version
)

val service = hoconService<MyConfig>()
   .ignoreFields("identifier")  // Won't show in completions
   .build()
```

## Architecture

```
HoconService<T>
    ├── HoconSchemaProvider<T>    (reflection-based schema extraction)
    ├── HoconParser<T>             (TypeSafe Config + config4k parsing)
    ├── HoconCompletionProvider<T> (context-aware completions)
    ├── HoconValidator<T>          (optional custom validation)
    └── HoconCompletionCustomizer  (optional completion extensions)
```

## Core Components

| Component | Purpose | Key Features |
|-----------|---------|--------------|
| **HoconSchemaProvider** | Extract schema from Kotlin types | Reflection-based, supports all Kotlin types, cached |
| **HoconParser** | Parse and validate HOCON | Uses TypeSafe Config + config4k, LSP diagnostics |
| **HoconCompletionProvider** | Generate completions | Context-aware, smart snippets, extensible |
| **HoconCompletionCustomizer** | Extend completions | Domain-specific suggestions, custom documentation |
| **HoconValidator** | Custom validation | Add business rules beyond type checking |
| **HoconService** | Coordinate everything | Manages caching, provides unified API |

## Extension Points

The framework provides several extension points via the `HoconCompletionCustomizer` interface:

- **`getMapKeyCompletions`**: Suggest known map keys (e.g., feature names, plugin names)
- **`getValueCompletions`**: Suggest valid values (e.g., enum values, constants)
- **`getObjectPropertyCompletions`**: Filter or enhance property suggestions
- **`getTopLevelCompletions`**: Customize root-level suggestions
- **`getPropertySnippet`**: Custom snippet templates
- **`getPropertyDocumentation`**: Custom inline documentation

## Examples

See:
- **taxi.conf**: `lang.taxi.lsp.taxiconf.*` - Full example with validation and customizations
- **Documentation**: `docs/hocon-support.md` - Comprehensive guide with examples
- **Tests**: `TaxiConfServiceTest.kt` - Testing patterns

## Benefits

1. **No Schema Duplication**: Schema is extracted from your Kotlin types via reflection
2. **Consistent Validation**: Same parsing behavior in VSCode, CLI, and runtime
3. **Easy to Extend**: Add new HOCON file types in minutes
4. **Type Safe**: Strong typing throughout
5. **Maintainable**: Schema automatically stays in sync with code changes

## Why This Matters for Orbital

Orbital uses many HOCON configuration files across different services. With this framework:

- ✅ Add language server support for any config file by just pointing to its data class
- ✅ Ensure config validation in IDE matches runtime validation
- ✅ Get auto-completion for all config properties for free
- ✅ Custom completions for service-specific concepts (endpoints, features, etc.)
- ✅ No schema maintenance overhead - reflection keeps everything in sync

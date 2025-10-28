# Taxi Compiler Architecture Analysis

## Executive Summary

The Taxi compiler is a multi-stage compiler that transforms Taxi language source files into a `TaxiDocument` object model. It uses ANTLR 4 for parsing and implements a semantic compilation phase using a `TokenProcessor` with a centralized `TypeSystem` for type resolution. The current architecture is monolithic with full compilation required for each build, lacking mechanisms for incremental compilation.

---

## 1. Compilation Pipeline (Step by Step)

### Phase 1: Token Collection and Parsing
```
Input CharStream(s)
        ↓
[CompilerTokenCache - caches parsed results]
        ↓
TaxiLexer (ANTLR) → Tokenizes input
        ↓
TaxiParser (ANTLR) → Builds parse tree
        ↓
TokenCollator (parse tree listener)
        ↓
Tokens object (structured token collection)
```

**Details:**
1. `Compiler` constructor accepts one or more `CharStream` inputs (from strings, files, or `CharStreams`)
2. `collectTokens()` method (lazy evaluated via `parseResult`) processes all inputs
3. `CompilerTokenCache` caches parsing results to avoid re-parsing the same input
4. `TaxiTokenStreamParser` (configurable via `ParserCustomizer`) creates the lexer and parser
5. `TokenCollator` is a parse tree listener that:
   - Collects type declarations (ObjectType, EnumType, TypeAlias, AnnotationType)
   - Collects service declarations
   - Collects policies, functions, queries
   - Tracks imports and inline type definitions
   - Builds `TokenStore` for location-based token lookup
6. Syntax errors are collected but don't stop processing (error recovery)

### Phase 2: Import Collection
```
Tokens.imports
        ↓
ImportedTypeCollator
        ↓
BFS traversal of type references
        ↓
List<ImportableToken>
        ↓
TypeSystem (imports registered)
```

**Details:**
1. `ImportedTypeCollator` processes explicit imports from the `Tokens` object
2. Performs BFS traversal of referenced types to collect transitive dependencies
3. Handles circular imports gracefully
4. Returns list of all imported types to be registered in `TypeSystem`

### Phase 3: Semantic Compilation
```
TokenProcessor.compile()
        ↓
├─ createEmptyTypes()
│  └─ Registers undefined placeholders for all types/functions
│
├─ compileTokens()
│  ├─ Enums first (can reference primitives)
│  └─ Other types (models, type aliases, annotations)
│
├─ compileTypeExtensions()
│  └─ Field refinements and annotations on existing types
│
├─ compileServices()
│
├─ compilePolicies()
│
├─ compileFunctions()
│
├─ applySynonymsToEnums()
│
├─ compileQueries()
│
└─ compileTopLevelExpressions()
        ↓
TaxiDocument (with fully compiled types)
```

**Details:**
1. **createEmptyTypes()**: Creates undefined placeholders in `TypeSystem` for all declared tokens
   - Enables forward references and circular dependencies
   - Marked with `createEmptyTypesPerformed` flag (idempotent)

2. **compileTokens()**: Processes each type declaration
   - Enums compiled first (simpler, no type dependencies)
   - Other types compiled in order, with circular reference detection
   - Each token tracks its own compilation state via `tokensCurrentlyCompiling` set

3. **compileTypeExtensions()**: Applies field refinements and annotations

4. **compileServices()**: Converts service declarations with operation signatures

5. **compileFunctions()**: Converts function declarations with parameters/return types

6. **applySynonymsToEnums()**: Maps synonym aliases to enum values

7. **compileQueries()**: Compiles TaxiQL queries (named and anonymous)

8. **compileTopLevelExpressions()**: Compiles standalone expressions

### Phase 4: Output Generation
```
TypeSystem state + collected services/policies/functions/queries
        ↓
TaxiDocument (immutable result)
```

---

## 2. Key Classes and Their Responsibilities

### Core Compilation Classes

#### `Compiler.kt` (Main Entry Point)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/Compiler.kt`
- **Responsibilities**:
  - Accepts one or more `CharStream` inputs (from files, strings, or pre-loaded sources)
  - Manages compilation lifecycle via public methods:
    - `compile(): TaxiDocument` - throws on errors
    - `compileWithMessages(): Pair<List<CompilationError>, TaxiDocument>` - returns errors
    - `validate(): List<CompilationError>` - validation only
  - Lazy initialization of parse results via properties
  - Caches parser results via `CompilerTokenCache`
  - Provides utilities for tooling (LSP, editors):
    - `contextAt()` - find AST node at location
    - `getNearestToken()` - fuzzy location matching
    - `lookupSymbolByName()` - symbol resolution
    - `declaredTypeNames()` - list declared types without imports
    - `declaredServiceNames()` - list declared services

- **Key Methods**:
  ```kotlin
  val parseResult: CollectedTokens by lazy { collectTokens() }
  val tokens: Tokens by lazy { parseResult.tokens }
  val syntaxErrors: List<CompilationError> by lazy { parseResult.errors }
  
  fun compile(): TaxiDocument
  fun compileWithMessages(): Pair<List<CompilationError>, TaxiDocument>
  ```

#### `TokenProcessor.kt` (Semantic Compilation)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/compiler/TokenProcessor.kt`
- **Responsibilities**:
  - Performs semantic analysis and type compilation
  - Manages `TypeSystem` (type registry and resolution)
  - Orchestrates compilation of all token types
  - Error collection during compilation
  - Type checking via `TypeChecker`

- **Key State**:
  ```kotlin
  val typeSystem: TypeSystem
  val tokens: Tokens
  val errors: MutableList<CompilationError>
  
  private val tokensCurrentlyCompiling: MutableSet<String>  // Circular ref detection
  private val services: MutableList<Service>
  private val policies: MutableList<Policy>
  private val functions: MutableList<Function>
  private val queries: MutableList<TaxiQlQuery>
  ```

- **Key Methods**:
  ```kotlin
  fun buildTaxiDocument(): Pair<List<CompilationError>, TaxiDocument>
  private fun compile()  // Orchestrates full compilation
  private fun compileTokens()  // Compile all types
  private fun compileToken(tokenName: String, token: ParserRuleContext)
  private fun compileType(...): Either<List<CompilationError>, ObjectType>
  private fun compileEnum(...): Either<List<CompilationError>, EnumType>
  ```

#### `TaxiDocument.kt` (Output Structure)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/TaxiDocument.kt`
- **Responsibilities**:
  - Immutable container for compiled schema
  - Provides lookup methods by qualified name
  - Merging of documents
  - Namespace-based organization

- **Structure**:
  ```kotlin
  data class TaxiDocument(
    val types: Set<Type>,
    val services: Set<Service>,
    val policies: Set<Policy> = emptySet(),
    val functions: Set<Function> = emptySet(),
    val annotations: Set<Annotation> = emptySet(),
    val views: Set<View> = emptySet(),
    val queries: Set<TaxiQlQuery> = emptySet(),
    val expressions: Set<Expression> = emptySet()
  )
  ```

### Token Collection Classes

#### `TokenCollator.kt` (Parse Tree Visitor)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/TokenCollator.kt`
- **Responsibilities**:
  - ANTLR parse tree listener
  - Collects all declarations by walking the tree
  - Tracks inline type definitions
  - Builds `TokenStore` for location-based lookups
  - Records exceptions during parsing

- **Key Methods**:
  ```kotlin
  override fun exitEveryRule(ctx: ParserRuleContext)
  override fun exitTypeDeclaration(ctx: TypeDeclarationContext)
  override fun exitEnumDeclaration(ctx: EnumDeclarationContext)
  override fun exitServiceDeclaration(ctx: ServiceDeclarationContext)
  override fun exitImportDeclaration(ctx: ImportDeclarationContext)
  
  fun tokens(): Tokens  // Returns collected tokens
  ```

#### `Tokens.kt` (Token Storage Structure)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/TokenCollator.kt`
- **Responsibilities**:
  - Immutable container for all parsed tokens
  - Maps qualified names to declarations
  - Combines multiple token sets (from multiple files)
  - Tracks imports and type references

- **Structure**:
  ```kotlin
  data class Tokens(
    val imports: List<Pair<String, ImportDeclarationContext>>,
    val unparsedTypes: Map<String, Pair<Namespace, ParserRuleContext>>,
    val unparsedInlineTypes: Map<String, String>,
    val unparsedExtensions: List<Pair<Namespace, ParserRuleContext>>,
    val unparsedServices: Map<String, Pair<Namespace, ServiceDeclarationContext>>,
    val unparsedPolicies: Map<String, Pair<Namespace, PolicyDeclarationContext>>,
    val unparsedFunctions: Map<String, Pair<Namespace, FunctionDeclarationContext>>,
    val namedQueries: List<Pair<Namespace, NamedQueryContext>>,
    val anonymousQueries: List<Pair<Namespace, AnonymousQueryContext>>,
    val topLevelExpressions: List<ExpressionGroupContext>,
    val tokenStore: TokenStore
  )
  ```

#### `TokenStore.kt` (Location-Based Lookups)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/TokenStore.kt`
- **Responsibilities**:
  - Maps source locations (line, column) to AST nodes
  - Tracks type references by source name
  - Enables tooling to find context at a location

- **Structure**:
  ```kotlin
  class TokenStore(
    private val tables: MutableMap<String, TokenTable>,  // per-source token tables
    private val typeReferencesBySourceName: ArrayListMultimap<String, TypeReferenceContext>
  )
  
  typealias TokenTable = Table<RowIndex, ColumnIndex, ParserRuleContext>  // TreeBasedTable
  ```

### Type System Classes

#### `TypeSystem.kt` (Type Registry and Resolution)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/TypeSystem.kt`
- **Responsibilities**:
  - Central registry for all types, functions, services
  - Handles type name qualification (namespace + type name)
  - Imports management
  - Symbol resolution with ambiguity detection

- **Key State**:
  ```kotlin
  private val importedTokenMap: Map<String, ImportableToken>
  private val compiledTokens: MutableMap<String, ImportableToken>
  private val serviceDefinitionMap: Map<String, ServiceDefinition>
  val symbolTree: SymbolTree  // For name resolution
  ```

- **Key Methods**:
  ```kotlin
  fun register(type: DefinableToken<*>): DefinableToken<*>
  fun getType(qualifiedName: String): Type
  fun getTokenIfPresent(qualifiedName: String): ImportableToken?
  fun qualify(namespace: String, name: String, imports: List<QualifiedName>): String
  fun isDefined(qualifiedName: String): Boolean
  ```

#### `ImportedTypeCollator.kt` (Dependency Collection)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/compiler/ImportTypeCollator.kt`
- **Responsibilities**:
  - Collects all imported types from import declarations
  - Performs BFS traversal of type references
  - Identifies which imported types are actually needed
  - Handles circular imports

- **Algorithm**:
  ```
  1. Start with explicit imports from source
  2. For each imported type, add its referencedTypes to queue
  3. Continue until queue is empty
  4. Return all collected types
  ```

### Compilation Helper Classes

#### `FieldCompiler.kt` (Field Compilation)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/compiler/fields/FieldCompiler.kt`
- **Responsibilities**:
  - Compiles type fields with type inference
  - Resolves field types by name qualification
  - Detects field circular dependencies
  - Handles spread operators (...)
  - Supports conditional and calculated fields

- **Key Methods**:
  ```kotlin
  fun compileAllFields(): List<Field>
  fun provideField(fieldName: String, requestingToken: ParserRuleContext): Either<List<CompilationError>, Field>
  private fun compileField(member: TypeMemberDeclarationContext): Either<List<CompilationError>, Field>
  ```

#### `TaxiTokenStreamParser.kt` (ANTLR Integration)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/TaxiTokenStreamParser.kt`
- **Responsibilities**:
  - Bridges to ANTLR lexer and parser
  - Error collection via `CollectingErrorListener`
  - Extensible via `ParserCustomizer` interface
  - Returns `TokenStreamParseResult` with tokens and errors

- **Key Classes**:
  ```kotlin
  interface TaxiTokenStreamParser {
    fun parse(input: CharStream): TokenStreamParseResult
  }
  
  data class TokenStreamParseResult(
    val tokens: Tokens,
    val errors: List<CompilationError>,
    val syntheticTokens: List<Token> = emptyList()
  )
  ```

#### `TypeChecker.kt` (Type Compatibility)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/compiler/TypeChecker.kt`
- **Responsibilities**:
  - Type compatibility checking (assignability)
  - Array/Stream type handling
  - Feature toggle support (disabled, enabled, soft_enabled)
  - Type projection validation

- **Key Functions**:
  ```kotlin
  fun TypeChecker.assertIsAssignable(valueType: Type, receiverType: Type, token: ParserRuleContext): CompilationError?
  fun TypeChecker.assertIsProjectable(sourceType: Type, targetType: Type, token: ParserRuleContext): CompilationError?
  ```

---

## 3. File Dependencies Handling

### How Files Are Loaded

#### `TaxiSourcesLoader.kt` (File System Integration)
- **Path**: `/home/user/taxilang/compiler/src/main/java/lang/taxi/packages/TaxiSourcesLoader.kt`
- **Responsibilities**:
  - Loads .taxi files from directory recursively
  - Loads package dependencies from taxi.conf
  - Integrates with PackageManager for remote dependencies

- **Flow**:
  ```
  loadPackageAndDependencies(path)
    ├─ Load taxi.conf → TaxiPackageProject
    ├─ Fetch remote dependencies via PackageManager
    ├─ Load local .taxi files
    └─ Return TaxiPackageSources (project + all sources)
  ```

### Dependency Tracking

#### Current Approach
1. **No explicit dependency graph** - all files treated independently
2. **Flat compilation** - all files compiled into single global TypeSystem
3. **Import references** - tracked via `ImportedTypeCollator`
4. **Type references** - stored in `TokenStore.typeReferencesBySourceName`
5. **Field dependencies** - tracked during field compilation via `FieldCompiler.provideField()`

#### Tracking Information Available
- `usedTypedNamesInSource(sourceName)` - types referenced in a source
- `typeNamesForSource(sourceName)` - types declared in a source
- `importedTypesInSource(sourceName)` - imports in a source
- `TokenStore.getTypeReferencesForSourceName()` - all type references

#### What's NOT Tracked
- Explicit file dependencies (which .taxi file imports which other .taxi file)
- Compilation order dependencies
- Type definition locations (only tokenization locations)
- Function or service interdependencies
- Policy dependencies

---

## 4. Compilation Unit and Location Tracking

### CompilationUnit
```kotlin
data class CompilationUnit(
  val source: SourceCode,
  val location: SourceLocation
)

data class SourceLocation(
  val line: Int,
  val char: Int
)

data class SourceCode(
  val sourceName: String,
  val content: String,
  val filePath: Path? = null,
  val language: SourceCodeLanguage = TAXI
)
```

### Tracking During Compilation
1. Each AST node has `start` and `stop` tokens with source location
2. Errors record source name and line/column
3. Types track their `compilationUnit` source
4. Fields track their declaration locations
5. `TokenStore` maintains table of (line, column) → AST node

---

## 5. Current Limitations Affecting Incremental Compilation

### Major Limitations

1. **Monolithic Type System**
   - Single global `TypeSystem` for entire compilation
   - No file-level type isolation
   - All types must be registered before field compilation
   - Circular reference detection requires full compilation pass

2. **Full Compilation Required**
   - `createEmptyTypes()` pre-registers all types
   - Type checking depends on complete type registry
   - No partial compilation or lazy compilation of types
   - All services, policies, functions compiled together

3. **No Explicit Dependency Graph**
   - File dependencies not explicitly tracked
   - Cannot determine which files can be recompiled independently
   - No file-level change detection
   - Transitive dependencies computed on-the-fly

4. **Import Resolution Complexity**
   - Imports collected via BFS traversal at compile time
   - No caching of import results
   - Circular import handling requires full traversal
   - Hard to determine which imports are affected by changes

5. **Field Compilation Interdependencies**
   - Fields compiled on-demand but within single pass
   - Type references resolved during field compilation
   - Circular field dependencies detected at compile time
   - No cached field compilation results

6. **Error Accumulation Model**
   - All errors collected in single pass
   - Cannot stop early on breaking changes
   - Full compilation always executed even with fatal errors
   - No error-based dependency invalidation

7. **Token Caching (Limited)**
   - `CompilerTokenCache` only caches parsing results
   - No semantic compilation caching
   - Not leveraged across multiple compilations
   - Cache key is `CharStream` object identity, not content hash

### What Would Support Incremental Compilation

1. **File-level type isolation** - types only reference in same file or imported files
2. **Explicit file dependency graph** - track which files depend on which
3. **Incremental type registry** - add/update types incrementally
4. **Cached field compilation** - cache compiled fields per type
5. **Hash-based change detection** - detect which files actually changed
6. **Lazy import resolution** - only resolve imports for modified files
7. **Partial error recovery** - continue with valid types despite errors
8. **Incremental validation** - validate only affected types

---

## 6. Existing Infrastructure for Incremental Support

### Already Available

1. **Source Name Normalization** (`SourceNames.normalize()`)
   - Consistent source name tracking across compilation

2. **Type Reference Tracking** (`TokenStore.typeReferencesBySourceName`)
   - Can determine which types are referenced in each file

3. **Type Declaration Tracking** (`Tokens.typeNamesForSource()`)
   - Can determine which types are declared in each file

4. **Import Tracking** (`Tokens.importTokensInSource()`)
   - Can determine which types are imported in each file

5. **Compilation Unit Tracking**
   - Each type knows its source file and location
   - Can trace definition origins

6. **Parser Customization** (`ParserCustomizer`)
   - Extension point for custom parsing logic

7. **Token Cache** (`CompilerTokenCache`)
   - Foundation for caching can be extended

8. **Error Collection Pattern**
   - Already uses `Either<CompilationError, T>` for error handling
   - Could support partial compilation with errors

### Recommendations for Incremental Compilation

1. **Build explicit dependency graph**
   - File → Types declared
   - File → Types imported
   - File → Types used (via `usedTypedNamesInSource`)

2. **Extend TypeSystem for incremental updates**
   - Support adding/removing types incrementally
   - Track which types are "dirty" (changed)
   - Only recompile dirty types and dependents

3. **Cache field compilation**
   - Store compiled fields per type
   - Invalidate on type definition changes
   - Reuse for unchanged types

4. **Implement content-hash based change detection**
   - Replace stream-based with hash-based caching
   - Detect actual content changes

5. **Add source-level compilation phases**
   - Phase 1: Type name collection per source
   - Phase 2: Import collection per source
   - Phase 3: Incremental type compilation
   - Phase 4: Service/policy compilation

---

## 7. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Compiler Entry Point                         │
│  compile(): TaxiDocument | compileWithMessages()               │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                    Phase 1: Parsing
                           │
        ┌──────────────────┴──────────────────┐
        │                                     │
    ┌───▼────┐  ┌──────────┐   ┌──────────┐  │
    │ ANTLR  │  │ Lexer    │   │ Parser   │  │
    │ Input  │→ │(Taxi)    │→  │(ANTLR)   │  │
    └────────┘  └──────────┘   └────┬─────┘  │
                                     │       │
        ┌────────────────────────────┴───┐   │
        │                                │   │
    ┌───▼──────────────────┐    ┌───────▼──┐│
    │  TokenCollator       │    │TokenStore││
    │  (Parse Listener)    │    │  (Index) ││
    └────────┬─────────────┘    └──────────┘│
             │                             │
        ┌────▼──────────────────────────┐  │
        │  Tokens Object                │  │
        │  - unparsedTypes              │  │
        │  - unparsedServices           │  │
        │  - unparsedFunctions          │  │
        │  - unparsedPolicies           │  │
        │  - imports                    │  │
        │  - tokenStore                 │  │
        └────┬───────────────────────────┘  │
             │                              │
        Phase 2: Import Resolution          │
             │                              │
    ┌────────▼─────────────┐   Syntax      │
    │ ImportedTypeCollator │   Errors ────┤
    │  (BFS traversal)     │               │
    └────────┬─────────────┘               │
             │                              │
        ┌────▼──────────────────────────┐  │
        │  List<ImportableToken>        │  │
        │  (resolved imports)           │  │
        └────┬───────────────────────────┘  │
             │                              │
        Phase 3: Semantic Compilation       │
             │                              │
    ┌────────▼────────────────────────┐   │
    │  TokenProcessor.compile()       │   │
    │                                │   │
    │  1. createEmptyTypes()         │   │
    │     (placeholder registration) │   │
    │                                │   │
    │  2. compileTokens()            │   │
    │     ├─ compileType()           │   │
    │     │  ├─ FieldCompiler        │   │
    │     │  └─ ExpressionCompiler   │   │
    │     ├─ compileEnum()           │   │
    │     └─ compileAnnotationType() │   │
    │                                │   │
    │  3. compileTypeExtensions()    │   │
    │  4. compileServices()          │   │
    │  5. compilePolicies()          │   │
    │  6. compileFunctions()         │   │
    │  7. compileQueries()           │   │
    │  8. compileTopLevelExpressions()   │   │
    │                                │   │
    │  TypeChecker validation        │   │
    │  throughout                    │   │
    └────────┬─────────────────────────┘   │
             │                              │
        ┌────▼──────────────────────────┐  │
        │  TypeSystem                   │  │
        │  - compiledTokens             │  │
        │  - importedTokenMap           │  │
        │  - symbolTree                 │  │
        └────┬───────────────────────────┘  │
             │                              │
    Semantic│ Errors                       │
    collect├────────────────────────────────┤
             │                              │
        Phase 4: Output                      │
             │                              │
        ┌────▼──────────────────────────┐  │
        │  TaxiDocument                 │  │
        │  - types                      │  │
        │  - services                   │  │
        │  - policies                   │  │
        │  - functions                  │  │
        │  - queries                    │  │
        └────┬───────────────────────────┘  │
             │                              │
        Return combined                    │
        errors + document                  │
                                            │
             └──────────────────────────────┘
```

---

## 8. Key Files Summary

| File | Purpose | Key Classes |
|------|---------|------------|
| `Compiler.kt` | Main entry point | Compiler, CompilationError |
| `TaxiDocument.kt` | Output structure | TaxiDocument, NamespacedTaxiDocument |
| `TokenProcessor.kt` | Semantic compilation | TokenProcessor |
| `TokenCollator.kt` | Token collection | TokenCollator, Tokens |
| `TokenStore.kt` | Location indexing | TokenStore |
| `TypeSystem.kt` | Type registry | TypeSystem |
| `ImportTypeCollator.kt` | Import resolution | ImportedTypeCollator |
| `FieldCompiler.kt` | Field compilation | FieldCompiler |
| `TypeChecker.kt` | Type checking | TypeChecker extensions |
| `TaxiTokenStreamParser.kt` | ANTLR bridge | DefaultTaxiTokenStreamParser |
| `TaxiSourcesLoader.kt` | File loading | TaxiSourcesLoader |

---

## 9. Recommendations for Future Improvements

### For Incremental Compilation
1. Implement file-level dependency tracking
2. Add semantic compilation caching per type
3. Use content hash-based change detection
4. Support partial compilation with error recovery
5. Implement lazy import resolution

### For Performance
1. Extend TokenCache beyond parsing phase
2. Cache TypeSystem state between compilations
3. Parallelize independent type compilation
4. Lazy-load standard library types

### For Maintainability
1. Document compilation phases with diagrams
2. Add metrics/profiling for compilation stages
3. Consider separation of concerns (split TokenProcessor)
4. Add instrumentation for debugging compilation issues


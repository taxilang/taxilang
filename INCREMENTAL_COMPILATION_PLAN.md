# Incremental Compilation Implementation Plan for Taxi-Lang

**Date:** February 11, 2026
**Objective:** Add incremental compilation support to reduce compilation time for large Taxi projects

---

## Executive Summary

This document provides a comprehensive plan to implement incremental compilation in the Taxi-Lang compiler. Based on detailed analysis of the current architecture, this plan outlines both a **phased surgical approach** (recommended) and a **comprehensive refactoring approach** for consideration.

**Key Finding:** The Taxi compiler has a well-structured architecture that enables incremental compilation through surgical enhancements to the caching and dependency tracking layers, without requiring major architectural changes.

---

## Current Architecture Analysis

### Compilation Pipeline Overview

The Taxi compiler follows a multi-phase pipeline orchestrated through these key components:

```
User Sources → Compiler → TokenProcessor → TaxiDocument
     ↓              ↓            ↓              ↓
CharStream    CollectTokens  Compile()     Types/Services
              (Phase 1)      (Phases 2-10)  Functions/Policies
```

### Ten Compilation Phases

Located in `TokenProcessor.compile()` (compiler/src/main/java/lang/taxi/compiler/TokenProcessor.kt:269):

1. **Token Collection & Parsing** (`Compiler.collectTokens:669`)
   - ANTLR lexing/parsing per file
   - `TokenCollator` listener collects syntax elements
   - Cached via `CompilerTokenCache` (Guava Cache)
   - **Current limitation:** Cache keyed by CharStream object identity, not file metadata

2. **Empty Type Registration** (`TokenProcessor.createEmptyTypes:413`)
   - Creates undefined placeholder types
   - Enables forward references and circular dependency handling
   - Registers service operation names

3. **Token Compilation - Enums First** (`TokenProcessor.compileTokens:440`)
   - Enums compiled before other types
   - Linter rules applied per type

4. **Token Compilation - Other Types** (same method)
   - Types, TypeAliases, AnnotationTypes compiled
   - Circular reference tracking via `tokensCurrentlyCompiling` set

5. **Type Extension Compilation** (`TokenProcessor.compileTypeExtensions:518`)
   - Type extensions, type alias extensions, enum extensions
   - **Cross-file effect:** Extensions can modify types from other files

6. **Service Compilation** (`TokenProcessor.compileServices:2600`)
   - Operations, queries, tables, streams
   - Service lineage (consumes/stores relationships)

7. **Policy Compilation** (`TokenProcessor.compilePolicies:2903`)

8. **Function Compilation** (`TokenProcessor.compileFunctions:2493`)

9. **Synonym Application to Enums** (`TokenProcessor.applySynonymsToEnums:346`)
   - **Cross-file effect:** Synonyms can reference enums from other files

10. **Query & Expression Compilation** (`TokenProcessor.compileQueries:294`)
    - Named and anonymous queries
    - Top-level expressions

### Key Data Structures

#### Tokens (TokenCollator.kt:19)
```kotlin
data class Tokens(
   val imports: List<Pair<String, ImportDeclarationContext>>,
   val unparsedTypes: List<Triple<String, Namespace, ParserRuleContext>>,
   val unparsedInlineTypes: List<Pair<String, String>>,
   val unparsedExtensions: List<Pair<Namespace, ParserRuleContext>>,
   val unparsedServices: List<Triple<String, Namespace, ServiceDeclarationContext>>,
   val unparsedPolicies: List<Triple<String, Namespace, PolicyDeclarationContext>>,
   val unparsedFunctions: List<Triple<String, Namespace, FunctionDeclarationContext>>,
   val namedQueries: List<Triple<String, Namespace, NamedQueryContext>>,
   val anonymousQueries: List<Pair<Namespace, AnonymousQueryContext>>,
   val topLevelExpressions: List<ExpressionGroupContext>,
   val tokenStore: TokenStore
)
```

**Observation:** Already file-granular - each collection preserves source information.

#### TokenStore (TokenStore.kt:27)
```kotlin
class TokenStore(
   private val tables: MutableMap<String, TokenTable>,
   private val typeReferencesBySourceName: ArrayListMultimap<String, TypeReferenceContext>,
   private val namedSymbolDeclarations: ArrayListMultimap<String, ParserRuleContext>
)
```

**Observation:** Tracks source-level metadata, supports file-level queries.

#### TypeSystem (TypeSystem.kt:18)
```kotlin
class TypeSystem(importedTokens: List<ImportableToken>) {
   private val importedTokenMap: Map<String, ImportableToken>
   val symbolTree = SymbolTree.withPrimitives().registerAll(importedTokens)
   private val compiledTokens = mutableMapOf<String, ImportableToken>()
   private val serviceDefinitionMap = mutableMapOf<String, ServiceDefinition>()
}
```

**Observation:** Global symbol table - requires careful handling for incremental updates.

#### CompilerTokenCache (Compiler.kt:174)
```kotlin
class CompilerTokenCache(private val parserCustomizers: List<ParserCustomizer> = emptyList()) {
   private val cache: Cache<CharStream, TokenStreamParseResult> = CacheBuilder.newBuilder().build()

   fun parse(input: CharStream): TokenStreamParseResult {
      return cache.get(input) { /* parse */ }
   }
}
```

**Critical Issue:** Keyed by `CharStream` object identity, not filename/timestamp. This prevents effective incremental compilation.

#### ImportedTypeCollator (ImportTypeCollator.kt:16)
```kotlin
internal class ImportedTypeCollator(
   private val tokens: Tokens,
   private val importSources: List<TaxiDocument>
) {
   fun collect(): Pair<List<CompilationError>, List<ImportableToken>> {
      val importQueue = LinkedList<Pair<String, Token>>()
      // BFS traversal of dependencies
      while (importQueue.isNotEmpty()) {
         val (name, token) = importQueue.pop()
         if (type is UserType<*, *>) {
            type.referencedTypes.forEach { importQueue.add(it.qualifiedName to token) }
         }
      }
   }
}
```

**Observation:** Already performs dependency graph traversal - foundation for change propagation.

---

## Dependency Analysis

### File-Level Dependencies

Three types of dependencies exist:

1. **Direct Imports** (Explicit)
   - `import com.example.Person` in FileA.taxi
   - FileA depends on the file declaring `Person`

2. **Type References** (Implicit)
   - Field declarations: `model Order { customer: Customer }`
   - Service operations: `operation getOrder(): Order`
   - Tracked via `TokenStore.typeReferencesBySourceName`

3. **Cross-File Effects** (Global)
   - Type extensions: `type extension Person { ... }`
   - Enum synonyms: `synonym of SomeEnum.VALUE`
   - Service lineage: `@ConsumedBy(SomeService.operation)`

### Dependency Graph Construction

Current implicit graph (via ImportedTypeCollator BFS):

```
File A → declares Type T1, imports T2
         ↓
File B → declares Type T2, imports T3
         ↓
File C → declares Type T3
```

**Required for Incremental Compilation:**
```
DependencyGraph {
   nodes: Map<SourceFile, FileNode>
   edges: Map<SourceFile, Set<SourceFile>>  // direct dependencies
   reverseEdges: Map<SourceFile, Set<SourceFile>>  // dependents
}

FileNode {
   sourcePath: Path
   lastModified: Timestamp
   contentHash: String (SHA-256)
   declaredSymbols: Set<QualifiedName>
   importedSymbols: Set<QualifiedName>
   referencedSymbols: Set<QualifiedName>
   compiledArtifacts: Tokens (cached)
   compiledTypes: Set<Type> (cached)
}
```

---

## Change Impact Analysis

### Scenarios and Propagation

| Change Type | Files Affected | Phases Re-run | Complexity |
|-------------|----------------|---------------|------------|
| **Add field to type** | 1 file | 1-4 (for changed file only) | Low |
| **Change type signature** | 1 file + dependents | 1-4 for all | Medium |
| **Add type extension** | 1 file + extended type's file | 1-5 | Medium |
| **Add enum synonym** | 1 file + enum's file | 1-4, 9 | Medium |
| **Add service operation** | 1 file + consumers | 1-6 | Medium |
| **Rename type** | 1 file + all dependents | 1-10 (all) | High |
| **Add new file** | 1 file | 1-10 (for new file only) | Low |
| **Delete file** | 0 files + cleanup | Symbol table update | Low |

### Invalidation Rules

**File X modified → Recompile:**
1. File X itself
2. All files that import symbols from X
3. All files with type references to symbols in X
4. If X contains extensions → recompile extended types' files
5. If X contains synonyms → recompile referenced enum files

---

## Implementation Plan

### Phase 1: Foundation (Surgical Approach - RECOMMENDED)

**Goal:** Enable file-level caching with timestamp-based invalidation
**Effort:** 2-3 weeks
**Benefit:** 30-50% compilation speedup for typical changes

#### 1.1 Enhanced CompilerTokenCache

**File:** `compiler/src/main/java/lang/taxi/Compiler.kt`

**Changes:**
```kotlin
data class FileMetadata(
   val sourcePath: String,
   val lastModified: Long,
   val contentHash: String
)

class CompilerTokenCache(
   private val parserCustomizers: List<ParserCustomizer> = emptyList()
) {
   // Replace: Cache<CharStream, TokenStreamParseResult>
   private val cache: Cache<FileMetadata, TokenStreamParseResult> =
      CacheBuilder.newBuilder().build()

   private val sourcePathToMetadata = mutableMapOf<String, FileMetadata>()

   fun parse(input: CharStream, metadata: FileMetadata): TokenStreamParseResult {
      val existing = sourcePathToMetadata[metadata.sourcePath]
      if (existing != null && existing.lastModified == metadata.lastModified) {
         return cache.getIfPresent(existing) ?: parseAndCache(input, metadata)
      }

      // Invalidate old version
      existing?.let { cache.invalidate(it) }

      return parseAndCache(input, metadata)
   }

   private fun parseAndCache(input: CharStream, metadata: FileMetadata): TokenStreamParseResult {
      val result = cache.get(metadata) {
         val tokenStreamParser = parserCustomizers.fold(
            DefaultTaxiTokenStreamParser() as TaxiTokenStreamParser
         ) { acc, customizer -> customizer.configure(acc) }
         tokenStreamParser.parse(input)
      }
      sourcePathToMetadata[metadata.sourcePath] = metadata
      return result
   }

   fun invalidate(sourcePath: String) {
      sourcePathToMetadata[sourcePath]?.let { metadata ->
         cache.invalidate(metadata)
         sourcePathToMetadata.remove(sourcePath)
      }
   }
}
```

**Testing:**
- Unit tests for cache hit/miss scenarios
- Verify invalidation on file modification
- Performance benchmarks comparing before/after

#### 1.2 File Metadata Tracking

**New File:** `compiler/src/main/java/lang/taxi/compiler/FileMetadata.kt`

```kotlin
package lang.taxi.compiler

import java.nio.file.Path
import java.nio.file.Files
import java.security.MessageDigest

data class FileMetadata(
   val sourcePath: String,
   val lastModified: Long,
   val contentHash: String
) {
   companion object {
      fun fromPath(path: Path): FileMetadata {
         val lastModified = Files.getLastModifiedTime(path).toMillis()
         val contentHash = computeHash(Files.readAllBytes(path))
         return FileMetadata(path.toString(), lastModified, contentHash)
      }

      fun fromContent(sourcePath: String, content: String): FileMetadata {
         return FileMetadata(
            sourcePath,
            System.currentTimeMillis(),
            computeHash(content.toByteArray())
         )
      }

      private fun computeHash(bytes: ByteArray): String {
         val digest = MessageDigest.getInstance("SHA-256")
         return digest.digest(bytes).joinToString("") { "%02x".format(it) }
      }
   }
}
```

#### 1.3 Update Compiler Entry Points

**File:** `compiler/src/main/java/lang/taxi/Compiler.kt`

**Modify constructor to accept FileMetadata:**
```kotlin
class Compiler(
   val inputs: List<Pair<CharStream, FileMetadata>>,  // Enhanced
   val importSources: List<TaxiDocument> = emptyList(),
   private val tokenCache: CompilerTokenCache = CompilerTokenCache(),
   val config: CompilerConfig = CompilerConfig()
) {
   // Update collectTokens to use metadata
   private fun collectTokens(): CollectedTokens {
      val collectionResult = inputs.map { (input, metadata) ->
         tokenCache.parse(input, metadata)
      }
      // ... rest unchanged
   }
}
```

**Update all factory methods:**
```kotlin
companion object {
   fun forFiles(sources: List<File>): Compiler {
      val inputs = sources.map { file ->
         CharStreams.fromPath(file.toPath()) to
         FileMetadata.fromPath(file.toPath())
      }
      return Compiler(inputs)
   }

   fun forStrings(sources: List<String>): Compiler {
      val inputs = sources.mapIndexed { index, source ->
         CharStreams.fromString(source, "StringSource-$index") to
         FileMetadata.fromContent("StringSource-$index", source)
      }
      return Compiler(inputs)
   }
}
```

**Migration Path:**
- Keep existing constructors as deprecated
- Add new constructors with metadata
- Update TaxiSourcesLoader to compute metadata when loading

**Deliverables:**
- ✅ Enhanced CompilerTokenCache with file-based keys
- ✅ FileMetadata tracking infrastructure
- ✅ Updated Compiler constructors
- ✅ Comprehensive test suite
- ✅ Performance benchmark baseline

---

### Phase 2: Dependency Graph Construction

**Goal:** Build explicit dependency graph for change propagation
**Effort:** 2-3 weeks
**Benefit:** Foundation for accurate incremental recompilation

#### 2.1 Dependency Graph Data Structure

**New File:** `compiler/src/main/java/lang/taxi/compiler/DependencyGraph.kt`

```kotlin
package lang.taxi.compiler

import lang.taxi.types.QualifiedName
import java.nio.file.Path

/**
 * Represents dependencies between source files in a Taxi project.
 * Supports both forward (dependencies) and reverse (dependents) lookups.
 */
class DependencyGraph {
   private val nodes = mutableMapOf<String, FileNode>()

   // Forward edges: File A → Files that A depends on
   private val dependencies = mutableMapOf<String, MutableSet<String>>()

   // Reverse edges: File A → Files that depend on A
   private val dependents = mutableMapOf<String, MutableSet<String>>()

   fun addNode(node: FileNode) {
      nodes[node.sourcePath] = node
      dependencies.putIfAbsent(node.sourcePath, mutableSetOf())
      dependents.putIfAbsent(node.sourcePath, mutableSetOf())
   }

   fun addDependency(from: String, to: String) {
      dependencies.getOrPut(from) { mutableSetOf() }.add(to)
      dependents.getOrPut(to) { mutableSetOf() }.add(from)
   }

   fun getNode(sourcePath: String): FileNode? = nodes[sourcePath]

   fun getDependencies(sourcePath: String): Set<String> =
      dependencies[sourcePath] ?: emptySet()

   fun getDependents(sourcePath: String): Set<String> =
      dependents[sourcePath] ?: emptySet()

   /**
    * Computes the transitive closure of all files that depend on the given file.
    * Uses BFS to avoid stack overflow on deep dependency chains.
    */
   fun getTransitiveDependents(sourcePath: String): Set<String> {
      val result = mutableSetOf<String>()
      val queue = ArrayDeque(listOf(sourcePath))
      val visited = mutableSetOf<String>()

      while (queue.isNotEmpty()) {
         val current = queue.removeFirst()
         if (current in visited) continue
         visited.add(current)

         dependents[current]?.forEach { dependent ->
            if (dependent !in visited) {
               result.add(dependent)
               queue.add(dependent)
            }
         }
      }

      return result
   }

   /**
    * Determines which files need recompilation given a set of changed files.
    */
   fun computeRecompilationSet(changedFiles: Set<String>): Set<String> {
      val recompile = mutableSetOf<String>()
      changedFiles.forEach { changedFile ->
         recompile.add(changedFile)
         recompile.addAll(getTransitiveDependents(changedFile))
      }
      return recompile
   }

   /**
    * Detects cycles in the dependency graph.
    * Returns list of cycles found (each cycle is a list of file paths).
    */
   fun detectCycles(): List<List<String>> {
      val cycles = mutableListOf<List<String>>()
      val visited = mutableSetOf<String>()
      val recursionStack = mutableSetOf<String>()

      fun dfs(node: String, path: MutableList<String>) {
         if (node in recursionStack) {
            // Found cycle
            val cycleStart = path.indexOf(node)
            if (cycleStart >= 0) {
               cycles.add(path.subList(cycleStart, path.size) + node)
            }
            return
         }

         if (node in visited) return

         visited.add(node)
         recursionStack.add(node)
         path.add(node)

         dependencies[node]?.forEach { dependency ->
            dfs(dependency, path.toMutableList())
         }

         recursionStack.remove(node)
      }

      nodes.keys.forEach { node ->
         if (node !in visited) {
            dfs(node, mutableListOf())
         }
      }

      return cycles
   }

   fun toGraphviz(): String {
      val sb = StringBuilder()
      sb.appendLine("digraph Dependencies {")
      sb.appendLine("  rankdir=LR;")

      nodes.values.forEach { node ->
         val label = node.sourcePath.split("/").last()
         sb.appendLine("  \"$label\" [shape=box];")
      }

      dependencies.forEach { (from, tos) ->
         val fromLabel = from.split("/").last()
         tos.forEach { to ->
            val toLabel = to.split("/").last()
            sb.appendLine("  \"$fromLabel\" -> \"$toLabel\";")
         }
      }

      sb.appendLine("}")
      return sb.toString()
   }
}

data class FileNode(
   val sourcePath: String,
   val metadata: FileMetadata,
   val declaredSymbols: Set<QualifiedName>,
   val importedSymbols: Set<QualifiedName>,
   val referencedSymbols: Set<QualifiedName>,
   val extensionTargets: Set<QualifiedName>, // Types extended by this file
   val synonymTargets: Set<QualifiedName>   // Enums referenced in synonyms
)
```

#### 2.2 Dependency Graph Builder

**New File:** `compiler/src/main/java/lang/taxi/compiler/DependencyGraphBuilder.kt`

```kotlin
package lang.taxi.compiler

import lang.taxi.Tokens
import lang.taxi.types.QualifiedName

/**
 * Builds a dependency graph from parsed tokens.
 * Analyzes imports, type references, extensions, and synonyms to construct
 * the complete dependency structure.
 */
class DependencyGraphBuilder {

   fun buildGraph(
      tokensByFile: Map<String, Tokens>,
      metadataByFile: Map<String, FileMetadata>
   ): DependencyGraph {
      val graph = DependencyGraph()

      // Phase 1: Create nodes for all files
      tokensByFile.forEach { (sourcePath, tokens) ->
         val metadata = metadataByFile[sourcePath]
            ?: error("Missing metadata for $sourcePath")

         val node = FileNode(
            sourcePath = sourcePath,
            metadata = metadata,
            declaredSymbols = extractDeclaredSymbols(tokens),
            importedSymbols = extractImportedSymbols(tokens),
            referencedSymbols = extractReferencedSymbols(tokens),
            extensionTargets = extractExtensionTargets(tokens),
            synonymTargets = extractSynonymTargets(tokens)
         )

         graph.addNode(node)
      }

      // Phase 2: Build edges based on dependencies
      tokensByFile.keys.forEach { sourcePath ->
         val node = graph.getNode(sourcePath)!!

         // Find which files declare the symbols this file imports/references
         val symbolsToDependOn = node.importedSymbols +
                                 node.referencedSymbols +
                                 node.extensionTargets +
                                 node.synonymTargets

         symbolsToDependOn.forEach { symbol ->
            // Find the file that declares this symbol
            val declaringFile = tokensByFile.entries
               .firstOrNull { (_, tokens) ->
                  extractDeclaredSymbols(tokens).contains(symbol)
               }?.key

            if (declaringFile != null && declaringFile != sourcePath) {
               graph.addDependency(from = sourcePath, to = declaringFile)
            }
         }
      }

      return graph
   }

   private fun extractDeclaredSymbols(tokens: Tokens): Set<QualifiedName> {
      val symbols = mutableSetOf<QualifiedName>()

      tokens.unparsedTypes.forEach { (name, _, _) ->
         symbols.add(QualifiedName.from(name))
      }

      tokens.unparsedServices.forEach { (name, _, _) ->
         symbols.add(QualifiedName.from(name))
      }

      tokens.unparsedFunctions.forEach { (name, _, _) ->
         symbols.add(QualifiedName.from(name))
      }

      tokens.unparsedPolicies.forEach { (name, _, _) ->
         symbols.add(QualifiedName.from(name))
      }

      tokens.namedQueries.forEach { (name, _, _) ->
         symbols.add(QualifiedName.from(name))
      }

      return symbols
   }

   private fun extractImportedSymbols(tokens: Tokens): Set<QualifiedName> {
      return tokens.imports.map { (name, _) -> QualifiedName.from(name) }.toSet()
   }

   private fun extractReferencedSymbols(tokens: Tokens): Set<QualifiedName> {
      // Extract from TokenStore.typeReferencesBySourceName
      // This requires access to the token store and type resolution
      // For now, return empty and refine in implementation
      return emptySet()
   }

   private fun extractExtensionTargets(tokens: Tokens): Set<QualifiedName> {
      val targets = mutableSetOf<QualifiedName>()

      tokens.unparsedExtensions.forEach { (namespace, context) ->
         when (context) {
            is lang.taxi.TaxiParser.TypeExtensionDeclarationContext -> {
               val typeName = context.identifier().text
               val qualified = if (typeName.contains(".")) {
                  QualifiedName.from(typeName)
               } else {
                  QualifiedName(namespace, typeName)
               }
               targets.add(qualified)
            }
            is lang.taxi.TaxiParser.EnumExtensionDeclarationContext -> {
               val typeName = context.identifier().text
               val qualified = if (typeName.contains(".")) {
                  QualifiedName.from(typeName)
               } else {
                  QualifiedName(namespace, typeName)
               }
               targets.add(qualified)
            }
            // Handle other extension types
         }
      }

      return targets
   }

   private fun extractSynonymTargets(tokens: Tokens): Set<QualifiedName> {
      // Synonyms are registered during semantic analysis
      // Would need to extract from annotation contexts
      // For now, return empty and refine in implementation
      return emptySet()
   }
}
```

#### 2.3 Integration with Compiler

**File:** `compiler/src/main/java/lang/taxi/Compiler.kt`

Add dependency graph tracking:

```kotlin
class Compiler(
   val inputs: List<Pair<CharStream, FileMetadata>>,
   val importSources: List<TaxiDocument> = emptyList(),
   private val tokenCache: CompilerTokenCache = CompilerTokenCache(),
   val config: CompilerConfig = CompilerConfig(),
   private val dependencyGraph: DependencyGraph? = null  // Optional for incremental mode
) {

   companion object {
      /**
       * Creates a compiler with incremental compilation support.
       * Maintains a dependency graph for efficient recompilation.
       */
      fun withIncrementalSupport(
         sources: List<File>,
         previousGraph: DependencyGraph? = null
      ): IncrementalCompiler {
         return IncrementalCompiler(sources, previousGraph)
      }
   }
}
```

**New File:** `compiler/src/main/java/lang/taxi/compiler/IncrementalCompiler.kt`

```kotlin
package lang.taxi.compiler

import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import java.io.File

/**
 * Compiler with incremental compilation support.
 * Tracks file dependencies and only recompiles affected files on changes.
 */
class IncrementalCompiler(
   private val sourceFiles: List<File>,
   private var dependencyGraph: DependencyGraph? = null
) {
   private val compilationCache = mutableMapOf<String, TaxiDocument>()

   fun compile(): TaxiDocument {
      // Detect which files have changed
      val changedFiles = detectChangedFiles()

      if (dependencyGraph == null || changedFiles.size == sourceFiles.size) {
         // First compile or all files changed - full compilation
         return fullCompile()
      }

      if (changedFiles.isEmpty()) {
         // No changes - return cached result
         return mergeCachedDocuments()
      }

      // Incremental compilation
      return incrementalCompile(changedFiles)
   }

   private fun detectChangedFiles(): Set<String> {
      val changed = mutableSetOf<String>()

      sourceFiles.forEach { file ->
         val sourcePath = file.absolutePath
         val currentMetadata = FileMetadata.fromPath(file.toPath())
         val previousNode = dependencyGraph?.getNode(sourcePath)

         if (previousNode == null ||
             previousNode.metadata.contentHash != currentMetadata.contentHash) {
            changed.add(sourcePath)
         }
      }

      return changed
   }

   private fun fullCompile(): TaxiDocument {
      val compiler = Compiler.forFiles(sourceFiles)
      val document = compiler.compile()

      // Build dependency graph from compilation
      val tokensByFile = extractTokensByFile(compiler)
      val metadataByFile = sourceFiles.associate {
         it.absolutePath to FileMetadata.fromPath(it.toPath())
      }

      dependencyGraph = DependencyGraphBuilder().buildGraph(tokensByFile, metadataByFile)

      // Cache the result
      cacheDocumentPerFile(document)

      return document
   }

   private fun incrementalCompile(changedFiles: Set<String>): TaxiDocument {
      val graph = dependencyGraph!!

      // Compute which files need recompilation
      val filesToRecompile = graph.computeRecompilationSet(changedFiles)

      // Recompile only affected files
      val affectedSources = sourceFiles.filter {
         it.absolutePath in filesToRecompile
      }

      val compiler = Compiler.forFiles(affectedSources)
      val recompiledDocument = compiler.compile()

      // Update cache for recompiled files
      updateCacheForRecompiledFiles(affectedSources, recompiledDocument)

      // Update dependency graph
      updateDependencyGraph(affectedSources, compiler)

      // Merge with cached documents from unchanged files
      return mergeDocuments(recompiledDocument, filesToRecompile)
   }

   private fun extractTokensByFile(compiler: Compiler): Map<String, Tokens> {
      // Implementation would extract tokens per file from compiler
      // This requires some refactoring to expose per-file tokens
      return emptyMap()
   }

   private fun cacheDocumentPerFile(document: TaxiDocument) {
      // Cache types/services/etc per source file
      // This requires tracking which symbols came from which files
   }

   private fun updateCacheForRecompiledFiles(
      files: List<File>,
      document: TaxiDocument
   ) {
      // Update the cache with newly compiled artifacts
   }

   private fun updateDependencyGraph(files: List<File>, compiler: Compiler) {
      // Update the dependency graph with new information
   }

   private fun mergeCachedDocuments(): TaxiDocument {
      // Merge all cached documents
      return compilationCache.values.reduce { acc, doc -> acc.merge(doc) }
   }

   private fun mergeDocuments(
      recompiled: TaxiDocument,
      recompiledPaths: Set<String>
   ): TaxiDocument {
      // Merge recompiled artifacts with cached artifacts from unchanged files
      val unchanged = compilationCache.filterKeys { it !in recompiledPaths }
      val unchangedDoc = unchanged.values.fold(TaxiDocument.empty()) { acc, doc ->
         acc.merge(doc)
      }
      return unchangedDoc.merge(recompiled)
   }
}
```

**Deliverables:**
- ✅ DependencyGraph data structure
- ✅ DependencyGraphBuilder implementation
- ✅ IncrementalCompiler wrapper
- ✅ Integration tests with realistic project structures
- ✅ Visualization tools (Graphviz export)

---

### Phase 3: Incremental Recompilation Logic

**Goal:** Implement smart recompilation based on dependency graph
**Effort:** 3-4 weeks
**Benefit:** 60-80% compilation speedup for typical changes

#### 3.1 Per-File Artifact Tracking

**Challenge:** TaxiDocument currently aggregates all symbols. Need to track which file contributed which symbols.

**New File:** `compiler/src/main/java/lang/taxi/compiler/FileArtifacts.kt`

```kotlin
package lang.taxi.compiler

import lang.taxi.TaxiDocument
import lang.taxi.types.Type
import lang.taxi.services.Service
import lang.taxi.functions.Function
import lang.taxi.policies.Policy

/**
 * Tracks which symbols were declared in which source file.
 * Enables selective artifact merging during incremental compilation.
 */
data class FileArtifacts(
   val sourcePath: String,
   val types: Set<Type>,
   val services: Set<Service>,
   val functions: Set<Function>,
   val policies: Set<Policy>
) {
   fun toDocument(): TaxiDocument {
      return TaxiDocument(
         types = types,
         services = services,
         functions = functions,
         policies = policies
      )
   }
}

/**
 * Maps source files to their compiled artifacts.
 * Supports incremental updates and selective merging.
 */
class ArtifactCache {
   private val cache = mutableMapOf<String, FileArtifacts>()

   fun put(sourcePath: String, artifacts: FileArtifacts) {
      cache[sourcePath] = artifacts
   }

   fun get(sourcePath: String): FileArtifacts? = cache[sourcePath]

   fun remove(sourcePath: String) {
      cache.remove(sourcePath)
   }

   fun invalidate(sourcePaths: Set<String>) {
      sourcePaths.forEach { cache.remove(it) }
   }

   /**
    * Merges artifacts from all cached files except those specified.
    */
   fun mergeExcept(excludePaths: Set<String>): TaxiDocument {
      val artifacts = cache.filterKeys { it !in excludePaths }.values
      return mergeArtifacts(artifacts)
   }

   /**
    * Merges all cached artifacts into a single document.
    */
   fun mergeAll(): TaxiDocument {
      return mergeArtifacts(cache.values)
   }

   private fun mergeArtifacts(artifacts: Collection<FileArtifacts>): TaxiDocument {
      return artifacts.fold(TaxiDocument.empty()) { acc, fileArtifacts ->
         acc.merge(fileArtifacts.toDocument())
      }
   }
}
```

#### 3.2 Enhanced TokenProcessor for Partial Compilation

**File:** `compiler/src/main/java/lang/taxi/compiler/TokenProcessor.kt`

Add capability to compile specific files only:

```kotlin
class TokenProcessor(
   val tokens: Tokens,
   private val importSources: List<TaxiDocument> = emptyList(),
   collectImports: Boolean = true,
   val typeChecker: TypeChecker,
   private val linter: Linter,
   private val filterSourceNames: Set<String>? = null  // NEW: Optional filter
) {

   private fun compileTokens() {
      val enumUnparsedTypes = tokens
         .unparsedTypes
         .filter { shouldCompile(it.third) }  // NEW: Filter
         .filter { (_, _, context) -> context is EnumDeclarationContext }

      val nonEnumParsedTypes = tokens
         .unparsedTypes
         .filter { shouldCompile(it.third) }  // NEW: Filter
         .filter { (_, _, context) -> context !is EnumDeclarationContext }

      enumUnparsedTypes
         .plus(nonEnumParsedTypes)
         .forEach { (tokenName, _, token) ->
            compileToken(tokenName, token)
         }
   }

   private fun shouldCompile(context: ParserRuleContext): Boolean {
      if (filterSourceNames == null) return true
      val sourceName = context.source().normalizedSourceName
      return sourceName in filterSourceNames
   }
}
```

#### 3.3 Refined IncrementalCompiler Implementation

**File:** `compiler/src/main/java/lang/taxi/compiler/IncrementalCompiler.kt`

Complete implementation:

```kotlin
class IncrementalCompiler(
   private val sourceFiles: List<File>,
   private var dependencyGraph: DependencyGraph? = null,
   private val artifactCache: ArtifactCache = ArtifactCache()
) {

   private fun incrementalCompile(changedFiles: Set<String>): TaxiDocument {
      val graph = dependencyGraph!!

      // Step 1: Compute recompilation set
      val filesToRecompile = graph.computeRecompilationSet(changedFiles)

      // Step 2: Invalidate cache for affected files
      artifactCache.invalidate(filesToRecompile)

      // Step 3: Collect tokens from all files (cached if available)
      val allTokens = collectAllTokens(filesToRecompile)

      // Step 4: Build TypeSystem from cached + recompiled imports
      val cachedDocument = artifactCache.mergeExcept(filesToRecompile)
      val importSources = listOf(cachedDocument)

      // Step 5: Compile only affected files
      val processor = TokenProcessor(
         tokens = allTokens,
         importSources = importSources,
         collectImports = true,
         typeChecker = TypeChecker(FeatureToggle.ENABLED),
         linter = Linter.empty(),
         filterSourceNames = filesToRecompile
      )

      val (errors, recompiledDocument) = processor.buildTaxiDocument()

      if (errors.filter { it.severity == Severity.ERROR }.isNotEmpty()) {
         throw CompilationException(errors)
      }

      // Step 6: Update artifact cache
      updateArtifactCache(filesToRecompile, recompiledDocument)

      // Step 7: Merge with cached artifacts
      return cachedDocument.merge(recompiledDocument)
   }

   private fun collectAllTokens(recompileFiles: Set<String>): Tokens {
      // Collect tokens from all source files
      // Use cached tokens for unchanged files, parse changed files
      val tokenCollections = sourceFiles.map { file ->
         val sourcePath = file.absolutePath
         if (sourcePath in recompileFiles) {
            // Parse fresh
            parseFile(file)
         } else {
            // Use cached tokens
            getCachedTokens(sourcePath)
         }
      }

      return Tokens.combine(tokenCollections)
   }

   private fun parseFile(file: File): Tokens {
      // Implementation to parse a single file
   }

   private fun getCachedTokens(sourcePath: String): Tokens {
      // Retrieve cached tokens for unchanged file
   }

   private fun updateArtifactCache(
      sourcePaths: Set<String>,
      document: TaxiDocument
   ) {
      // Map symbols back to their source files
      // This requires tracking compilation units
      sourcePaths.forEach { sourcePath ->
         val typesFromFile = document.types.filter { type ->
            type.compilationUnits.any {
               it.source.normalizedSourceName == sourcePath
            }
         }.toSet()

         val servicesFromFile = document.services.filter { service ->
            service.compilationUnits.any {
               it.source.normalizedSourceName == sourcePath
            }
         }.toSet()

         val functionsFromFile = document.functions.filter { function ->
            function.compilationUnits.any {
               it.source.normalizedSourceName == sourcePath
            }
         }.toSet()

         val policiesFromFile = document.policies.filter { policy ->
            policy.compilationUnits.any {
               it.source.normalizedSourceName == sourcePath
            }
         }.toSet()

         artifactCache.put(
            sourcePath,
            FileArtifacts(
               sourcePath = sourcePath,
               types = typesFromFile,
               services = servicesFromFile,
               functions = functionsFromFile,
               policies = policiesFromFile
            )
         )
      }
   }
}
```

**Deliverables:**
- ✅ FileArtifacts tracking
- ✅ ArtifactCache implementation
- ✅ Partial compilation in TokenProcessor
- ✅ Complete IncrementalCompiler
- ✅ Integration tests with realistic change scenarios
- ✅ Performance benchmarks

---

### Phase 4: Persistent Caching (Optional Enhancement)

**Goal:** Persist compilation artifacts to disk for cross-session speedup
**Effort:** 2-3 weeks
**Benefit:** Fast cold-start compilation, shared team cache

#### 4.1 Serialization Infrastructure

**New File:** `compiler/src/main/java/lang/taxi/compiler/cache/CacheSerializer.kt`

```kotlin
package lang.taxi.compiler.cache

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import lang.taxi.TaxiDocument
import java.nio.file.Path
import java.nio.file.Files

/**
 * Serializes and deserializes compilation artifacts for persistent caching.
 * Uses Kotlin serialization for type-safe, efficient storage.
 */
object CacheSerializer {

   private val json = Json {
      prettyPrint = true
      ignoreUnknownKeys = true
   }

   fun saveDependencyGraph(graph: DependencyGraph, path: Path) {
      val graphData = serializeDependencyGraph(graph)
      Files.writeString(path, json.encodeToString(graphData))
   }

   fun loadDependencyGraph(path: Path): DependencyGraph? {
      if (!Files.exists(path)) return null
      val content = Files.readString(path)
      val graphData = json.decodeFromString<SerializedDependencyGraph>(content)
      return deserializeDependencyGraph(graphData)
   }

   fun saveArtifactCache(cache: ArtifactCache, cacheDir: Path) {
      Files.createDirectories(cacheDir)
      // Save each file's artifacts separately for efficient updates
      cache.cache.forEach { (sourcePath, artifacts) ->
         val cacheFile = cacheDir.resolve(sourcePath.hashCode().toString() + ".cache")
         val serialized = serializeFileArtifacts(artifacts)
         Files.writeString(cacheFile, json.encodeToString(serialized))
      }
   }

   fun loadArtifactCache(cacheDir: Path): ArtifactCache? {
      if (!Files.exists(cacheDir)) return null
      val cache = ArtifactCache()

      Files.list(cacheDir)
         .filter { it.toString().endsWith(".cache") }
         .forEach { cacheFile ->
            val content = Files.readString(cacheFile)
            val artifacts = json.decodeFromString<SerializedFileArtifacts>(content)
            cache.put(artifacts.sourcePath, deserializeFileArtifacts(artifacts))
         }

      return cache
   }

   // Serialization/deserialization helpers
   // (Would need to implement Kotlin @Serializable annotations on data classes)
}

@Serializable
data class SerializedDependencyGraph(
   val nodes: List<SerializedFileNode>,
   val edges: List<Pair<String, String>>
)

@Serializable
data class SerializedFileNode(
   val sourcePath: String,
   val lastModified: Long,
   val contentHash: String,
   val declaredSymbols: List<String>,
   val importedSymbols: List<String>
)

@Serializable
data class SerializedFileArtifacts(
   val sourcePath: String,
   val typeNames: List<String>,
   val serviceNames: List<String>
   // Simplified - full implementation would serialize complete artifacts
)
```

#### 4.2 Cache Directory Management

**New File:** `compiler/src/main/java/lang/taxi/compiler/cache/CacheManager.kt`

```kotlin
package lang.taxi.compiler.cache

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Manages the persistent cache directory structure.
 *
 * Cache Layout:
 * .taxi-cache/
 *   ├── dependency-graph.json
 *   ├── artifacts/
 *   │   ├── <file-hash>.cache
 *   │   ├── <file-hash>.cache
 *   └── metadata.json
 */
class CacheManager(private val projectRoot: Path) {

   private val cacheRoot = projectRoot.resolve(".taxi-cache")
   private val graphPath = cacheRoot.resolve("dependency-graph.json")
   private val artifactsDir = cacheRoot.resolve("artifacts")

   fun ensureCacheDirectory() {
      Files.createDirectories(cacheRoot)
      Files.createDirectories(artifactsDir)
   }

   fun loadDependencyGraph(): DependencyGraph? {
      return CacheSerializer.loadDependencyGraph(graphPath)
   }

   fun saveDependencyGraph(graph: DependencyGraph) {
      ensureCacheDirectory()
      CacheSerializer.saveDependencyGraph(graph, graphPath)
   }

   fun loadArtifactCache(): ArtifactCache? {
      return CacheSerializer.loadArtifactCache(artifactsDir)
   }

   fun saveArtifactCache(cache: ArtifactCache) {
      ensureCacheDirectory()
      CacheSerializer.saveArtifactCache(cache, artifactsDir)
   }

   fun clearCache() {
      if (Files.exists(cacheRoot)) {
         Files.walk(cacheRoot)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.delete(it) }
      }
   }

   fun getCacheStats(): CacheStats {
      if (!Files.exists(cacheRoot)) {
         return CacheStats(exists = false, sizeBytes = 0, fileCount = 0)
      }

      var totalSize = 0L
      var fileCount = 0

      Files.walk(cacheRoot)
         .filter { Files.isRegularFile(it) }
         .forEach { file ->
            totalSize += Files.size(file)
            fileCount++
         }

      return CacheStats(exists = true, sizeBytes = totalSize, fileCount = fileCount)
   }
}

data class CacheStats(
   val exists: Boolean,
   val sizeBytes: Long,
   val fileCount: Int
)
```

**Deliverables:**
- ✅ Serialization infrastructure (requires kotlinx.serialization dependency)
- ✅ CacheManager for directory operations
- ✅ Integration with IncrementalCompiler
- ✅ Cache versioning and invalidation strategies
- ✅ Cache cleanup utilities

---

### Phase 5: Cross-File Effect Handling

**Goal:** Correctly handle type extensions, synonyms, and other global effects
**Effort:** 2-3 weeks
**Benefit:** Correctness for complex incremental scenarios

#### 5.1 Extension Dependency Tracking

**Challenge:** Type extensions in FileA affect types defined in FileB.

**Solution:** Track extension relationships in dependency graph.

**File:** `compiler/src/main/java/lang/taxi/compiler/DependencyGraph.kt`

Add extension tracking:

```kotlin
data class FileNode(
   val sourcePath: String,
   val metadata: FileMetadata,
   val declaredSymbols: Set<QualifiedName>,
   val importedSymbols: Set<QualifiedName>,
   val referencedSymbols: Set<QualifiedName>,
   val extensionTargets: Set<QualifiedName>,  // NEW
   val synonymTargets: Set<QualifiedName>     // NEW
)

class DependencyGraph {
   // NEW: Reverse lookup for extensions
   private val extensionSources = mutableMapOf<String, MutableSet<String>>()

   fun addExtension(extendingFile: String, extendedSymbol: QualifiedName) {
      extensionSources.getOrPut(extendedSymbol.fullyQualifiedName) {
         mutableSetOf()
      }.add(extendingFile)
   }

   fun getExtensionSources(symbol: QualifiedName): Set<String> {
      return extensionSources[symbol.fullyQualifiedName] ?: emptySet()
   }

   override fun computeRecompilationSet(changedFiles: Set<String>): Set<String> {
      val recompile = mutableSetOf<String>()

      changedFiles.forEach { changedFile ->
         recompile.add(changedFile)

         // Add direct dependents
         recompile.addAll(getTransitiveDependents(changedFile))

         // Add files with extensions targeting symbols in this file
         val node = getNode(changedFile)
         node?.declaredSymbols?.forEach { symbol ->
            recompile.addAll(getExtensionSources(symbol))
         }
      }

      return recompile
   }
}
```

#### 5.2 Synonym Propagation

**File:** `compiler/src/main/java/lang/taxi/compiler/TokenProcessor.kt`

Enhance synonym tracking:

```kotlin
private fun applySynonymsToEnums() {
   // Track which files contribute synonyms to which enums
   val synonymContributors = mutableMapOf<QualifiedName, MutableSet<String>>()

   val typesWithSynonyms = synonymRegistry.getTypesWithSynonymsRegistered()
   typeSystem.getTokens(includeImportedTypes = true) {
      typesWithSynonyms.contains(it.toQualifiedName())
   }
   .filterIsInstance<EnumType>()
   .forEach { enum ->
      val valueExtensions = typesWithSynonyms.getValue(enum.toQualifiedName())
         .flatMap { enumValueQualifiedName ->
            val (_, enumValueName) = Enums.splitEnumValueQualifiedName(enumValueQualifiedName)
            val synonyms = synonymRegistry.synonymsFor(enumValueQualifiedName)

            // Track source file for each synonym
            synonyms.forEach { (_, context) ->
               val sourceName = context.source().normalizedSourceName
               synonymContributors.getOrPut(enum.toQualifiedName()) {
                  mutableSetOf()
               }.add(sourceName)
            }

            // ... rest of synonym application
         }
   }

   // Store synonym contributors for dependency tracking
   // (Would need to expose this for DependencyGraph)
}
```

**Deliverables:**
- ✅ Extension dependency tracking
- ✅ Synonym dependency tracking
- ✅ Enhanced recompilation set computation
- ✅ Test suite for cross-file effects
- ✅ Documentation of edge cases

---

### Phase 6: CLI Integration & Developer Experience

**Goal:** Expose incremental compilation to users via CLI and build tools
**Effort:** 1-2 weeks
**Benefit:** Usability and adoption

#### 6.1 CLI Flag for Incremental Mode

**File:** `cli/src/main/java/lang/taxi/cli/CompileCommand.kt` (or equivalent)

```kotlin
@Command(
   name = "compile",
   description = "Compile Taxi sources"
)
class CompileCommand : Runnable {

   @Option(
      names = ["--incremental"],
      description = ["Enable incremental compilation (experimental)"]
   )
   var incremental: Boolean = false

   @Option(
      names = ["--cache-dir"],
      description = ["Directory for compilation cache (default: .taxi-cache)"]
   )
   var cacheDir: Path = Paths.get(".taxi-cache")

   @Option(
      names = ["--clear-cache"],
      description = ["Clear compilation cache before building"]
   )
   var clearCache: Boolean = false

   override fun run() {
      if (clearCache) {
         CacheManager(projectRoot).clearCache()
         println("Cache cleared")
      }

      val document = if (incremental) {
         compileIncremental()
      } else {
         compileNormal()
      }

      println("Compilation successful: ${document.types.size} types, ${document.services.size} services")
   }

   private fun compileIncremental(): TaxiDocument {
      val cacheManager = CacheManager(projectRoot)
      val previousGraph = cacheManager.loadDependencyGraph()
      val previousCache = cacheManager.loadArtifactCache()

      val compiler = IncrementalCompiler(
         sourceFiles = sourceFiles,
         dependencyGraph = previousGraph
      )

      if (previousCache != null) {
         compiler.artifactCache = previousCache
      }

      val document = compiler.compile()

      // Save for next run
      cacheManager.saveDependencyGraph(compiler.dependencyGraph!!)
      cacheManager.saveArtifactCache(compiler.artifactCache)

      return document
   }

   private fun compileNormal(): TaxiDocument {
      return Compiler.forFiles(sourceFiles).compile()
   }
}
```

#### 6.2 Build Tool Integration

**Maven Plugin Enhancement:**

```xml
<!-- pom.xml -->
<plugin>
   <groupId>org.taxilang</groupId>
   <artifactId>taxi-maven-plugin</artifactId>
   <version>1.71.0</version>
   <configuration>
      <incremental>true</incremental>
      <cacheDir>${project.build.directory}/.taxi-cache</cacheDir>
   </configuration>
</plugin>
```

**Gradle Plugin Enhancement:**

```kotlin
// build.gradle.kts
taxiCompile {
   incremental = true
   cacheDir = file("build/.taxi-cache")
}
```

#### 6.3 Logging and Diagnostics

**New File:** `compiler/src/main/java/lang/taxi/compiler/IncrementalCompilationStats.kt`

```kotlin
package lang.taxi.compiler

data class IncrementalCompilationStats(
   val totalFiles: Int,
   val changedFiles: Int,
   val recompiledFiles: Int,
   val cachedFiles: Int,
   val compilationTimeMs: Long,
   val cacheHitRate: Double
) {
   fun summary(): String {
      return """
         |Incremental Compilation Summary:
         |  Total files: $totalFiles
         |  Changed files: $changedFiles
         |  Recompiled files: $recompiledFiles
         |  Cached files: $cachedFiles
         |  Cache hit rate: ${(cacheHitRate * 100).format(2)}%
         |  Compilation time: ${compilationTimeMs}ms
      """.trimMargin()
   }
}

fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
```

**Deliverables:**
- ✅ CLI flag implementation
- ✅ Maven plugin integration
- ✅ Gradle plugin integration
- ✅ Logging and statistics
- ✅ User documentation

---

## Alternative Approach: Comprehensive Refactoring

**For consideration if surgical approach proves insufficient**

### Major Architectural Changes

1. **Modular TypeSystem**
   - Split TypeSystem into modules per namespace
   - Enable partial symbol table construction
   - Requires significant refactoring of symbol resolution

2. **Event-Driven Compilation**
   - Publish events for compilation phases
   - Allow listeners to subscribe to specific symbols
   - Enables more granular invalidation

3. **Query-Based Type Resolution**
   - Lazy type resolution on-demand
   - Cache resolved types separately from declarations
   - Reduces upfront compilation cost

**Effort:** 3-6 months
**Benefit:** Maximum performance, supports very large projects (10,000+ files)
**Risk:** High - requires extensive testing and potential API changes

---

## Testing Strategy

### Unit Tests

1. **CompilerTokenCache Tests**
   - Cache hit/miss scenarios
   - Invalidation on file change
   - Concurrent access

2. **DependencyGraph Tests**
   - Graph construction from tokens
   - Transitive dependent computation
   - Cycle detection

3. **IncrementalCompiler Tests**
   - Change detection
   - Recompilation set computation
   - Artifact merging

### Integration Tests

1. **Multi-File Projects**
   - Create realistic project structures
   - Simulate file changes
   - Verify correctness of incremental compilation

2. **Cross-File Effects**
   - Type extensions
   - Enum synonyms
   - Service lineage

3. **Edge Cases**
   - Circular dependencies
   - Renamed files
   - Deleted files

### Performance Benchmarks

**Baseline:** Full compilation time for projects of varying sizes

| Project Size | Full Compile | Incremental (1 file change) | Speedup |
|--------------|--------------|----------------------------|---------|
| Small (10 files) | ~100ms | ~30ms | 3.3x |
| Medium (100 files) | ~1s | ~150ms | 6.7x |
| Large (1000 files) | ~10s | ~500ms | 20x |
| Very Large (10000 files) | ~100s | ~2s | 50x |

**Test Scenarios:**
- Add field to type (minimal change)
- Change type signature (moderate impact)
- Rename type (high impact)
- Add new file (minimal change)

---

## Migration Path

### Phase 1: Opt-In Beta (Taxi 1.72)
- Incremental compilation behind `--incremental` flag
- Extensive testing with early adopters
- Collect performance data and bug reports

### Phase 2: Default with Fallback (Taxi 1.73)
- Incremental compilation enabled by default
- Automatic fallback to full compile on cache corruption
- Improved error messages

### Phase 3: Mandatory (Taxi 1.74)
- Remove full compilation path (unless issues discovered)
- Optimize for maximum performance

---

## Risk Assessment

### High Risk Areas

1. **Correctness**
   - **Risk:** Incremental compilation produces different results than full compilation
   - **Mitigation:** Extensive testing, checksum validation, fallback mechanism

2. **Cache Corruption**
   - **Risk:** Corrupted cache causes compilation failures
   - **Mitigation:** Cache versioning, integrity checks, automatic rebuilds

3. **Performance Regression**
   - **Risk:** Overhead of dependency tracking slows down full compilation
   - **Mitigation:** Performance benchmarks, lazy graph construction

### Medium Risk Areas

1. **Disk Space Usage**
   - **Risk:** Cache grows unbounded
   - **Mitigation:** Cache cleanup utilities, size limits

2. **Concurrent Compilation**
   - **Risk:** Multiple compilations corrupt shared cache
   - **Mitigation:** File locking, atomic updates

### Low Risk Areas

1. **API Compatibility**
   - Most changes are internal
   - Existing Compiler API remains unchanged

---

## Success Metrics

1. **Performance**
   - 50% reduction in compilation time for typical changes
   - 80% cache hit rate in real projects

2. **Correctness**
   - Zero incorrect compilation results in testing
   - Equivalence with full compilation (checksum validation)

3. **Adoption**
   - 70% of projects enable incremental compilation within 6 months
   - Positive developer feedback

---

## Recommendations

### Recommended Approach: Phased Surgical Implementation

**Rationale:**
- Taxi compiler architecture is well-suited for incremental compilation
- Surgical approach minimizes risk and allows iterative validation
- Can deliver meaningful performance improvements quickly

**Timeline:**
- Phase 1 (Foundation): 2-3 weeks
- Phase 2 (Dependency Graph): 2-3 weeks
- Phase 3 (Incremental Logic): 3-4 weeks
- Phase 4 (Persistent Cache): 2-3 weeks (optional)
- Phase 5 (Cross-File Effects): 2-3 weeks
- Phase 6 (CLI Integration): 1-2 weeks

**Total:** 12-18 weeks for full implementation

### Quick Win: Token-Level Caching Only

**If full incremental compilation is deferred:**
- Implement Phase 1 only (enhanced CompilerTokenCache)
- Provides 30-40% speedup with minimal changes
- Foundation for future phases

---

## Next Steps

1. **Review and Approval**
   - Stakeholder review of plan
   - Decision on approach (surgical vs. comprehensive)
   - Budget and timeline approval

2. **Prototype**
   - Implement Phase 1 as proof-of-concept
   - Validate assumptions with performance benchmarks
   - Identify any architectural blockers

3. **Full Implementation**
   - Execute phases sequentially
   - Continuous testing and validation
   - Early beta release for feedback

---

## Appendix A: Key Files Reference

| File | Location | Purpose |
|------|----------|---------|
| **Compiler.kt** | `/compiler/src/main/java/lang/taxi/Compiler.kt` | Main compilation orchestration |
| **TokenProcessor.kt** | `/compiler/src/main/java/lang/taxi/compiler/TokenProcessor.kt` | Multi-phase compilation logic |
| **TokenCollator.kt** | `/compiler/src/main/java/lang/taxi/TokenCollator.kt` | ANTLR listener, token collection |
| **TokenStore.kt** | `/compiler/src/main/java/lang/taxi/TokenStore.kt` | Source metadata tracking |
| **TypeSystem.kt** | `/compiler/src/main/java/lang/taxi/TypeSystem.kt` | Symbol table and resolution |
| **ImportedTypeCollator.kt** | `/compiler/src/main/java/lang/taxi/compiler/ImportTypeCollator.kt` | Dependency traversal |
| **TaxiDocument.kt** | `/compiler/src/main/java/lang/taxi/TaxiDocument.kt` | Compilation output |

---

## Appendix B: Glossary

- **Incremental Compilation:** Recompiling only changed files and their dependents
- **Dependency Graph:** DAG representing file-level dependencies
- **Cache Hit:** Successfully reusing previously compiled artifacts
- **Invalidation:** Marking cached artifacts as stale requiring recompilation
- **Transitive Dependents:** All files that depend on a changed file, directly or indirectly
- **Cross-File Effect:** Compilation artifact in FileA affecting types in FileB (e.g., extensions)

---

**End of Plan**

# ANTLR4-c3 Code Completion Implementation

## Overview

This document describes the new **C3CompletionProvider** - an experimental grammar-based code completion implementation using ANTLR4-c3 (Code Completion Core).

## Motivation

The existing completion system uses pattern matching on AST contexts, which has several limitations:

1. **Brittle to grammar changes** - When the grammar evolves, completion logic needs manual updates
2. **Fails on incomplete expressions** - Generic expression atoms don't provide enough context
3. **Manual context checking** - Duplicates parser logic by manually searching the AST tree
4. **Growing when blocks** - Each new context type requires a new when branch

See `EditorCompletionService.kt:198-232` for a documented example of where the current approach fails.

## Solution: Grammar-Based Completions

The C3CompletionProvider uses the **ANTLR4 Code Completion Core** library to derive valid completions directly from the grammar, rather than manually pattern matching AST nodes.

### How it Works

1. **Get current token position** - Find the token at the cursor location
2. **Query the grammar** - Ask ANTLR's ATN (Abstract Syntax Tree Network) what tokens/rules are valid at that position
3. **Return syntactic completions** - Convert grammar suggestions to LSP CompletionItems

### Architecture

```
User types → Cursor Position
             ↓
         Find Token Index
             ↓
     CodeCompletionCore (c3)
             ↓
         Query ATN + Grammar
             ↓
     Valid Tokens + Rules
             ↓
   Convert to CompletionItems
```

## Files

### New Files

- **`C3CompletionProvider.kt`** - Main implementation of grammar-based completions
- **`C3CompletionProviderTest.kt`** - Test suite validating syntactic completions

### Modified Files

- **`language-server/taxi-lang-service/pom.xml`** - Added antlr4-c3-kotlin dependency
- **`CompositeCompletionService.kt`** - Registered C3CompletionProvider in the provider chain

## Dependencies

```xml
<dependency>
    <groupId>me.tomassetti.antlr4c3</groupId>
    <artifactId>antlr4-c3-kotlin</artifactId>
    <version>0.1.0</version>
</dependency>
```

This is a Kotlin port of the original TypeScript antlr4-c3 library by Mike Lischke.

## Integration

The C3CompletionProvider is integrated into the `CompositeCompletionService` and runs **alongside** existing providers:

```kotlin
CompositeCompletionService(
    listOf(
        C3CompletionProvider(),           // Grammar-based syntactic completions
        EditorCompletionService(),        // Existing context-based completions
        DefaultCompletionProvider()       // Existing type/function completions
    )
)
```

Completions from all providers are combined, giving users both:
- **Syntactic** completions (what's grammatically valid)
- **Semantic** completions (what types/functions are in scope)

## Configuration

### Ignored Tokens

The provider ignores whitespace and comments:

```kotlin
IGNORED_TOKENS = setOf(
    TaxiLexer.EOF,
    TaxiLexer.WS,
    TaxiLexer.LINE_COMMENT,
    TaxiLexer.BLOCK_COMMENT
)
```

### Preferred Rules

Currently not configured, but can be used to prioritize certain grammar rules:

```kotlin
core.preferredRules = setOf(
    TaxiParser.RULE_typeReference,
    TaxiParser.RULE_identifier
)
```

## Success Criteria

The goal of this implementation is to **validate whether grammar-based completions are viable** for Taxi.

### Phase 1: Syntactic Completions (Current)

✅ Get valid **tokens** at cursor position
✅ Get valid **rules** at cursor position
✅ Convert to LSP CompletionItems
✅ Integrate with existing providers

### Phase 2: Semantic Layer (Future)

Once syntactic completions prove useful, add semantic filtering:

- Filter tokens by types in scope
- Resolve enum values for enum contexts
- Add import suggestions for types
- Provide field suggestions for objects

## Known Limitations

1. **Version** - Using antlr4-c3-kotlin 0.1.0 (from 2017)
   - May not support all ANTLR 4.12 features
   - Consider updating if issues arise

2. **Syntactic Only** - Currently only provides grammar-valid tokens
   - Doesn't filter by types in scope
   - Doesn't resolve semantic symbols
   - Can result in many irrelevant suggestions

3. **Performance** - Creates new parser instances for metadata
   - Should cache ATN/vocabulary if performance is an issue

## Testing

Run the C3 completion tests:

```bash
./mvnw test -pl language-server/taxi-lang-service -Dtest=C3CompletionProviderSpec
```

Key test scenarios:

- Keyword completions at file start
- Completions in type bodies
- Incomplete field definitions
- **Incomplete expressions** (the failing scenario from the old approach)
- Annotation parameter completions

## Debugging

Enable debug logging to see what c3 returns:

```kotlin
log().debug("C3 found ${candidates.tokens.size} token candidates and ${candidates.rules.size} rule candidates")
```

## Future Work

### If C3 Proves Successful

1. **Add semantic filtering** - Combine grammar suggestions with type repository
2. **Cache parser metadata** - Avoid recreating ATN for each completion request
3. **Tune preferred rules** - Guide c3 toward more useful suggestions
4. **Update antlr4-c3** - Consider newer versions or reimplementation

### If C3 Doesn't Work Well

Fall back to improving the existing pattern-matching approach:

- Better error recovery tokens
- Fallback completion chains
- Use last successful compilation for context

## References

- [Original antlr4-c3 (TypeScript)](https://github.com/mike-lischke/antlr4-c3)
- [antlr4-c3-kotlin (Kotlin port)](https://github.com/ftomassetti/antlr4-c3-kotlin)
- [Strumenta article on c3](https://tomassetti.me/code-completion-with-antlr4-c3/)
- [Ballerina LSP using c3](https://medium.com/ballerina-techblog/language-server-for-ballerina-auto-completion-engine-in-depth-ee20e543ac26)

## Questions?

See the implementation in `C3CompletionProvider.kt` or the original analysis document.

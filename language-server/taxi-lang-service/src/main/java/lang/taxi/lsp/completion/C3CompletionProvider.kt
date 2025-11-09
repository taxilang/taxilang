package lang.taxi.lsp.completion

import com.strumenta.antlr4c3.CodeCompletionCore
import lang.taxi.TaxiLexer
import lang.taxi.TaxiParser
import lang.taxi.lsp.CompilationResult
import lang.taxi.utils.log
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionParams
import java.util.concurrent.CompletableFuture

/**
 * A completion provider that uses ANTLR4-c3 (Code Completion Core) to provide
 * grammar-based syntactic completions.
 *
 * This provider focuses on determining what tokens are syntactically valid at the
 * cursor position, based on the Taxi grammar itself, rather than manually matching
 * AST contexts.
 *
 * This is experimental - the goal is to validate whether c3 provides better
 * completions than manual AST pattern matching, especially for incomplete expressions.
 */
class C3CompletionProvider : CompletionProvider {

    companion object {
        private val IGNORED_TOKENS = setOf(
            TaxiLexer.EOF,
            TaxiLexer.WS,
            TaxiLexer.LINE_COMMENT,
            TaxiLexer.BLOCK_COMMENT
        )
    }

    override fun getCompletionsForContext(
        compilationResult: CompilationResult,
        params: CompletionParams,
        importDecorator: ImportCompletionDecorator,
        contextAtCursor: ParserRuleContext?,
        lastSuccessfulCompilation: CompilationResult?,
        typeRepository: TypeRepository
    ): CompletableFuture<CompletionItemList> {

        try {
            val completions = collectC3Completions(compilationResult, params, contextAtCursor)
            log().info("C3CompletionProvider returned ${completions.size} completions")
            return completed(completions)
        } catch (e: Exception) {
            log().error("Error in C3CompletionProvider", e)
            return completed(emptyList())
        }
    }

    private fun collectC3Completions(
        compilationResult: CompilationResult,
        params: CompletionParams,
        contextAtCursor: ParserRuleContext?
    ): List<CompletionItem> {

        // Get the source
        val source = compilationResult.getSource(params.textDocument) ?: run {
            log().debug("No source found for ${params.textDocument.uri}")
            return emptyList()
        }

        // Find the token index at cursor position
        val tokenIndex = findTokenIndexAtPosition(compilationResult, params)
        if (tokenIndex == null) {
            log().debug("Could not find token at position ${params.position.line}:${params.position.character}")
            return emptyList()
        }

        log().debug("Found token index $tokenIndex at position ${params.position.line}:${params.position.character}")

        // Create a parser instance for the current source
        val parser = createTaxiParser(compilationResult, params)

        // Create the CodeCompletionCore with the Java API
        // Constructor: CodeCompletionCore(Parser parser, Set<Integer> preferredRules, Set<Integer> ignoredTokens)
        val core = CodeCompletionCore(
            parser,
            null, // preferredRules - null means no preference
            IGNORED_TOKENS
        )

        // Collect candidates
        // Java API: collectCandidates(int caretTokenIndex, ParserRuleContext context)
        val candidates = core.collectCandidates(tokenIndex, contextAtCursor)

        log().debug("C3 found ${candidates.tokens.size} token candidates and ${candidates.rules.size} rule candidates")

        // Convert candidates to LSP CompletionItems
        return convertCandidatesToCompletions(candidates, parser)
    }

    private fun findTokenIndexAtPosition(
        compilationResult: CompilationResult,
        params: CompletionParams
    ): Int? {
        val normalizedUri = params.textDocument.normalizedUriPath()
        val input = compilationResult.compiler.inputs.firstOrNull {
            it.sourceName == normalizedUri
        } ?: return null

        val lexer = TaxiLexer(input)
        val tokens = org.antlr.v4.runtime.CommonTokenStream(lexer)
        tokens.fill()

        val line = params.position.line // 0-based in LSP
        val char = params.position.character

        // Find the token at or before the cursor position
        val tokenList = tokens.tokens
        for (i in tokenList.indices) {
            val token = tokenList[i]
            val tokenLine = token.line - 1 // ANTLR is 1-based
            val tokenChar = token.charPositionInLine

            // If this token is at or after cursor, return previous token index
            if (tokenLine > line || (tokenLine == line && tokenChar >= char)) {
                return maxOf(0, i - 1)
            }
        }

        // Cursor is after all tokens
        return maxOf(0, tokenList.size - 1)
    }

    private fun createTaxiParser(compilationResult: CompilationResult, params: CompletionParams): TaxiParser {
        // Create a parser for the actual source file being edited
        val normalizedUri = params.textDocument.normalizedUriPath()
        val input = compilationResult.compiler.inputs.firstOrNull {
            it.sourceName == normalizedUri
        }

        if (input != null) {
            val lexer = TaxiLexer(input)
            val tokens = org.antlr.v4.runtime.CommonTokenStream(lexer)
            return TaxiParser(tokens)
        }

        // Fallback to dummy parser if source not found
        val dummyInput = org.antlr.v4.runtime.CharStreams.fromString("")
        val lexer = TaxiLexer(dummyInput)
        val tokens = org.antlr.v4.runtime.CommonTokenStream(lexer)
        return TaxiParser(tokens)
    }

    private fun convertCandidatesToCompletions(
        candidates: CodeCompletionCore.CandidatesCollection,
        parser: Parser
    ): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()

        // Convert token candidates
        candidates.tokens.forEach { (tokenType, _) ->
            val tokenName = parser.vocabulary.getSymbolicName(tokenType) ?: return@forEach
            val displayName = parser.vocabulary.getDisplayName(tokenType)

            // Convert token names to user-friendly labels
            val label = when {
                displayName.startsWith("'") && displayName.endsWith("'") ->
                    displayName.substring(1, displayName.length - 1)
                tokenName.startsWith("K_") ->
                    tokenName.substring(2).lowercase()
                else -> displayName
            }

            completions.add(CompletionItem(label).apply {
                kind = CompletionItemKind.Keyword
                detail = "Keyword (from grammar)"
                documentation = markdown("Syntactically valid token at this position")
                insertText = label
            })
        }

        // Convert rule candidates
        candidates.rules.forEach { (ruleIndex, _) ->
            val ruleName = parser.ruleNames.getOrNull(ruleIndex) ?: return@forEach

            // Convert rule names to user-friendly labels
            val label = when (ruleName) {
                "typeReference" -> "Type reference"
                "identifier" -> "Identifier"
                "expressionGroup" -> "Expression"
                "typeDeclaration" -> "Type declaration"
                "enumDeclaration" -> "Enum declaration"
                else -> ruleName
            }

            completions.add(CompletionItem(label).apply {
                kind = CompletionItemKind.Snippet
                detail = "Expected: $ruleName"
                documentation = markdown("Grammar rule: $ruleName")
                // For now, don't provide insertText for rules - they're just hints
            })
        }

        return completions
    }
}

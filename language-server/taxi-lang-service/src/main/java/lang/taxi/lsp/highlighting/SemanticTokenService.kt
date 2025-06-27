package lang.taxi.lsp.highlighting

import lang.taxi.TaxiLexer
import lang.taxi.TaxiParser.TypeKindContext
import lang.taxi.lsp.CompilationResult
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokens
import java.util.concurrent.CompletableFuture

class SemanticTokenService {
   companion object {
      val operators = setOf(
         TaxiLexer.MULT,
         TaxiLexer.DIV,
         TaxiLexer.PLUS,
         TaxiLexer.MINUS,
         TaxiLexer.MOD,
         TaxiLexer.GT,
         TaxiLexer.GE,
         TaxiLexer.LT,
         TaxiLexer.LE,
         TaxiLexer.EQ,
         TaxiLexer.NQ,
         TaxiLexer.COALESCE,
         TaxiLexer.LOGICAL_OR,
         TaxiLexer.LOGICAL_AND,
      )
      val punctuation = setOf(
         TaxiLexer.LPAREN,
         TaxiLexer.RPAREN,
      )

      val keywords = setOf(
         "namespace",
         "import",
         "parameter",
         "closed",
         "type",
         "model",
         "inherits",
         "by",
         "annotation",
         "enum",
         "lenient",
         "synonym",
         "of",
         "alias",
         "as",
         "service",
         "operation",
         "table",
         "stream",
         "lineage",
         "consumes",
         "stores",
         "read",
         "write",
         "policy",
         "against",
         "internal",
         "external",
         "declare",
         "function",
         "extension",
         "query",
         "find",
         "map",
         "given",
         "when",
         "else",
         "this",
         "using",
         "excluding",
         "call",
         "with",
      )
      val tokensToIndex: Map<TaxiSemanticTokenType, Int> = TaxiSemanticTokenType.values()
         .associateWith { SemanticTokenTypes.ALL.indexOf(it.tokenType) }
   }

   fun computeTokens(
      lastCompilationResult: CompilationResult,
      lastSuccessfulCompilation: CompilationResult?,
      uri: String,
      range: Range?
   ): CompletableFuture<SemanticTokens> {
      val tokenTable = lastCompilationResult.compiler.tokens.tokenStore.tokenTable(uri)
      val rootToken = tokenTable.values().first()
      val semanticTokens = semanticTokensFromContext(rootToken)
      val tokensInVsCodeFormat = semanticTokens.flatMap { it.toIntArray() }
      return CompletableFuture.completedFuture(SemanticTokens(tokensInVsCodeFormat))
   }

   fun semanticTokensFromContext(
      ctx: ParserRuleContext,  // The parsed context from your compiler
      tokenTypes: List<String> = SemanticTokenTypes.ALL,
      tokenModifiers: List<String> = SemanticTokenModifiers.ALL
   ): List<SemanticHighlightToken> {
      val semanticTokens = mutableListOf<SemanticHighlightToken>()
      var prevLine = 0
      var prevChar = 0

      // Walk through the context's tokens
      for (i in 0 until ctx.childCount) {
         val child = ctx.getChild(i)
         when (child) {
            is TerminalNode -> {
               val token = child.symbol
               val line = token.line - 1  // Convert to 0-based
               val startChar = token.charPositionInLine
               val deltaLine = line - prevLine
               val deltaChar = if (deltaLine == 0) startChar - prevChar else startChar

               // Map the token type based on your grammar
               val taxiTokenType: TaxiSemanticTokenType = when {
                  token.type == -1 -> continue // EOF
                  operators.contains(token.type) -> TaxiSemanticTokenType.OPERATOR
                  punctuation.contains(token.type) -> TaxiSemanticTokenType.PUNCTUATION
                  // Example mappings - adjust based on your Taxi grammar
                  token.type == TaxiLexer.IdentifierToken -> {
                     getIdentifierKind(child, token)
                  }

                  TaxiLexer.ruleNames.getOrNull(token.type)?.startsWith("T__") ?: false -> {
                     // These are T__ rules -- which are basically tokens that we haven't defined names
                     // for. Things like . fall into this category.
                     // Just ignore them.
                     continue
                  }


                  token.type == TaxiLexer.IntegerLiteral -> TaxiSemanticTokenType.NUMERIC_LITERAL
                  token.type == TaxiLexer.BooleanLiteral -> TaxiSemanticTokenType.NUMERIC_LITERAL
                  token.type == TaxiLexer.DecimalLiteral -> TaxiSemanticTokenType.NUMERIC_LITERAL
                  token.type == TaxiLexer.StringLiteral -> TaxiSemanticTokenType.STRING_LITERAL
                  keywords.contains(token.text) -> TaxiSemanticTokenType.KEYWORD

                  else -> {
                     // TODO: Add more mappings here...
                     continue
                  }
               }

               // Determine modifiers based on context
               val modifiers = buildModifiers(child.parent as RuleContext?, tokenModifiers)

               // LSP format: line delta, character delta, length, token type, modifiers
               semanticTokens.add(
                  SemanticHighlightToken(
                     deltaLine,
                     deltaChar,
                     token.text.length,
                     taxiTokenType,
                     modifiers
                  )
               )

               prevLine = line
               prevChar = startChar
            }

            is ParserRuleContext -> {
               // Recursively process nested contexts
               semanticTokens.addAll(semanticTokensFromContext(child, tokenTypes, tokenModifiers))
            }
         }
      }
      return semanticTokens
   }

   private fun getIdentifierKind(child: TerminalNode, token: Token): TaxiSemanticTokenType {
      return when {
         child.parent is TypeKindContext && token.text == "model" -> TaxiSemanticTokenType.MODEL
         child.parent is TypeKindContext && token.text == "type" -> TaxiSemanticTokenType.TYPE
         else -> {
            // Unhandled - add a mapping for this
            // THis is a default.
            TaxiSemanticTokenType.ENTITY_NAME
         }
      }
   }

   private fun buildModifiers(
      context: RuleContext?,
      tokenModifiers: List<String>
   ): Int {
      var modifierBits = 0

      return 0
      // Example modifier logic - adjust based on your needs
//      when (context) {
//         is YourTypeDeclarationContext -> {
//            modifierBits = modifierBits or (1 shl tokenModifiers.indexOf(SemanticTokenModifiers.DECLARATION))
//         }
//         is YourClosedModelContext -> {
//            modifierBits = modifierBits or (1 shl tokenModifiers.indexOf(SemanticTokenModifiers.READONLY))
//         }
//         // Add more context checks...
//      }
//
//      return modifierBits
   }
}

data class SemanticHighlightToken(
   val line: Int,
   val char: Int,
   val length: Int,
   val tokenType: TaxiSemanticTokenType,
   val modifiers: Int
) {
   private val tokenTypeIndex = tokensToIndex[tokenType]!!

   companion object {
      val tokensToIndex: Map<TaxiSemanticTokenType, Int> = TaxiSemanticTokenType.values()
         .associateWith { SemanticTokenTypes.ALL.indexOf(it.tokenType) }

   }

   // LSP format: line delta, character delta, length, token type, modifiers
   fun toIntArray(): List<Int> {
      return listOf(
         line, char, length, tokenTypeIndex, modifiers
      )
   }
}

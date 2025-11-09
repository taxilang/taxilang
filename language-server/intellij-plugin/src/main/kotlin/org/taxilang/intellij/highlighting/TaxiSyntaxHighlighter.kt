package org.taxilang.intellij.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import lang.taxi.TaxiLexer
import org.taxilang.intellij.highlighting.TaxiTokenTypes

import org.taxilang.intellij.highlighting.TaxiTokenTypes.BOOLEAN_LITERAL
import org.taxilang.intellij.highlighting.TaxiTokenTypes.COMMENT
import org.taxilang.intellij.highlighting.TaxiTokenTypes.DECIMAL_LITERAL
import org.taxilang.intellij.highlighting.TaxiTokenTypes.DOCUMENTATION
import org.taxilang.intellij.highlighting.TaxiTokenTypes.FALSE
import org.taxilang.intellij.highlighting.TaxiTokenTypes.IDENTIFIER_TOKEN
import org.taxilang.intellij.highlighting.TaxiTokenTypes.INTEGER_LITERAL
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_ALIAS
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_AS
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_CALL
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_CLOSED
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_DECLARE
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_ELSE
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_EXCEPT
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_EXCLUDING
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_EXTENSION
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_FILTER
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_FIND
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_GIVEN
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_IMPORT
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_INHERITS
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_MAP
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_MODEL
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_NAMESPACE
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_PARAMETER
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_PARTIAL
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_QUERY
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_READ
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_STREAM
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_TABLE
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_TYPE
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_USING
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_WHEN
import org.taxilang.intellij.highlighting.TaxiTokenTypes.K_WRITE
import org.taxilang.intellij.highlighting.TaxiTokenTypes.LINE_COMMENT
import org.taxilang.intellij.highlighting.TaxiTokenTypes.LPAREN
import org.taxilang.intellij.highlighting.TaxiTokenTypes.NAME
import org.taxilang.intellij.highlighting.TaxiTokenTypes.RPAREN
import org.taxilang.intellij.highlighting.TaxiTokenTypes.STRING
import org.taxilang.intellij.highlighting.TaxiTokenTypes.STRING_LITERAL
import org.taxilang.intellij.highlighting.TaxiTokenTypes.TRUE



class TaxiSyntaxHighlighter : SyntaxHighlighterBase() {
   companion object {
      private fun TokenSet.asTextAttributes(attributes: TextAttributesKey): TokensWithConfig {
         val taxiName = "TAXI_${attributes.externalName}"
         val taxiAttributes = TextAttributesKey.createTextAttributesKey(
            taxiName,
            attributes
         )
         return TokensWithConfig(this, arrayOf(taxiAttributes))
      }

      private data class TokensWithConfig(
         val tokens: TokenSet,
         val attributes: Array<TextAttributesKey>
      )

      private val KEYWORDS = TokenSet.create(
         TaxiTokenTypes.K_TYPE, K_MODEL, K_ALIAS, K_PARTIAL, K_CLOSED, K_PARAMETER, K_INHERITS,
         K_AS, K_EXTENSION, K_IMPORT, K_NAMESPACE, K_QUERY, K_FIND, K_MAP, K_GIVEN,
         K_STREAM, K_TABLE, K_FILTER, K_CALL, K_READ, K_WRITE, K_DECLARE,
         K_WHEN, K_ELSE, K_EXCEPT, K_USING, K_EXCLUDING
      ).asTextAttributes(DefaultLanguageHighlighterColors.KEYWORD)

      private val LINE_COMMENTS = TokenSet.create(
         LINE_COMMENT
      ).asTextAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT)

      private val BLOCK_COMMENTS = TokenSet.create(
         COMMENT
      ).asTextAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT)

      private val DOC_COMMENTS = TokenSet.create(
         DOCUMENTATION
      ).asTextAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT)

      private val STRINGS = TokenSet.create(
         STRING_LITERAL, STRING
      ).asTextAttributes(DefaultLanguageHighlighterColors.STRING)

      private val NUMBERS = TokenSet.create(
         INTEGER_LITERAL, DECIMAL_LITERAL, TaxiTokenTypes.NUMBER
      ).asTextAttributes(DefaultLanguageHighlighterColors.NUMBER)

      private val BOOLEANS = TokenSet.create(
         BOOLEAN_LITERAL, TRUE, FALSE
      ).asTextAttributes(DefaultLanguageHighlighterColors.KEYWORD)

      private val OPERATORS = TokenSet.create(
         TaxiTokenTypes.IN, TaxiTokenTypes.NOT_IN, TaxiTokenTypes.LIKE,
         TaxiTokenTypes.AND, TaxiTokenTypes.OR, TaxiTokenTypes.NOT,
         TaxiTokenTypes.LOGICAL_AND, TaxiTokenTypes.LOGICAL_OR, TaxiTokenTypes.COALESCE,
         TaxiTokenTypes.PLUS, TaxiTokenTypes.MINUS, TaxiTokenTypes.MULT,
         TaxiTokenTypes.DIV, TaxiTokenTypes.MOD,
         TaxiTokenTypes.GT, TaxiTokenTypes.GE, TaxiTokenTypes.LT,
         TaxiTokenTypes.LE, TaxiTokenTypes.EQ, TaxiTokenTypes.NQ,
         TaxiTokenTypes.SPREAD_OPERATOR
      ).asTextAttributes(DefaultLanguageHighlighterColors.OPERATION_SIGN)

      private val PARENTHESES = TokenSet.create(
         LPAREN, RPAREN
      ).asTextAttributes(DefaultLanguageHighlighterColors.PARENTHESES)

      private val IDENTIFIERS = TokenSet.create(
         IDENTIFIER_TOKEN, NAME
      ).asTextAttributes(DefaultLanguageHighlighterColors.IDENTIFIER)

      private val attributeSets: Set<TokensWithConfig> = setOf(
         KEYWORDS,
         LINE_COMMENTS,
         BLOCK_COMMENTS,
         DOC_COMMENTS,
         STRINGS,
         NUMBERS,
         BOOLEANS,
         OPERATORS,
         PARENTHESES,
         IDENTIFIERS
      )
   }

   override fun getHighlightingLexer(): Lexer = TaxiLexerAdapter()

   override fun getTokenHighlights(elementType: IElementType): Array<TextAttributesKey> {
      return attributeSets.firstOrNull { it.tokens.contains(elementType) }
         ?.attributes ?: emptyArray()
   }
}

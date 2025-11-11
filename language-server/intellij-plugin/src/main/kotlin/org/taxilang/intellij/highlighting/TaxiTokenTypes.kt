package org.taxilang.intellij.highlighting

import com.intellij.psi.tree.TokenSet
import lang.taxi.TaxiLexer
import lang.taxi.TaxiParser
import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory
import org.antlr.intellij.adaptor.lexer.TokenIElementType
import org.taxilang.intellij.TaxiLanguage

object TaxiTokenTypes {
   init {
      // This must be called before accessing any token types
      // It generates IElementTypes for all tokens defined in your lexer
      PSIElementTypeFactory.defineLanguageIElementTypes(
         TaxiLanguage,
         TaxiLexer.tokenNames,
         TaxiParser.ruleNames
      )
   }

   // Get all token element types
   private val tokenElementTypes: MutableList<TokenIElementType> = PSIElementTypeFactory.getTokenIElementTypes(TaxiLanguage)
   // Comments and whitespace
   val LINE_COMMENT: TokenIElementType = tokenElementTypes[TaxiLexer.LINE_COMMENT]
   val COMMENT: TokenIElementType = tokenElementTypes[TaxiLexer.COMMENT]
   val DOCUMENTATION: TokenIElementType = tokenElementTypes[TaxiLexer.DOCUMENTATION]
   val WS: TokenIElementType = tokenElementTypes[TaxiLexer.WS]

   // Literals
   val STRING_LITERAL: TokenIElementType = tokenElementTypes[TaxiLexer.StringLiteral]
   val INTEGER_LITERAL: TokenIElementType = tokenElementTypes[TaxiLexer.IntegerLiteral]
   val DECIMAL_LITERAL: TokenIElementType = tokenElementTypes[TaxiLexer.DecimalLiteral]
   val BOOLEAN_LITERAL: TokenIElementType = tokenElementTypes[TaxiLexer.BooleanLiteral]

   // Keywords - type system
   val K_TYPE: TokenIElementType = tokenElementTypes[TaxiLexer.K_Type]
   val K_MODEL: TokenIElementType = tokenElementTypes[TaxiLexer.K_Model]
   val K_ALIAS: TokenIElementType = tokenElementTypes[TaxiLexer.K_Alias]
   val K_PARTIAL: TokenIElementType = tokenElementTypes[TaxiLexer.K_Partial]
   val K_CLOSED: TokenIElementType = tokenElementTypes[TaxiLexer.K_Closed]
   val K_PARAMETER: TokenIElementType = tokenElementTypes[TaxiLexer.K_Parameter]
   val K_INHERITS: TokenIElementType = tokenElementTypes[TaxiLexer.K_Inherits]
   val K_AS: TokenIElementType = tokenElementTypes[TaxiLexer.K_As]
   val K_EXTENSION: TokenIElementType = tokenElementTypes[TaxiLexer.K_Extension]

   // Keywords - namespace/import
   val K_IMPORT: TokenIElementType = tokenElementTypes[TaxiLexer.K_Import]
   val K_NAMESPACE: TokenIElementType = tokenElementTypes[TaxiLexer.K_Namespace]

   // Keywords - query
   val K_QUERY: TokenIElementType = tokenElementTypes[TaxiLexer.K_Query]
   val K_FIND: TokenIElementType = tokenElementTypes[TaxiLexer.K_Find]
   val K_MAP: TokenIElementType = tokenElementTypes[TaxiLexer.K_Map]
   val K_GIVEN: TokenIElementType = tokenElementTypes[TaxiLexer.K_Given]
   val K_STREAM: TokenIElementType = tokenElementTypes[TaxiLexer.K_Stream]
   val K_TABLE: TokenIElementType = tokenElementTypes[TaxiLexer.K_Table]
   val K_FILTER: TokenIElementType = tokenElementTypes[TaxiLexer.K_Filter]
   val K_CALL: TokenIElementType = tokenElementTypes[TaxiLexer.K_Call]

   // Keywords - operations
   val K_READ: TokenIElementType = tokenElementTypes[TaxiLexer.K_Read]
   val K_WRITE: TokenIElementType = tokenElementTypes[TaxiLexer.K_Write]
   val K_DECLARE: TokenIElementType = tokenElementTypes[TaxiLexer.K_Declare]

   // Keywords - control flow
   val K_WHEN: TokenIElementType = tokenElementTypes[TaxiLexer.K_When]
   val K_ELSE: TokenIElementType = tokenElementTypes[TaxiLexer.K_Else]

   // Keywords - other
   val K_EXCEPT: TokenIElementType = tokenElementTypes[TaxiLexer.K_Except]
   val K_USING: TokenIElementType = tokenElementTypes[TaxiLexer.K_Using]
   val K_EXCLUDING: TokenIElementType = tokenElementTypes[TaxiLexer.K_Excluding]

   // Operators
   val IN: TokenIElementType = tokenElementTypes[TaxiLexer.IN]
   val NOT_IN: TokenIElementType = tokenElementTypes[TaxiLexer.NOT_IN]
   val LIKE: TokenIElementType = tokenElementTypes[TaxiLexer.LIKE]
   val AND: TokenIElementType = tokenElementTypes[TaxiLexer.AND]
   val OR: TokenIElementType = tokenElementTypes[TaxiLexer.OR]
   val NOT: TokenIElementType = tokenElementTypes[TaxiLexer.NOT]
   val LOGICAL_AND: TokenIElementType = tokenElementTypes[TaxiLexer.LOGICAL_AND]
   val LOGICAL_OR: TokenIElementType = tokenElementTypes[TaxiLexer.LOGICAL_OR]
   val COALESCE: TokenIElementType = tokenElementTypes[TaxiLexer.COALESCE]

   // Arithmetic operators
   val PLUS: TokenIElementType = tokenElementTypes[TaxiLexer.PLUS]
   val MINUS: TokenIElementType = tokenElementTypes[TaxiLexer.MINUS]
   val MULT: TokenIElementType = tokenElementTypes[TaxiLexer.MULT]
   val DIV: TokenIElementType = tokenElementTypes[TaxiLexer.DIV]
   val MOD: TokenIElementType = tokenElementTypes[TaxiLexer.MOD]

   // Comparison operators
   val GT: TokenIElementType = tokenElementTypes[TaxiLexer.GT]
   val GE: TokenIElementType = tokenElementTypes[TaxiLexer.GE]
   val LT: TokenIElementType = tokenElementTypes[TaxiLexer.LT]
   val LE: TokenIElementType = tokenElementTypes[TaxiLexer.LE]
   val EQ: TokenIElementType = tokenElementTypes[TaxiLexer.EQ]
   val NQ: TokenIElementType = tokenElementTypes[TaxiLexer.NQ]

   // Special operators
   val SPREAD_OPERATOR: TokenIElementType = tokenElementTypes[TaxiLexer.SPREAD_OPERATOR]

   // Punctuation
   val LPAREN: TokenIElementType = tokenElementTypes[TaxiLexer.LPAREN]
   val RPAREN: TokenIElementType = tokenElementTypes[TaxiLexer.RPAREN]

   // Boolean literals
   val TRUE: TokenIElementType = tokenElementTypes[TaxiLexer.TRUE]
   val FALSE: TokenIElementType = tokenElementTypes[TaxiLexer.FALSE]

   // Identifiers
   val IDENTIFIER_TOKEN: TokenIElementType = tokenElementTypes[TaxiLexer.IdentifierToken]
   val NAME: TokenIElementType = tokenElementTypes[TaxiLexer.NAME]

   // String and number (fallback tokens)
   val STRING: TokenIElementType = tokenElementTypes[TaxiLexer.STRING]
   val NUMBER: TokenIElementType = tokenElementTypes[TaxiLexer.NUMBER]

   // TokenSets for categorization
   val KEYWORDS = TokenSet.create(
      K_TYPE, K_MODEL, K_ALIAS, K_PARTIAL, K_CLOSED, K_PARAMETER, K_INHERITS,
      K_AS, K_EXTENSION, K_IMPORT, K_NAMESPACE, K_QUERY, K_FIND, K_MAP, K_GIVEN,
      K_STREAM, K_TABLE, K_FILTER, K_CALL, K_READ, K_WRITE, K_DECLARE,
      K_WHEN, K_ELSE, K_EXCEPT, K_USING, K_EXCLUDING
   )

   val COMMENTS = TokenSet.create(LINE_COMMENT, COMMENT, DOCUMENTATION)

   val LITERALS = TokenSet.create(
      STRING_LITERAL, INTEGER_LITERAL, DECIMAL_LITERAL,
      BOOLEAN_LITERAL, TRUE, FALSE, STRING, NUMBER
   )

   val OPERATORS = TokenSet.create(
      IN, NOT_IN, LIKE, AND, OR, NOT, LOGICAL_AND, LOGICAL_OR, COALESCE,
      PLUS, MINUS, MULT, DIV, MOD, GT, GE, LT, LE, EQ, NQ, SPREAD_OPERATOR
   )
}

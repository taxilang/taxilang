package org.taxilang.intellij.highlighting

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import lang.taxi.TaxiParser
import org.antlr.intellij.adaptor.parser.ANTLRParserAdaptor
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.tree.ParseTree
import org.taxilang.intellij.TaxiFileType
import org.taxilang.intellij.TaxiLanguage

/**
 * Parser definition for Taxi language.
 *
 * This tells IntelliJ which tokens are whitespace and comments so they can be
 * handled appropriately (e.g., stripped during parsing, styled differently, etc.)
 */
class TaxiParserDefinition : ParserDefinition {
   companion object {
      val FILE = IFileElementType(TaxiLanguage)
   }

   override fun createLexer(project: Project?): Lexer {
      return TaxiLexerAdapter()
   }

   override fun createParser(project: Project?): PsiParser {
      // Use the official ANTLRParserAdaptor
      // It converts ANTLR parse trees to IntelliJ PSI trees
      return TaxiParserAdaptor
   }

   override fun getFileNodeType(): IFileElementType = FILE

   override fun getCommentTokens(): TokenSet {
      // Tell IntelliJ which tokens are comments
      return TaxiTokenTypes.COMMENTS
   }

   override fun getWhitespaceTokens(): TokenSet {
      // Tell IntelliJ which tokens are whitespace
      // This is critical - the WS token from channel(HIDDEN) needs to be declared here
      return TokenSet.create(TaxiTokenTypes.WS)
   }

   override fun getStringLiteralElements(): TokenSet {
      return TokenSet.create(TaxiTokenTypes.STRING_LITERAL, TaxiTokenTypes.STRING)
   }

   override fun createElement(node: ASTNode): PsiElement {
      // Get the element type from the node
      val elType = node.elementType

      // For rule nodes, we can use ANTLRPsiNode
      // For token nodes, we shouldn't be here (tokens are leaves)
      if (elType is org.antlr.intellij.adaptor.lexer.RuleIElementType) {
         return org.antlr.intellij.adaptor.psi.ANTLRPsiNode(node)
      }

      // Fallback for any other node type
      return org.antlr.intellij.adaptor.psi.ANTLRPsiNode(node)
   }

   override fun createFile(viewProvider: FileViewProvider): PsiFile {
      return TaxiFile(viewProvider)
   }
}

/**
 * PSI file representation for Taxi files
 */
class TaxiFile(viewProvider: FileViewProvider) : com.intellij.extapi.psi.PsiFileBase(viewProvider, TaxiLanguage) {
   override fun getFileType() = TaxiFileType
   override fun toString() = "Taxi File"
}

private object TaxiParserAdaptor : ANTLRParserAdaptor(TaxiLanguage, TaxiParser(null)) {
   override fun parse(parser: Parser?, root: IElementType?): ParseTree {
      return (parser as TaxiParser).document()
   }
}

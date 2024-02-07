package lang.taxi.lsp.utils

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

object Ranges {

   fun fullLine(lineNumber:Int):Range {
      return Range(
         Position(lineNumber,0),
         Position(lineNumber + 1,0)
      )
   }
   fun insertAtTokenLocation(token: Token): Range {
      return Range(
         token.asPosition(), token.asPosition()
      )
   }

   fun replaceToken(context: ParserRuleContext): Range {
      return context.asRange()
   }
}

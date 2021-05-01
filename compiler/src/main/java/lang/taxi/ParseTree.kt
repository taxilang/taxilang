package lang.taxi

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

fun ParseTree.childAtLocation(oneBasedLineIndex: Int, zeroBasedCharIndex: Int): ParseTree {
   val children = when (this) {
      is ParserRuleContext -> this.children ?: emptyList()
      else -> emptyList()
   }
   return if (children.isEmpty()) {
      this
   } else {
      val enclosingChild = children
         .firstOrNull {
            when (it) {
               is ParserRuleContext -> it.containsLocation(oneBasedLineIndex, zeroBasedCharIndex)
               is TerminalNode -> {
                  it.symbol.line == oneBasedLineIndex && it.symbol.charPositionInLine == zeroBasedCharIndex
               }
               else -> error("Lookup not handled for type ${it::class.simpleName}")
            }
         };
      val enclosingGrandchild = enclosingChild?.childAtLocation(oneBasedLineIndex, zeroBasedCharIndex)
      val cursorLocation = TokenLocation(oneBasedLineIndex, zeroBasedCharIndex)
      return if (enclosingGrandchild == null) {
         // Find the last token before the cursor
         val tokenBeforeCursorPosition = children.reversed()
            .firstOrNull { node ->
               val endLocation = when (node) {
                  is ParserRuleContext -> node.stop.asTokenLocation()
                  is TerminalNode -> node.symbol.asTokenLocation()
                  else -> error("Unhandled node type ${node::class.simpleName}")
               }
               endLocation < cursorLocation
            }
         tokenBeforeCursorPosition ?: this
      } else {
         enclosingGrandchild
      }
   }
}

fun ParserRuleContext.containsLocation(oneBasedLineIndex: Int, zeroBasedCharIndex: Int): Boolean {
   val startLocation = this.start.asTokenLocation()
   val endLocation = this.stop.asTokenLocation()
   val requestedLocation = TokenLocation(oneBasedLineIndex, zeroBasedCharIndex)
   val startsBefore = startLocation <= requestedLocation
   val endsAfter = endLocation >= requestedLocation
   return startsBefore && endsAfter

}

private data class TokenLocation(val oneBasedLineIndex: Int, val zeroBasedCharIndex: Int) {
   operator fun compareTo(other: TokenLocation): Int {
      return when {
         this.oneBasedLineIndex == other.oneBasedLineIndex -> this.zeroBasedCharIndex.compareTo(other.zeroBasedCharIndex)
         this.oneBasedLineIndex < other.oneBasedLineIndex -> -1
         this.oneBasedLineIndex > other.oneBasedLineIndex -> 1
         else -> error("Unhandled comparison branch in Location class - this shouldn't happen")
      }
   }
}
private fun Token.asTokenLocation():TokenLocation {
   return lang.taxi.TokenLocation(this.line, this.charPositionInLine)
}

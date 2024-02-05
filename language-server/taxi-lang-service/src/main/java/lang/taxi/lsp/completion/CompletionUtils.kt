package lang.taxi.lsp.completion

import org.antlr.v4.runtime.Token
import org.eclipse.lsp4j.Position

/**
 * Indicates if a Position is between two tokens from the compiler.
 * If either of the provided tokens are null, returns false
 */
fun Position.isBetween(left: Token?, right: Token?): Boolean {
   if (left == null || right == null) return false
   val editorLine = this.line // zero-based
   val startLine = (left.line - 1) // antlr lines are 1-based
   val endLine = right.line - 1 // antrl lines are 1-based
   if (editorLine !in startLine..endLine) return false
   return when {
      // On a line inbetween the start and end
      this.line > startLine && this.line < endLine -> true
      this.line == startLine -> this.character >= left.charPositionInLine
      this.line == endLine -> this.character < right.charPositionInLine
      else -> false
   }
}

fun Token.locationIsBeforeOrEqualTo(oneBasedLineNumber: Int, character: Int): Boolean {
   return this.line <= oneBasedLineNumber && this.charPositionInLine <= character
}

fun Token.locationIsAfterOrEqualTo(oneBasedLineNumber: Int, character: Int): Boolean {
   return when {
      this.line < oneBasedLineNumber -> false
      this.line >= oneBasedLineNumber && this.charPositionInLine >= character -> true
      else -> false
   }
}

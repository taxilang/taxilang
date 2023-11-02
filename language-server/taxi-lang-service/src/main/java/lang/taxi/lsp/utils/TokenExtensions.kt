package lang.taxi.lsp.utils

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.util.Positions

fun ParserRuleContext.asLineRange(): Range {
    val start = this.start.asPosition()
    val stop  = Position(this.start.line, 0)
    return Range(start,stop)
}
fun ParserRuleContext.asRange(): Range {
    val start = this.start.asPosition()
    val stop  = this.stop.asPositionInclusive()
    return Range(start,stop)
}


fun Position.isBefore(position: Position): Boolean {
   return when {
      this.line < position.line -> true
      this.line == position.line -> this.character < position.character
      else -> false
   }
}
fun Range.contains(position: Position): Boolean {
   return when {
      position.line < this.start.line -> false
      position.line > this.end.line -> false
      position.line == this.start.line && position.line == this.end.line -> position.isBefore(this.end)
      position.line == this.end.line -> position.character <= this.end.character
      else -> true
   }
}

fun Range.isBefore(position: Position): Boolean {
   return when {
      this.start.line < position.line -> true
      this.start.line == position.line -> this.start.character < position.character
      else -> false
   }
}


fun Token.asPosition(): Position {
   return Position(this.line - 1, this.charPositionInLine)
}

private fun Token.asPositionInclusive(): Position {
   return Position(this.line - 1, this.charPositionInLine + this.text.length)
}

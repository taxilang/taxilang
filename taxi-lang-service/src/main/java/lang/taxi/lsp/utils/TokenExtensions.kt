package lang.taxi.lsp.utils

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

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

private fun Token.asPosition(): Position {
    return Position(this.line -1, this.charPositionInLine)
}
private fun Token.asPositionInclusive():Position {
    return Position(this.line -1, this.charPositionInLine + this.text.length)
}
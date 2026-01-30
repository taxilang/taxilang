package lang.taxi.utils

import arrow.core.Either
import arrow.core.left
import lang.taxi.CompilationError
import lang.taxi.toCompilationUnit
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

fun ParserRuleContext.createCompilationError(message: String): Either<List<CompilationError>, Nothing> {
   return listOf(CompilationError(this.toCompilationUnit(), message))
      .left()
}
fun ParserRuleContext.createInternalError(message: String): Either<List<CompilationError>, Nothing> {
   return listOf(CompilationError(this.toCompilationUnit(), "An internal error occurred: $message"))
      .left()
}


fun TerminalNode.unescaped():String {
   return this.text.unescaped()
}
@Deprecated("Unescaping reserved words is handled at the grammar level now, and should not be necessary")
fun String.unescaped():String {
   return this.removeSurrounding("`")
}

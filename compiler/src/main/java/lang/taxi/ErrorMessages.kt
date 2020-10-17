package lang.taxi

import lang.taxi.utils.log
import org.antlr.v4.runtime.*


object ErrorMessages {
   fun unresolvedType(type: String) = "$type is not defined"
}

class CollectingErrorListener(private val sourceName: String) : BaseErrorListener() {

   val errors: MutableList<CompilationError> = mutableListOf()
   override fun syntaxError(recognizer: Recognizer<*, *>, offendingSymbol: Any,
                            line: Int, charPositionInLine: Int,
                            msg: String, e: RecognitionException?) {

//      var sourceName = recognizer.inputStream.sourceName
//      if (!sourceName.isEmpty()) {
//         sourceName = String.format("%s:%d:%d: ", sourceName, line, charPositionInLine)
//      }
      when {
         e is NoViableAltException && offendingSymbol is Token -> errors.add(CompilationError(offendingSymbol, "Syntax error.  That's all we know.", sourceName))
         offendingSymbol is Token -> errors.add(CompilationError(offendingSymbol, msg, sourceName))
         else -> log().error("Unhandled error situation - offending symbol was not a token")
      }
   }
}

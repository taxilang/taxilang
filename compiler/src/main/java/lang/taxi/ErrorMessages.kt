package lang.taxi

import lang.taxi.utils.log
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token


object ErrorMessages {
   fun unresolvedType(type:String) = "Unresolved type: $type"
}

class CollectingErrorListener(private val sourceName: String) : BaseErrorListener() {

   val errors:MutableList<CompilationError> = mutableListOf()
   override fun syntaxError(recognizer: Recognizer<*, *>, offendingSymbol: Any,
                   line: Int, charPositionInLine: Int,
                   msg: String, e: RecognitionException?) {

//      var sourceName = recognizer.inputStream.sourceName
//      if (!sourceName.isEmpty()) {
//         sourceName = String.format("%s:%d:%d: ", sourceName, line, charPositionInLine)
//      }

      if (offendingSymbol is Token) {
         errors.add(CompilationError(offendingSymbol,msg, sourceName))
      } else {
         log().error("Unhandled error situation - offending symbol was not a token")
      }
   }
}

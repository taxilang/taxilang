package lang.taxi

import lang.taxi.types.CompilationUnit
import lang.taxi.utils.log
import org.antlr.v4.runtime.*


object ErrorMessages {
   @Deprecated("Use Errors.unresolvedType()")
   fun unresolvedType(type: String) = "$type is not defined"
}
enum class ErrorCodes(val errorCode: Int) {
   UNRESOLVED_TYPE(1)
}
object Errors {
   fun unresolvedType(type: String, compilationUnit: CompilationUnit):CompilationError {
      return CompilationError(
         compilationUnit,
         "$type is not defined",
         errorCode = ErrorCodes.UNRESOLVED_TYPE.errorCode
      )
   }
}

class CollectingErrorListener(private val sourceName: String, private val listener: TokenCollator) : BaseErrorListener() {


   val errors: MutableList<CompilationError> = mutableListOf()
   override fun syntaxError(recognizer: Recognizer<*, *>, offendingSymbol: Any,
                            line: Int, charPositionInLine: Int,
                            msg: String, e: RecognitionException?) {

//      var sourceName = recognizer.inputStream.sourceName
//      if (!sourceName.isEmpty()) {
//         sourceName = String.format("%s:%d:%d: ", sourceName, line, charPositionInLine)
//      }
      when {
         e is NoViableAltException && offendingSymbol is Token -> errors.add(CompilationError(offendingSymbol, "Syntax error at '${e.offendingToken.text}'.  That's all we know.", sourceName, stack = listener.currentStack()))
         offendingSymbol is Token -> errors.add(CompilationError(offendingSymbol, msg, sourceName))
         else -> log().error("Unhandled error situation - offending symbol was not a token")
      }
   }
}

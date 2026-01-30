package lang.taxi.functions.stdlib

import lang.taxi.functions.vyne.aggregations.Aggregations
import lang.taxi.sources.SourceCode
import lang.taxi.types.BuiltInSourceCode
import lang.taxi.types.BuiltInTypes
import lang.taxi.types.QualifiedName

object StdLib : BuiltInSourceCode {
   // Note: because of a bug in the antlr definition,
   // we can't put these in lang.taxi.stdlib. :(
   const val namespace = "taxi.stdlib"
   val functions =
      Strings.functions +
         Aggregations.functions +
         Functional.functions +
         Collections.functions +
         ObjectFunctions.functions +
         Transformations.functions +
         Dates.functions +
         Errors.functions +
         EnumFunctions.functions +
         Math.functions +
         listOf(Coalesce)
   val taxi = functions.namespacedTaxi() + BuiltInTypes.taxi
   override val sourceCode: SourceCode = SourceCode("stdlib.taxi", taxi)
}

fun stdLibName(name: String): QualifiedName = QualifiedName.from("${StdLib.namespace}.$name")

object Coalesce : FunctionApi {
   override val taxi: String = "declare function coalesce(Any...):Any"
   override val name: QualifiedName = stdLibName("coalesce")
}

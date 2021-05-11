package lang.taxi.functions.stdlib

import lang.taxi.functions.vyne.aggregations.Aggregations
import lang.taxi.types.QualifiedName

object StdLib  {
   // Note: because of a bug in the antlr definition,
   // we can't put these in lang.taxi.stdlib. :(
   val namespace = "taxi.stdlib"
   val functions = Strings.functions + Aggregations.functions + listOf(Coalesce)
   val taxi = functions.namespacedTaxi()

   fun stdLibName(name:String):QualifiedName = QualifiedName.from("$namespace.$name")
}

object Coalesce: FunctionApi {
   override val taxi: String = "declare function coalesce(Any...):Any"
   override val name: QualifiedName = StdLib.stdLibName("coalesce")
}

package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object StdLib  {
   // Note: because of a bug in the antlr definition,
   // we can't put these in lang.taxi.stdlib. :(
   val namespace = "taxi.stdlib"
   val functions = Strings.functions
   val taxi = functions.namespacedTaxi()

   fun stdLibName(name:String):QualifiedName = QualifiedName.from("$namespace.$name")
}

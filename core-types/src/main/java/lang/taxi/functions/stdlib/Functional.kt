package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Functional {
   val functions:List<FunctionApi>  = listOf(
      Reduce,
   )
}
object Reduce: FunctionApi {
   override val taxi: String
      get() = "declare function <T,A> reduce(T[], (T,A) -> A):A"
   override val name: QualifiedName
      get() = StdLib.stdLibName("reduce")
}

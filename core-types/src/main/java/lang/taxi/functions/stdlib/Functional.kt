package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Functional {
   val functions:List<FunctionApi>  = listOf(
      Reduce,
      Fold,
      Sum
   )
}
object Reduce: FunctionApi {
   override val taxi: String
      get() = "declare function <T,A> reduce(collection: T[], callback: (T,A) -> A):A"
   override val name: QualifiedName
      get() = StdLib.stdLibName("reduce")
}

object Fold : FunctionApi {
   override val taxi: String
      get() = "declare function <T,A> fold(collection: T[], initial: A, callback: (T,A) -> A):A"
   override val name: QualifiedName
      get() = StdLib.stdLibName("fold")
}

object Sum : FunctionApi {
   override val taxi: String
      get() = "declare function <T,A> sum(collection: T[], callback: (T) -> A):A"
   override val name: QualifiedName
      get() = StdLib.stdLibName("sum")
}

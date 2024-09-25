package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Functional {
   val functions:List<FunctionApi>  = listOf(
      Reduce,
      Fold,
      Map,
      Sum,
      Max,
      Min
   )
}
object Reduce: FunctionApi {
   override val taxi: String
      get() = "declare extension function <T,A> reduce(collection: T[], callback: (T,A) -> A):A"
   override val name: QualifiedName
      get() = stdLibName("reduce")
}

object Map : FunctionApi {
   override val taxi: String
      get() = "declare extension function <T,A> map(collection: T[], callback: (T) -> A):A[]"
   override val name: QualifiedName
      get() = stdLibName("map")
}
object Fold : FunctionApi {
   override val taxi: String
      get() = "declare extension function <T,A> fold(collection: T[], initial: A, callback: (T,A) -> A):A"
   override val name: QualifiedName
      get() = stdLibName("fold")
}

object Max : FunctionApi {
   override val taxi: String
      get() = "declare extension function <T,A> max(collection: T[], callback: (T) -> A):A"
   override val name: QualifiedName
      get() = stdLibName("max")
}

object Min : FunctionApi {
   override val taxi: String
      get() = "declare extension function <T,A> min(collection: T[], callback: (T) -> A):A"
   override val name: QualifiedName
      get() = stdLibName("min")
}


object Sum : FunctionApi {
   override val taxi: String
      get() = "declare extension function <T,A> sum(collection: T[], callback: (T) -> A):A"
   override val name: QualifiedName
      get() = stdLibName("sum")
}

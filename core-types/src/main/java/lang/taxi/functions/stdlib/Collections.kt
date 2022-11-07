package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Collections {
   val functions: List<FunctionApi> = listOf(Contains, AllOf, AnyOf, NoneOf, Single, FilterAll, ArrayEquals)
}

object NoneOf : FunctionApi {
   override val taxi: String = "declare function noneOf(values:Boolean...): Boolean"
   override val name: QualifiedName = stdLibName("noneOf")
}

object AnyOf : FunctionApi {
   override val taxi: String = "declare function anyOf(values:Boolean...): Boolean"
   override val name: QualifiedName = stdLibName("anyOf")
}

object AllOf : FunctionApi {
   override val taxi: String = "declare function allOf(values:Boolean...): Boolean"
   override val name: QualifiedName = stdLibName("allOf")
}

object Contains : FunctionApi {
   override val taxi: String = "declare function <T> contains(collection: T[], searchTarget:T): Boolean"
   override val name: QualifiedName = stdLibName("contains")
}

object Single : FunctionApi {
   override val taxi: String =
      "declare function <T> single(collection:T[], callback: (T) -> Boolean):T"
   override val name: QualifiedName = stdLibName("single")
}

object FilterAll : FunctionApi {
   // This naming sucks, but we use filter as a reserved word in the grammar :(
   override val taxi: String =
      "declare function <T> filterAll(collection:T[], callback: (T) -> Boolean):T[]"
   override val name: QualifiedName = stdLibName("filterAll")
}

object ArrayEquals : FunctionApi {
   override val taxi: String
      get() = "declare function <T>  arrayEquals(a:T[], b:T[]):Boolean"
   override val name: QualifiedName = stdLibName("arrayEquals")
}

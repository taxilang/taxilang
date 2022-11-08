package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Collections {
   val functions: List<FunctionApi> = listOf(Contains, AllOf, AnyOf, NoneOf, Single, FilterAll, SingleBy)
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

object SingleBy : FunctionApi {
   override val taxi: String =
      """
         [[ Similar to Single, where the collection is searched for a single matching value.
         However, results are first grouped by selector.  The results of this are cached to improve future performance
         ]]
         declare function <T,A> singleBy(collection:T[], groupingFunction: (T) -> A, searchValue: A):T""".trimIndent()
   override val name: QualifiedName = stdLibName("singleBy")
}

object FilterAll : FunctionApi {
   // This naming sucks, but we use filter as a reserved word in the grammar :(
   override val taxi: String =
      "declare function <T> filterAll(collection:T[], callback: (T) -> Boolean):T[]"
   override val name: QualifiedName = stdLibName("filterAll")
}


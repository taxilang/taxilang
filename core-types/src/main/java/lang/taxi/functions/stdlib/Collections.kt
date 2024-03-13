package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Collections {
   val functions: List<FunctionApi> = listOf(
      Contains,
      AllOf,
      AnyOf,
      NoneOf,
      Single,
      Filter,
      FilterEach,
      SingleBy,
      First,
      Last,
      GetAtIndex
   )
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

object First : FunctionApi {
   override val taxi: String = """
      [[ Returns the first item within the collection ]]
      declare function <T> first(collection: T[]):T"""
   override val name: QualifiedName = stdLibName("first")
}

object Last : FunctionApi {
   override val taxi: String = """
      [[ Returns the last item within the collection ]]
      declare function <T> last(collection: T[]):T""".trimIndent()
   override val name: QualifiedName = stdLibName("last")
}

object GetAtIndex : FunctionApi {
   override val taxi: String = """
      [[ Returns the item at the provided index ]]
      declare function <T> getAtIndex(collection: T[], index: Int):T""".trimIndent()
   override val name: QualifiedName = stdLibName("getAtIndex")
}


object Filter : FunctionApi {
   override val taxi: String =
      "declare extension function <T> filter(collection:T[], callback: (T) -> Boolean):T[]"
   override val name: QualifiedName = stdLibName("filter")
}

object FilterEach : FunctionApi {
   override val taxi: String =
      """[[ Evaluates the predicate against the provided value, returning the value if the predicate
         returns true, or null.
         Intended for use against filtering streams, where null values are excluded
         ]]
         declare extension function <T> filterEach(item: T, callback: (T) -> Boolean):T?""".trimIndent()
   override val name: QualifiedName = stdLibName("filterEach")
}

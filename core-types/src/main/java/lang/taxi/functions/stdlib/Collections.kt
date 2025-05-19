package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Collections {
   val functions: List<FunctionApi> = listOf(
      Contains,
      AllOf,
      AnyOf,
      NoneOf,
      All,
      Any,
      None,
      Single,
      Filter,
      FilterEach,
      SingleBy,
      First,
      ExactlyOne,
      Last,
      GetAtIndex,
      Intersection,
      ListOf,
      JoinToString
   )
}

object NoneOf : FunctionApi {
   override val taxi: String = """
      [[ Returns true if all of the provided boolean values are false.
      See also: none()
      ]]
      declare function noneOf(values:Boolean...): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("noneOf")
}

object None : FunctionApi {
   override val taxi: String =
      """[[ Returns true if none of the items in the collection satisfy the predicate ]]
         declare extension function <T> none(collection: T[], predicate: (T) -> Boolean): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("none")
}

object AnyOf : FunctionApi {
   override val taxi: String = """
       [[ Returns true if any of the provided boolean values are true.
      See also: any()
      ]]
      declare function anyOf(values:Boolean...): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("anyOf")
}

object Any : FunctionApi {
   override val taxi: String =
      """[[ Returns true if any of the items in the collection satisfy the predicate ]]
         declare extension function <T> any(collection: T[], predicate: (T) -> Boolean): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("any")
}


object AllOf : FunctionApi {
   override val taxi: String = "declare function allOf(values:Boolean...): Boolean"
   override val name: QualifiedName = stdLibName("allOf")
}

object All : FunctionApi {
   override val taxi: String =
      """[[ Returns true if all of the items in the collection satisfy the predicate ]]
         declare extension function <T> all(collection: T[], predicate: (T) -> Boolean): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("all")
}

object Contains : FunctionApi {
   override val taxi: String = "declare extension function <T> contains(collection: T[], searchTarget:T): Boolean"
   override val name: QualifiedName = stdLibName("contains")
}

object ExactlyOne : FunctionApi {
   override val taxi: String =
      """
         [[ Returns the only item from the provided collection, or errors if there isn't exactly one item in the collection ]]
         declare extension function <T> exactlyOne(collection:T[]):T""".trimIndent()
   override val name: QualifiedName = stdLibName("exactlyOne")
}

object Single : FunctionApi {
   override val taxi: String =
      "declare extension function <T> single(collection:T[], callback: (T) -> Boolean):T"
   override val name: QualifiedName = stdLibName("single")
}

object SingleBy : FunctionApi {
   override val taxi: String =
      """
         [[ Similar to Single, where the collection is searched for a single matching value.
         However, results are first grouped by selector.  The results of this are cached to improve future performance
         ]]
         declare extension function <T,A> singleBy(collection:T[], groupingFunction: (T) -> A, searchValue: A):T""".trimIndent()
   override val name: QualifiedName = stdLibName("singleBy")
}

object First : FunctionApi {
   override val taxi: String = """
      [[ Returns the first item within the collection ]]
      declare extension function <T> first(collection: T[]):T"""
   override val name: QualifiedName = stdLibName("first")
}

object Last : FunctionApi {
   override val taxi: String = """
      [[ Returns the last item within the collection ]]
      declare extension function <T> last(collection: T[]):T""".trimIndent()
   override val name: QualifiedName = stdLibName("last")
}

object GetAtIndex : FunctionApi {
   override val taxi: String = """
      [[ Returns the item at the provided index ]]
      declare extension function <T> getAtIndex(collection: T[], index: Int):T""".trimIndent()
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

object Intersection : FunctionApi {
   override val taxi: String =
      """[[
         Returns a collection containing the items present in both the provided collections
         ]]
         declare extension function <T> intersection(collectionA: T[], collectionB: T[]):T[]""".trimIndent()
   override val name: QualifiedName = stdLibName("intersection")
}

object ListOf : FunctionApi {
   override val taxi: String =
      """[[
         xxx
         Returns an array containing the provided values
         ]]
         declare function <T> listOf(values:T...):T[]""".trimIndent()
   override val name: QualifiedName = stdLibName("listOf")
}

object JoinToString : FunctionApi {
   override val taxi: String = """
   [[ Creates a string from all the elements separated using separator and using the given prefix and postfix if supplied. Null values are omitted ]]
   declare extension function <T> joinToString(values:T[], separator: String = ",", prefix: String? = null, postfix: String? = null): String
   """.trimIndent()

   override val name: QualifiedName = stdLibName("joinToString")
}

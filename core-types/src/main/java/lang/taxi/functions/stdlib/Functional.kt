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
      get() = """
         [[ Performs a mapping transformation function on every member of the provided array.

         If the callback is in the form of a type expression, then each input value is converted
         into an instance of that type.

         For example:

         ```taxi
         // Will convert Person[] to Person2[]
         Person[].map((Person) -> Person2)
         ```

         If the callback is in the form of any other type of expression, then that expression is
         evaluated against each member of the input array.

         For example:

         ```taxi
         // Will return an array of Title, where the text has been converted to uppercase
         Film[].map ( (Title) -> Title.upperCase() )
         ```
         ]]
         declare extension function <T,A> map(collection: T[], callback: (T) -> A):A[]""".trimIndent()
   override val name: QualifiedName
      get() = stdLibName("map")
}
object Fold : FunctionApi {
   override val taxi: String
      get() = """
         |[[
         | Iterates over a collection, combining elements to produce a single accumulated result.
         |
         | Example:
         |
         |```taxi
         |// Given:
         |model Entry {
         |   weight:Weight inherits Int
         |   score:Score inherits Int
         |}
         |
         |type WeightedAverage by (Entry[]) -> Entry[].fold(0, (Entry, Int) -> Int + (Weight*Score))
         |```
         |]]
         |declare extension function <T,A> fold(collection: T[], initial: A, callback: (T,A) -> A):A
         |
      """.trimMargin()

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

package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

object Functional {
   val functions: List<FunctionApi> = listOf(
      Reduce,
      Fold,
      Map,
   )
}

object Reduce : FunctionApi {
   override val taxi: String = """
      [[
      Reduces a collection to a single value by applying a combining function to each element and the accumulated result.

      Unlike `fold`, there is no initial value — the first element of the collection is used as the starting accumulator.

      ```taxi
      // Sum a list of integers
      find {
         total: Int = numbers.reduce((Int, Int) -> Int + Int)
      }
      ```
      ]]
      declare extension function <T,A> reduce(collection: T[], callback: (T,A) -> A):A""".trimIndent()
   override val name: QualifiedName = stdLibName("reduce")
}

object Map : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Applies a transformation function to every element in a collection, returning a new array of the results.

      If the callback is a type expression, each element is converted to that type:

      ```taxi
      // Convert Person[] to Name[]
      Person[].map((Person) -> Name)
      ```

      If the callback is any other expression, it is evaluated against each element:

      ```taxi
      // Return an array of uppercased titles
      Film[].map((Title) -> Title.upperCase())
      ```
      ]]
      declare extension function <T,A> map(collection: T[], callback: (T) -> A):A[]""".trimIndent()
   override val name: QualifiedName = stdLibName("map")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/2VQTUvEMBD9K8NcdhdiqcgiFBT8uOhBFjw2PcR03I22aUxScSn9706bXQpKIJnhPd5HBgz6QK3CAtuupgZ2vqt7HWGQFsCqlgp44RuMPZA3McBr9MbuJ9R5oxneTc+CP5I2rWqkHVHgV0/+yNp78002abpkEIqzVVnBDZQTBDCcLFd3zjW0EmePy2ybwyj+kO6V5bOw8ux6+5/1wMH8cWFdZTlrTaSKQ0r7bmy91A2p7xzqHDVrlVuvT3E3cHE7UzapolOel0g+YDGMAkPs33gsK4H040hHqp9DZ/kTBomzg8SC++JcUaIAialImlNcidUkHqLSn081FrZvGvby3QcrpnX8Bd4aQSq8AQAA
      DocsSnippet(
         markdown = "Extracts a field from each object in a collection, returning a typed array.",
         query = StubQueryMessage(
            schema = """
               model Product {
                  name: Name inherits String
                  price: Price inherits Decimal
               }
            """.trimIndent(),
            query = """
               given {
                  products: Product[] = [
                     { name: 'Apple', price: 1.50 },
                     { name: 'Banana', price: 0.75 },
                     { name: 'Cherry', price: 3.00 }
                  ]
               }
               find {
                  names: Name[] = products.map((Product) -> Name)
               }
            """.trimIndent(),
            expectedJson = """{"names": ["Apple", "Banana", "Cherry"]}"""
         )
      )
   )
}

object Fold : FunctionApi {
   override val taxi: String = """
      [[
      Iterates over a collection, combining elements with an accumulator to produce a single result.

      Unlike `reduce`, `fold` starts with an explicit `initial` value as the accumulator.

      ```taxi
      // Compute a weighted sum
      model Entry {
         weight: Weight inherits Int
         score: Score inherits Int
      }
      type WeightedTotal by (Entry[]) -> Entry[].fold(0, (Entry, Int) -> Int + (Weight * Score))
      ```
      ]]
      declare extension function <T,A> fold(collection: T[], initial: A, callback: (T,A) -> A):A""".trimIndent()

   override val name: QualifiedName = stdLibName("fold")
}


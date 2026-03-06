package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

object Math {
   val functions: List<FunctionApi> = listOf(
      Round,
      Sum,
      Max,
      Min,
      Average
   )
}

object Max : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the maximum value in the collection, computed by applying the selector function to each element.

      ```taxi
      find { products: Product[] } as {
         mostExpensive: Price = products.max((Product) -> Price)
      }
      ```
      ]]
      declare extension function <T,A> max(collection: T[], callback: (T) -> A):A""".trimIndent()
   override val name: QualifiedName = stdLibName("max")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3VQTUvEMBD9K0Muuwu1VKUIhRX8uOip4LHtIaZxN9pOY5LKLqX/3YlpXVFLDsm8vPfmzQzMir1sOctY29Wygdx0dS8cDCUCIG9lBk/OKNz5WhslCMj9BQr30ihn4V4K1fKmxJFF7L2X5khuO/UhMbjoYGmz2byoYAuF/wIYpiarG60buYrmHudxmsAY/SLdcqRzYiXxVfqXdUfBzPHEuowT8vKkikKW+KKwDtFafsh/zrT9DhvT13o9Bd7A2XVgbL5UChdVCpdUrnO8WdLZvv1f53equaHBnDSWZcMYMev6Z3oWVcTkQUvhZP1oO6StDyWbRypZGDwCwqbAHvMr89gpjkfT+CL1razj4u2hZhn2TUOdTfdK/qEcPwFGxCD/LQIAAA==
      DocsSnippet(
         markdown = "Returns the maximum price from a collection of products.",
         query = StubQueryMessage(
            schema = """
               model Product {
                  name: String
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
                  maxPrice: Price = products.max((Product) -> Price)
               }
            """.trimIndent(),
            expectedJson = """{"maxPrice": 3.00}"""
         )
      )
   )
}

object Min : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the minimum value in the collection, computed by applying the selector function to each element.

      ```taxi
      find { products: Product[] } as {
         cheapest: Price = products.min((Product) -> Price)
      }
      ```
      ]]
      declare extension function <T,A> min(collection: T[], callback: (T) -> A):A""".trimIndent()
   override val name: QualifiedName = stdLibName("min")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3VQTUvEMBD9K0Muuwu1VKUIhRX8uOip4LHtIaZxN9pOY5LKLqX/3YlpXVFLDsm8vPfmzQzMir1sOctY29Wygdx0dS8cDCUCIG9lBk/OKNz5WhslCMj9BQr30ihn4V4K1fKmxJFF7L2X5khuO/UhMbjoYGmz2byoYAuF/wIYpiarG60buYrmHudxmsAY/SLdcqRzYiXxVfqXdUfBzPHEuowT8vKkikKW+KKwDtFafsh/zrT9DhvT13o9Bd7A2XVgbL5UChdVCpdUrnO8WdLZvv1f53equaHBnDSWZcMYMev6Z3oWVcTkQUvhZP1oO6StDyWbRypZGDwCwqbAHvMr89gpjkfT+CL1razj4u2hZhn2TUOdTfdK/qEcPwFGxCD/LQIAAA==
      DocsSnippet(
         markdown = "Returns the minimum price from a collection of products.",
         query = StubQueryMessage(
            schema = """
               model Product {
                  name: String
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
                  minPrice: Price = products.min((Product) -> Price)
               }
            """.trimIndent(),
            expectedJson = """{"minPrice": 0.75}"""
         )
      )
   )
}


object Sum : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the sum of values in the collection, computed by applying the selector function to each element.

      ```taxi
      find { order: Order } as {
         total: Price = order.items.sum((Item) -> Price)
      }
      ```
      ]]
      declare extension function <T,A> sum(collection: T[], callback: (T) -> A):A""".trimIndent()
   override val name: QualifiedName = stdLibName("sum")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/3VQTUvEMBD9K0Muuwu1VKUIhRX8uOip4LHtIaZxN9pOY5LKLqX/3YlpXVFLDsm8vPfmzQzMir1sOctY29Wygdx0dS8cDCUCIG9lBk/OKNz5WhslCMj9BQr30ihn4V4K1fKmxJFF7L2X5khuO/UhMbjoYGmz2byoYAuF/wIYpiarG60buYrmHudxmsAY/SLdcqRzYiXxVfqXdUfBzPHEuowT8vKkikKW+KKwDtFafsh/zrT9DhvT13o9Bd7A2XVgbL5UChdVCpdUrnO8WdLZvv1f53equaHBnDSWZcMYMev6Z3oWVcTkQUvhZP1oO6StDyWbRypZGDwCwqbAHvMr89gpjkfT+CL1razj4u2hZhn2TUOdTfdK/qEcPwFGxCD/LQIAAA==
      DocsSnippet(
         markdown = "Sums a numeric field across all elements of a collection.",
         query = StubQueryMessage(
            schema = """
               model Product {
                  name: String
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
                  totalPrice: Price = products.sum((Product) -> Price)
               }
            """.trimIndent(),
            expectedJson = """{"totalPrice": 5.25}"""
         )
      )
   )
}

object Round : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Rounds a decimal value to the specified number of decimal places.

      `precision` defaults to `0` (round to nearest integer).

      ```taxi
      find { measurement: Measurement } as {
         rounded: Decimal = measurement.value.round(2)
      }
      ```
      ]]
      declare extension function round(value: Decimal, precision: Int = 0): Decimal
   """.trimIndent()
   override val name: QualifiedName = stdLibName("round")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22OywrCMBBFf2WYlUIoaa2IAXdu9Besi5iMWNumNQ9QQv/dlKK4cHfncrhnIjp1o06iQGT4CGRfKV5royFWBsD2wWjShYA9qbqTLezmbrHK8jJfbxkUyx+Q/wM3DHiCxmQYpJUdebIORRwZOh8uKZ7ODOk5kPKkj6436YdY4UdeoYBJx+Db8akrMz5tOi9Vc9AoTGjbpLD9PQ3N5/gGzKfCneEAAAA=
      DocsSnippet(
         markdown = "Rounds a decimal to the specified number of decimal places.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  rounded2: Decimal = round(3.14159, 2)
                  rounded0: Decimal = round(3.7, 0)
               }
            """.trimIndent(),
            expectedJson = """{"rounded2": 3.14, "rounded0": 4.0}"""
         )
      )
   )
}

object Average : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the arithmetic mean of a collection of numeric values.

      All values in the collection must be of the same numeric primitive type (Int, Decimal, etc.).
      Returns null if the collection is empty or contains non-numeric values.

      ```taxi
      find { order: Order } as {
         averageItemPrice: Decimal = order.items.map((Item) -> Item::price).average()
      }
      ```
      ]]
      declare extension function average(values: Any[]):Decimal
   """.trimIndent()
   override val name: QualifiedName = stdLibName("average")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22PMU/EMAyF/4rl6SqV6raDSDCxHCtj0yGkpheuSXtJWoGi/nfcRLcgJttPeu99Thj0haxCgXbqaYRzJAtJOoCgJ08C3vcBxl3ImxjglbSxapRuwxpvC/kftg5mJVdchv1B5Ji2g2dodxEg3eMej80RtvqP+vSvesrqLnbcJ92ncX1pUesg7ihcolbyaqBDLm+smg95LRgVPLxkrCbHVlVhn5VXliL5gCJtNYa4fPDadjXS90w6Uv8WJsffJYncJ7HA794Qlb6eexRuGUeO8tMXG8q5/QIgonxdUwEAAA==
      DocsSnippet(
         markdown = "Computes the average score from a collection of items.",
         query = StubQueryMessage(
            schema = """
               model Item {
                  score: Score inherits Decimal
               }
            """.trimIndent(),
            query = """
               given {
                  items: Item[] = [
                     { score: 80.0 },
                     { score: 90.0 },
                     { score: 70.0 }
                  ]
               }
               find {
                  avg: Decimal = average(items.map((item: Item) -> item.score))
               }
            """.trimIndent(),
            expectedJson = """{"avg": 80.0}"""
         )
      )
   )
}

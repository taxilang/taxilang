package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

/**
 * This class provides the API of the stdlib of functions.
 * We don't ship implementations - that's up to a parsing library (such as Vyne)
 * to provide.
 */
object Strings {
   val functions: List<FunctionApi> = listOf(
      Left,
      Right,
      Mid,
      Concat,
      Uppercase,
      Lowercase,
      Trim,
      Length,
      IndexOf,
      Replace,
      ContainsString,
      ContainsPattern,
      PadStart,
      PadEnd,
      ApplyFormat,
      StartsWith,
      EndsWith,
      Matches
//      Coalesce
   )
}

object Concat : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Joins all provided values into a single string, in the order they are passed. Null values are omitted.

      ```taxi
      find { person: Person } as {
         fullName: String = concat(person.firstName, ' ', person.lastName)
      }
      ```
      ]]
      declare function concat(Any...):String""".trimIndent()
   override val name: QualifiedName = stdLibName("concat")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/zXNMQ7CMAwF0KtYXgpSThCJHVgZGAhDSEwppEnrpBIo6t0xBSRL_n95v2J2N-otakSF40T8knjtoodqIgC0TFS62Go4FJYPG3ApOltWzZZCSI2C3x0TB9-sTZxFGizbngpxRl1nhblMF4mns0J6DuQK-X1OUbaqwf-GQQ0GF1bBwhn8YLlY99h51HEKQWxOdxG-dX4D37HXy8IAAAA=
      DocsSnippet(
         markdown = "Concatenates multiple string values into a single string.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  greeting: String = concat('Hello', ', ', 'World')
               }
            """.trimIndent(),
            expectedJson = """{"greeting": "Hello, World"}"""
         )
      )
   )
}

object Trim : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Removes leading and trailing whitespace from a string.

      ```taxi
      find { order: Order } as {
         cleanRef: String = order.referenceCode.trim()
      }
      ```
      ]]
      declare extension function trim(String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("trim")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/03NPQ7CMAyGX6XpgibExe0SJydcHTmGAyqeQsG7w6gX3t0iUdPlb9Lv+xvRV2fqDCrs+ppa2BsXIGoGsIE6r+AYnOUmLzRPmOJtJPeU48beiZe7Sgi1cDuIXyxPzDC0lKSQlIZl5iRVzj2TAiaxaT5Zrv9d+35kEWU8e2bp5qPaePui1XqpH4wzHQVyHlWcUvRhLCXmRYr0GKgKVB98z/Jg1PiTalSwnXEfTHXNalQ8tq3YXH8RZlmnN28OWx4LAQAA
      DocsSnippet(
         markdown = "Strips leading and trailing whitespace from a string.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  trimmed: String = '  hello world  '.trim()
               }
            """.trimIndent(),
            expectedJson = """{"trimmed": "hello world"}"""
         )
      )
   )
}

object Left : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the leftmost `count` characters from a string.

      ```taxi
      find { account: Account } as {
         prefix: String = account.code.left(3)
      }
      ```
      ]]
      declare extension function left(source:String, count:Int):String""".trimIndent()
   override val name: QualifiedName = stdLibName("left")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22Ouw7CMAxFf8XyAkgZYGCJxIqAlYGBMITGhUCaQpIiUNV/x6UC8dqu7SPfU2PM9lRolIgCzxWFG8fcegO18gC5DTFN7YUkLFOwfgcTcJSnfm9GzpWrMjjTEzAetLDTP2ywu/1/uLBm/Aby+I0JGA0ZbVjspIMuKFGIKOtGYEzVluN6I5CuJ8oSmUUsPavXCl/OCiUofDxVKDg+BbvDo6g7tDIfy7Y0Jp0d5walr5xjh1AeuKkbmzvu+98POQEAAA==
      DocsSnippet(
         markdown = "Extracts the first N characters from a string.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  firstFive: String = left('HelloWorld', 5)
               }
            """.trimIndent(),
            expectedJson = """{"firstFive": "Hello"}"""
         )
      )
   )
}

object Right : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the rightmost `count` characters from a string.

      ```taxi
      find { account: Account } as {
         suffix: String = account.code.right(4)
      }
      ```
      ]]
      declare extension function right(source:String, count:Int):String""".trimIndent()
   override val name: QualifiedName = stdLibName("right")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22Ouw7CMAxFf8XyAkgZYGCJxIqAlYGBMITGhUCaQpIiUNV/x6UC8dqu7SPfU2PM9lRolIgCzxWFG8fcegO18gC5DTFN7YUkLFOwfgcTcJSnfm9GzpWrMjjTEzAetLDTP2ywu/1/uLBm/Aby+I0JGA0ZbVjspIMuKFGIKOtGYEzVluN6I5CuJ8oSmUUsPavXCl/OCiUofDxVKDg+BbvDo6g7tDIfy7Y0Jp0d5walr5xjh1AeuKkbmzvu+98POQEAAA==
      DocsSnippet(
         markdown = "Extracts the last N characters from a string.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  lastFive: String = right('HelloWorld', 5)
               }
            """.trimIndent(),
            expectedJson = """{"lastFive": "World"}"""
         )
      )
   )
}

object Mid : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the middle of a string, starting at `startIndex` (inclusive) and ending just before `endIndex` (exclusive).

      Uses zero-based indexing, identical to `String.substring()` in Java/Kotlin.

      ```taxi
      find { order: Order } as {
         yearPart: String = order.isoDate.mid(0, 4)
      }
      ```
      ]]
      declare extension function mid(source: String, startIndex: Int, endIndex: Int):String""".trimIndent()
   override val name: QualifiedName = stdLibName("mid")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22Ouw7CMAxFf8XyAkgZYGCJxIqAlYGBMITGhUCaQpIiUNV/x6UC8dqu7SPfU2PM9lRolIgCzxWFG8fcegO18gC5DTFN7YUkLFOwfgcTcJSnfm9GzpWrMjjTEzAetLDTP2ywu/1/uLBm/Aby+I0JGA0ZbVjspIMuKFGIKOtGYEzVluN6I5CuJ8oSmUUsPavXCl/OCiUofDxVKDg+BbvDo6g7tDIfy7Y0Jp0d5walr5xjh1AeuKkbmzvu+98POQEAAA==
      DocsSnippet(
         markdown = "Extracts a substring using a zero-based start index (inclusive) and end index (exclusive).",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  mid5: String = mid('HelloWorld', 5, 10)
               }
            """.trimIndent(),
            expectedJson = """{"mid5": "World"}"""
         )
      )
   )
}

object Uppercase : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Converts all characters in the string to upper case.

      ```taxi
      find { product: Product } as {
         upperName: String = product.name.upperCase()
      }
      ```
      ]]
      declare extension function upperCase(String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("upperCase")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/1WOwQ6CMBBEf2WzFzTpFzTxpCZqSEj04MF6qLAKWlpsS9AQ/t1CL3ib2Z3Mmx5dXlItkSMyfLdkv0HeK11ALzRA2zRkOZy8rfQDVtGvpaNFUpJSBjpjVZEsx6wy3V928jG726ZpBufsmG7G7BBYjbSyJk/WIe8Hhs63tyAvV4b0aSj3VByc0WFNL3DCCuQgcFYlkIXDhIm/2SSBI8R5mb/2BXLdKhWY1jxDc7TDD/zpSrz8AAAA
      DocsSnippet(
         markdown = "Converts a string to upper case.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  upper: String = upperCase('hello world')
               }
            """.trimIndent(),
            expectedJson = """{"upper": "HELLO WORLD"}"""
         )
      )
   )
}


object Lowercase : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Converts all characters in the string to lower case.

      ```taxi
      find { product: Product } as {
         lowerName: String = product.name.lowerCase()
      }
      ```
      ]]
      declare extension function lowerCase(String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("lowerCase")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/1WOwQ6CMBBEf2WzFzTpFzTxpCZqSEj04MF6qLAKWlpsS9AQ/t1CL3ib2Z3Mmx5dXlItkSMyfLdkv0HeK11ALzRA2zRkOZy8rfQDVtGvpaNFUpJSBjpjVZEsx6wy3V928jG726ZpBufsmG7G7BBYjbSyJk/WIe8Hhs63tyAvV4b0aSj3VByc0WFNL3DCCuQgcFYlkIXDhIm/2SSBI8R5mb/2BXLdKhWY1jxDc7TDD/zpSrz8AAAA
      DocsSnippet(
         markdown = "Converts a string to lower case.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  lower: String = lowerCase('HELLO WORLD')
               }
            """.trimIndent(),
            expectedJson = """{"lower": "hello world"}"""
         )
      )
   )
}

object PadStart : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Pads the string to the specified total `length` by prepending `padWith` characters at the start.

      `padWith` should be a single character — if multiple characters are passed, only the first is used.

      ```taxi
      find { order: Order } as {
         paddedId: String = order.id.padStart(8, '0')
      }
      ```
      ]]
      declare extension function padStart(input: String, length: Int, padWith: String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("padStart")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/1WOMQvCMBCF/8pxSxSC1CIOAUcHXR2NQ2xOW03TmqSglP53rxYRb3r37vG+6zEWJdUGFaLER0fhxfJSeQu99gCtsZasgkMKlb/CZjQOyYQ0E6tcSFhLEJmY/6L5f3br7UyU5FzD4WXG6YWYg/YD01oTTE2JQkTVDxJj6s4sjyeJ9GypSGT3sfH8T69xateoQGPGs8o1Svj6+XT4gBbjaBwJMZnivrOofOccA0Nz49ppHd7RoJKf+wAAAA==
      DocsSnippet(
         markdown = "Zero-pads a short string to the target length from the left.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  padded: String = padStart('42', 6, '0')
               }
            """.trimIndent(),
            expectedJson = """{"padded": "000042"}"""
         )
      )
   )
}

object PadEnd : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Pads the string to the specified total `length` by appending `padWith` characters at the end.

      `padWith` should be a single character — if multiple characters are passed, only the first is used.

      ```taxi
      find { product: Product } as {
         paddedCode: String = product.code.padEnd(10, ' ')
      }
      ```
      ]]
      declare extension function padEnd(input: String, length: Int, padWith: String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("padEnd")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/1WOMQvCMBCF/8pxSxSC1CIOAUcHXR2NQ2xOW03TmqSglP53rxYRb3r37vG+6zEWJdUGFaLER0fhxfJSeQu99gCtsZasgkMKlb/CZjQOyYQ0E6tcSFhLEJmY/6L5f3br7UyU5FzD4WXG6YWYg/YD01oTTE2JQkTVDxJj6s4sjyeJ9GypSGT3sfH8T69xateoQGPGs8o1Svj6+XT4gBbjaBwJMZnivrOofOccA0Nz49ppHd7RoJKf+wAAAA==
      DocsSnippet(
         markdown = "Pads a string to the target length by appending characters on the right.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  padded: String = padEnd('hello', 10, '.')
               }
            """.trimIndent(),
            expectedJson = """{"padded": "hello....."}"""
         )
      )
   )
}

object ApplyFormat : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Applies a format string to the input value using [Java Formatter](https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html) conventions.

      Accepts any value (string, number, date, etc.) as input. The format string controls width, padding, precision, and alignment.

      ```taxi
      // Zero-pad an integer to 6 digits
      find { order: Order } as {
         paddedId: order.id.applyFormat('%06d')
      }
      ```
      ]]
      declare extension function applyFormat(input: Any, format: String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("applyFormat")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/z3NwQrCMBAE0F9ZFqQKRUKpl4BXQa8ejYfYbLWapjFJwRL6764K3mYZ9k3G2Nyo1ygRS3yOFCaObecMZOUAoB1Cr1MiI-GYQueusIW6Wmvv7bT7dstiITamWCk3M-F10D0lChFlnkuMabxwPJ1LpJenhqVDHByPZIV_XKEEhUKIulL4YWLSzWNvULrRWlbDcOff3zm_AeUEUP61AAAA
      DocsSnippet(
         markdown = "Formats an integer with zero-padding using `%05d`.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  formatted: String = 42.applyFormat('%05d')
               }
            """.trimIndent(),
            expectedJson = """{"formatted": "00042"}"""
         )
      ),
      // playgroundUrl: http://localhost:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/yXNMQvCMBAF4L9yHEgUqugacBXs6mgczua01TSJSQqW0P9uam96b3jfZYxNyz2hRKzwM3AYS3x0VkNWFgA8ac1awiWFzj7hCKJlY5zYkfdmPLnQU1qL1fawj2Kj7FQUT4F6ThwiyjxVGNNwL_F6q5C_npvEuo7Olj9Z4eIrlKDwL8N8CmcoJmreZ43SDsYUN7hXWS91-gGUEYI0ugAAAA==
      DocsSnippet(
         markdown = "Left-justifies a string in a 10-character wide field using `%-10s`.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  padded: String = 'hello'.applyFormat('%-10s')
               }
            """.trimIndent(),
            expectedJson = """{"padded": "hello     "}"""
         )
      )
   )
}

object Length : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the number of characters in the string.

      ```taxi
      find { message: Message } as {
         charCount: Int = message.body.length()
      }
      ```
      ]]
      declare extension function length(String):Int""".trimIndent()
   override val name: QualifiedName = stdLibName("length")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/zWOPQ/CIBCG/8rpFlrJC1LLYIkVUVaGDpjBjS/g4thwdhBVlP/eCxGIlu353Se/r8Nc7amxqBEVnlriH4m1jw46EwFqz7ksUssa1oV93MEcAtXlZbKkEBJ8Jg5uouDtdaCDFdif6QFmv9s/0e9XuvHOhUdWiv/kTMF0KnQvdkfLtqFCnFF3vcJc2i+Jm61CuhypKuRWOUXx7wzexQ1qMDj8alBJujmO/XVnPIw6f+phNRdbHT4c6tiGIBKcvmVqfPa/s+TdOD8BAAA=
      DocsSnippet(
         markdown = "Returns the character count of a string.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  len: Int = length('Hello')
               }
            """.trimIndent(),
            expectedJson = """{"len": 5}"""
         )
      )
   )
}

object IndexOf : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the zero-based index of `valueToSearchFor` within `source`, or `-1` if not found.

      ```taxi
      find { path: FilePath } as {
         dotPosition: Int = path.value.indexOf('.')
      }
      ```
      ]]
      declare extension function indexOf(source:String, valueToSearchFor:String):Int""".trimIndent()
   override val name: QualifiedName = stdLibName("indexOf")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/zWOPQ/CIBCG/8rpFlrJC1LLYIkVUVaGDpjBjS/g4thwdhBVlP/eCxGIlu353Se/r8Nc7amxqBEVnlriH4m1jw46EwFqz7ksUssa1oV93MEcAtXlZbKkEBJ8Jg5uouDtdaCDFdif6QFmv9s/0e9XuvHOhUdWiv/kTMF0KnQvdkfLtqFCnFF3vcJc2i+Jm61CuhypKuRWOUXx7wzexQ1qMDj8alBJujmO/XVnPIw6f+phNRdbHT4c6tiGIBKcvmVqfPa/s+TdOD8BAAA=
      DocsSnippet(
         markdown = "Returns the index of a substring within a string, or `-1` if not found.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  pos: Int = indexOf('Hello World', 'World')
               }
            """.trimIndent(),
            expectedJson = """{"pos": 6}"""
         )
      )
   )
}

object ContainsString : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if `source` contains `valueToSearchFor` as a substring. The match is case-sensitive.

      ```taxi
      // Check if a description mentions a keyword
      find {
         mentionsDiscount: Boolean = Description.containsString("discount")
      }
      ```
      ]]
      declare extension function containsString(source:String, valueToSearchFor:String):Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("containsString")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/5WPMW7DMAxFr0JwagEjBxCQpVu7esgQZWBkulYrU44oJy0E372yWxTN2I3EJ95_LKhu4JHQIDZ4mTl91rH30kGxAgADaTufNScvrwaeYgxMAnuwOHAIEW4xhc7izkXJ5EXb7fLB4k_wuFEcKbcs6rO_8r8wh1_MUgUnSjRy5qRoytKg5uqG5nhqkD8mdpm7F41SXyiV_EfdooGcZm5q453MGvQUlFe8ZnLvzx0amUOobSm-Veb3unwBN3GwOysBAAA=
      DocsSnippet(
         markdown = "Checks for a case-sensitive substring match.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  hasSubstring: Boolean = "hello world".containsString("world")
                  caseSensitive: Boolean = "hello world".containsString("World")
               }
            """.trimIndent(),
            expectedJson = """{"hasSubstring": true, "caseSensitive": false}"""
         )
      )
   )
}

object StartsWith : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if `source` begins with the specified prefix. The match is case-sensitive.

      ```taxi
      find { files: File[] } as {
         tempFiles: File[] = files.filter((File) -> FileName.startsWith('tmp_'))
      }
      ```
      ]]
      declare extension function startsWith(source: String, valueToSearchFor:String):Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("startsWith")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42OzQrCMBCEX2XZSxWCDxDw4kkFwVsPxkNMtqQak5ofqIS+uykVz95mZ5mZr2BUhp4SOSLDV6bwrrLrnYYiHICR8Ryo60cOO+8tSQdbaPZkrYfWB6ubTUwypNj2yayWR7Oek86fZFLmv9xi1dxUKQYZ5JMShYi8TAxjyrcqL1eGNA6kEulj9K5yFoE/QIEcUsjEQOB3e7Y6aSPNrXVOPQ4aucvW1pHg77VqOacPbD33xAcBAAA=
      DocsSnippet(
         markdown = "Returns `true` when the string begins with the specified prefix, case-sensitively.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  hasPrefix: Boolean = 'Hello World'.startsWith('Hello')
                  noMatch: Boolean = 'Hello World'.startsWith('World')
               }
            """.trimIndent(),
            expectedJson = """{"hasPrefix": true, "noMatch": false}"""
         )
      )
   )
}

object EndsWith : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if `source` ends with the specified suffix. The match is case-sensitive.

      ```taxi
      // Check if a filename has a specific extension
      find {
         isCsv: Boolean = FileName.endsWith(".csv")
      }
      ```
      ]]
      declare extension function endsWith(source: String, valueToSearchFor:String):Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("endsWith")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42OsQ7CMAxEf8XyBFLVD4jEwgYrAwNlCI2jtoSkxAkqivrvuEXsbHeW7-4V5Lajh0aFWOEzU3yLtL03UBoPAJ3mU7a2nxTsQ3CkPeygwY6cC3XLrwZr8obPfeo2otfLdk36MNJ_oYGDX1OzMIw66gclioyqzBVyyjeRl2uFNI3UJjJH-RfKIo0_ugYVpJipkpllePFWO6alkpNu7weDymfnZCGGQXq-dv4A2th-5gIBAAA=
      DocsSnippet(
         markdown = "Checks whether a string ends with a given suffix, case-sensitively.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  hasSuffix: Boolean = "hello.csv".endsWith(".csv")
                  nope: Boolean = "hello.csv".endsWith(".json")
               }
            """.trimIndent(),
            expectedJson = """{"hasSuffix": true, "nope": false}"""
         )
      )
   )
}


object Replace : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Replaces all occurrences of `searchValue` in `source` with `replacement`, returning a new string.

      ```taxi
      find { order: Order } as {
         cleanRef: String = order.reference.replace('-', '_')
      }
      ```
      ]]
      declare extension function replace(source: String, searchValue:String, replacement: String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("replace")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/z2NwQrCMBBEf2XZSxTyBQHv6lXBg/EQk1WradJuUqiE/rsppd7eDMybgsm+qDWoECX2A/G34qMJDooOAEydN5acglPmJjxht1YbsSfvI1wieyckiD+czdiIrQ5TNXaGTUuZOKEqk8SUh3vF600ijR3ZTO6YYqifReN6plGBxkU/yzTOqpSN/RwcqjB4X80c33W/xOkHp+5L7MgAAAA=
      DocsSnippet(
         markdown = "Replaces all occurrences of a substring with a new value.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  replaced: String = replace('Hello World', 'World', 'Taxi')
               }
            """.trimIndent(),
            expectedJson = """{"replaced": "Hello Taxi"}"""
         )
      )
   )
}

object Matches : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if the **entire** source string matches the provided regular expression.

      Uses full-string matching — the pattern must match the entire string, not just a part of it.
      Use `containsPattern` for partial (substring) matching.

      ```taxi
      find { account: Account } as {
         isValidCode: Boolean = account.code.matches('^[A-Z]{3}-\d{4}$')
      }
      ```
      ]]
      declare extension function matches(source: String, regex: String): Boolean
      """.trimIndent()
   override val name: QualifiedName = stdLibName("matches")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42OSwoCMRBEr9I0QlSCBwi4cSEoeAKjEDM9RM0kYz6ghLm7CYK4dFfdRdWrglEbGhQKRI6PTOFVZX91HRTpAPps7UElbQRsvLekHKyBGbLWs9XQDIpzdjarpZ+xRUs4v/0zQy0wVeyoghooUYgoysQxpnyp8njiSM+RdKJuH72rw4rE7yKJAlLIxEHiD7S9e2UjteaYlL7vOhSu2hUU/K3Wfc7pDeD3fW38AAAA=
      DocsSnippet(
         markdown = "Returns `true` only when the full string matches the regex pattern.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  fullMatch: Boolean = 'hello'.matches('^h.*o${'$'}')
                  noFullMatch: Boolean = 'hello'.matches('^he')
               }
            """.trimIndent(),
            expectedJson = """{"fullMatch": true, "noFullMatch": false}"""
         )
      )
   )
}

object ContainsPattern : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if any part of the source string matches the provided regular expression.

      Uses partial matching — the regex does not need to match the entire string. Use `^` and `$` anchors for full-string matching.

      ```taxi
      // Partial match — returns true if the string contains the pattern anywhere
      find {
         startsWithHe: Boolean = "hello".containsPattern("^he")
         containsEll: Boolean = "hello".containsPattern("ell")
      }
      ```
      ]]
      declare extension function containsPattern(source: String, regex: String): Boolean
      """.trimIndent()
   override val name: QualifiedName = stdLibName("containsPattern")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42PQWrDQAxFryK0SsHkAAPddJdCIJBlpgXVo2AnY407I0NS47tHbtrgZXaSvp70_4ilbrgjdIgVfg-cr1YeWwkwegGAjtQWyl4pq4O3lCKTwCt4bDjG5HFdJ1FqpexIlbOsPH427PFliW_bECI_y5vwz0vazheeJS_Xn19ysjA9ZerYhIJunCosOnxZefiokC8918rhvSSxuKPHZUyPDjQPXMFjfve_FP6MzaMjxcLzx6JUnzcBnQwxmoGcTvbm3k43im1mlGoBAAA=
      DocsSnippet(
         markdown = "Demonstrates partial matching: anchor patterns, substring patterns, and non-matching patterns.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  matchesStart: Boolean = "hello".containsPattern("^he")
                  matchesMiddle: Boolean = "hello".containsPattern("ell")
                  noMatch: Boolean = "hello".containsPattern("xyz")
               }
            """.trimIndent(),
            expectedJson = """{"matchesStart": true, "matchesMiddle": true, "noMatch": false}"""
         )
      )
   )
}



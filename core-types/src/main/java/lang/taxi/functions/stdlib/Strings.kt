package lang.taxi.functions.stdlib

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
      Find,
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

object Concat : FunctionApi {
   override val taxi: String = "declare function concat(Any...):String"
   override val name: QualifiedName = stdLibName("concat")

}

object Trim : FunctionApi {
   override val taxi: String = "declare extension function trim(String):String"
   override val name: QualifiedName = stdLibName("trim")
}

object Left : FunctionApi {
   override val taxi: String = """
      [[ Returns the left most characters from the source string ]]
      declare extension function left(source:String,count:Int):String""".trimIndent()
   override val name: QualifiedName = stdLibName("left")
}

object Right : FunctionApi {
   override val taxi: String = "declare extension function right(source:String,count:Int):String"
   override val name: QualifiedName = stdLibName("right")
}

object Mid : FunctionApi {
   override val taxi: String = """
      [[
      Returns the middle of a string, starting at the `startIndex`, and ending right before the `endIndex`.

      * `startIndex` - the start index (inclusive)
      * `endIndex` - the end index (exclusive)
      ]]
      declare extension function mid(source: String,startIndex: Int,endIndex: Int):String""".trimIndent()
   override val name: QualifiedName = stdLibName("mid")
}

object Uppercase : FunctionApi {
   override val taxi: String = "declare extension function upperCase(String):String"
   override val name: QualifiedName = stdLibName("upperCase")
}


object Lowercase : FunctionApi {
   override val taxi: String = "declare extension function lowerCase(String):String"
   override val name: QualifiedName = stdLibName("lowerCase")
}

object PadStart : FunctionApi {
   override val taxi: String = """
      [[ Pads the string to the specified length at the start with the specified character or space.
      padWith should be a single character - if multiple characters are passed, only the first is used
      ]]
      declare extension function padStart(input: String, length: Int, padWith: String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("padStart")
}

object PadEnd : FunctionApi {
   override val taxi: String = """
      [[ Pads the string to the specified length at the end with the specified character or space.
       padWith should be a single character - if multiple characters are passed, only the first is used
       ]]
      declare extension function padEnd(input: String, length: Int, padWith: String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("padEnd")
}

object ApplyFormat : FunctionApi {
   override val taxi: String = """
      [[ Applies the provided format to the input string, and returns the result.

       Formats follow the format defined by the [Java Formatter](https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html) conventions
       ]]
      declare extension function applyFormat(input: Any, format: String):String""".trimIndent().format()
   override val name: QualifiedName = stdLibName("applyFormat")
}

object Length : FunctionApi {
   override val taxi: String = "declare extension function length(String):Int"
   override val name: QualifiedName = stdLibName("length")
}

object Find : FunctionApi {
   override val taxi: String = """
         [[ Returns the index of `valueToSearchFor` within `source` ]]
         declare extension function indexOf(source:String, valueToSearchFor:String):Int""".trimIndent()
   override val name: QualifiedName = stdLibName("indexOf")
}

object ContainsString : FunctionApi {
   override val taxi: String = """
         [[ Returns true if `valueToSearchFor` within `source` ]]
         declare extension function containsString(source:String, valueToSearchFor:String):Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("containsString")
}

object StartsWith : FunctionApi {
   override val taxi: String = """
      [[ Returns true if the source string begins with the provided prefix ]]
      declare extension function startsWith(source: String, valueToSearchFor:String):Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("startsWith")
}

object EndsWith : FunctionApi {
   override val taxi: String = """
      [[ Returns true if the source string ends with the provided prefix ]]
      declare extension function endsWith(source: String, valueToSearchFor:String):Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("endsWith")
}


object Replace : FunctionApi {
   override val taxi: String = """[[
      Replaces the contents of the provided String, returning a new String
      Accepts three args:
       * `source: String`: The string to search
       * `searchValue: String`: The string to search for
       * `replacement: String`: The string to use as a replacement
      ]]
      declare extension function replace(source: String, searchValue:String, replacement: String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("replace")
}

object Matches : FunctionApi {
   override val taxi: String = """
      [[
        Returns `true` if the **entire** source string matches the provided regular expression.
        This uses full-string matching — the pattern must match the **entire string**, not just a part of it.

        For example:
          `"hello".matches("^h.*o${'$'}")` => `true`
          `"hello".matches("^he")`    => `false` (partial matches are not considered valid)
      ]]
      declare extension function matches(source: String, regex: String): Boolean
      """.trimIndent()
   override val name: QualifiedName = stdLibName("matches")
}

object ContainsPattern : FunctionApi {
   override val taxi: String = """
      [[
        Returns true if any part of the source string matches the provided regular expression.
        This allows partial matches and does not require the entire string to conform.

        For example:
          `"hello".containsPattern("^he")`   => `true`
          `"hello".containsPattern("ell")`   => `true`
          `"hello".containsPattern("xyz")`   => `false`
      ]]
      declare extension function containsPattern(source: String, regex: String): Boolean
      """.trimIndent()
   override val name: QualifiedName = stdLibName("containsPattern")
}



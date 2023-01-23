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
      ContainsString
//      Coalesce
   )
}

object Concat : FunctionApi {
   override val taxi: String = "declare function concat(String...):String"
   override val name: QualifiedName = stdLibName("concat")

}

object Trim : FunctionApi {
   override val taxi: String = "declare function trim(String):String"
   override val name: QualifiedName = stdLibName("trim")
}

object Left : FunctionApi {
   override val taxi: String = """
      [[ Returns the left most characters from the source string ]]
      declare function left(source:String,count:Int):String""".trimIndent()
   override val name: QualifiedName = stdLibName("left")
}

object Right : FunctionApi {
   override val taxi: String = "declare function right(source:String,count:Int):String"
   override val name: QualifiedName = stdLibName("right")
}

object Mid : FunctionApi {
   override val taxi: String = """
      [[
      Returns the middle of a string, starting at the `startIndex`, and ending right before the `endIndex`.

      * `startIndex` - the start index (inclusive)
      * `endIndex` - the end index (exclusive)
      ]]
      declare function mid(source: String,startIndex: Int,endIndex: Int):String""".trimIndent()
   override val name: QualifiedName = stdLibName("mid")
}

object Uppercase : FunctionApi {
   override val taxi: String = "declare function upperCase(String):String"
   override val name: QualifiedName = stdLibName("upperCase")
}


object Lowercase : FunctionApi {
   override val taxi: String = "declare function lowerCase(String):String"
   override val name: QualifiedName = stdLibName("lowerCase")
}

object Length: FunctionApi {
   override val taxi: String
      get() = "declare function length(String):Int"
   override val name: QualifiedName
      get() = stdLibName("length")
}

object Find: FunctionApi {
   override val taxi: String
      get() = """
         [[ Returns the index of `valueToSearchFor` within `source` ]]
         declare function indexOf(source:String, valueToSearchFor:String):Int""".trimIndent()
   override val name: QualifiedName
      get() = stdLibName("indexOf")
}

object ContainsString : FunctionApi {
   override val taxi: String
      get() = """
         [[ Returns true if `valueToSearchFor` within `source` ]]
         declare function containsString(source:String, valueToSearchFor:String):Boolean""".trimIndent()
   override val name: QualifiedName
      get() = stdLibName("containsString")
}

object Replace : FunctionApi {
   override val taxi: String = """[[
      Replaces the contents of the provided String, returning a new String
      Accepts three args:
       * `source: String`: The string to search
       * `searchValue: String`: The string to search for
       * `replacement: String`: The string to use as a replacement
      ]]
      declare function replace(source: String, searchValue:String, replacement: String):String""".trimIndent()
   override val name: QualifiedName = stdLibName("replace")

}


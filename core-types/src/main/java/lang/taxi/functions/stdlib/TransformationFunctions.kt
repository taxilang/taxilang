package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Transformations {
   val functions: List<FunctionApi> = listOf(Convert)
}

object Convert : FunctionApi {
   override val taxi: String = """
      [[
      Converts the provided source into the target type reference.
       Conversions are performed locally, using only the data provided in source - ie.,
       no services or graph searches are performed.

       This method is less powerful than using a standard projection (eg., A as B), because:

        - Only the exact facts passed in the source are considered
        - No graph searches or remote invocations are performed

        As a result, it's also more performant.
       ]]
      declare function <T> convert(source: Any, targetType: lang.taxi.Type<T>): T""".trimIndent()
   override val name: QualifiedName = stdLibName("convert")
}

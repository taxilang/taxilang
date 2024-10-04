package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Transformations {
   val functions: List<FunctionApi> = listOf(Convert,ToRawType)
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
      declare extension function <T> convert(source: Any, targetType: lang.taxi.Type<T>): T""".trimIndent()
   override val name: QualifiedName = stdLibName("convert")
}

object ToRawType : FunctionApi {
   override val taxi: String = """
   [[
   Removes the semantic typing from the provided scalar value, returning a scalar value
   typed with the raw primitive type.

   This can be useful if trying to compare values ignoring their type.

   - If called with an Array, will remove semantic types of all members. The array should only contain scalar values.
   - If called with a scalar value, will remove the semantic type of the value

   Not supported for calling on an object (or array of objects), as Taxi's type system
   is structural, and needs to know the structure of the type it's operating on.
   ]]
   declare extension function toRawType(source: Any):Any
   """
   override val name: QualifiedName = stdLibName("toRawType")
}

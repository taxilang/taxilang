package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Math {
   val functions: List<FunctionApi> = listOf(
      Round,
      Sum,
      Max,
      Min
   )
}

object Max : FunctionApi {
   override val taxi: String = "declare extension function <T,A> max(collection: T[], callback: (T) -> A):A"
   override val name: QualifiedName = stdLibName("max")
}

object Min : FunctionApi {
   override val taxi: String = "declare extension function <T,A> min(collection: T[], callback: (T) -> A):A"
   override val name: QualifiedName = stdLibName("min")
}


object Sum : FunctionApi {
   override val taxi: String = "declare extension function <T,A> sum(collection: T[], callback: (T) -> A):A"
   override val name: QualifiedName = stdLibName("sum")
}

object Round : FunctionApi {
   override val taxi: String = """
      [[  Rounds a decimal value to the specified precision. ]]
      declare extension function round(value: Decimal, precision: Int = 0): Decimal
   """.trimIndent()
   override val name: QualifiedName = stdLibName("round")
}

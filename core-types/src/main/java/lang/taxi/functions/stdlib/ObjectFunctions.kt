package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object ObjectFunctions {
   val functions: List<FunctionApi> = listOf(
      Equals,
      EmptyInstance
   )
}

object Equals : FunctionApi {
   override val taxi: String = "declare extension function <A,B> equals(a:A, b:B): Boolean"
   override val name: QualifiedName = stdLibName("equals")
}


object EmptyInstance : FunctionApi {
   override val taxi: String = """
      [[ Returns an instance of the requested type, with all properties empty.
      Scalars are null, collections are empty, and object types are recursed into, also populated as empty
      ]]
      declare extension function <A> emptyInstance(instanceType:Type<A>): A""".trimIndent()
   override val name: QualifiedName = stdLibName("emptyInstance")
}

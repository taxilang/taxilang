package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object ObjectFunctions {
   val functions: List<FunctionApi> = listOf(Equals)
}

object Equals : FunctionApi {
   override val taxi: String = "declare function <A,B> equals(a:A, b:B): Boolean"
   override val name: QualifiedName = stdLibName("equals")
}

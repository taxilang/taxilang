package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Errors {
   val functions: List<FunctionApi> = listOf(Throw)

}

object Throw : FunctionApi {
   override val taxi: String = "declare function throw(error:Any):Void"
   override val name: QualifiedName
      get() = stdLibName("throw")
}

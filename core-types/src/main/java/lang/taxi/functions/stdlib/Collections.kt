package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Collections {
   val functions: List<FunctionApi> = listOf(Contains)
}

object Contains : FunctionApi {
   override val taxi: String
      get() = "declare function <T> contains(collection: T[], searchTarget:T): Boolean"
   override val name: QualifiedName = StdLib.stdLibName("contains")
}

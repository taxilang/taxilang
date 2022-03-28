package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Collections {
   val functions: List<FunctionApi> = listOf(Contains, AllOf, AnyOf, NoneOf)
}

object NoneOf : FunctionApi {
   override val taxi: String = "declare function noneOf(values:Boolean...): Boolean"
   override val name: QualifiedName = stdLibName("noneOf")
}

object AnyOf : FunctionApi {
   override val taxi: String = "declare function anyOf(values:Boolean...): Boolean"
   override val name: QualifiedName = stdLibName("anyOf")
}

object AllOf : FunctionApi {
   override val taxi: String = "declare function allOf(values:Boolean...): Boolean"
   override val name: QualifiedName = stdLibName("allOf")
}

object Contains : FunctionApi {
   override val taxi: String = "declare function <T> contains(collection: T[], searchTarget:T): Boolean"
   override val name: QualifiedName = stdLibName("contains")
}

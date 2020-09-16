package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

interface FunctionApi {
   val taxi: String
   val name: QualifiedName
}


fun List<FunctionApi>.namespacedTaxi():String {
   val result = groupBy { it.name.namespace }
      .map { (namespace,functions) ->
         val functionTaxi = functions.joinToString("\n") { it.taxi }
         """namespace $namespace {
            |$functionTaxi
            |}
         """.trimMargin()
      }.joinToString("\n")
   return result

}

fun String.inNamespace(namespace:String):String {
   return """namespace $namespace {
      |$this
      |}
   """.trimMargin()
}

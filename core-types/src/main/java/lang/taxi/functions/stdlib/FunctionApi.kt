package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

interface FunctionApi {
   val taxi: String
   val name: QualifiedName
}

/**
 * Docs interface.
 * Allows us to embed runnable examples in a functions docs.
 * The examples become executable both inline, and with a link to Taxi playground.
 *
 * Not required for all functions - only those that are complex, or where a user could benefit from
 * seeing a runnable example.
 */
interface HasRunnableExamples {
   val examples: List<DocsSnippet>
}
data class DocsSnippet(
   /**
    * A short title, suitable for the specific example being shown
    */
   val snippetTitle: String? = null,
   /**
    * A short 1-2 sentence description of what the example shows, using markdown
    */
   val markdown: String,
   val query: StubQueryMessage
)

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

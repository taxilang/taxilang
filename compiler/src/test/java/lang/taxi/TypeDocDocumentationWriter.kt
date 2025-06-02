package lang.taxi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import lang.taxi.functions.stdlib.FunctionApi
import lang.taxi.functions.stdlib.HasRunnableExamples

private data class DocSection(
   val title: String,
   val preamble: String,
   val functions: List<FunctionApi>
)

class TypeDocDocumentationWriter(val schema: TaxiDocument) {
   private val sections = mutableListOf<DocSection>()
   fun appendSection(sectionTitle: String, preamble: String, functions: List<FunctionApi>): TypeDocDocumentationWriter {
      sections.add(
         DocSection(
            sectionTitle, preamble, functions
         )
      )
      return this
   }

   fun generate(): String {
      return sections.joinToString("\n\n") { generateSection(it) }
   }

   private fun generateSection(section: DocSection): String {
      val functions = section.functions
         .sortedBy { it.name.typeName }
         .joinToString("\n\n------\n\n") { generateFunction(it) }

      return """## ${section.title}

${section.preamble}

$functions
      """.trimMargin()
   }

   private fun generateFunction(functionApi: FunctionApi): String {
      val function = schema.function(functionApi.name.fullyQualifiedName)
      val taxiWithoutTypeDoc = functionApi.taxi.trim().let {
         if (it.startsWith("[[")) {
            it.substringAfter("]]").trim()
         } else it
      }

      val runnableSnippets = if (functionApi is HasRunnableExamples) {

         functionApi.examples.joinToString("\n\n", prefix = "#### Examples\n") { example ->
            val queryJson = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(example.query)
            """
${example.markdown}

<PlaygroundSnippet scenario={$queryJson
}></PlaygroundSnippet>
            """.trimIndent()
         }
      } else ""
      return """### ${functionApi.name.typeName}
`${functionApi.name.fullyQualifiedName}`

```taxi
$taxiWithoutTypeDoc
```

${function.typeDoc ?: ""}

$runnableSnippets
      """.trim()
   }
}

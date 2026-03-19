package lang.taxi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import lang.taxi.functions.stdlib.FunctionApi
import lang.taxi.functions.stdlib.HasRunnableExamples

private data class DocSection(
   val title: String,
   val preamble: String,
   val functions: List<FunctionApi>
)

class StdLibSummaryWriter(private val schema: TaxiDocument) {
   private val sections = mutableListOf<DocSection>()

   fun appendSection(sectionTitle: String, preamble: String, functions: List<FunctionApi>): StdLibSummaryWriter {
      sections.add(DocSection(sectionTitle, preamble, functions))
      return this
   }

   fun generateSummary(): String {
      val preamble = """---
IMPORTANT: This file is generated.  Do not edit manually.  For the preamble, edit stdlib.mdx in compiler/src/test/resource. All other content is generated directly from classes

title: Taxi StdLib
description: Reference documentation on functions provided in Taxi's StdLib packages
---

The Taxi Standard Library provides a collection of built-in functions for common operations
including string manipulation, date handling, math, collections, and more.

"""
      val sectionStrings = sections.map { section ->

         val sectionHeader = """## ${section.title}
               |
               |${section.preamble}
               |
               || Function | Description |
               ||----------|-------------|
            """.trimMargin()
         val sectionTable = section.functions.sortedBy { it.name.typeName }.joinToString("\n") { functionApi ->
            val function = schema.function(functionApi.name.fullyQualifiedName)
            val signature = extractSignature(functionApi)
            val description = extractShortDescription(function?.typeDoc)
            val link = "./stdlib/${functionApi.name.typeName}"
            "| [`${functionApi.name.typeName}`]($link) | `$signature`<br /> $description |"
         }
         sectionHeader + "\n" +sectionTable

      }
      return preamble + sectionStrings.joinToString("\n") + "\n"
   }

   fun generateFunctionDocs(): Map<String, String> {
      return sections.flatMap { section ->
         section.functions.map { functionApi ->
            val fileName = "${functionApi.name.typeName}.mdx"
            val content = generateFunctionPage(functionApi, section.title)
            fileName to content
         }
      }.toMap()
   }

   private fun generateFunctionPage(functionApi: FunctionApi, sectionTitle: String): String {
      val function = schema.function(functionApi.name.fullyQualifiedName)
      val taxiWithoutTypeDoc = extractTaxiWithoutTypeDoc(functionApi)

      val runnableSnippets = if (functionApi is HasRunnableExamples) {
         functionApi.examples.joinToString("\n\n", prefix = "## Examples\n\n") { example ->
            val queryJson = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(example.query)
            """
${example.markdown}

<PlaygroundSnippet scenario={$queryJson
}></PlaygroundSnippet>
            """.trimIndent()
         }
      } else ""

      return """
---
IMPORTANT: This file is generated. Do not edit manually.

title: ${functionApi.name.typeName}
---

import { Callout } from '@/components/docs/Callout';
import PlaygroundSnippet from "@/components/PlaygroundSnippet";


**Full name:** `${functionApi.name.fullyQualifiedName}`

## Signature

```taxi
$taxiWithoutTypeDoc
```

## Description

${function?.typeDoc?.trim() ?: "_No description available._"}

$runnableSnippets

""".trimStart()
   }

   private fun extractTaxiWithoutTypeDoc(functionApi: FunctionApi): String {
      return functionApi.taxi.trim().let {
         if (it.startsWith("[[")) it.substringAfter("]]").trim() else it
      }
   }

   private fun extractSignature(functionApi: FunctionApi): String {
      return extractTaxiWithoutTypeDoc(functionApi)
         .replace(Regex("^\\s*declare\\s+(extension\\s+)?function\\s+"), "")
         .trim()
   }

   private fun extractShortDescription(typeDoc: String?): String {
      if (typeDoc.isNullOrBlank()) return ""
      // Find the first non-blank line, then take the first sentence
      val firstLine = typeDoc.trim().lines().firstOrNull { it.isNotBlank() }?.trim() ?: return ""
      val firstSentence = firstLine.split(Regex("(?<=[.!?])\\s+")).first().trim()
      return if (firstSentence.length > 120) firstSentence.take(117) + "..." else firstSentence
   }
}

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

         functionApi.examples.joinToString("\n\n", prefix = "### Examples\n") { example ->
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

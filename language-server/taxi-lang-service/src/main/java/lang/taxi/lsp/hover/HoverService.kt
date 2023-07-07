package lang.taxi.lsp.hover

import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.TypeReferenceContext
import lang.taxi.findNamespace
import lang.taxi.lsp.CompilationResult
import lang.taxi.lsp.completion.normalizedUriPath
import lang.taxi.searchUpForRule
import lang.taxi.types.*
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import java.util.concurrent.CompletableFuture

class HoverService {
   fun hover(
      compilationResult: CompilationResult,
      lastSuccessfulCompilationResult: CompilationResult?,
      params: HoverParams
   ): CompletableFuture<Hover> {
      val context = compilationResult.compiler.contextAt(
         params.position.line,
         params.position.character,
         params.textDocument.normalizedUriPath()
      )
      val qualifiedName = when (context) {
         is TaxiParser.TypeReferenceContext -> compilationResult.compiler.lookupTypeByName(context)
         is TaxiParser.ListOfInheritedTypesContext -> compilationResult.compiler.lookupTypeByName(
            context.typeReference(
               0
            )
         )

         is TaxiParser.IdentifierContext -> {
            // WE could be a field in a model
            context.searchUpForRule<TypeReferenceContext>()
               ?.let { typeReference -> compilationResult.compiler.lookupTypeByName(typeReference) }
            // ... or we could be a model definition, in which case resolve the name from he identifier
               ?: context.let {
                  val namespace = context.findNamespace()
                  QualifiedName(namespace, context.text)
               }
         }

         else -> null
      }

      val content = getTypeDoc(qualifiedName, lastSuccessfulCompilationResult, compilationResult)
      val hover = if (content != null) {
         Hover(content)
      } else {
         Hover(emptyList())
      }
      return CompletableFuture.completedFuture(hover)
   }

   private fun getTypeDoc(
      qualifiedName: QualifiedName?,
      lastSuccessfulCompilationResult: CompilationResult?,
      compilationResult: CompilationResult
   ): MarkupContent? {
      if (qualifiedName != null) {
         val taxiDocument = listOfNotNull(
            lastSuccessfulCompilationResult?.document,
            compilationResult.document
         ).firstOrNull()
         if (taxiDocument != null && taxiDocument.containsType(qualifiedName.fullyQualifiedName)) {
            val type = taxiDocument.type(qualifiedName)
            return generateDocumentation(type)
         }
      }
      return null
   }

   private fun generateDocumentation(type: Type): MarkupContent {
      val typeLabel = when (type) {
         is EnumType -> "enum"
         is ObjectType -> {
            if (type.fields.isNotEmpty()) {
               "model"
            } else {
               "type"
            }
         }

         else -> "type"
      }
      val typeDoc = if (type is Documented) {
         type.typeDoc?.trim() + "\n\n---\n\n"
      } else {
         ""
      }
      val docs = """
## $typeLabel ${type.toQualifiedName().typeName}

`${type.qualifiedName}`
${getInheritenceDocs(type)}

---

$typeDoc

${getSource(type)}
        """.trim()
      return MarkupContent("markdown", docs)
   }

   private fun getSource(type: Type): String {
      val src = type.compilationUnits.joinToString("\n---\n") {
         """```
// Defined at ${it.source.sourceName}:

${it.source.content}
```            """.trim()
      }
      return """### Source

$src
       """.trimMargin()
   }

   private fun getInheritenceDocs(type: Type): String {
      val inheritenceDeclaration = if (type.inheritsFromPrimitive) {
         type.getInheritanceGraph().joinToString(separator = "->") { it.qualifiedName }
//            type.basePrimitive!!.toQualifiedName().typeName
      } else if (type is EnumType && type.baseEnum != null) {
         "inherits ${type.baseEnum!!.qualifiedName}"
      } else if (type.inheritsFrom.isNotEmpty()) {
         "inherits ${type.inheritsFrom.joinToString { it.qualifiedName }}"
      } else {
         ""
      }
      return if (!type.inheritsFromPrimitive && type.inheritsFrom.isEmpty()) {
         ""
      } else {
         """### Inheritance

${type.getInheritanceGraph().joinToString("\n") { " * ${it.qualifiedName}" }}
          """

      }
   }

}

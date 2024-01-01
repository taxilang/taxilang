package lang.taxi.sources

import lang.taxi.formatter.TaxiCodeFormatter
import lang.taxi.types.Arrays
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.SourceNames
import lang.taxi.utils.trimEmptyLines
import java.io.File
import java.nio.file.Path

data class SourceLocation(val line: Int, val char: Int) {
   companion object {
      val UNKNOWN_POSITION = SourceLocation(1, 1)
   }
}

/**
 * Indicates the language of a source code.
 * Stringly typed (rather than an enum), as in
 * theory there can be any type of source stashed here.
 * Use SourceCodeLanguages for common values
 */
typealias SourceCodeLanguage = String

object SourceCodeLanguages {
   const val TAXI = "taxi"
   const val WSDL = "wsdl"
}


data class SourceCode(
   val sourceName: String,
   val content: String,
   var path: Path? = null,
   val language: SourceCodeLanguage = SourceCodeLanguages.TAXI
) {
   fun formattedSource():String {
      return TaxiCodeFormatter.format(content)
   }
   companion object {
      fun unspecified(): SourceCode = SourceCode("Not specified", "")
      fun from(file: File): SourceCode {
         return from(file.toPath())
      }

      fun from(path: Path): SourceCode {
         return SourceCode(SourceNames.normalize(path.toUri()), path.toFile().readText(), path)
      }
   }

   val normalizedSourceName: String = lang.taxi.types.SourceNames.normalize(sourceName)

   /**
    * Wraps the source, ensuring that a namespace declaraion is present, and that
    * any required imports are also present.
    *
    * We do this because the source is originally JUST the extract from the file that
    * resulted in an compiled element being created.  However, without the namespace and imports,
    * the source fragment on its own is invalid.
    */
   fun makeStandalone(namespace: String, dependantTypeNames: List<QualifiedName>): SourceCode {
      val sourceContainsNamespaceDeclaration = (this.content.lines().any { it.trim().startsWith("namespace") })
      val requiresNamespaceDeclaration = namespace.isNotEmpty() && !sourceContainsNamespaceDeclaration
      val allDependantTypes = dependantTypeNames + dependantTypeNames.flatMap { it.parameters }
      val requiredImports =
         allDependantTypes.filter {
            it.namespace != namespace && !PrimitiveType.isPrimitiveType(it.fullyQualifiedName) && !Arrays.isArray(
               it
            )
         }
      val presentImports = this.content.lines().filter { it.trim().startsWith("import") }
      val missingImports =
         requiredImports.filter { qualifiedName -> presentImports.none { it == qualifiedName.fullyQualifiedName } }

      val contentWithoutImports = this.content.lines().filter { !it.trim().startsWith("import") }
         .map { it.trimIndent() }
         .joinToString("\n")
      val allImports = presentImports + missingImports.map { "import ${it.fullyQualifiedName}" }
      val importPrelude =  allImports.joinToString("\n")

      val namespacedContent = if (requiresNamespaceDeclaration) {
         """namespace $namespace {
            |${contentWithoutImports.lines().map { it.prependIndent("   ") }.joinToString("\n")}
            |}
         """.trimMargin()
      } else {
         contentWithoutImports
      }
      val finalSource = """$importPrelude
         |
         |$namespacedContent
      """.trimMargin()
         .trimEmptyLines(preserveIndent = true)
      return this.copy(content = finalSource)
   }
}


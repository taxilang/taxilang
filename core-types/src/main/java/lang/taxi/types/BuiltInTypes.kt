package lang.taxi.types

import lang.taxi.functions.stdlib.StdLib
import lang.taxi.functions.stdlib.stdLibName
import lang.taxi.sources.SourceCode
import lang.taxi.annotations.HttpService

interface BuiltIn : TaxiStatementGenerator, HasQualifiedName

interface BuiltInSourceCode {
   val sourceCode: SourceCode
}

/**
 * Libs (like stdlib) that are bundled into the compiler.
 * These are pre-compiled in the compiler as a static reference (see Compiler::builtInCompiledTaxi)
 */
object BuiltInLibs {
   val builtInLibs: List<BuiltInSourceCode> = listOf(
      StdLib,
      HttpService
   )
   val builtInSources: List<SourceCode> = builtInLibs.map { it.sourceCode }

}



/**
 * These are inbuilt core aspects of the language.
 *
 * These get folded into stdlib - which eventually needs to move
 * out of Taxi code, and be versioned separately as a standalone
 * taxi project.
 *
 * See also, taxi-stdlib-annotations, which we may wish to
 * merge here at some point.
 */
object BuiltInTypes {

   val builtIns = listOf<BuiltIn>(
      FormatAnnotation,
   )
   fun isBuiltIn(name: QualifiedName): Boolean = names.contains(name)

   val names = builtIns.map { it.name }
   val taxi = builtIns.joinToString("\n") { it.asTaxi() }

   object FormatAnnotation : BuiltIn {
      override val name = stdLibName("Format")
      override fun asTaxi(): String = """
         namespace taxi.stdlib {
            [[ Declares a format (and optionally an offset)
            for date formats
            ]]
            annotation Format {
                value : String?
                offset : Int = 0
            }
         }
      """.trimIndent()
   }

}

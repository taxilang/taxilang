package lang.taxi.types

import lang.taxi.functions.stdlib.stdLibName

interface BuiltIn : TaxiStatementGenerator, HasQualifiedName

/**
 * These are inbuilt core aspects of the language.
 *
 * See also, taxi-stdlib-annotations, which we may wish to
 * merge here at some point.
 */
object BuiltIns {

   val builtIns = listOf<BuiltIn>(
      FormatAnnotation
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
                value : String
                offset : Int by default(0)
            }
         }
      """.trimIndent()
   }

}

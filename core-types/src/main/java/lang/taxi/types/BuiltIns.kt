package lang.taxi.types

import lang.taxi.functions.stdlib.stdLibName

/**
 * These are inbuilt core aspects of the language.
 *
 * See also, taxi-stdlib-annotations, which we may wish to
 * merge here at some point.
 */
object BuiltIns {
   val taxi = listOf(
      FormatAnnotation
   ).joinToString("\n") { it.asTaxi() }

   object FormatAnnotation : TaxiStatementGenerator {
      val NAME = stdLibName("Format")
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

package lang.taxi.generators.avro

import lang.taxi.types.Annotation
import lang.taxi.types.toQualifiedName

object AvroAnnotationSchema {
   val taxi = listOf(AvroMessageAnnotation.taxi, AvroFieldAnnotation.taxi).joinToString("\n")
}
object AvroMessageAnnotation {
   val NAME = "lang.taxi.formats.AvroMessage"
   val taxi = """
      namespace lang.taxi.formats {
         annotation ${NAME.toQualifiedName().typeName}
      }
   """.trimIndent()

   fun annotation():Annotation {
      return Annotation(NAME)
   }
}
object AvroFieldAnnotation {
   val NAME = "lang.taxi.formats.AvroField"
   val taxi = """namespace lang.taxi.formats {
      |
      |annotation ${NAME.toQualifiedName().typeName} {
      | ordinal: Int
      |}
      |
      |}
   """.trimMargin()
   fun annotation(ordinal:Int):Annotation {
      return Annotation(NAME, mapOf("ordinal" to ordinal))
   }
}

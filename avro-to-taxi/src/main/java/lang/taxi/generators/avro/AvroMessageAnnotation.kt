package lang.taxi.generators.avro

import lang.taxi.types.Annotation

object AvroMessageAnnotation {
   val NAME = "lang.taxi.formats.AvroMessage"
   val taxi = """
      annotation $NAME
   """.trimIndent()

   fun annotation():Annotation {
      return Annotation(NAME)
   }
}
object AvroFieldAnnotation {
   val NAME = "lang.taxi.formats.AvroField"
   val taxi = """annotation $NAME {
      | ordinal: Int
      |}
   """.trimMargin()
   fun annotation(ordinal:Int):Annotation {
      return Annotation(NAME, mapOf("ordinal" to ordinal))
   }
}

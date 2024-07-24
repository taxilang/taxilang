package lang.taxi.generators.avro

import lang.taxi.types.Annotation
import lang.taxi.types.toQualifiedName

object AvroAnnotationSchema {
   val taxi = listOf(AvroMessageAnnotation.taxi, AvroFieldAnnotation.taxi).joinToString("\n")
}
object AvroMessageAnnotation {
   val NAME = "lang.taxi.formats.AvroMessage"
   val AVRO_MESSAGE_WRAPPER_NAME = "lang.taxi.formats.AvroMessageWrapper"
   val taxi = """
      namespace lang.taxi.formats {
         annotation ${NAME.toQualifiedName().typeName}

         [[
         Indicates that a model is a wrapper around an AvroMessage.
         This should be used when embedding an Avro message within another message, such as to capture
         Kafka message keys or headers.

        The message payload will be treated as an Avro message, even if an Avro schema is not explicitly declared for this type.

         Any fields declared within this message, aside from the Avro message itself, must be populated
         through expressions or meta-value suppliers (e.g., KafkaMessageKey or KafkaMessageHeader).
         ]]
         annotation AvroMessageWrapper
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

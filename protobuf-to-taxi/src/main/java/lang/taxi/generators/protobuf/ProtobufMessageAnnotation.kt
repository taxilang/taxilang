package lang.taxi.generators.protobuf

import lang.taxi.types.Annotation

object ProtobufMessageAnnotation {
   val NAME = "lang.taxi.formats.ProtobufMessage"
   val taxi = """
      annotation $NAME {
         packageName : String
         messageName : String
      }
   """.trimIndent()

   fun annotation(packageName: String?, messageName: String): Annotation {
      return Annotation(
         NAME,
         mapOf(
            "packageName" to (packageName ?: ""),
            "messageName" to messageName
         )
      )
   }
}

object ProtobufFieldAnnotation {
   val NAME = "lang.taxi.formats.ProtobufField"
   val taxi = """
      annotation $NAME {
         tag : Int
         protoType : String
      }
   """.trimIndent()

   fun annotation(tag: Int, protoTypeName: String): Annotation {
      return Annotation(
         NAME,
         mapOf(
            "tag" to tag,
            "protoType" to protoTypeName
         )
      )
   }
}

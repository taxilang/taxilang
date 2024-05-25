package lang.taxi.generators.avro

import java.net.URI

object AvroSchemaFormats {
   // In future, extend support to idl and avpr
   val supportedFormats = listOf("avsc")

   fun isSupportedFormat(uri: URI): Boolean {
      val file = uri.toURL().file
      return supportedFormats.any { file.endsWith(it) }
   }
   fun isSupportedFormat(extension: String): Boolean = supportedFormats.any { extension == it }
}

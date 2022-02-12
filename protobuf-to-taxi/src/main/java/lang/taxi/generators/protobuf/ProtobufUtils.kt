package lang.taxi.generators.protobuf

object ProtobufUtils {
   fun findPackageName(rawProto: String): String {
      val breakTerms = listOf("message", "enum", "import")
      return rawProto.lineSequence()
         .takeWhile { line -> !breakTerms.any { line.trim().startsWith(it) } }
         .filter { line -> line.trim().startsWith("package") }
         .map { packageName ->
            packageName.trim()
               .removeSurrounding(prefix = "package", suffix = ";")
               .trim()
         }
         .firstOrNull() ?: ""
   }
}

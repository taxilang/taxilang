package lang.taxi.xsd

import java.net.URI

object SchemaNames {

   const val XML_NAMESPACE = "http://www.w3.org/2001/XMLSchema"
   private val specialNames = mapOf(
      "www.w3.org" to "org.w3"
   )
   val XML_PACKAGE_NAME = schemaNamespaceToPackageName(XML_NAMESPACE)


   fun schemaNamespaceToPackageName(schemaNamespace: String): String {
      return tryParseAsUri(schemaNamespace)
         ?: TODO("Failed to parse $schemaNamespace to a package name")
   }

   private fun tryParseAsUri(schemaNamespace: String): String? {
      try {
         val uri = URI.create(schemaNamespace)
         if (uri.scheme == "urn") {
            return tryParseAsUrn(schemaNamespace, uri)
         }

         val authority = uri.authority
         if (specialNames.containsKey(authority)) {
            return specialNames[authority]
         }
         return uri.authority.split(".")
            .reversed()
            .filterNot { it == "www" }
            .joinToString(".")
      } catch (e: Exception) {
         return null
      }

   }

   private fun tryParseAsUrn(schemaNamespace: String, uri: URI): String {
      val parts =  uri.schemeSpecificPart
         .split(":", ".")
      // We need to sanitize any parts that are only numeric, and
      // join them with their predecessor element.
      // Otherwise, we end up with package names like 1.2.3, which are illegal
      val sanitizedParts = mutableListOf<String>()
      parts.forEach { part ->
         when (part.toIntOrNull()) {
            null -> {
               // Not a valid number, to safe to add as part
               sanitizedParts.add(part)
            }
            else -> {
               // This part of the uri is a number.  It's not
               // valid on it's own, so append it to the last
               // part
               sanitizedParts[sanitizedParts.lastIndex] = sanitizedParts.last() + part
            }
         }
      }
      return sanitizedParts.joinToString(".")
   }
}

@file:OptIn(ExperimentalStdlibApi::class)

package lang.taxi.generators

import lang.taxi.types.QualifiedName

object NamingUtils {
   private val illegalIdentifierCharacters = "[^a-zA-Z0-9\$_]+".toRegex()
   fun String.replaceFirstCharacterIfDigit(): String {
      return this.replaceFirstChar { char: Char ->
         if (!char.isDigit()) {
            char.toString()
         } else {
            when (char) {
               '0' -> "Zero"
               '1' -> "One"
               '2' -> "Two"
               '3' -> "Three"
               '4' -> "Four"
               '5' -> "Five"
               '6' -> "Six"
               '7' -> "Seven"
               '8' -> "Eight"
               '9' -> "Nine"
               else -> error("Unexpected digit character: $char")
            }
         }
      }
   }

   fun String.replaceIllegalCharacters(): String = replace(illegalIdentifierCharacters, "_")
      .replaceFirstCharacterIfDigit()
   fun String.removeIllegalCharacters(): String = replace(illegalIdentifierCharacters, "")

   fun qualifyTypeNameIfRaw(typeName: String, defaultNamespace: String): QualifiedName {
      val qualifiedName = QualifiedName.from(typeName)
      return if (qualifiedName.namespace.isEmpty()) {
         QualifiedName(defaultNamespace, typeName).replaceIllegalCharacters()
      } else {
         qualifiedName.replaceIllegalCharacters()
      }
   }

   /**
    * returns a word converted from seperator-case to TitleCase
    * eg:  given foo-bar-baz will return FooBarBaz
    */
   fun String.toCapitalizedWords(separatorCharacters: List<String> = listOf("-", "_")): String {
      return separatorCharacters.fold(this) { candidate, seperator -> candidate.toCapitalizedWords(seperator) }
   }

   private fun String.toCapitalizedWords(separatorCharacter: String): String {
      return this.split(separatorCharacter)
         .joinToString(separator = "") { it.capitalize() }
   }

   fun QualifiedName.replaceIllegalCharacters(): QualifiedName {
      return QualifiedName(
         this.namespace.split(".").joinToString(".") { it.replaceIllegalCharacters() },
         this.typeName.replaceIllegalCharacters(),
         this.parameters.map { it.replaceIllegalCharacters() }
      )
   }
}

package lang.taxi.types

import java.io.IOException
import java.io.StreamTokenizer
import java.io.StringReader
import java.lang.Exception

private data class GenericTypeName(val baseType: String, val params: List<GenericTypeName>) {
   fun toQualifiedName(): QualifiedName {
      val parts = this.baseType.split(".")
      val typeName = parts.last()
      val namespace = parts.dropLast(1).joinToString(".")

      return QualifiedName(namespace, typeName, this.params.map { it.toQualifiedName() })
   }
}

// Note - tests for this are currently in Vyne, let's move them
//
object QualifiedNameParser {
   fun parse(s: String): QualifiedName {
      val expandedName = convertArrayShorthand(s)
      val tokenizer = StreamTokenizer(StringReader(expandedName))
      tokenizer.wordChars('_'.toInt(), '_'.toInt())
      tokenizer.wordChars('@'.toInt(),'@'.toInt())
      try {
         val genericName = parse(tokenizer, listOf(StreamTokenizer.TT_EOF)) // Parse until the end
         return genericName.toQualifiedName()
      } catch (e: IOException) {
         throw RuntimeException()
      }

   }


   // Converts Foo[] to lang.taxi.Array<Foo>
   private fun convertArrayShorthand(name: String): String {
      if (name.endsWith("[]")) {
         val arrayType = name.removeSuffix("[]")
         return PrimitiveType.ARRAY.qualifiedName + "<$arrayType>"
      } else {
         return name
      }
   }

   private fun parse(tokenizer: StreamTokenizer, terminalCharacterCodes: List<Int>): GenericTypeName {
      val BOF_MARKER = -4
      val baseNameParts = mutableListOf<String>()
      val params = mutableListOf<GenericTypeName>()
      try {

         while (!terminalCharacterCodes.contains(tokenizer.ttype)) {
            when (tokenizer.ttype) {
               BOF_MARKER -> {
                  tokenizer.nextToken() // Skip it
               }
               '<'.toInt() -> {
                  do {
                     tokenizer.nextToken()  // Skip '<' or ','
                     params.add(parse(tokenizer, terminalCharacterCodes = listOf('>'.toInt(), ','.toInt())))
                  } while (tokenizer.ttype == ','.toInt())
                  tokenizer.nextToken() // Skip past the closing >
               }
               else -> {
                  baseNameParts.add(tokenizer.sval)
                  tokenizer.nextToken()
               }
            }
         }
      } catch (e: Exception) {
         // TODO  : Bring logging in here...
         throw e
      }
      return GenericTypeName(baseNameParts.joinToString(""), params)
   }


}

package lang.taxi.types

import lang.taxi.Type
import java.lang.IllegalArgumentException

enum class PrimitiveType(val declaration: String) : Type {
   BOOLEAN("Boolean"),
   STRING("String"),
   INTEGER("Int"),
   DOUBLE("Double");

   override val qualifiedName: String
      get() = "lang.taxi.$declaration"

   companion object {
      private val typesByName = values().associateBy { it.declaration }
      private val typesByQualifiedName = values().associateBy { it.qualifiedName }
      private val typesByLookup = typesByName + typesByQualifiedName

      fun fromDeclaration(value: String): PrimitiveType {
         return typesByLookup[value] ?: throw IllegalArgumentException("$value is not a valid primative")
      }

      fun isPrimitiveType(qualifiedName: String): Boolean {
         return typesByLookup.containsKey(qualifiedName)
      }
   }
}

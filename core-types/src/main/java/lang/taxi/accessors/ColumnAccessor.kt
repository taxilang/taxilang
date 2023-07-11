package lang.taxi.accessors

import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type
import lang.taxi.utils.quotedIfNotAlready


// TODO : This is duplicating concepts in ColumnMapping, one should die.
data class ColumnAccessor(val index: Any?, override val returnType: Type) :
   PathBasedAccessor, TaxiStatementGenerator {
   override fun toString(): String {
      return "ColumnAccessor(index=$index, returnType=${returnType.qualifiedName})"
   }

   override fun enabledForValueType(value: Any): Boolean {
      // Column acessors are a pain, and we need to replace them with something else.
      // The goal here is that if we've already read the value from CSV to a Map<>,
      // we don't want to use the accessors again.
      // This is similar to the preparsed concept from Cask, but a little more fleixble.
      return value !is Map<*, *>
   }

   override val path: String = index.toString()
   override fun asTaxi(): String {
      return when {
         index is String -> """by column(${index.quotedIfNotAlready()})"""
         index is Int -> """by column(${index.toString()})"""
         else -> error("Unhandled branch")
      }
   }
}

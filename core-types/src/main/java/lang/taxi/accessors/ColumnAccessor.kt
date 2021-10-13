package lang.taxi.accessors

import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type
import lang.taxi.utils.quoted
import lang.taxi.utils.quotedIfNotAlready


// TODO : This is duplicating concepts in ColumnMapping, one should die.
data class ColumnAccessor(val index: Any?, override val defaultValue: Any?, override val returnType: Type) :
   PathBasedAccessor, TaxiStatementGenerator, AccessorWithDefault {
   override fun toString(): String {
      return "ColumnAccessor(index=$index, defaultValue=$defaultValue, returnType=${returnType.qualifiedName})"
   }

   override val path: String = index.toString()
   override fun asTaxi(): String {
      return when {
         index is String -> """by column(${index.quotedIfNotAlready()})"""
         index is Int -> """by column(${index.toString()})"""
         defaultValue is String -> """by default(${defaultValue.quoted()})"""
         else -> """by default($defaultValue)"""
      }
   }
}

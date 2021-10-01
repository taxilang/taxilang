package lang.taxi.accessors

import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type

/**
 * Accessor that instructs a field should be constructed by iterating an array
 */
data class CollectionProjectionExpressionAccessor(val type: Type) : Accessor, TaxiStatementGenerator {
   override val returnType: Type = type
   override fun asTaxi(): String {
      return "[${type.toQualifiedName().parameterizedName}]"
   }
}

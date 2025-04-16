package lang.taxi.accessors

import lang.taxi.types.FieldSetExpression
import lang.taxi.types.TaxiStatementGenerator

// This is for scenarios where a scalar field has been assigned a when block.
// Ideally, we'd use the same approach for both destructured when blocks (ie., when blocks that
// assign multiple fields), and scalar when blocks (a when block that assigns a single field).
data class ConditionalAccessor(val expression: FieldSetExpression) : Accessor, TaxiStatementGenerator {
   override fun asTaxi(): String {
      return "by ${expression.asTaxi()}"
   }
}


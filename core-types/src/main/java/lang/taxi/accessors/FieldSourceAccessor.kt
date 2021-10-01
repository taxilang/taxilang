package lang.taxi.accessors

import lang.taxi.services.FieldName
import lang.taxi.types.QualifiedName
import lang.taxi.types.TaxiStatementGenerator

data class FieldSourceAccessor(
   val sourceAttributeName: FieldName,
   val attributeType: QualifiedName,
   val sourceType: QualifiedName
) : Accessor, TaxiStatementGenerator {
   override fun asTaxi(): String {
      return "by (this.$sourceAttributeName)"
   }
}

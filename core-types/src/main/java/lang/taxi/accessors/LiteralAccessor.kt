package lang.taxi.accessors

import lang.taxi.types.PrimitiveType
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type
import lang.taxi.utils.quoted
import java.math.BigDecimal


data class LiteralAccessor(val value: Any) : Accessor, TaxiStatementGenerator {
   companion object {
      fun returnTypeOf(value:Any): PrimitiveType {
         return when (value) {
            is String -> PrimitiveType.STRING
            is Int -> PrimitiveType.INTEGER
            is Double -> PrimitiveType.DECIMAL
            is BigDecimal -> PrimitiveType.DECIMAL
            is Boolean -> PrimitiveType.BOOLEAN
            else -> {
               PrimitiveType.ANY
            }
         }
      }
   }
   override val returnType: Type
      get() {
         return returnTypeOf(value)
      }

   override fun asTaxi(): String {
      return when (value) {
         is String -> value.quoted()
         else -> value.toString()
      }
   }

}

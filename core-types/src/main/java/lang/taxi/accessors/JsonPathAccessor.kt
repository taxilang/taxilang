package lang.taxi.accessors

import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type


data class JsonPathAccessor(override val path: String, override val returnType: Type) : PathBasedAccessor,
   TaxiStatementGenerator {
   override fun asTaxi(): String = """by jsonPath("$path")"""
}

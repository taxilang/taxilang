package lang.taxi.accessors

import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type

data class XpathAccessor(override val path: String, override val returnType: Type) : PathBasedAccessor,
   TaxiStatementGenerator {
   override fun asTaxi(): String = """by xpath("$path")"""
}

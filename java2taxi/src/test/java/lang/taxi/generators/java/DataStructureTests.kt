package lang.taxi.generators.java

import lang.taxi.Compiler
import lang.taxi.DataType
import lang.taxi.Namespace
import org.junit.Test
import java.math.BigDecimal

class DataStructureTests {

    @Test
    fun given_structThatIsAnnotated_then_schemaIsGeneratedCorrectly() {
        @Namespace("taxi.example")
        @DataType
        data class Invoice(@field:DataType("ClientId") val clientId: String,
                           @field:DataType("InvoiceValue") val invoiceValue: BigDecimal,
                // Just to test self-referential types
                           val previousInvoice: Invoice?)

        val taxiDef = TaxiGenerator().forClasses(Invoice::class.java).generateAsStrings().first()
        val expected = """
namespace taxi.example
type Invoice {
    clientId : ClientId as String
    invoiceValue : InvoiceValue as Decimal
    previousInvoice : Invoice
}
"""
        expectToCompileTheSame(taxiDef, expected)
    }

    private fun expectToCompileTheSame(generated: String, expected: String) {
        val doc = Compiler(generated).compile()
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

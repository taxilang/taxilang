package lang.taxi.generators.java

import com.winterbe.expekt.expect
import lang.taxi.Compiler
import lang.taxi.DataType
import lang.taxi.Namespace
import org.junit.Test
import java.math.BigDecimal

class DataStructureTests {

    @Test
    fun given_structThatIsAnnotated_then_schemaIsGeneratedCorrectly() {


        @Namespace("taxi.example.invoices")
        @DataType
        data class Invoice(@field:DataType("taxi.example.clients.ClientId") val clientId: String,
                           @field:DataType("InvoiceValue") val invoiceValue: BigDecimal,
                // Just to test self-referential types
                           val previousInvoice: Invoice?)

        @Namespace("taxi.example.clients")
        @DataType
        data class Client(@field:DataType("ClientId") val clientId: String)

        val taxiDef = TaxiGenerator().forClasses(Invoice::class.java, Client::class.java).generateAsStrings()
        expect(taxiDef).to.have.size(2)
        val expected = """
namespace taxi.example.invoices
type Invoice {
    clientId : taxi.example.clients.ClientId
    invoiceValue : InvoiceValue as Decimal
    previousInvoice : Invoice
}
---
namespace taxi.example.clients
type Client {
    clientId : ClientId as String
}
""".split("---")
        expectToCompileTheSame(taxiDef, expected)
    }

    private fun expectToCompileTheSame(generated: List<String>, expected: List<String>) {
        val generatedDoc = Compiler.fromStrings(generated).compile()
        val expectedDoc = Compiler.fromStrings(expected).compile()
        expect(generatedDoc).to.equal(expectedDoc)
    }
}

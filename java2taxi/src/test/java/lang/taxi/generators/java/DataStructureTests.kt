package lang.taxi.generators.java

import com.winterbe.expekt.expect
import lang.taxi.annotations.DataType
import lang.taxi.annotations.Namespace
import lang.taxi.types.PrimitiveType
import org.junit.Test
import java.math.BigDecimal

class DataStructureTests {

    @Test
    fun given_structDoesNotMapPrimativeType_then_itIsMappedToTaxiPrimative() {
        @DataType
        @Namespace("taxi.example")
        data class Client(val clientId: String)

        val taxiDef = TaxiGenerator().forClasses(Client::class.java).generateModel()
        expect(taxiDef.objectType("taxi.example.Client").field("clientId").type).to.equal(PrimitiveType.STRING)
    }

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
        TestHelpers.expectToCompileTheSame(taxiDef, expected)
    }


}

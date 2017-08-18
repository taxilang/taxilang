package lang.taxi.generators.java

import com.winterbe.expekt.expect
import lang.taxi.annotations.DataType
import lang.taxi.annotations.Namespace
import lang.taxi.annotations.ParameterType
import lang.taxi.types.PrimitiveType
import org.junit.Test
import java.math.BigDecimal

class DataStructureTests {

    @Test
    fun when_structMixesNamespaces_then_validSchemaIsGenerated() {
        @DataType("polymer.creditInc.Client")
        data class Client(@field:DataType("polymer.creditInc.ClientId") val clientId: String,
                          @field:DataType("polymer.creditInc.ClientName") val clientName: String,
                          @field:DataType("isic.uk.SIC2008") val sicCode: String
        )

        val taxiDef = TaxiGenerator().forClasses(Client::class.java).generateAsStrings()
        val expected = """
namespace polymer.creditInc {
    type Client {
        clientId : ClientId as String
        clientName : ClientName as String
        sicCode : isic.uk.SIC2008
    }
}

namespace isic.uk {
    type alias SIC2008 as String
}"""
        TestHelpers.expectToCompileTheSame(taxiDef, expected)
    }

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


    @Test
    fun given_structureDeclaresParamFromAnotherNamespace_then_itIsParsedToTheNamespaceCorrectly() {
        @DataType("namespaceA.Money")
        data class Money(val amount: BigDecimal)

        @DataType("namespaceB.Invoice")
        // Note - this is a redundant (but still valid) type alias
        data class Invoice(@field:DataType("namespaceA.Money") val money: Money)

        val taxiDef = TaxiGenerator().forClasses(Invoice::class.java, Money::class.java).generateAsStrings()
        val expected = """
namespace namespaceA {
    type Money {
       amount : Decimal
    }
}
namespace namespaceB {
    type Invoice {
       money : namespaceA.Money
    }
}"""
        TestHelpers.expectToCompileTheSame(taxiDef, expected)
    }

    @Test
    fun given_typeContainsReferenceToAnotherType_when_anEmptyDataTypeAnnotationIsUsed_then_theTypeIsMappedCorrectly() {
        @DataType("namespaceA.Money")
        data class Money(val amount: BigDecimal)

        @DataType("namespaceB.Invoice")
        // Same test as above, but the DataType doesn't contain anything -- should
        // detect the data type from the annotated value on Money
        data class Invoice(@field:DataType val money: Money)

        val taxiDef = TaxiGenerator().forClasses(Invoice::class.java, Money::class.java).generateAsStrings()
        val expected = """
namespace namespaceA {
    type Money {
       amount : Decimal
    }
}
namespace namespaceB {
    type Invoice {
       money : namespaceA.Money
    }
}"""
        TestHelpers.expectToCompileTheSame(taxiDef, expected)
    }

    @Test
    fun given_typeIsAParameterType_that_schemaIsGeneratedCorrectly() {
        @DataType("namespaceA.Money")
        @ParameterType
        data class Money(val amount: BigDecimal)

        val taxiDef = TaxiGenerator().forClasses(Money::class.java).generateAsStrings()
        val expected = """
namespace namespaceA {
    parameter type Money {
        amount : Decimal
    }
}"""

        TestHelpers.expectToCompileTheSame(taxiDef, expected)

    }
}

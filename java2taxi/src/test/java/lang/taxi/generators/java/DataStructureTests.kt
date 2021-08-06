package lang.taxi.generators.java

import com.winterbe.expekt.expect
import lang.taxi.annotations.Constraint
import lang.taxi.annotations.DataType
import lang.taxi.annotations.Namespace
import lang.taxi.annotations.ParameterType
import lang.taxi.generators.kotlin.TypeAliasRegister
import lang.taxi.testing.TestHelpers
import lang.taxi.types.PrimitiveType
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DataStructureTests {
   val typeMapper = DefaultTypeMapper(typeAliasRegister = TypeAliasRegister.forPackageNames(listOf("lang.taxi.generators.java")))

   @Test
   fun `When a java class based schema generated`() {
      val taxiDef = TaxiGenerator(typeMapper).forClasses(JavaTypeTest::class.java).generateAsStrings()
      val expected = """
namespace companyX.common {

   model Product {
      productIdentifier : ProductIdentifier
      productName : ProductName
      assetId : assetId
      hash : hash
   }

   type ProductIdentifier inherits Int

   type ProductName inherits String

   type assetId inherits Int

   type hash inherits Int


}"""
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun when_structMixesNamespaces_then_validSchemaIsGenerated() {
      @DataType("demo.Client")
      data class Client(
         @field:DataType("demo.ClientId") val clientId: String,
         val clientName: String,
         @field:DataType("isic.uk.SIC2008") val sicCode: String
      )

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Client::class.java).generateAsStrings()
      val expected = """
namespace demo {
   type ClientId inherits String
    model Client {
        clientId : ClientId
        clientName : String
        sicCode : isic.uk.SIC2008
    }
}

namespace isic.uk {
    type SIC2008 inherits String
}"""
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun given_structDoesNotMapPrimitiveType_then_itIsMappedToTaxiPrimative() {
      @DataType
      @Namespace("taxi.example")
      data class Client(val clientId: String)

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Client::class.java).generateModel()
      expect(taxiDef.objectType("taxi.example.Client").field("clientId").type).to.equal(PrimitiveType.STRING)
   }

   @Test
   fun given_typeHasNamespaceAnnotation_then_namespaceIsCorrectlyDefined() {
      @DataType
      @Namespace("taxi.example")
      data class Client(val clientId: String)

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Client::class.java).generateModel()

      expect(taxiDef.containsType("taxi.example.Client")).to.equal(true)
   }

   @Test
   fun given_structThatIsAnnotated_then_schemaIsGeneratedCorrectly() {


      @Namespace("taxi.example.invoices")
      @DataType
      data class Invoice(
         @field:DataType("taxi.example.clients.ClientId") val clientId: String,
         @field:DataType("InvoiceValue") val invoiceValue: BigDecimal,
         // Just to test self-referential types
         val previousInvoice: Invoice?
      )

      @Namespace("taxi.example.clients")
      @DataType
      data class Client(@field:DataType("ClientId") val clientId: String)

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Invoice::class.java, Client::class.java).generateAsStrings()
      expect(taxiDef).to.have.size(2)
      val expected = """
namespace taxi.example.invoices
type InvoiceValue inherits Decimal
model Invoice {
    clientId : taxi.example.clients.ClientId
    invoiceValue : InvoiceValue
    previousInvoice : Invoice?
}
---
namespace taxi.example.clients
type ClientId inherits String
model Client {
    clientId : ClientId
}
""".split("---")
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun `kotlin types with type aliases generate the correct property names`() {

   }


   @Test
   fun given_structureDeclaresParamFromAnotherNamespace_then_itIsParsedToTheNamespaceCorrectly() {
      @DataType("namespaceA.Money")
      data class Money(val amount: BigDecimal)

      @DataType("namespaceB.Invoice")
      // Note - this is a redundant (but still valid) type alias
      data class Invoice(@field:DataType("namespaceA.Money") val money: Money)

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Invoice::class.java, Money::class.java).generateAsStrings()
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

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Invoice::class.java, Money::class.java).generateAsStrings()
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

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Money::class.java).generateAsStrings()
      val expected = """
namespace namespaceA {
    parameter type Money {
        amount : Decimal
    }
}"""

      TestHelpers.expectToCompileTheSame(taxiDef, expected)

   }

   @Test
   fun given_typeExpressesConstraint_that_schemaIsGeneratedCorrectly() {
      @DataType("vyne.Money")
      data class Money(val amount: BigDecimal, val currency: String)

      @ParameterType
      @DataType("vyne.SomeRequest")
      data class SomeRequest(
         @Constraint("currency = 'USD'")
         val amount: Money
      )

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Money::class.java, SomeRequest::class.java).generateAsStrings()
      val expected = """
            namespace vyne {
                 parameter type SomeRequest {
                    amount : Money(this.currency = "USD")
                 }
                 type Money {
                    amount : Decimal
                    currency : String
                 }
            }
"""
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun given_typeExtendsAnotherType_that_schemaIsGeneratedCorrectly() {
      @DataType("vyne.BaseType")
      open class BaseType(val name: String)

      @DataType("vyne.SubType")
      class SubType(val age: Int, name: String) : BaseType(name)

      val expected = """
            namespace vyne {
                type BaseType {
                    name : String
                }
                type SubType inherits BaseType {
                    age : Int
                }
            }
        """.trimIndent()

      val taxiDef = TaxiGenerator(typeMapper).forClasses(BaseType::class.java, SubType::class.java).generateAsStrings()

      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun given_typeImplementsAnAnnotatedInterface_that_schemaIsGeneratedCorrectly() {
      // NOTE:  The property of "name" actually appears on the generated BaseType, not the generated SubType.
      // This is because ALL taxi properties are inherited (and can't be re-declared), therefore it doesn't
      // make sense for the property to appear on the subtype.
      // NB - we may need to revisit this if extensions on inherited fields becomes a thing, and
      // we have to amend our declarations.

      @DataType("vyne.SubType")
      class SubType(val age: Int, override val name: String) : BaseType

      val expected = """
            namespace vyne {
                type BaseType {
                    name : String
                }
                type SubType inherits BaseType {
                    age : Int
                }
            }
        """.trimIndent()

      val taxiDef = TaxiGenerator(typeMapper).forClasses(SubType::class.java).generateAsStrings()

      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }


   @Test
   fun given_typeIsCollection_that_schemaIsGeneratedCorrectly() {
      @DataType("foo.Person")
      data class Person(val name: String)

      @DataType("foo.AddressBook")
      data class AddressBook(
         val peopleList: List<Person>,
         val peopleSet: Set<Person>
      )

      val taxiDef = TaxiGenerator(typeMapper).forClasses(AddressBook::class.java).generateAsStrings()

      val expected = """
            namespace foo {
                type Person {
                   name : String
                }
                type AddressBook {
                    peopleList : Person[]
                    peopleSet : Person[]
                }
            }
        """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun given_typeContainsEnum_that_schemaIsGeneratedCorrectly() {

      @DataType("foo.Book")
      data class Book(val title: String, val classification: Classification)

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Book::class.java).generateAsStrings()

      // Note: It's actually wrong that Classification ends up in the foo namespace.
      // But, I'll fix that later.
      val expected = """
            namespace foo {
                enum Classification {
                    FICTION,
                    NON_FICTION
                }
                type Book {
                    title:String
                    classification:Classification
                }
            }
        """.trimIndent()

      TestHelpers.expectToCompileTheSame(taxiDef, expected)

   }


   @Test
   @Disabled("Needs investigation - looks like type aliases not being registered correctly - is the plugin running in the build?")
   fun given_typeDeclaresFieldUsingTypeAlias_then_typeAliasIsCorrectlyEmitted() {

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Car::class.java).generateAsStrings()
      val expected = """
            namespace foo {
                type alias PersonName as String
                type Person {
                    name : PersonName
                }

                type alias Adult as Person
                type Car {
                    driver : Adult
                    passenger : Person
                }
            }
        """.trimIndent()

      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun given_typeAliasMapsToList_then_itIsMappedCorrectly() {
      @DataType("foo.Bus")
      data class Bus(val passengers: Passengers)

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Bus::class.java).generateAsStrings()
      val expected = """
            namespace foo {

            model Bus {
                passengers : Person[] // was Passengers, See below
            }

             model Person {
                name : PersonName
            }

            type PersonName inherits String

            // Type aliases for arrays are not currently generating correctly.
            // Not fixing this right now, as I'm not sure if they should, given the move
            // away from aliases.
            // type alias Passengers as Person[]
        }
        """
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun given_fieldNameIsReservedWord_then_itIsEscaped() {
      @DataType("foo.Example")
      data class Example(val type: String)

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Example::class.java).generateAsStrings()
      val expected = """
namespace foo {
    type Example {
        `type` : String
    }
}
        """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)

   }

   @Test
   fun given_dataTypeIndicatesTypeIsClosed_then_itIsMarkedAsClosedInGeneratedTaxi() {
      @DataType("foo.Money", closed = true)
      data class Money(val currency: String, val amount: Int)

      val taxiDef = TaxiGenerator(typeMapper).forClasses(Money::class.java).generateAsStrings()
      val expected = """
namespace foo {
    closed type Money {
        currency : String
        amount : Int
    }
}
        """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }


   @DataType("foo.Classification")
   enum class Classification {
      FICTION, NON_FICTION
   }


}

@DataType("foo.Person")
data class Person(val name: PersonName)

@DataType
@Namespace("foo")
data class Car(val driver: Adult, val passenger: Person)


@DataType("foo.PersonName")
typealias PersonName = String

@DataType("foo.Adult")
typealias Adult = Person

@DataType("foo.Passengers")
typealias Passengers = List<Person>

@DataType("vyne.BaseType")
interface BaseType {
   val name: String;
}

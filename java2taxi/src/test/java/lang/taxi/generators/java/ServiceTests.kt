package lang.taxi.generators.java

import com.winterbe.expekt.expect
import lang.taxi.TypeAliasRegistry
import lang.taxi.annotations.*
import lang.taxi.testing.TestHelpers
import org.junit.Test
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

class ServiceTests {
    @DataType("taxi.example.Money")
    data class Money(
            @field:DataType("taxi.example.Currency") val currency: String,
            @field:DataType("taxi.example.MoneyAmount") val value: BigDecimal)

    @DataType
    @Namespace("taxi.example")
    data class Person(@field:DataType("taxi.example.PersonId") val personId: String)


    @RestController
    @Service("taxi.example.PersonService")
    class MyService {
        @Operation
        fun findPerson(@DataType("taxi.example.PersonId") personId: String): Person {
            TODO("not real")
        }

        @Operation
        @ResponseContract(basedOn = "source",
                constraints = ResponseConstraint("currency = targetCurrency")
        )
        fun convertRates(@Parameter(constraints = Constraint("currency = 'GBP'")) source: Money, @Parameter(name = "targetCurrency") targetCurrency: String): Money {
            TODO("Not a real service")
        }

    }

    // TODO : This test sometimes fails, which is annoying.
    @Test
    fun generatesServiceTemplate() {
        val taxiDef = TaxiGenerator().forClasses(MyService::class.java, Person::class.java).generateAsStrings()
        expect(taxiDef).to.have.size(1)
        val expected = """
namespace taxi.example

type Person {
    personId : PersonId as String
}
type Money {
    currency : Currency as String
    value : MoneyAmount as Decimal
}
service PersonService {
    operation findPerson(PersonId) : Person
    operation convertRates( Money( currency = "GBP" ),
        targetCurrency : String ) : Money( from source, currency = targetCurrency )
}

"""
        TestHelpers.expectToCompileTheSame(taxiDef, expected)
    }

    @Test
    fun given_serviceReturnsPrimitiveWithAnnotation_then_typeAliasIsGenerated() {
        @Service("TestService")
        @Namespace("taxi.example")
        class TestService {
            @DataType("taxi.example.EmailAddress")
            @Operation
            fun findEmail(@DataType("taxi.example.PersonId") input: String): String {
                TODO("Not a real service")
            }
        }

        val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
        expect(taxiDef).to.have.size(1)


        val expected = """
namespace taxi.example
type alias EmailAddress as String
type alias PersonId as String
service TestService {
    operation findEmail(PersonId):EmailAddress
}"""
        TestHelpers.expectToCompileTheSame(taxiDef, expected)
    }


    @Test
    fun givenOperationReturnsTypeAliasedList_then_schemaIsGeneratedCorrectly() {
        @Service("TestService")
        @Namespace("foo")
        class TestService {
            @Operation
            fun listPeopleNames(): List<PersonName> {
                TODO()
            }
        }
        TypeAliasRegistry.register(TypeAliases::class)
        val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
        val expected = """
namespace foo {

   type alias PersonName as String

   service TestService {
      operation listPeopleNames(  ) : PersonName[]
   }
}
        """.trimIndent()
        TestHelpers.expectToCompileTheSame(taxiDef, expected)
    }

    @Test
    fun givenOperationReturnsList_then_schemaIsGeneratedCorrectly() {
        @Service("TestService")
        @Namespace("taxi.example")
        class TestService {
            @Operation
            fun listPeople(): List<ServiceTests.Person> {
                TODO()
            }
        }
        TypeAliasRegistry.register(TypeAliases::class)
        val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
        val expected = """
    namespace taxi.example {

   type Person {
      personId : PersonId
   }

   type alias PersonId as String

   service TestService {
      operation listPeople(  ) : Person[]
   }
}

""".trimIndent()
        TestHelpers.expectToCompileTheSame(taxiDef, expected)
    }

    @Test
    fun given_serviceAcceptsTypeAliasedPrimitive_then_signatureIsGeneratedCorrectly() {
        @Service("TestService")
        @Namespace("taxi.example")
        class TestService {
            @Operation
            fun findEmail(input: PersonName): PersonName {
                TODO("Not a real service")
            }
        }
        TypeAliasRegistry.register(TypeAliases::class)

        val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
        val expected = """
namespace foo {
    type alias PersonName as String
}
namespace taxi.example {
    service TestService {
        operation findEmail(  foo.PersonName ) : foo.PersonName
    }
}
        """.trimIndent()
        TestHelpers.expectToCompileTheSame(taxiDef, expected)
    }

}

@DataType("taxi.PersonList")
typealias PersonList = List<ServiceTests.Person>
package lang.taxi.generators.java

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import lang.taxi.annotations.*
import lang.taxi.demo.FirstName
import lang.taxi.generators.kotlin.TypeAliasRegister
import lang.taxi.testing.TestHelpers
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ServiceTests {
   val typeMapper = DefaultTypeMapper(typeAliasRegister = TypeAliasRegister.forPackageNames(listOf("lang.taxi.generators.java", "lang.taxi.demo")))

   @DataType("taxi.example.Money")
   data class Money(
      @field:DataType("taxi.example.Currency", documentation = "Describes the currency") val currency: String,
      @field:DataType("taxi.example.MoneyAmount") val value: BigDecimal)

   @DataType(documentation = "Models a person.")
   @Namespace("taxi.example")
   data class Person(@field:DataType("taxi.example.PersonId", documentation = "Defines the id of the person") val personId: String)


   @Service("taxi.example.PersonService")
   class MyService {
      @Operation
      fun findPerson(@DataType("taxi.example.PersonId", documentation = "The personId of the person you want to find") personId: String): Person {
         TODO("not real")
      }

      @Operation(documentation = "Returns a converted rate, where the currency has been updated based on the target")
      @ResponseContract(basedOn = "source",
         constraints = [ResponseConstraint("currency = targetCurrency")]
      )
      fun convertRates(@Parameter(constraints = [Constraint("currency = 'GBP'")]) source: Money, @Parameter(name = "targetCurrency") targetCurrency: String): Money {
         TODO("Not a real service")
      }

   }

   @Test
   fun generatesServiceTemplate() {
      val taxiDef = TaxiGenerator().forClasses(MyService::class.java, Person::class.java).generateAsStrings()
      expect(taxiDef).to.have.size(1)
      val expected = """
namespace taxi.example

type PersonId inherits String
type Currency inherits String
type MoneyAmount inherits Decimal
[[ Models a person. ]]
model Person {
   [[ Defines the id of the person ]]
    personId : PersonId
}
model Money {
    currency : Currency
    value : MoneyAmount
}
service PersonService {
    operation findPerson( personId: PersonId) : Person

    [[ Returns a converted rate, where the currency has been updated based on the target ]]
    operation convertRates( source: Money( this.currency == "GBP" ),
        targetCurrency : String ) : Money( from source, this.currency == targetCurrency )
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
type EmailAddress inherits String
type PersonId inherits String
service TestService {
    operation findEmail(input:PersonId):EmailAddress
}"""
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }


   @Test
   @Disabled("Needs investigation - looks like type aliases not being registered correctly - is the plugin running in the build?")
   fun givenOperationReturnsTypeAliasedList_then_schemaIsGeneratedCorrectly() {
      @Service("TestService")
      @Namespace("foo")
      class TestService {
         @Operation
         fun listPeopleNames(): List<PersonName> {
            TODO()
         }
      }
      val taxiDef = TaxiGenerator(typeMapper).forClasses(TestService::class.java).generateAsStrings()
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
      val taxiDef = TaxiGenerator(typeMapper).forClasses(TestService::class.java).generateAsStrings()
      val expected = """
    namespace taxi.example {

   model Person {
      personId : PersonId
   }

   type PersonId inherits String

   service TestService {
      operation listPeople(  ) : Person[]
   }
}

""".trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }


   @Test
   fun given_typeUsesTypeFromAnotherLibrary_then_itIsImported() {
      @Service("TestService")
      @Namespace("taxi.example")
      class TestService {
         @Operation
         fun findEmail(input: PersonName): FirstName {
            TODO("Not a real service")
         }
      }
      // Note - we're not using compilesSameAs(..) for tests involving imports, as they likely don't compile without the imported definition
      val taxiDef = TaxiGenerator(typeMapper).forClasses(TestService::class.java).generateAsStrings()
      taxiDef.joinToString("\n").should.contain("import lang.taxi.demo.FirstName")
   }

   @Test
   fun generatesValidTaxiFromJavaService() {
      // Note - we're not using compilesSameAs(..) for tests involving imports, as they likely don't compile without the imported definition
      val taxiDef = TaxiGenerator(typeMapper).forClasses(JavaServiceTest::class.java).generateAsStrings()
      // Imports should be collated to the top
      taxiDef[0].should.equal("import lang.taxi.FirstName")
      taxiDef[1].trimNewLines().should.equal("""namespace foo {
   model Person {
      name : PersonName
   }

   type PersonName inherits String


}""".trimNewLines())
      taxiDef[2].trimNewLines().should.equal("""namespace lang.taxi.generators.java {



   service JavaService {
      operation findByEmail(  arg0 : FirstName ) : foo.Person
   }
}""".trimNewLines())
   }

   @Test
   @Disabled("Needs investigation - looks like type aliases not being registered correctly - is the plugin running in the build?")
   fun given_serviceAcceptsTypeAliasedPrimitive_then_signatureIsGeneratedCorrectly() {
      @Service("TestService")
      @Namespace("taxi.example")
      class TestService {
         @Operation
         fun findEmail(input: PersonName): PersonName {
            TODO("Not a real service")
         }
      }
      val taxiDef = TaxiGenerator(typeMapper).forClasses(TestService::class.java).generateAsStrings()
      val expected = """
namespace foo {
    type PersonName inherits String
}
namespace taxi.example {
    service TestService {
        operation findEmail( input: foo.PersonName ) : foo.PersonName
    }
}
        """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }


   @Test
   fun given_operationDeclaresScope_then_itIsExported() {
      @Service("TestService")
      @Namespace("taxi.example")
      class TestService {
         @Operation(scope = "read")
         fun findEmail(input: String): String {
            TODO("Not a real service")
         }
      }

      val taxiDef = TaxiGenerator(typeMapper).forClasses(TestService::class.java).generateAsStrings()
      val expected = """
namespace taxi.example {
    service TestService {
        read operation findEmail( input:String ) : String
    }
}
        """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }
}

@DataType("taxi.PersonList")
typealias PersonList = List<ServiceTests.Person>

fun String.trimNewLines(): String {
   return this
      .lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString("")
}

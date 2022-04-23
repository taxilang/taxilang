package lang.taxi.generators.java.samplePackage

import lang.taxi.annotations.*
import lang.taxi.generators.java.*
import lang.taxi.generators.kotlin.TypeAliasRegister
import lang.taxi.testing.TestHelpers
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PackageScannerTests {
   val typeMapper = DefaultTypeMapper(
      typeAliasRegister = TypeAliasRegister.forPackageNames(
         listOf(
            "lang.taxi.generators.java",
            "lang.taxi.demo"
         )
      )
   )

   @DataType("taxi.example.Money")
   data class Money(
      @field:DataType("taxi.example.Currency", documentation = "Describes the currency") val currency: String,
      @field:DataType("taxi.example.MoneyAmount") val value: BigDecimal
   )

   @DataType(documentation = "Models a person.")
   @Namespace("taxi.example")
   data class Person(
      @field:DataType(
         "taxi.example.PersonId",
         documentation = "Defines the id of the person"
      ) val personId: String
   )


   @Service("taxi.example.PersonService")
   class MyService {
      @Operation
      fun findPerson(
         @DataType(
            "taxi.example.PersonId",
            documentation = "The personId of the person you want to find"
         ) personId: String
      ): Person {
         TODO("not real")
      }

      @Operation(documentation = "Returns a converted rate, where the currency has been updated based on the target")
      @ResponseContract(
         basedOn = "source",
         constraints = [ResponseConstraint("currency = targetCurrency")]
      )
      fun convertRates(
         @Parameter(constraints = [Constraint("currency = 'GBP'")]) source: Money,
         @Parameter(name = "targetCurrency") targetCurrency: String
      ): Money {
         TODO("Not a real service")
      }

   }

   @Test
   fun generatesFromPackageScan() {
      val taxiDef = TaxiGenerator().forPackage(MyService::class.java)
         .generateAsStrings()
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

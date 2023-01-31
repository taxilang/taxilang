package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import lang.taxi.types.ObjectType

class SpreadOperatorSpec : DescribeSpec({
   describe("Spread operator") {
      it("generates the correct fields without exclusions") {
         val (_, query) = """
            model Person {
               firstName : FirstName inherits String
               lastName : LastName inherits String
               address : Address
            }
            model Address {
               house : HouseNumber inherits Int
               streetName : String
            }
         """.compiledWithQuery(
            """
            find { Person } as {
               name : FirstName
               address : Address as {
                 isOddNumbered : Boolean
                 house : HouseNumber
                 ...
               }
               ...
            }
         """.trimIndent()
         )
         val projectedAddress = query.projectedObjectType
            .field("address").type.asA<ObjectType>()
         projectedAddress.hasField("isOddNumbered").shouldBeTrue()
         projectedAddress.hasField("house").shouldBeTrue()
         projectedAddress.field("house").type.qualifiedName.shouldBe("HouseNumber")
         projectedAddress.hasField("streetName").shouldBeTrue()
         projectedAddress.hasField("house").shouldBeTrue()
      }

      it("generates the correct fields with excluded fields") {
         val (_, query) = """
            model Address {
               house : HouseNumber inherits Int
               streetName : String
               secretCode : SecretCode inherits String
            }
            model Person {
               firstName : FirstName inherits String
               lastName : LastName inherits String
               address : Address
               secretAge : Age inherits Int
            }
         """.compiledWithQuery(
            """
            find { Person } as {
               name : FirstName
               address : Address as {
                 isOddNumbered : Boolean
                 house : HouseNumber
                 ... except {secretCode}
               }
               ... except {secretAge}
            }
         """.trimIndent()
         )
         val projectedAddress = query.projectedObjectType
            .field("address").type.asA<ObjectType>()
         projectedAddress.hasField("isOddNumbered").shouldBeTrue()
         projectedAddress.hasField("house").shouldBeTrue()
         projectedAddress.field("house").type.qualifiedName.shouldBe("HouseNumber")
         projectedAddress.hasField("streetName").shouldBeTrue()
         projectedAddress.hasField("house").shouldBeTrue()
         projectedAddress.hasField("secretCode").shouldBeFalse()
         projectedAddress.hasField("secretAge").shouldBeFalse()
      }

      it("is possible to exclude multiple fields") {
         val (_, query) = """
            type LastPurchasePrice inherits Decimal
            type Valuation inherits Decimal

            model Address {
               house : HouseNumber inherits Int
               streetName : String
               secretCode : SecretCode inherits String
               color : HouseColor inherits String
            }
            model Person {
               firstName : FirstName inherits String
               lastName : LastName inherits String
               address : Address
               secretAge : Age inherits Int
            }
         """.compiledWithQuery(
            """
            find { Person } as {
               name : FirstName
               address : Address as {
                 value : Valuation
                 lastPurchase : LastPurchasePrice
                 ... except {streetName, secretCode}
               }
            }
         """.trimIndent()
         )
         val projectedAddress = query.projectedObjectType
            .field("address").type.asA<ObjectType>()
         projectedAddress.hasField("value").shouldBeTrue()
         projectedAddress.hasField("lastPurchase").shouldBeTrue()
         projectedAddress.hasField("color").shouldBeTrue()

         projectedAddress.field("house").type.qualifiedName.shouldBe("HouseNumber")

         projectedAddress.hasField("streetName").shouldBeFalse()
         projectedAddress.hasField("secretCode").shouldBeFalse()
         projectedAddress.hasField("secretAge").shouldBeFalse()
      }

      it("is only allowed as the last item in the list") {
         val exception = """
            model Person {
               firstName : FirstName inherits String
               lastName : LastName inherits String
               address : Address
            }
            model Address {
               house : HouseNumber inherits Int
               streetName : String
            }
         """.compiledWithQueryProducingCompilationException(
            """
            find { Person } as {
               name : FirstName
               address : Address as {
                 isOddNumbered: Boolean
                 ...
                 house : HouseNumber
               }
            }
         """.trimIndent()
         )
         exception.message.should.contain("UnknownSource(6,5) missing '}' at 'house'")
      }

      it("is not allowed for models") {
         val errors = """
            model Person {
               firstName : FirstName inherits String
               lastName : LastName inherits String
               address : Address inherits String
               ...
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.contain("Spread operator is not allowed for model definitions.")
      }

      it("allows except as a field name") {
         val errors = """
            model Person {
               except : FirstName inherits String
               lastName : LastName inherits String
            }
         """.validated()
         errors.should.have.size(0)
      }
   }
})


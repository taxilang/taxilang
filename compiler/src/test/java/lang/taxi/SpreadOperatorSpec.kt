package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import lang.taxi.types.ObjectType

class SpreadOperatorSpec : DescribeSpec({
   describe("Spread operator") {
      it("generates the correct fields") {
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
               address : Address
               ...
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.contain("Address was not resolved as either a type or a function")
      }
   }
})


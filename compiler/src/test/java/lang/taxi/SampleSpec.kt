package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import lang.taxi.types.ObjectType

class SampleSpec : DescribeSpec({
   describe("") {
      it("") {
         val (taxi,query) = """
            model Person {
               firstName : FirstName inherits String
               lastNAme : LastName inherits String
               address : Address
            }
            model Address {
               house : HouseNumber inherits Int
               streetName : String
            }
         """.compiledWithQuery("""
            find { Person } as {
               name : FirstName
               address : Address as {
                 isOddNumbered: Boolean
                 house,
                 ...
               }
            }
         """.trimIndent())
         val projectedAddress = query.projectedObjectType
            .field("address").asA<ObjectType>()
         projectedAddress.hasField("isOddNumbered").shouldBeTrue()
         projectedAddress.hasField("house").shouldBeTrue()
         projectedAddress.field("house").type.qualifiedName.shouldBe("HouseNumber")
         projectedAddress.hasField("streetName").shouldBeTrue()
      }
   }
})

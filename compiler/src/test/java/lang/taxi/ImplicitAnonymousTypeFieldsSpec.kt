package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import lang.taxi.types.ObjectType

class ImplicitAnonymousTypeFieldsSpec : DescribeSpec({
   describe("Implicit anonymous type fields") {
      it("are generated correctly") {
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
                 isOddNumbered: Boolean
                 house
                 streetName
               }
            }
         """.trimIndent()
         )
         val projectedAddress = query.projectedObjectType!!
            .field("address").type.asA<ObjectType>()
         projectedAddress.hasField("isOddNumbered").shouldBeTrue()
         projectedAddress.hasField("house").shouldBeTrue()
         projectedAddress.field("house").type.qualifiedName.shouldBe("HouseNumber")
         projectedAddress.hasField("streetName").shouldBeTrue()
         projectedAddress.hasField("house").shouldBeTrue()
      }
   }

   it("is only allowed as the last item in the list") {
      val schema = """
         model Person {
               firstName : FirstName inherits String
               lastName : LastName inherits String
               address : Address
            }
            model Address {
               streetName : String
            }
      """.trimIndent()
      val taxi = Compiler(schema).compile()

      val src = """
            find { Person } as {
               name : FirstName
               address : Address as {
                 isOddNumbered: Boolean
                 house
               }
            }
      """.trimIndent()
      val query = Compiler(source = src, importSources = listOf(taxi)).queriesWithErrorMessages().first
      query.shouldContainMessage("Field house does not have a type and cannot be found on the type being projected (Address).")
   }
})


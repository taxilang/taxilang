package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import lang.taxi.types.Field
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
         val projectedAddress = query.projectedObjectType!!
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
         val projectedAddress = query.projectedObjectType!!
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
         val projectedAddress = query.projectedObjectType!!
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

      it("is possible to use spread operator in an inline projection") {
         val (schema, query) = """model Person {
           id : PersonId inherits Int
           name : PersonName inherits String
          }
          model Movie {
            cast : Person[]
         }
         """.compiledWithQuery(
            """
find { Movie[] } as {
    cast : Person[]
    aListers : filterAll(this.cast, (Person) -> containsString(PersonName, 'a')) as  { // Inferred return type is Person
       ...except { id }
    }
}[]
         """.trimIndent()
         )

         val projectedAListers = query.projectedObjectType!!
            .field("aListers").type.asA<ObjectType>()
         projectedAListers.hasField("name").shouldBeTrue()
         projectedAListers.hasField("id").shouldBeFalse()
      }

      it("is possible to use a spread operator in an inline projection, and add fields from another object") {
         val (schema, query) = """
         type CreditScore inherits Int
         type BloodType inherits String
         model Person {
           id : PersonId inherits Int
           name : PersonName inherits String
          }
          model Movie {
            cast : Person[]
         }
         """.compiledWithQuery(
            """
find { Movie[] } as {
    cast : Person[]
    aListers : filterAll(this.cast, (Person) -> containsString(PersonName, 'a')) as  { // Inferred return type is Person
       bloodType : BloodType
       creditScore : CreditScore
       ...except { id }
    }
}[]
         """.trimIndent()
         )
         val projectedAListers = query.projectedObjectType!!
            .field("aListers").type.asA<ObjectType>()
         projectedAListers.hasField("name").shouldBeTrue()
         projectedAListers.hasField("id").shouldBeFalse()
         projectedAListers.hasField("bloodType").shouldBeTrue()
         projectedAListers.hasField("creditScore").shouldBeTrue()
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

      it("can mix nested anonymous types in a spread object") {
         val (schema, query) = """
         model Person {
           id : PersonId inherits Int
           name : PersonName inherits String
          }
          type TwitterHandle inherits String
          model Movie {
            star : Person
         }""".compiledWithQuery(
            """
         find { Movie } as {
            cast : Person as {
               socials: { // <--- Nested anonymous object
                 twitter : TwitterHandle
               }
               ...
            }
         }
         """.trimIndent()
         )
         val castField = query.projectedObjectType!!
            .field("cast")

         castField.type.anonymous.shouldBeTrue()
         val socialsField = castField.type.asA<ObjectType>().field("socials")
         socialsField.type.anonymous.shouldBeTrue()
         val socialsType = socialsField.type.asA<ObjectType>()
         socialsType.anonymous.shouldBeTrue()
         socialsType.field("twitter").type.qualifiedName.shouldBe("TwitterHandle")
      }
   }
})


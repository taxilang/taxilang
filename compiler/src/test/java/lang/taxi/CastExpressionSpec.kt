package lang.taxi

import io.kotest.assertions.fail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.CastExpression
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.ObjectExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.WhenExpression

class CastExpressionSpec : DescribeSpec({
   describe("casting") {
      it("should support casting in function ") {
         val field = """
      enum CurrencyCode {
            USD,
            EUR
      }
      model Trade {
         symbol: String
         currency: CurrencyCode = (CurrencyCode) right(this.symbol,4)
      }
         """.compiled()
            .model("Trade")
            .field("currency")
         val castExpression = field.accessor.shouldBeInstanceOf<CastExpression>()
         castExpression.type.qualifiedName.shouldBe("CurrencyCode")
         castExpression.expression.shouldBeInstanceOf<FunctionExpression>()
      }
      it("should support casting in a projection") {
         val (taxi, query) = """
         model Person {
            personId: PersonId inherits String
         }
         """.compiledWithQuery(
            """
            find { Person } as {
               personId : Long = (Long) PersonId
            }
         """.trimIndent()
         )
         val personIdField = query.projectedObjectType!!
            .field("personId")
         personIdField.type.qualifiedName.shouldBe("lang.taxi.Long")
         val cast = personIdField.accessor.shouldBeInstanceOf<CastExpression>()
         cast.expression.shouldBeInstanceOf<TypeExpression>()
            .type.qualifiedName.shouldBe("PersonId")
         cast.type.qualifiedName.shouldBe("lang.taxi.Long")
      }

      it("should raise error if cast type is not assignable ") {
         """
      type CurrencyCode inherits String
      model Trade {
         symbol: String
         currency: Int = (CurrencyCode) right(this.symbol,4)
      }
         """.validated()
            .shouldContainMessage("Type mismatch. Type of CurrencyCode is not assignable to type lang.taxi.Int")
      }


      it("should allow a cast to infer type expression") {
         val parameter = """
            service PeopleService {
            operation findPeople(
               @HttpHeader(name = "If-Modified-Since") ifModifiedSince : Instant = addDays((Instant) now(), -1)
            ):String
         }
         """.compiled()
            .service("PeopleService")
            .operation("findPeople")
            .parameters.single()
         parameter.defaultValue.shouldNotBeNull()
      }
      it("is possible to use a cast to construct an error") {
         val policy = """
            model NotAuthorizedError {
               message : ErrorMessage inherits String
            }
            model Film
            model UserInfo {
               groups: String[]
            }

            policy PolicyWithError against Film (userInfo : UserInfo) -> {
               read {
                  when {
                     userInfo.groups.contains( 'ADMIN' ) -> Film
                     else -> throw( (NotAuthorizedError) { message: 'Not Authorized' })
                  }
               }
            }
         """.compiled()
            .policy("PolicyWithError")
         val whenClause = policy.rules.single().expression
            .shouldBeInstanceOf<WhenExpression>()
         val elseClause = whenClause.cases.single { it.matchExpression is ElseMatchExpression }
         val castExpression = elseClause.assignments.single().shouldBeInstanceOf<InlineAssignmentExpression>()
            .assignment.shouldBeInstanceOf<FunctionExpression>()
            .inputs.single().shouldBeInstanceOf<CastExpression>()
         val errorObjectExpression = castExpression.expression.shouldBeInstanceOf<ObjectExpression>()
         val errorMessageLiteralExpression =  errorObjectExpression.expressionMap["message"] as LiteralExpression
          val errorValue = errorMessageLiteralExpression.asTypedValue()
         errorValue.value.shouldBe("Not Authorized")
         castExpression.type.qualifiedName.shouldBe("NotAuthorizedError")
      }

      it("should allow casting between compatible types inline") {
         """
         type ReleaseYear inherits Int
         type PublicDomainYear inherits Int
         model Movie {
            released : ReleaseYear
            isCopyright : Boolean
            publicDomain : PublicDomainYear = when {
               this.isCopyright -> this.released + 30
               else -> (PublicDomainYear) this.released // This is the test -- casting should be possible with compatible base types
            }
         }
      """.compiled()
      }
   }
})

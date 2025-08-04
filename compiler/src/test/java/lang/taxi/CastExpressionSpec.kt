package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.CastExpression
import lang.taxi.expressions.ExtensionFunctionExpression
import lang.taxi.expressions.FieldReferenceExpression
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.ObjectLiteralExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.MemberTypeReferenceExpression
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
         val errorObjectExpression = castExpression.expression.shouldBeInstanceOf<ObjectLiteralExpression>()
         val errorMessageLiteralExpression =  errorObjectExpression.expressionMap["message"] as LiteralExpression
          val errorValue = errorMessageLiteralExpression.asTypedValue()
         errorValue.value.shouldBe("Not Authorized")
         castExpression.type.qualifiedName.shouldBe("NotAuthorizedError")
      }

      it("should allow casting using attribute selector") {
        val expression =  """
            type EntityId inherits String
            type FilmId inherits String

            model Film

            model Entity {
              id : EntityId = (EntityId) Film::FilmId
           }
         """.compiled()
            .model("Entity")
            .field("id")
            .accessor.shouldBeInstanceOf<CastExpression>()

         val memberReference = expression.expression.shouldBeInstanceOf<MemberTypeReferenceExpression>()
         memberReference.memberSource.parameterizedName.shouldBe("Film")
         memberReference.targetType.qualifiedName.shouldBe("FilmId")

         expression.returnType.qualifiedName.shouldBe("EntityId")
      }

      it("should allow casting using field selector") {
         val expression =  """
            type EntityId inherits String
            type FilmId inherits String

            model Film {
               filmId : FilmId
            }

            model Entity {
              film: Film
              id : EntityId = (EntityId)  this.film.filmId
           }
         """.compiled()
            .model("Entity")
            .field("id")
            .accessor.shouldBeInstanceOf<CastExpression>()

         val fieldReferenceExpression = expression.expression.shouldBeInstanceOf<FieldReferenceExpression>()
         fieldReferenceExpression.path.shouldBe("film.filmId")
         fieldReferenceExpression.returnType.qualifiedName.shouldBe("FilmId")

         expression.returnType.qualifiedName.shouldBe("EntityId")
      }

      it("should allow casting using extension function on field selector") {
         val expression =  """
            type EntityId inherits String
            type FilmId inherits String

            model Film {
               filmId : FilmId
            }

            model Entity {
              film: Film
              id : EntityId = (EntityId)  this.film.filmId.upperCase()
           }
         """.compiled()
            .model("Entity")
            .field("id")
            .accessor.shouldBeInstanceOf<CastExpression>()

         val extensionFunctionExpression = expression.expression.shouldBeInstanceOf<ExtensionFunctionExpression>()
         extensionFunctionExpression.receiverValue.shouldBeInstanceOf<FieldReferenceExpression>()
            .path.shouldBe("film.filmId")
         extensionFunctionExpression.functionExpression.function.qualifiedName.shouldBe("taxi.stdlib.upperCase")

         expression.returnType.qualifiedName.shouldBe("EntityId")
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

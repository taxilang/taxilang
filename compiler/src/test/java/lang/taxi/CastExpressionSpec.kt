package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.CastExpression
import lang.taxi.expressions.FunctionExpression

class CastExpressionSpec :DescribeSpec({
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

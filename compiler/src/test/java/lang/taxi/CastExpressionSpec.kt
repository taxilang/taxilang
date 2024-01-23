package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import lang.taxi.expressions.CastExpression
import lang.taxi.expressions.FunctionExpression
import lang.taxi.functions.FunctionAccessor

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
         val field = """
      type CurrencyCode inherits String
      model Trade {
         symbol: String
         currency: Int = (CurrencyCode) right(this.symbol,4)
      }
         """.validated()
            .shouldContainMessage("Type mismatch. Type of CurrencyCode is not assignable to type lang.taxi.Int")
      }
   }
})

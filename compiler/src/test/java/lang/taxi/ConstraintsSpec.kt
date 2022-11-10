package lang.taxi

import com.winterbe.expekt.expect
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import org.junit.jupiter.api.Test

class ConstraintsSpec : DescribeSpec({
   describe("Constraints") {
      it("can declare constraints on types") {
         val source = """
type Money {
   amount : Amount as Decimal
   currency : Currency as String
}
type SomeServiceRequest {
   amount : Money(Currency == 'GBP')
   clientId : ClientId as String
}
"""
         val doc = Compiler(source).compile()
         val request = doc.objectType("SomeServiceRequest")

         val amountField = request.field("amount")
         expect(amountField.constraints).to.have.size(1)
         val constraint = amountField.constraints.single() as ExpressionConstraint
         val expression = constraint.expression as OperatorExpression

         expression.lhs.asA<TypeExpression>().type.qualifiedName.shouldBe("Currency")
         expression.rhs.asA<LiteralExpression>().value.shouldBe("GBP")
      }
   }
})

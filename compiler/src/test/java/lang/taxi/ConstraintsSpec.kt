package lang.taxi

import com.winterbe.expekt.expect
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.query.convertToPropertyConstraint
import lang.taxi.services.operations.constraints.ExpressionConstraint

class ConstraintsSpec : DescribeSpec({
   describe("Constraints") {
      it("can declare constraints on types") {
         val source = """
type Money {
   amount : Amount inherits Decimal
   currency : Currency inherits String
}
type SomeServiceRequest {
   amount : Money(Currency == 'GBP')
   clientId : ClientId inherits String
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

   it("can downgrade expression constraints") {
      val (schema,query) = """
         model Person {
            name : Name inherits String
         }""".compiledWithQuery("find { Person[]( Name == 'Jimmy' ) }")
      val constraint = query.typesToFind.single().constraints.single() as ExpressionConstraint
      val converted = constraint.convertToPropertyConstraint()
   }
})

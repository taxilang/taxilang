package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.OperatorExpression
import lang.taxi.types.Field
import lang.taxi.types.FormulaOperator

class OperatorsSpec : DescribeSpec({
   describe("basic operator symbols") {
      it("can compile basic operators") {
         val model = """
            model Foo {
               usingAdd : 1 + 2
               usingMult : 1 * 2
               usingDiv : 1 / 2
               usingSub : 1 - 2
               usingMod : 1 % 2
            }
         """.compiled()
            .model("Foo")
         model.field("usingAdd").expressionOperator().shouldBe(FormulaOperator.Add)
         model.field("usingMult").expressionOperator().shouldBe(FormulaOperator.Multiply)
         model.field("usingDiv").expressionOperator().shouldBe(FormulaOperator.Divide)
         model.field("usingSub").expressionOperator().shouldBe(FormulaOperator.Subtract)
         model.field("usingMod").expressionOperator().shouldBe(FormulaOperator.Modulo)
      }
   }
})

private fun Field.expressionOperator(): FormulaOperator {
   return this.accessor
      .shouldBeInstanceOf<OperatorExpression>()
      .operator
}

package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.query.convertToConstraint
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
            lastName: LastName inherits String
            age: Age inherits Int
         }""".compiledWithQuery("find { Person[]( Name == 'Jimmy' && LastName == 'Page' || Age == 99) }")
      val constraint = query.typesToFind.single().constraints.single() as ExpressionConstraint
      val converted = constraint.convertToConstraint()
      converted.size.should.equal(5)
   }

   // ORB-766
   it("does not parse a function call as a constraint") {
      val (schema,query) = """
         closed model Person {
           name : PersonName inherits String
          }
          model Movie {
            cast : Person[]
         }
      """.compiledWithQuery("""
         find { Movie[] } as {
         // This isn't a constraint, it's a function call
         // but it could be parsed either way
             starring : first(Person[]) as {
               starsName : PersonName
            }
         }[]
      """.trimIndent())
      query.projectedObjectType!!
         .field("starring")
         .constraints.shouldBeEmpty()
   }
})

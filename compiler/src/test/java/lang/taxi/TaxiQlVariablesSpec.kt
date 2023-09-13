package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.types.PrimitiveType
import java.time.LocalDate

class TaxiQlVariablesSpec : DescribeSpec({
   describe("defining variables in a taxiql query") {
      it("should be possible to define a variable in a given block") {
         val (schema,query) = """model Person {
            firstName : FirstName inherits String
            }
         """.compiledWithQuery("given { name : FirstName = 'Jimmy' } find { Person }")
         val parameter = query.facts.single()
         parameter.value.hasValue.shouldBeTrue()
         parameter.value.typedValue.value.shouldBe("Jimmy")
      }
      it("is not permitted to define a variable in a given block without a value") {
         val compilationException = """model Person {
            firstName : FirstName inherits String
            }
         """.compiledWithQueryProducingCompilationException("given { name : FirstName  } find { Person }")
         compilationException.errors[0].detailMessage.shouldContain("mismatched input '}'")
      }

      it("can query a date variable using a comparison") {
         val (schema, query) = """
            model Person {
               dateOfBirth : DateOfBirth inherits Date
            }
         """.compiledWithQuery("""find { Person[]( DateOfBirth >= '2020-10-15' ) }""")
         val expression = query.typesToFind.single().constraints.single().asA<ExpressionConstraint>()
            .expression as OperatorExpression
         val literal = expression.rhs.asA<LiteralExpression>().literal
         literal.value.shouldBe(LocalDate.parse("2020-10-15"))
         literal.returnType.qualifiedName.shouldBe(PrimitiveType.LOCAL_DATE.qualifiedName)
      }
   }
})

package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.query.FactValue
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.ObjectType
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

      it("can use expressions in a given block fact") {
            val (schema,query) = "".compiledWithQuery("""
               given { d : DateTime = parseDate('2023-06-11T09:00:30') }
               find { result : DateTime  }
            """.trimIndent())
         val factExpression = query.facts.single().value.shouldBeInstanceOf<FactValue.Expression>()
         factExpression.type.shouldBe(PrimitiveType.DATE_TIME)
         // Adding this assertion, as parseDate() is defined as:
         // fun <T> parseDate(String):T
         // and want to ensure that return type inference is working correctly
         factExpression.expression.asA<FunctionExpression>().function.returnType.shouldBe(PrimitiveType.DATE_TIME)
      }

      it("can use expressions that rely on other variables in a given block") {
         val (schema,query) = "".compiledWithQuery("""
               given {
                     message : String = "hello",
                     upperMessage : String = upperCase(message)
                }
               find { String }
            """.trimIndent())
         query.facts.shouldHaveSize(2)
         val expression = query.facts[1].value.shouldBeInstanceOf<FactValue.Expression>()
         val functionExpression = expression.expression.shouldBeInstanceOf<FunctionExpression>()
         functionExpression.inputs.shouldHaveSize(1)
         val selector = functionExpression.inputs.single().shouldBeInstanceOf<ArgumentSelector>()
      }

      it("can use expressions that rely on other expressions in a given block") {
         val (schema,query) = "".compiledWithQuery("""
               given {
                     message : String = "hello",
                     upperMessage : String = upperCase(message),
                     lowerMessage : String = lowerCase(upperMessage)
                }
               find { String }
            """.trimIndent())
         query.facts.shouldHaveSize(3)
         val expression = query.facts[2].value.shouldBeInstanceOf<FactValue.Expression>()
         val functionExpression = expression.expression.shouldBeInstanceOf<FunctionExpression>()
         functionExpression.inputs.shouldHaveSize(1)
         val selector = functionExpression.inputs.single().shouldBeInstanceOf<ArgumentSelector>()
      }

      it("can use facts from given as inputs in expressions") {
         val(schema,query) = "".compiledWithQuery("""
            given { message : String = "hello" }
            find { result : String = upperCase(message) }
         """.trimIndent())
         val accessor = query.discoveryType!!.type.asA<ObjectType>()
            .field("result")
            .accessor!!
         val functionExpression = accessor.shouldBeInstanceOf<FunctionExpression>()
         functionExpression.inputs[0].shouldBeInstanceOf<ArgumentSelector>()
      }
   }
})

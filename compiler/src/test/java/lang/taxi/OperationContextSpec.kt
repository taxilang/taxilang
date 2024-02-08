package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.linter.LinterRuleConfiguration
import lang.taxi.query.TaxiQlQuery
import lang.taxi.services.Parameter
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.services.operations.constraints.PropertyFieldNameIdentifier
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.services.operations.constraints.PropertyTypeIdentifier
import lang.taxi.services.operations.constraints.RelativeValueExpression
import lang.taxi.toggles.FeatureToggle
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.AttributePath
import lang.taxi.types.FormulaOperator
import lang.taxi.types.QualifiedName
import org.antlr.v4.runtime.CharStreams
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class OperationContextSpec : DescribeSpec({
   describe("declaring context to operations") {
      val taxi = """
         type TransactionEventDateTime inherits Instant
         model Trade {
            tradeId : TradeId inherits String
            tradeDate : TradeDate inherits Instant
            @Format( "yyyy-MM-dd HH:mm:ss.SSSSSSS")
            orderDateTime : TransactionEventDateTime
         }
         type EmployeeCode inherits String
      """.trimIndent()

      describe("compiling declaration of context") {
         it("compiles return type with property type equal to param") {
            val operation = """
         $taxi
         service TradeService {
            operation getTrade(id:TradeId):Trade(this.tradeId == id)
         }
         """.compiled().service("TradeService").operation("getTrade")
            operation.contract!!.returnTypeConstraints.should.have.size(1)
            val constraint = operation.contract!!.returnTypeConstraints.first() as ExpressionConstraint
            constraint.operatorExpression.operator.shouldBe(FormulaOperator.Equal)
            constraint.operatorExpression.lhs.asA<ArgumentSelector>().should {
               it.path.shouldBe("tradeId")
               it.returnType.qualifiedName.shouldBe("TradeId")
            }
            constraint.operatorExpression.rhs.asA<ArgumentSelector>().should {
               it.path.shouldBe("id")
               it.scope.should { scope ->
                  scope.shouldBeInstanceOf<Parameter>()
                  scope.name.shouldBe("id")
                  scope.type.qualifiedName.shouldBe("TradeId")
               }
            }
         }

         // ths is commented, because I can't decide on a syntax that I like.
         // Once we decide on a syntax, then most of the compiler infra is already in place.
         xit("compiles return type with property type equal to param identified by type") {
            val operation = """
         $taxi
         service TradeService {
            operation getTrade(id:TradeId):Trade(
//            TODO : pick a syntax...
//               this:TradeId = :TradeId
//               TradeId = TradeId   // :(
//               this:TradeId = input:TradeId // most explicit, but kinda ugly
            )
         }
         """.compiled().service("TradeService").operation("getTrade")
            operation.contract!!.returnTypeConstraints.should.have.size(1)

         }

         it("should not fail if provided type is not an attribute of the return type") {
            val errors = """
         $taxi
         service TradeService {
            operation getTrade(id:TradeId):Trade(EmployeeCode == id)
         }
         """.validated()
            errors.should.be.empty
         }

         it("should fail if referenced param is not an input") {
            val errors = """
         $taxi
         service TradeService {
            operation getTrade(id:TradeId):Trade(TradeId == foo)
         }
         """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.equal("foo is not defined")
         }

         it("compiles return type with property field equal to param") {
            val operation = """
         $taxi
         service TradeService {
            operation getTrade(tradeId:TradeId):Trade(this.tradeId == tradeId)
         }
         """.compiled().service("TradeService").operation("getTrade")
            operation.contract!!.returnTypeConstraints.should.have.size(1)
            val constraint = operation.contract!!.returnTypeConstraints.first() as ExpressionConstraint
            constraint.operatorExpression.operator.shouldBe(FormulaOperator.Equal)
            constraint.operatorExpression.lhs.asA<ArgumentSelector>().should {
               it.scope.type.qualifiedName.shouldBe("Trade")
               it.path.shouldBe("tradeId")
               it.returnType.qualifiedName.shouldBe("TradeId")
            }
            constraint.operatorExpression.rhs.asA<ArgumentSelector>().should {
               it.scope.should { scope ->
                  scope.shouldBeInstanceOf<Parameter>()
                  scope.name.shouldBe("tradeId")
               }
               it.returnType.qualifiedName.shouldBe("TradeId")
            }
         }

         it("should fail if referenced property is not present on target type") {
            val errors = """
         $taxi
         service TradeService {
            operation getTrade(id:TradeId):Trade(this.something == id)
         }
         """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.equal("Cannot resolve reference something against type Trade")
         }


         it("compiles return type with property type greater than param") {
            val operation = """
         $taxi
         service TradeService {
            operation getTradesAfter(startDate:Instant):Trade[](TradeDate >= startDate)
         }
         """.compiled().service("TradeService").operation("getTradesAfter")
            operation.contract!!.returnTypeConstraints.should.have.size(1)
            val constraint = operation.contract!!.returnTypeConstraints.first() as ExpressionConstraint
            constraint.operatorExpression.operator.shouldBe(FormulaOperator.GreaterThanOrEqual)
         }

         it("compiles return type with multiple property params") {
            val schema = """
         $taxi
         service TradeService {
            operation getTradesAfter(startDate:Instant, endDate:Instant):Trade[](
               TradeDate >= startDate && TradeDate < endDate
            )
         }
         """.compiled()

            val operation = schema.service("TradeService").operation("getTradesAfter")
            val expressionConstraint = operation.contract!!.returnTypeConstraints[0] as ExpressionConstraint
            expressionConstraint.operatorExpression.operator.shouldBe(FormulaOperator.LogicalAnd)
            expressionConstraint.operatorExpression.lhs.asA<OperatorExpression>().should { lhs ->
               lhs.lhs.asA<TypeExpression>().type.qualifiedName.shouldBe("TradeDate")
               lhs.operator.shouldBe(FormulaOperator.GreaterThanOrEqual)
               lhs.rhs.asA<ArgumentSelector>().path.shouldBe("startDate")
            }
            expressionConstraint.operatorExpression.rhs.asA<OperatorExpression>().should { lhs ->
               lhs.lhs.asA<TypeExpression>().type.qualifiedName.shouldBe("TradeDate")
               lhs.operator.shouldBe(FormulaOperator.LessThan)
               lhs.rhs.asA<ArgumentSelector>().path.shouldBe("endDate")
            }
         }

         it("should compile constraints for base types") {
            val errors = """
         $taxi
         service TradeService {
           operation getTradesAfter(startDateTime:TransactionEventDateTime):Trade[](
               TransactionEventDateTime >= startDateTime
            )
         }
         """.compiled()
         }
      }


   }
})

fun String.validated(
   config: CompilerConfig = TestCompilerOptions.config,
   linterRules: List<LinterRuleConfiguration> = emptyList()
): List<CompilationMessage> {

   return Compiler(this, config = config.copy(linterRuleConfiguration = linterRules)).validate()
}

fun List<String>.compiled(
   config: CompilerConfig = TestCompilerOptions.config,
   linterRules: List<LinterRuleConfiguration> = emptyList()
): TaxiDocument {
   return Compiler(
      this.map { CharStreams.fromString(it) },
      config = config.copy(linterRuleConfiguration = linterRules)
   ).compile()
}
fun String.compiled(
   config: CompilerConfig = TestCompilerOptions.config,
   linterRules: List<LinterRuleConfiguration> = emptyList()
): TaxiDocument {
   return Compiler(this, config = config.copy(linterRuleConfiguration = linterRules)).compile()
}

fun String.compiledQueries(
   config: CompilerConfig = TestCompilerOptions.config,
   linterRules: List<LinterRuleConfiguration> = emptyList()
): List<TaxiQlQuery> {
   return Compiler(this, config = config.copy(linterRuleConfiguration = linterRules)).queries()
}

object TestCompilerOptions {
   val config = CompilerConfig(
      // Note, in our tests, we run with the type checker enabled.
      typeCheckerEnabled = FeatureToggle.ENABLED
   )
}

// Syntax sugar to help testing
val ExpressionConstraint.operatorExpression: OperatorExpression
   get() {
      return if (this.expression is OperatorExpression) this.expression as OperatorExpression else error("Expected an OperatorExpression, but was ${this::class.simpleName}")
   }

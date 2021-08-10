package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.linter.LinterRuleConfiguration
import lang.taxi.query.TaxiQlQuery
import lang.taxi.services.operations.constraints.PropertyFieldNameIdentifier
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.services.operations.constraints.PropertyTypeIdentifier
import lang.taxi.services.operations.constraints.RelativeValueExpression
import lang.taxi.toggles.FeatureToggle
import lang.taxi.types.AttributePath
import lang.taxi.types.QualifiedName
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object OperationContextSpec : Spek({
   describe("declaring context to operations") {
      val taxi = """
         type TransactionEventDateTime inherits Instant
         model Trade {
            tradeId : TradeId inherits String
            tradeDate : TradeDate inherits Instant
            orderDateTime : TransactionEventDateTime( @format = "yyyy-MM-dd HH:mm:ss.SSSSSSS")
         }
         type EmployeeCode inherits String
      """.trimIndent()

      describe("compiling declaration of context") {
         it("compiles return type with property type equal to param") {
            val operation = """
         $taxi
         service TradeService {
            operation getTrade(id:TradeId):Trade(
               TradeId = id
            )
         }
         """.compiled().service("TradeService").operation("getTrade")
            operation.contract!!.returnTypeConstraints.should.have.size(1)
            val constraint = operation.contract!!.returnTypeConstraints.first() as PropertyToParameterConstraint
            constraint.operator.should.equal(Operator.EQUAL)
            val propertyIdentifier = constraint.propertyIdentifier as PropertyTypeIdentifier
            propertyIdentifier.type.parameterizedName.should.equal("TradeId")

            val valueExpression = constraint.expectedValue as RelativeValueExpression
            valueExpression.path.path.should.equal("id")
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
            operation getTrade(id:TradeId):Trade(
               EmployeeCode = id
            )
         }
         """.validated()
            errors.should.be.empty
         }

         it("should fail if referenced param is not an input") {
            val errors = """
         $taxi
         service TradeService {
            operation getTrade(id:TradeId):Trade(
               TradeId = foo
            )
         }
         """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.equal("Operation getTrade does not declare a parameter with name foo")
         }

         it("compiles return type with property field equal to param") {
            val operation = """
         $taxi
         service TradeService {
            operation getTrade(tradeId:TradeId):Trade(
               this.tradeId = tradeId
            )
         }
         """.compiled().service("TradeService").operation("getTrade")
            operation.contract!!.returnTypeConstraints.should.have.size(1)
            val constraint = operation.contract!!.returnTypeConstraints.first() as PropertyToParameterConstraint
            constraint.operator.should.equal(Operator.EQUAL)
            val propertyIdentifier = constraint.propertyIdentifier as PropertyFieldNameIdentifier
            propertyIdentifier.name.path.should.equal("tradeId")

            val valueExpression = constraint.expectedValue as RelativeValueExpression
            valueExpression.path.path.should.equal("tradeId")
         }

         it("should fail if referenced property is not present on target type") {
            val errors = """
         $taxi
         service TradeService {
            operation getTrade(id:TradeId):Trade(
               this.something = id
            )
         }
         """.validated()
            errors.should.have.size(1)
            errors.first().detailMessage.should.equal("Type Trade does not contain a property something")
         }


         it("compiles return type with property type greater than param") {
            val operation = """
         $taxi
         service TradeService {
            operation getTradesAfter(startDate:Instant):Trade[](
               TradeDate >= startDate
            )
         }
         """.compiled().service("TradeService").operation("getTradesAfter")
            operation.contract!!.returnTypeConstraints.should.have.size(1)
            val constraint = operation.contract!!.returnTypeConstraints.first() as PropertyToParameterConstraint
            constraint.operator.should.equal(Operator.GREATER_THAN_OR_EQUAL_TO)
         }

         it("compiles return type with multiple property params") {
            val operation = """
         $taxi
         service TradeService {
            operation getTradesAfter(startDate:Instant, endDate:Instant):Trade[](
               TradeDate >= startDate,
               TradeDate < endDate
            )
         }
         """.compiled().service("TradeService").operation("getTradesAfter")
            val constraints = operation.contract!!.returnTypeConstraints
            constraints.should.contain(
               PropertyToParameterConstraint(
                  PropertyTypeIdentifier(QualifiedName.from("TradeDate")),
                  Operator.GREATER_THAN_OR_EQUAL_TO,
                  RelativeValueExpression(AttributePath.from("startDate")),
                  emptyList()
               )
            )
            constraints.should.contain(
               PropertyToParameterConstraint(
                  PropertyTypeIdentifier(QualifiedName.from("TradeDate")),
                  Operator.LESS_THAN,
                  RelativeValueExpression(AttributePath.from("endDate")),
                  emptyList()
               )
            )
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

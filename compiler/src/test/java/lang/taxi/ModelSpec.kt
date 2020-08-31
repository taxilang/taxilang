package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.CalculatedFieldSetExpression
import lang.taxi.types.ConditionalAccessor
import lang.taxi.types.FormulaOperator
import lang.taxi.types.QualifiedName
import lang.taxi.types.TerenaryFieldSetExpression
import lang.taxi.types.TerenaryFormulaOperator
import lang.taxi.types.UnaryCalculatedFieldSetExpression
import lang.taxi.types.UnaryFormulaOperator
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

class ModelSpec : Spek({
   describe("model syntax") {
      describe("simple grammar") {
         it("should allow declaration of a model") {
            val src = """
model Person {
   firstName : FirstName as String
   lastName : LastName as String
}
           """.trimIndent()
            val person = Compiler(src).compile().model("Person")
            person.should.not.be.`null`
            person.hasField("firstName").should.be.`true`
            person.hasField("lastName").should.be.`true`
         }
      }

      describe("simple grammar") {
         it("should allow definition of calculated fields") {
            val src = """
               type QtyTick inherits Decimal
               type Qty inherits Decimal
               model Trade {
                  qtyTotal: Decimal as (QtyTick * Qty)
               }
           """.trimIndent()
            val trade = Compiler(src).compile().model("Trade")
            trade.should.not.be.`null`
            trade.hasField("qtyTotal").should.be.`true`
            trade.field("qtyTotal").formula.should.not.be.`null`
            val field = trade.field("qtyTotal")
            field.type.qualifiedName.should.startWith("lang.taxi.CalculatedDecimal_")
            val multiplication = field.formula
            multiplication!!.operandFields.should.contain(QualifiedName.from("QtyTick"))
            multiplication.operandFields.should.contain(QualifiedName.from("Qty"))
            multiplication.operator.should.equal(FormulaOperator.Multiply)
         }

         it("should allow concatenation of a date + time fields to an instant") {
            val src = """
               type TransactionDate inherits Date
               type TransactionTime inherits Time
               type TransactionDateTime inherits Instant
               model Transaction {
                  timestamp : TransactionDateTime as (TransactionDate + TransactionTime)
               }
            """.trimIndent()
            val transaction = Compiler(src).compile().model("Transaction")
            val formula = transaction.field("timestamp").formula!!
            formula.operator.should.equal(FormulaOperator.Add)
            formula.operandFields[0].fullyQualifiedName.should.equal("TransactionDate")
            formula.operandFields[1].fullyQualifiedName.should.equal("TransactionTime")
         }

         it("should not allow concatenation of time + date fields") {
            assertFailsWith(CompilationException::class) {
               val src = """
                 type TransactionDate inherits Date
               type TransactionTime inherits Time
               type TransactionDateTime inherits Instant
               model Transaction {
                  timestamp : TransactionDateTime as (TransactionTime + TransactionDate)
               }
               """.trimIndent()
               Compiler(src).compile().model("Trade")
            }
         }

         it("should not allow invalid operations on Date Time fields") {
            listOf(FormulaOperator.Divide, FormulaOperator.Multiply, FormulaOperator.Subtract).forEach { operator ->
               assertFailsWith(CompilationException::class) {
                  val src = """
                 type TransactionDate inherits Date
               type TransactionTime inherits Time
               type TransactionDateTime inherits Instant
               model Transaction {
                  timestamp : TransactionDateTime as (TransactionTime ${operator.symbol} TransactionDate)
               }
               """.trimIndent()
                  Compiler(src).compile().model("Trade")
               }
            }
         }

         it("should allow concatenation of strings") {
            val src = """
               type FirstName inherits String
               type LastName inherits String
               type FullName inherits String

               model Person {
                  fullName : FullName as (FirstName + LastName)
               }

            """.trimIndent()
            val transaction = Compiler(src).compile().model("Person")
            val formula = transaction.field("fullName").formula!!
            formula.operator.should.equal(FormulaOperator.Add)
            formula.operandFields[0].fullyQualifiedName.should.equal("FirstName")
            formula.operandFields[1].fullyQualifiedName.should.equal("LastName")
         }



         it("should not allow definition of calculated fields when one of the operand is String") {
            assertFailsWith(CompilationException::class) {
               val src = """
                  type QtyTick inherits Decimal
                  type Qty inherits String
                  model Trade {
                     qtyTotal: Decimal as (QtyTick * Qty)
                  }
               """.trimIndent()
               Compiler(src).compile().model("Trade")
            }
         }

         it("should not allow definition of calculated fields when one of the operand is inherited from Instant") {
            assertFailsWith(CompilationException::class) {
               val src = """
                  type QtyTick inherits Decimal
                  type Qty inherits Instant
                  model Trade {
                     qtyTotal: Decimal as (QtyTick * Qty)
                  }
               """.trimIndent()
               Compiler(src).compile().model("Trade")
            }
         }

         it("should not allow definition of calculated fields when one of the operand is inherited from Boolean") {
            assertFailsWith(CompilationException::class) {
               val src = """
                  type QtyTick inherits Boolean
                  type Qty inherits Decimal
                  model Trade {
                     qtyTotal: Decimal as (QtyTick * Qty)
                  }
               """.trimIndent()
               Compiler(src).compile().model("Trade")
            }
         }

         it("should not allow definition of calculated fields when one of the operand is inherited from Date") {
            assertFailsWith(CompilationException::class) {
               val src = """
                  type QtyTick inherits Date
                  type Qty inherits Decimal
                  model Trade {
                     qtyTotal: Decimal as (QtyTick * Qty)
                  }
               """.trimIndent()
               Compiler(src).compile().model("Trade")
            }
         }

         it("should not allow definition of calculated fields when one of the operand is inherited from Date") {
            assertFailsWith(CompilationException::class) {
               val src = """
                  type QtyTick inherits Time
                  type Qty inherits Decimal
                  model Trade {
                     qtyTotal: Decimal as (QtyTick * Qty)
                  }
               """.trimIndent()
               Compiler(src).compile().model("Trade")
            }
         }

         it("should allow formulas on fields") {
            val src = """
               type FirstName inherits String
               type LastName inherits String
               type FullName inherits String

               model Person {
                  firstName: FirstName
                  lastName: LastName
                  fullName : FullName by (this.firstName + this.lastName)
               }

            """.trimIndent()
            val transaction = Compiler(src).compile().model("Person")
            val accessor = transaction.field("fullName").accessor as ConditionalAccessor
            val calculatedFieldSetExpression = accessor.expression as CalculatedFieldSetExpression
            calculatedFieldSetExpression.operator.should.equal(FormulaOperator.Add)
            calculatedFieldSetExpression.operand1.fieldName.should.equal("firstName")
            calculatedFieldSetExpression.operand2.fieldName.should.equal("lastName")
         }

         it("compilation should fail for invalid formulas") {
            assertFailsWith(CompilationException::class) {
               val src = """
               type FirstName inherits String
               type LastName inherits String
               type FullName inherits String

               model Person {
                  firstName: FirstName
                  lastName: LastName
                  fullName : FullName by (this.firstName + this.invalidField)
               }

            """.trimIndent()
               val transaction = Compiler(src).compile().model("Person")
            }
         }

         it("should allow unary formulas on fields") {
            val src = """
               type FirstName inherits String
               type FullName inherits String

               model Person {
                  firstName: FirstName
                  leftName : FullName by left(this.firstName, 5)
               }

            """.trimIndent()
            val transaction = Compiler(src).compile().model("Person")
            val accessor = transaction.field("leftName").accessor as ConditionalAccessor
            val unaryCalculatedFieldSetExpression = accessor.expression as UnaryCalculatedFieldSetExpression
            unaryCalculatedFieldSetExpression.operator.should.equal(UnaryFormulaOperator.Left)
            unaryCalculatedFieldSetExpression.operand.fieldName.should.equal("firstName")
            unaryCalculatedFieldSetExpression.literal.should.equal("5")
         }

         it("should allow teranary formulas on fields") {
            val src = """
               type TradeId inherits String
               type OrderId inherits String
               type MarketId inherits String

               model Record {
                  tradeId: TradeId
                  orderId: OrderId
                  marketId: MarketId
                  id: String by concat3(this.tradeId, this.orderId, this.marketId, "-")
               }

            """.trimIndent()
            val transaction = Compiler(src).compile().model("Record")
            val accessor = transaction.field("id").accessor as ConditionalAccessor
            val tereanryCalculatedFieldSetExpression = accessor.expression as TerenaryFieldSetExpression
            tereanryCalculatedFieldSetExpression.operator.should.equal(TerenaryFormulaOperator.Concat3)
            tereanryCalculatedFieldSetExpression.operand1.fieldName.should.equal("tradeId")
            tereanryCalculatedFieldSetExpression.operand2.fieldName.should.equal("orderId")
            tereanryCalculatedFieldSetExpression.operand3.fieldName.should.equal("marketId")
            tereanryCalculatedFieldSetExpression.literal.should.equal("-")
         }

         it("should allow coalesce on strings") {
            val src = """
               type FirstName inherits String
               type LastName inherits String
               type FullName inherits String

               model Person {
                  field1: String as coalesce(FirstName, LastName, FullName)
               }

            """.trimIndent()
            val transaction = Compiler(src).compile().model("Person")
            val formula = transaction.field("field1").formula!!
            formula.operator.should.equal(FormulaOperator.Coalesce)
            formula.operandFields[0].fullyQualifiedName.should.equal("FirstName")
            formula.operandFields[1].fullyQualifiedName.should.equal("LastName")
            formula.operandFields[2].fullyQualifiedName.should.equal("FullName")
         }

         it("should allow coalesce on Decimals") {
            val src = """
               type Qty inherits Decimal
               type QtyHit inherits Decimal
               type QtyFill inherits Decimal
               type SomeQty inherits Decimal
               type SomeAnotherQty inherits SomeQty

               model Foo {
                  field1: SomeAnotherQty as coalesce(Qty, QtyHit, QtyFill)
               }

            """.trimIndent()
            val transaction = Compiler(src).compile().model("Foo")
            val formula = transaction.field("field1").formula!!
            formula.operator.should.equal(FormulaOperator.Coalesce)
            formula.operandFields[0].fullyQualifiedName.should.equal("Qty")
            formula.operandFields[1].fullyQualifiedName.should.equal("QtyHit")
            formula.operandFields[2].fullyQualifiedName.should.equal("QtyFill")
         }

         it("should allow coalesce on Ints") {
            val src = """
               type IntOne inherits Int
               type IntTwo inherits Int
               type IntThree inherits Int

               model Foo {
                  field1: Int as coalesce(IntOne, IntTwo, IntThree)
               }

            """.trimIndent()
            val transaction = Compiler(src).compile().model("Foo")
            val formula = transaction.field("field1").formula!!
            formula.operator.should.equal(FormulaOperator.Coalesce)
            formula.operandFields[0].fullyQualifiedName.should.equal("IntOne")
            formula.operandFields[1].fullyQualifiedName.should.equal("IntTwo")
            formula.operandFields[2].fullyQualifiedName.should.equal("IntThree")
         }

         it("Can't mix types with coalesce") {
            assertFailsWith(CompilationException::class) {
               val src = """
               type IntOne inherits Int
               type DecimalOne inherits Decimal
               type IntThree inherits Int

               model Foo {
                  field1: Int as coalesce(IntOne, DecimalOne, IntThree)
               }

            """.trimIndent()
               val transaction = Compiler(src).compile().model("Foo")
            }
         }
      }
   }
})

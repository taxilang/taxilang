package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.FormulaOperator
import lang.taxi.types.QualifiedName
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
      }
   }
})

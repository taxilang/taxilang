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

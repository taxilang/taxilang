package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.CalculatedFieldSetExpression
import lang.taxi.types.ConditionalAccessor
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

         describe("finding fields") {
            val baseSchema = """
               type Name inherits String
               type FirstName inherits Name
               type LastName inherits Name
            """.trimIndent()
            it("can find top-level fields with type") {
               val taxi = """
               $baseSchema
               model Person {
                  firstName : FirstName
                  lastName : LastName
               }
            """.compiled()

               val person = taxi.model("Person")
               person.fieldReferencesAssignableTo(taxi.type("FirstName"))
                  .should.have.size(1)
               person.fieldReferencesAssignableTo(taxi.type("LastName"))
                  .should.have.size(1)
               person.fieldReferencesAssignableTo(taxi.type("Name"))
                  .should.have.size(2)

            }
            it("can find nested fields with type") {
               val taxi = """
               $baseSchema
               model Names {
                  firstName : FirstName
                  lastName : LastName
               }
               model Person {
                  names : Names
               }
            """.compiled()

               val person = taxi.model("Person")
               person.fieldReferencesAssignableTo(taxi.type("FirstName")).let { firstNameReferences ->
                  firstNameReferences.should.have.size(1)
                  firstNameReferences.first().description.should.equal("Person.names.firstName")
               }
               person.fieldReferencesAssignableTo(taxi.type("LastName")).let { lastNameReferences ->
                  lastNameReferences.should.have.size(1)
                  lastNameReferences.first().description.should.equal("Person.names.lastName")
               }

               person.fieldReferencesAssignableTo(taxi.type("Name")).let { nameReferences ->
                  nameReferences.should.have.size(2)
                  nameReferences.map { it.description }.should.equal(
                     listOf(
                        "Person.names.firstName", "Person.names.lastName"
                     )
                  )
               }
            }
            it("can find deeply nested fields with type") {
               val taxi = """
               $baseSchema
               model Identifier {
                  personNames : Names
               }
               model Names {
                  firstName : FirstName
                  lastName : LastName
               }
               model Person {
                  id : Identifier
               }
            """.compiled()

               val person = taxi.model("Person")
               person.fieldReferencesAssignableTo(taxi.type("FirstName")).let { firstNameReferences ->
                  firstNameReferences.should.have.size(1)
                  firstNameReferences.first().description.should.equal("Person.id.personNames.firstName")
               }
               person.fieldReferencesAssignableTo(taxi.type("LastName")).let { lastNameReferences ->
                  lastNameReferences.should.have.size(1)
                  lastNameReferences.first().description.should.equal("Person.id.personNames.lastName")
               }

               person.fieldReferencesAssignableTo(taxi.type("Name")).let { nameReferences ->
                  nameReferences.should.have.size(2)
                  nameReferences.map { it.description }.should.equal(
                     listOf(
                        "Person.id.personNames.firstName", "Person.id.personNames.lastName"
                     )
                  )
               }
            }
         }
      }
   }
})

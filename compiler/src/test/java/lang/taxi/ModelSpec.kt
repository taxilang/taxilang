package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.expressions.FieldReferenceExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.types.FormulaOperator
import kotlin.test.assertFailsWith

class ModelSpec : DescribeSpec({
   describe("model syntax") {
      it("is possible to define inline inheritance") {
         val type = """
                  model Person {
                     name : Name inherits String
                  }
               """.compiled()
            .model("Person")
            .field("name")
            .type
         type.qualifiedName.shouldBe("Name")
         type.inheritsFrom.single().qualifiedName.shouldBe("lang.taxi.String")
      }
      describe("simple grammar") {
         it("should allow declaration of a model") {
            val src = """
type FirstName inherits String
type LastName inherits String
model Person {
   firstName : FirstName
   lastName : LastName
}
           """.trimIndent()
            val person = Compiler(src).compile().model("Person")
            person.should.not.be.`null`
            person.hasField("firstName").should.be.`true`
            person.hasField("lastName").should.be.`true`
         }

         it("should set the source in the correct namespace") {
            val capturedSource= """
               namespace names {
                  type FirstName inherits String
                  type LastName inherits String
                  type NickName inherits String
               }
               namespace foo.bar {
                  model Person {
                     firstName : names.FirstName
                     lastName : names.LastName
                     nickNames: names.NickName[]
                  }
               }
            """.compiled()
               .model("foo.bar.Person")
               .compilationUnits.single().source
            capturedSource.content.withoutWhitespace().should.equal("""import names.FirstName
import names.LastName
import names.NickName
namespace foo.bar {
   model Person {
      firstName : names.FirstName
      lastName : names.LastName
      nickNames: names.NickName[]
   }
}""".withoutWhitespace())
         }
      }

      describe("simple grammar") {
         it("should allow concatenation of a date + time fields to an instant") {
            val src = """
               type TransactionDate inherits Date
               type TransactionTime inherits Time
               type TransactionDateTime inherits Instant
               model Transaction {
                  timestamp : TransactionDateTime by (TransactionDate + TransactionTime)
               }
            """.trimIndent()
            val timestamp = Compiler(src).compile().model("Transaction")
               .field("timestamp")
         }

         it("should not allow concatenation of time + date fields") {
               val src = """
                 type TransactionDate inherits Date
               type TransactionTime inherits Time
               type TransactionDateTime inherits String
               model Transaction {
                  timestamp : TransactionDateTime by (TransactionTime + TransactionDate)
               }
               """
                  .validated()
                  .shouldContainMessage("Type mismatch.  Type of lang.taxi.Instant is not assignable to type TransactionDateTime")
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
                  fullName : FullName by (FirstName + LastName)
               }

            """.trimIndent()
            val transaction = Compiler(src).compile().model("Person")
            val operatorExpression = transaction.field("fullName").accessor as OperatorExpression
            operatorExpression.lhs.asA<TypeExpression>().type.qualifiedName.should.equal("FirstName")
            operatorExpression.rhs.asA<TypeExpression>().type.qualifiedName.should.equal("LastName")
         }

         it("should not allow definition of calculated fields when one of the operand is String") {
               val src = """
                  type QtyTick inherits Decimal
                  type Qty inherits String
                  model Trade {
                     qtyTotal: Decimal by (QtyTick * Qty)
                  }
               """.trimIndent()
                  .validated()
                  .shouldContainMessage("Operations with symbol '*' is not supported on types Decimal and String")
         }

         it("should not allow definition of calculated fields when one of the operand is inherited from Instant") {
               val src = """
                  type QtyTick inherits Decimal
                  type Qty inherits Instant
                  model Trade {
                     qtyTotal: Decimal by (QtyTick * Qty)
                  }
               """.trimIndent()
                  .validated()
                  .shouldContainMessage("Operations with symbol '*' is not supported on types Decimal and Instant")
         }

         it("should not allow definition of calculated fields when one of the operand is inherited from Boolean") {
            """
                  type QtyTick inherits Boolean
                  type Qty inherits Decimal
                  model Trade {
                     qtyTotal: Decimal by (QtyTick * Qty)
                  }
               """
               .validated()
               .shouldContainMessage("Operations with symbol '*' is not supported on types Boolean and Decimal")
         }

         it("should not allow definition of calculated fields when one of the operand is inherited from Date") {
               val src = """
                  type QtyTick inherits Date
                  type Qty inherits Decimal
                  model Trade {
                     qtyTotal: Decimal by (QtyTick * Qty)
                  }
               """.validated()
                  .shouldContainMessage("Operations with symbol '*' is not supported on types Date and Decimal")
         }

         it("should not allow definition of calculated fields when one of the operand is inherited from Time") {
               val src = """
                  type QtyTick inherits Time
                  type Qty inherits Decimal
                  model Trade {
                     qtyTotal: Decimal by (QtyTick * Qty)
                  }
               """.trimIndent()
                  .validated()
                  .shouldContainMessage("Operations with symbol '*' is not supported on types Time and Decimal")
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
            val accessor = transaction.field("fullName").accessor as OperatorExpression
            accessor.lhs.asA<FieldReferenceExpression>().fieldName.should.equal("firstName")
            accessor.lhs.asA<FieldReferenceExpression>().returnType.qualifiedName.should.equal("FirstName")
            accessor.rhs.asA<FieldReferenceExpression>().fieldName.should.equal("lastName")
            accessor.rhs.asA<FieldReferenceExpression>().returnType.qualifiedName.should.equal("LastName")
         }

         it("compilation should fail for invalid formulas") {
               val src = """
               type FirstName inherits String
               type LastName inherits String
               type FullName inherits String

               model Person {
                  firstName: FirstName
                  lastName: LastName
                  fullName : FullName by (this.firstName + this.invalidField)
               }

            """.validated()
                  .shouldContainMessage("Field invalidField does not exist on type Person")
         }

         describe("finding fields") {
            val baseSchema = """
               type Name inherits String
               type FirstName inherits Name
               type LastName inherits Name
            """.trimIndent()

            it("does not return fields that are primitive when looking for assignable") {
               val taxi = """
                  $baseSchema
                  model Person {
                     title : String
                     firstName : FirstName
                     lastName : LastName
                  }
               """.compiled()
               val person = taxi.model("Person")
               person.fieldReferencesAssignableTo(taxi.type("FirstName")).let { fields ->
                  fields.should.have.size(1)
               }
            }

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

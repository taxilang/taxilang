package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.LiteralExpression
import lang.taxi.services.ConsumedOperation
import lang.taxi.services.OperationScope
import lang.taxi.services.Parameter
import lang.taxi.services.operations.constraints.ConstantValueExpression
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.services.operations.constraints.InstanceArgument
import lang.taxi.services.operations.constraints.PropertyFieldNameIdentifier
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.services.operations.constraints.ReturnValueDerivedFromParameterConstraint
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.FormulaOperator
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.VoidType
import org.junit.jupiter.api.Test

class ServicesGrammarTest {
   @Test
   fun canCompileServices() {
      val source = """
type Person {
    id : PersonId as Int
    name : PersonName as String
}

[[ Use PersonService whenever you need a person. ]]
@RestService
service PersonService {
    [[ Your favorite persons ]]
    @Get("/foo/bar")
    operation getPerson(@AnotherAnnotation PersonId, PersonName):Person
}
"""
      val doc = Compiler(source).compile()
      val personService = doc.service("PersonService")
      expect(personService).to.not.be.`null`
      expect(personService.annotations).to.have.size(1)
      expect(personService.operations).to.have.size(1)
      expect(personService.operation("getPerson")).to.not.be.`null`
      expect(personService.typeDoc).to.be.equal("Use PersonService whenever you need a person.")
      val getPersonMethod = personService.operation("getPerson")
      expect(getPersonMethod.annotations).size.equal(1)
      expect(getPersonMethod.parameters).size.equal(2)
      expect(getPersonMethod.parameters.first().type).to.equal(doc.type("PersonId"))
      expect(getPersonMethod.parameters.first().annotations).size(1)
      expect(getPersonMethod.parameters.first().name).to.equal("p0")
      expect(getPersonMethod.typeDoc).to.be.equal("Your favorite persons")
      expect(getPersonMethod.returnType).to.equal(doc.type("Person"))
   }


   @Test
   fun canDeclareNamesParameters() {
      val source = """
service MyService {
    operation calculateCreditRisk(amount : String) : Decimal
}
"""
      val doc = Compiler(source).compile()
      val operation = doc.service("MyService").operation("calculateCreditRisk")
      expect(operation.parameters).to.have.size(1)
      expect(operation.parameters[0].name).to.equal("amount")
   }


   val moneyType: String = """
type Money {
    currency : Currency as String
    value : MoneyAmount as Decimal
}
"""

   @Test
   fun servicesCanExpressConstraintsOnParameters() {
      val source = """
service MyService {
    operation calculateCreditRisk(Money(this.currency == 'GBP')) : Decimal
}
"""
      val doc = Compiler.forStrings(moneyType, source).compile()
      val param = doc.service("MyService").operation("calculateCreditRisk").parameters[0]
      expect(param.constraints).to.have.size(1)
      val constraint = param.constraints[0] as ExpressionConstraint
      constraint.operatorExpression.lhs.asA<ArgumentSelector>().should {
         it.scope.should { scope ->
            scope.shouldBeInstanceOf<InstanceArgument>()
            scope.type.qualifiedName.shouldBe("Money")
         }
         it.path.shouldBe("currency")
         it.returnType.qualifiedName.shouldBe("Currency")
      }
      constraint.operatorExpression.operator.shouldBe(FormulaOperator.Equal)
      constraint.operatorExpression.rhs.asA<LiteralExpression>().value.shouldBe("GBP")
   }

   @Test
   fun operationsMayHaveZeroParameters() {
      val source = """
            service MyService {
                operation doSomething()
            }
        """.trimIndent()
      val doc = Compiler.forStrings(source).compile()
      val operation = doc.service("MyService").operation("doSomething")
      expect(operation.parameters).to.be.empty
   }

   @Test
   fun operationsThatDontDeclareReturnTypeAreMappedToVoid() {
      val source = """
            service MyService {
                operation doSomething(name:String)
            }
        """.trimIndent()
      val doc = Compiler.forStrings(source).compile()
      val returnType = doc.service("MyService").operation("doSomething").returnType
      expect(returnType).to.equal(VoidType.VOID)
   }

   @Test
   fun operationsHaveSourceMapped() {
      val source = """
            service MyService {
                operation doSomething(name:String):String
            }
        """.trimIndent()
      val doc = Compiler.forStrings(source).compile()
      val operation = doc.service("MyService").operation("doSomething")
      expect(operation.compilationUnits).to.have.size(1)
      expect(operation.compilationUnits.first().source.content).to.not.be.empty

   }

   fun given_serviceConstraintReferencesInvalidAttribute_then_compilationErrorIsThrown() {
      val source =
         """type Money {
    currency : Currency as String
}
service MyService {
    // Invalid, because the parameter name 'ccy' is incorrect (should be 'currency')
    operation calculateCreditRisk(amount : Money(ccy = 'GBP')) : Decimal
}
"""
      Compiler(source).validate().should.have.size(1)
   }

   @Test
   fun operationsCanDeclareScopes() {
      val source = """
service MyService {
    read operation readSomething()
    write operation writeSomething()
    operation somethingElse()
}
        """.trimIndent()
      val doc = Compiler.forStrings(source).compile()
      expect(doc.service("MyService").operation("readSomething").scope).to.equal(OperationScope.READ_ONLY)
      expect(doc.service("MyService").operation("writeSomething").scope).to.equal(OperationScope.MUTATION)
      expect(doc.service("MyService").operation("somethingElse").scope).to.equal(OperationScope.READ_ONLY)
   }

   @Test
   fun servicesCanDeclareContractsOnReturnValues() {
      val source = """
service MyService {
    operation convertCurrency(source : Money, target : Currency) : Money( from source,  this.currency == target )
}
"""
      val doc = Compiler.forStrings(moneyType, source).compile()
      val operation = doc.service("MyService").operation("convertCurrency")
      expect(operation.contract).to.not.be.`null`
      val contract = operation.contract!!
      expect(contract.returnTypeConstraints).to.have.size(2)
      expect(contract.returnTypeConstraints[0]).instanceof(ReturnValueDerivedFromParameterConstraint::class.java)
      val originConstraint = contract.returnTypeConstraints[0] as ReturnValueDerivedFromParameterConstraint
      expect(originConstraint.attributePath.path).to.equal("source")

      val constraint = contract.returnTypeConstraints[1] as ExpressionConstraint
      constraint.operatorExpression.lhs.asA<ArgumentSelector>().should {
         it.scope.shouldBeInstanceOf<InstanceArgument>()
         it.returnType.qualifiedName.shouldBe("Currency")
         it.path.shouldBe("currency")
      }
      constraint.operatorExpression.operator.shouldBe(FormulaOperator.Equal)
      constraint.operatorExpression.rhs.asA<ArgumentSelector>().should {
         it.scope.should {scope ->
            scope.shouldBeInstanceOf<Parameter>()
            scope.name.shouldBe("target")
         }
      }
   }

   @Test
   fun servicesCanDeclareContractsWithNestedPropertiesOnReturnValues() {
      val source = """
type ConversionRequest {
    source : Money
    target : Currency
}
service MyService {
    operation convertCurrency(request : ConversionRequest) : Money( from request.source,  this.currency == request.target )
}
"""
      val doc = Compiler.forStrings(moneyType, source).compile()
      val operation = doc.service("MyService").operation("convertCurrency")

      expect(operation.contract).to.not.be.`null`
      val contract = operation.contract!!

      expect(contract.returnType.qualifiedName).to.equal("Money")
      expect(contract.returnTypeConstraints).to.have.size(2)

      expect(contract.returnTypeConstraints[0]).to.be.instanceof(ReturnValueDerivedFromParameterConstraint::class.java)
      val returnValueDerivedFromParameterConstraint =
         contract.returnTypeConstraints[0] as ReturnValueDerivedFromParameterConstraint
      expect(returnValueDerivedFromParameterConstraint.path).to.equal("request.source")
      expect(returnValueDerivedFromParameterConstraint.attributePath.parts).to.contain.elements("request", "source")

      val expressionConstraint = contract.returnTypeConstraints[1] as ExpressionConstraint
      expressionConstraint.operatorExpression.rhs.asA<ArgumentSelector>().should {
         it.path.shouldBe("request.target")
         it.returnType.qualifiedName.shouldBe("Currency")
      }
   }

   @Test
   fun operationsCanReturnFullyQualifiedNameType() {
      val src = """
service MyService {
   operation op1(  ) : lang.taxi.String
   operation op2(  ) : String
}

        """.trimIndent()
      val doc = Compiler.forStrings(src).compile()
      expect(doc.service("MyService").operation("op1").returnType).to.equal(PrimitiveType.STRING)
      expect(doc.service("MyService").operation("op2").returnType).to.equal(PrimitiveType.STRING)
   }

   @Test
   fun `services generate source with dependent types correctly`() {
      val types = """namespace people {
         |type PersonId inherits Int
         |model Person {
         |  firstName : FirstName inherits String
         |}
         |}
      """.trimMargin()
      val services = """
         import people.PersonId
         import people.Person
         namespace services {
            service PersonService {
               operation findPerson(PersonId):Person
            }
         }
      """.trimIndent()
      val serviceSource = Compiler.forStrings(listOf(types, services)).compile()
         .service("services.PersonService")
         .compilationUnits.single().source
      serviceSource.content.withoutWhitespace()
         .should.equal(
            """import people.PersonId
import people.Person
namespace services {
   service PersonService {
         operation findPerson(PersonId):Person
      }
}""".withoutWhitespace()
         )
   }

   @Test
   fun `service lineage can not consume an unknown operation`() {
      val schema = """
         type PersonId inherits Int
         model Person {
           firstName : FirstName inherits String
          }
         service NameService {
                 operation findPersonId(FirstName): PersonId
            }

         service PersonService {
               lineage {
                   consumes operation UnknownService.findPersonId
                   stores Person
               }
               operation findPerson(PersonId):Person
            }
      """.trimIndent()

      val errors = Compiler(schema).validate()
      errors.should.have.size(1)
      errors.first().detailMessage.should.equal("UnknownService.findPersonId is not defined")
   }

   @Test
   fun `service lineage can not store an unknown type`() {
      val schema = """
         type PersonId inherits Int
         model Person {
           firstName : FirstName inherits String
          }
         service NameService {
                 operation findPersonId(FirstName): PersonId
            }

         service PersonService {
               lineage {
                   consumes operation NameService.findPersonId
                   stores UnknownType
               }
               operation findPerson(PersonId):Person
            }
      """.trimIndent()

      val errors = Compiler(schema).validate()
      errors.should.have.size(1)
      errors.first().detailMessage.should.equal("unknown type UnknownType")
   }

   @Test
   fun `service lineage can define both consumed service and stored types`() {
      val schema = """
         type PersonId inherits Int
         model Person {
           firstName : FirstName inherits String
          }
         service NameService {
                 operation findPersonId(FirstName): PersonId
            }

         service PersonService {
               lineage {
                   consumes operation NameService.findPersonId
                   stores Person
               }
               operation findPerson(PersonId):Person
            }
      """.trimIndent()

      val personService = Compiler.forStrings(listOf(schema)).compile()
         .service("PersonService")
      personService.lineage!!.consumes.should.equal(listOf(ConsumedOperation("NameService", "findPersonId")))
      personService.lineage!!.stores.should.equal(listOf(QualifiedName.from("Person")))
   }

   @Test
   fun `services with lineage generate source with dependent types correctly`() {
      val types = """namespace people {
         type PersonId inherits Int
         model Person {
           firstName : FirstName inherits String
          }
         service NameService {
                 operation findPersonId(FirstName): PersonId
            }
         }
      """.trimMargin()
      val services = """
         import people.PersonId
         import people.Person
         namespace services {
            service PersonService {
               lineage {
                   consumes operation people.NameService.findPersonId
               }
               operation findPerson(PersonId):Person
            }
         }
      """.trimIndent()
      val personService = Compiler.forStrings(listOf(types, services)).compile()
         .service("services.PersonService")

      personService.lineage!!.consumes.should.equal(listOf(ConsumedOperation("people.NameService", "findPersonId")))
      personService.lineage!!.stores.should.be.empty

      val serviceSource = personService.compilationUnits.single().source
      serviceSource.content.withoutWhitespace()
         .should.equal(
            """
            import people.PersonId
            import people.Person
            namespace services {
               service PersonService {
                     lineage {
                         consumes operation people.NameService.findPersonId
                     }
                     operation findPerson(PersonId):Person
                  }
            }
         """.trimIndent().withoutWhitespace()
         )
   }

   @Test
   fun `can declare a table`() {
      val table = """model Actor
      service ActorService {

         [[ This is some typedoc ]]
         @MyAnnotation
         table actors : Actor[]
      }
      """.compiled()
         .service("ActorService")
         .table("actors")
      table.returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<Actor>")
      table.typeDoc!!.trim().shouldBe("This is some typedoc")
      table.annotations.shouldHaveSize(1)
      table.annotations.first().name.shouldBe("MyAnnotation")
   }

   @Test
   fun `can declare a table with array long-hand`() {
      val table = """model Actor
      service ActorService {

         [[ This is some typedoc ]]
         @MyAnnotation
         table actors : Array<Actor>
      }
      """.compiled()
         .service("ActorService")
         .table("actors")
      table.returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<Actor>")
      table.typeDoc!!.trim().shouldBe("This is some typedoc")
      table.annotations.shouldHaveSize(1)
      table.annotations.first().name.shouldBe("MyAnnotation")
   }

   @Test
   fun `it is invalid to declare a table that doesnt return an array`() {
      val errors = """model Actor
      service ActorService {
         table actors : Actor
      }
      """.validated()

      errors.shouldHaveSize(1)
      errors.single().detailMessage.shouldBe("A table operation must return an array. Consider returning Actor[]")
   }

   @Test
   fun `can declare a stream`() {
      val stream = """model Actor
      service ActorService {

         [[ This is some typedoc ]]
         @MyAnnotation
         stream actors : Stream<Actor>
      }
      """.compiled()
         .service("ActorService")
         .stream("actors")
      stream.returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Stream<Actor>")
      stream.typeDoc!!.trim().shouldBe("This is some typedoc")
      stream.annotations.shouldHaveSize(1)
      stream.annotations.first().name.shouldBe("MyAnnotation")
   }

   @Test
   fun `it is invalid to declare a stream that doesnt return a stream type`() {
      val errors = """model Actor
      service ActorService {
         stream actors : Actor
      }
      """.validated()

      errors.shouldHaveSize(1)
      errors.single().detailMessage.shouldBe("A stream operation must return a type of Stream<T>. Consider returning Stream<Actor>")
   }

}

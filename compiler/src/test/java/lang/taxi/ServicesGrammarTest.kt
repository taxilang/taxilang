package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.services.operations.constraints.ReturnValueDerivedFromParameterConstraint
import lang.taxi.types.PrimitiveType
import lang.taxi.types.VoidType
import org.junit.Test
import kotlin.test.fail

class ServicesGrammarTest {
   @Test
   fun canCompileServices() {
      val source = """
type Person {
    id : PersonId as Int
    name : PersonName as String
}

@RestService
service PersonService {
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
      val getPersonMethod = personService.operation("getPerson")
      expect(getPersonMethod.annotations).size.equal(1)
      expect(getPersonMethod.parameters).size.equal(2)
      expect(getPersonMethod.parameters.first().type).to.equal(doc.type("PersonId"))
      expect(getPersonMethod.parameters.first().annotations).size(1)
      expect(getPersonMethod.parameters.first().name).to.be.`null`
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
    operation calculateCreditRisk(Money(currency = 'GBP')) : Decimal
}
"""
      val doc = Compiler.forStrings(moneyType, source).compile()
      val param = doc.service("MyService").operation("calculateCreditRisk").parameters[0]
      expect(param.constraints).to.have.size(1)
      val constraint = param.constraints[0] as PropertyToParameterConstraint
      fail("Not Implemented")
//        expect(constraint.fieldName).to.equal("currency")
//        expect(constraint.expectedValue).to.equal("GBP")
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
      expect(doc.service("MyService").operation("readSomething").scope).to.equal("read")
      expect(doc.service("MyService").operation("writeSomething").scope).to.equal("write")
      expect(doc.service("MyService").operation("somethingElse").scope).to.be.`null`
   }

   @Test
   fun servicesCanDeclareContractsOnReturnValues() {
      val source = """
service MyService {
    operation convertCurrency(source : Money, target : Currency) : Money( from source,  this.currency = target )
}
"""
      val doc = Compiler.forStrings(moneyType, source).compile()
      val operation = doc.service("MyService").operation("convertCurrency")
      expect(operation.contract).to.not.be.`null`
      val contract = operation.contract!!
      expect(contract.returnTypeConstraints).to.have.size(2)
      val constraint = contract.returnTypeConstraints[1]
      expect(constraint).instanceof(PropertyToParameterConstraint::class.java)
      val valueConstraint = constraint as PropertyToParameterConstraint
      fail("not implemente")
//        expect(valueConstraint.parameterName).to.equal("currency")
//        expect(valueConstraint.attributePath.path).to.equal("target")
//
//        expect(contract.returnTypeConstraints[0]).instanceof(ReturnValueDerivedFromParameterConstraint::class.java)
//        val originConstraint = contract.returnTypeConstraints[0] as ReturnValueDerivedFromParameterConstraint
//        expect(originConstraint.attributePath.path).to.equal("source")
   }

   @Test
   fun servicesCanDeclareContractsWithNestedPropertiesOnReturnValues() {
      val source = """
type ConversionRequest {
    source : Money
    target : Currency
}
service MyService {
    operation convertCurrency(request : ConversionRequest) : Money( from request.source,  this.currency = request.target )
}
"""
      val doc = Compiler.forStrings(moneyType, source).compile()
      val operation = doc.service("MyService").operation("convertCurrency")

      expect(operation.contract).to.not.be.`null`
      val contract = operation.contract!!

      expect(contract.returnType.qualifiedName).to.equal("Money")
      expect(contract.returnTypeConstraints).to.have.size(2)

      expect(contract.returnTypeConstraints[0]).to.be.instanceof(ReturnValueDerivedFromParameterConstraint::class.java)
      val returnValueFromInputConstraiint = contract.returnTypeConstraints[0] as ReturnValueDerivedFromParameterConstraint
      expect(returnValueFromInputConstraiint.path).to.equal("request.source")
      expect(returnValueFromInputConstraiint.attributePath.parts).to.contain.elements("request", "source")

      expect(contract.returnTypeConstraints[1]).to.be.instanceof(PropertyToParameterConstraint::class.java)
      val attributeValueFromInputConstraiint = contract.returnTypeConstraints[1] as PropertyToParameterConstraint
      fail("not implemented")
//        expect(attributeValueFromInputConstraiint.parameterName).to.equal("currency")
//        expect(attributeValueFromInputConstraiint.attributePath.path).to.equal("request.target")
//        expect(attributeValueFromInputConstraiint.attributePath.parts).to.contain.elements("request", "target")
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

}

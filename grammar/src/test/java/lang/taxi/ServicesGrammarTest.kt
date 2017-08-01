package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.services.AttributeConstantValueConstraint
import lang.taxi.services.AttributeValueFromParameterConstraint
import org.junit.Test

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
        val doc = Compiler(moneyType, source).compile()
        val param = doc.service("MyService").operation("calculateCreditRisk").parameters[0]
        expect(param.constraints).to.have.size(1)
        val constraint = param.constraints[0] as AttributeConstantValueConstraint
        expect(constraint.fieldName).to.equal("currency")
        expect(constraint.expectedValue).to.equal("GBP")
    }

    @Test(expected = CompilationException::class)
    fun given_serviceConstraintReferencesInvalidAttribute_then_compilationErrorIsThrown() {
        val source =
                """type Money {
    currency : Currency as String
}
service MyService {
    operation calculateCreditRisk(amount : Money(ccy = 'GBP')) : Decimal
}
"""
        Compiler(source).compile()
    }

    @Test
    fun servicesCanDeclareContractsOnReturnValues() {
        val source = """
service MyService {
    operation convertCurrency(source : Money, target : Currency) : Money( currency = target )
}
"""
        val doc = Compiler(moneyType, source).compile()
        val operation = doc.service("MyService").operation("convertCurrency")
        expect(operation.contract).to.not.be.`null`
        expect(operation.contract!!.returnTypeConstraints).to.have.size(1)
        val constraint = operation.contract!!.returnTypeConstraints[0]
        expect(constraint).instanceof(AttributeValueFromParameterConstraint::class.java)
        val valueConstraint = constraint as AttributeValueFromParameterConstraint
        expect(valueConstraint.fieldName).to.equal("currency")
        expect(valueConstraint.parameterName).to.equal("target")
    }
}

package lang.taxi

import com.winterbe.expekt.expect
import org.junit.Test

class ServicesGrammarTest {
    @Test
    fun canCompileServices() {
        val source = """
type Person {
    id : PersonId as Int
    name : String
}

@RestService
service PersonService {
    @Get("/foo/bar")
    operation getPerson(@AnotherAnnotation PersonId):Person
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
        expect(getPersonMethod.parameters).size.equal(1)
        expect(getPersonMethod.parameters.first().type).to.equal(doc.type("PersonId"))
        expect(getPersonMethod.parameters.first().annotations).size(1)
        expect(getPersonMethod.returnType).to.equal(doc.type("Person"))

    }
}

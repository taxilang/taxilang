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
    // Design notes : Is "fun" a readable name for methods?  What about B.A's?  Would they understand it?
    // Design notes : Purposefully excluding the variable name here,
    // as it's not currently needed.
    @Get("/foo/bar")
    fun getPerson(@AnotherAnnotation PersonId):Person
}

"""
        val doc = Compiler(source).compile()
        val personService = doc.service("PersonService")
        expect(personService).to.not.be.`null`
        expect(personService.annotations).to.have.size(1)
        expect(personService.methods).to.have.size(1)
        expect(personService.method("getPerson")).to.not.be.`null`
        val getPersonMethod = personService.method("getPerson")
        expect(getPersonMethod.annotations).size.equal(1)
        expect(getPersonMethod.parameters).size.equal(1)
        expect(getPersonMethod.parameters.first().type).to.equal(doc.type("PersonId"))
        expect(getPersonMethod.parameters.first().annotations).size(1)
        expect(getPersonMethod.returnType).to.equal(doc.type("Person"))

    }
}

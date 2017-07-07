package lang.taxi

import org.junit.Test

class ServicesGrammarTest {
    @Test
    fun canCompileServices() {
        val source = """
type Person {
    id : PersonId as Int
    name : String
}
service PersonService {
    // Design notes : Is "fun" a readable name for methods?  What about B.A's?  Would they understand it?
    // Design notes : Purposefully excluding the variable name here, as it encourages less thought into property type aliases
    @Get("/foo/bar")
    fun getPerson(PersonId):Person
}

"""
    }
}

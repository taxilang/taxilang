package lang.taxi.generators.java

import com.winterbe.expekt.expect
import lang.taxi.DataType
import lang.taxi.Namespace
import lang.taxi.Operation
import lang.taxi.Service
import org.junit.Test
import org.springframework.web.bind.annotation.RestController

class ServiceTests {

    @DataType
    @Namespace("taxi.example")
    data class Person(@field:DataType("taxi.example.PersonId") val personId: String)

    @RestController
    @Namespace("taxi.example")
    @Service("PersonService")
    class MyService {
        @Operation
        fun findPerson(@DataType("taxi.example.PersonId") personId: String): Person {
            TODO("not real")
        }
    }

    @Test
    fun generatesServiceTemplate() {
        val taxiDef = TaxiGenerator().forClasses(MyService::class.java, Person::class.java).generateAsStrings()
        expect(taxiDef).to.have.size(1)
        val expected = """
namespace taxi.example

type Person {
    personId : PersonId as String
}
service PersonService {
    operation findPerson(PersonId) : Person
}"""
        TestHelpers.expectToCompileTheSame(taxiDef, expected)

    }
}

package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.kapt.KotlinTypeAlias
import org.junit.BeforeClass
import org.junit.Test
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMembers

class TypeAliasRegistryTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun registerAlias() {
            TypeAliasRegistry.register(
                    KotlinTypeAlias("lang.taxi",
                            "Adult",
                            "lang.taxi.Person",
                            emptySet())
            )
        }
    }


    @Test
    fun canFindTypeAliasOfField() {
        val driver = Car::class.declaredMembers.first { it.name == "driver" }
        val alias = TypeAliasRegistry.findTypeAlias(driver)
        expect(alias).not.to.be.`null`
        expect(alias!!.simpleName).to.equal("Adult")
    }

    @Test
    fun canFindTypeAliasOfParameter() {
        val method = Car::class.declaredFunctions.first { it.name == "doSomething" }
        val alias = TypeAliasRegistry.findTypeAlias(method.parameters[1])
        expect(alias).not.to.be.`null`
        expect(alias!!.simpleName).to.equal("Adult")
    }

    @Test
    fun canFindTypeAliasOfReturnValue() {
        val method = Car::class.declaredFunctions.first { it.name == "doSomething" }
        val alias = TypeAliasRegistry.findTypeAlias(method.returnType)
        expect(alias).not.to.be.`null`
        expect(alias!!.simpleName).to.equal("Adult")
    }

    @Test
    fun given_classIsNotAnAlias_then_findAliasReturnsNull() {
        val driver = Car::class.declaredMembers.first { it.name == "passenger" }
        val alias = TypeAliasRegistry.findTypeAlias(driver)
        expect(alias).to.be.`null`
    }

    @Test
    fun given_parameterIsNotAnAlias_then_findAliasReturnsNull() {
        val method = Car::class.declaredFunctions.first { it.name == "doSomethingElse" }
        val alias = TypeAliasRegistry.findTypeAlias(method.parameters[1])
        expect(alias).to.be.`null`
    }

    @Test
    fun given_parameterIsJava_then_findAliasReturnsNull() {
        val method = Book::class.declaredFunctions.first { it.name == "compareTo" }
        val alias = TypeAliasRegistry.findTypeAlias(method.parameters[1])
        expect(alias).to.be.`null`
    }
}

typealias Adult = Person

data class Person(val name: String)
data class Car(val driver: Adult, val passenger: Person) {
    fun doSomething(adult: Adult): Adult {
        TODO()
    }

    fun doSomethingElse(passenger: Person): Person {
        TODO()
    }
}
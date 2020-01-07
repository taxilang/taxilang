package lang.taxi

import com.winterbe.expekt.expect
import org.junit.Test

class ExtensionsTest {

    @Test
    fun typesCanAddAnnotationsThroughExtensions() {
        val source = """
type Person {
   name : String
}
@TypeAnnotation
type extension Person {
   @MyAnnotation(param2 = "bar")
   name
}
type extension Person {
   @AnotherAnnotation(param2 = "bar")
   name
}
"""
        val doc = Compiler(source).compile()
        val person = doc.objectType("Person")
        expect(person.field("name").annotations).size.to.equal(2)
        expect(person.annotations).size.to.equal(1)
    }

    @Test
    fun typesCanRefineDefinitionThroughExtensions() {
        val source = """
type Person {
   name : String
   age : Int
}
type alias FirstName as String

type extension Person {
    name : FirstName
}
        """
        val doc = Compiler(source).compile()
        val person = doc.objectType("Person")
        expect(person.field("name").type.qualifiedName).to.equal("FirstName")
        expect(person.field("age").type.qualifiedName).to.equal("lang.taxi.Int")
    }

    @Test(expected = CompilationException::class)
    fun refiningTypesMustMatchSamePrimitive() {
        val source = """
type Person {
   name : String
}

type alias FirstName as Int

type extension Person {
// This is invalid, as FirstName is aliased to Int.
    name : FirstName
}
        """
        Compiler(source).compile()
    }

    @Test(expected = CompilationException::class)
    fun cannotDefineMultipleTypeNarrowingExtensions() {
        val source = """
type Person {
   name : String
}
type alias FirstName as String
type alias LastName as String
type extension Person {
    name : FirstName
}
// This is invalid, as there is a max of a single refining extension allowed
type extension Person {
    name : LastName
}
        """
        Compiler(source).compile()
    }

   @Test
   fun canAddDocumentationThroughExtensions() {
      val source = """
type Person {
   name : String
}

[[
A person is a person who persons.
]]
type extension Person {
}
        """
      val doc = Compiler(source).compile()
      val person = doc.objectType("Person")
      expect(person.typeDoc?.trim()).to.equal("A person is a person who persons.")
   }

   @Test
   fun when_documentationExistsOnTypeAndExtension_then_theyAreConcatenated() {
      val source = """
[[
A person.  Little else is known.
]]
type Person {
   name : String
}

[[
A person is a person who persons.
]]
type extension Person {
}
        """
      val doc = Compiler(source).compile()
      val person = doc.objectType("Person")
      expect(person.typeDoc?.trim()).to.equal("""A person.  Little else is known.
         |A person is a person who persons.
      """.trimMargin())

   }
}

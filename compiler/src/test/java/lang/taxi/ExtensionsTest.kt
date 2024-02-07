package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import org.antlr.v4.runtime.CharStreams
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExtensionsTest {

   //Compiler(listOf(CharStreams.fromString(source1, "source1"), CharStreams.fromString(source2, "source2"))).compile()
   @Test
   fun `extend type in a different source file`() {
      val source1 = """
         type Person {
            name : String
         }
         @TypeAnnotation
         type extension Person {
            @MyAnnotation(param2 = "bar")
            name
         }"""
      val source2 = """
         type extension Person {
            @AnotherAnnotation(param2 = "bar")
            name
         }
      """.trimIndent()
      val doc = Compiler(listOf(CharStreams.fromString(source1, "source1"), CharStreams.fromString(source2, "source2"))).compile()
      val person = doc.objectType("Person")
      expect(person.field("name").annotations).size.to.equal(2)
      expect(person.annotations).size.to.equal(1)
   }

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
type FirstName inherits String

type extension Person {
    name : FirstName
}
        """
      val doc = Compiler(source).compile()
      val person = doc.objectType("Person")
      expect(person.field("name").type.qualifiedName).to.equal("FirstName")
      expect(person.field("age").type.qualifiedName).to.equal("lang.taxi.Int")
   }

   @Test
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
      assertThrows<CompilationException> {
         Compiler(source).compile()
      }
   }

   @Test
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
      assertThrows<CompilationException> {
         Compiler(source).compile()
      }
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


   @Test
   fun canDeclareEmptyEnumExtension() {
      val src = """
[[ Like in a rainbow ]]
enum Colors {
[[ Like a Rose]]
RED, BLUE
}

[[ Sometimes called Colours ]]
@Documented
enum extension Colors
      """.trimIndent()
      val doc = Compiler(src).compile()
      val enum = doc.enumType("Colors")
      expect(enum.typeDoc).to.equal("Like in a rainbow\nSometimes called Colours")
      expect(enum.annotations).to.have.size(1)

   }
   @Test
   fun canDeclareExtensionOnEnum() {
      val src = """
[[ Like in a rainbow ]]
enum Colors {
[[ Like a Rose]]
RED, BLUE
}

[[ Sometimes called Colours ]]
@Documented
enum extension Colors {
   [[ Reddish ]]
   RED,

   [[ Ocean like ]]
   @Documented
   BLUE
}
      """.trimIndent()
      val doc = Compiler(src).compile()
      val enum = doc.enumType("Colors")
      expect(enum.typeDoc).to.equal("Like in a rainbow\nSometimes called Colours")
      expect(enum.annotations).to.have.size(1)
      expect(enum.values).to.have.size(2)
      expect(enum.value("RED").typeDoc).to.equal("Like a Rose\nReddish")
      expect(enum.value("BLUE").annotations).to.have.size(1)
      expect(enum.value("BLUE").typeDoc).to.equal("Ocean like")

   }

   @Test
   fun modifyingMembersOfAnEnumViaAnExtensionIsIllegal() {
      val src = """
enum Colors {
RED, BLUE
}

enum extension Colors {
RED, GREEN
}"""
      try {
         val doc = Compiler(src).compile()
         fail("A compilation error was expected")
      } catch (exception:CompilationException) {
         expect(exception.errors).to.have.size(1)
         expect(exception.errors.first().detailMessage).to.equal("Cannot modify the members in an enum.  An extension attempted to add a new members GREEN")
      }
   }



   @Test
   fun declaringAnEnumExtension_when_decoratingASubsetOfMembers_then_theOtherMembersAreUnaffected() {
      val src = """
enum Colors {
RED, BLUE
}

enum extension Colors {
[[ Documented ]]
RED
}"""
   }

   @Test
   fun `An Int default value can't be assigned to a String based field`() {
      val source = """
         type alias FirstName as String
         type Person {
            name : String
         }
         type extension Person {
            name: FirstName = 42
         }
        """
      assertThrows<CompilationException> {
         Compiler(source).compile()
      }
   }

   @Test
   fun `A String default value can't be assigned to a Int based field`() {
      val source = """
         type alias Age as Int
         type Person {
            age : Int
         }
         type extension Person {
            age: Age by default ('jimmy')
         }
        """
      assertThrows<CompilationException> {
         Compiler(source).compile()
      }
   }
   @Test
   fun `A non-existing enum default value can't be assigned to a enum based field`() {
      val source = """
         enum CountryCode {
            UK,
            DE
         }
         type Person {
            birthCountry : CountryCode
         }
         type extension Person {
            birthCountry: CountryCode by default (CountryCode.TR)
         }
        """
      assertThrows<CompilationException> {
         Compiler(source).compile()
      }
   }
}


class MyTest {
   @Test
   fun can_declareDependenciesOnImportedTypes() {
      val srcA = """
namespace foo
type Customer {}
      """.trimIndent()
      val docA = Compiler(srcA).compile()
      val srcB = """
import foo.Customer
namespace bar

[[ I am the typedoc ]]
type extension Customer {}
      """.trimIndent()
      val docB = Compiler(srcB, importSources = listOf(docA)).compile()

      docB.objectType("foo.Customer").typeDoc.should.equal("I am the typedoc")

   }
}

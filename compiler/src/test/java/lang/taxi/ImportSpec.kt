package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.messages.Severity
import lang.taxi.sources.SourceCode
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName

class ImportSpec: DescribeSpec({
   describe("Import statements") {

      it("returns a list of imports in a file") {
         val src = """import foo.bar.baz
import fuzz.bizz.boz

type FooType
      """.trimIndent()
         val compiler = Compiler(src)
         compiler.contextAt(3, 9)!!.importsInFile().should.contain.elements(
            QualifiedName.from("foo.bar.baz"),
            QualifiedName.from("fuzz.bizz.boz")
         )
      }

      it("detects imports in a multi namespaced document") {
         val src = """import foo.bar.baz
import fuzz.bizz.boz

namespace Blah {
   type FooType
}

namespace Blurg {
   type FuzzType
}
      """.trimIndent()
         val compiler = Compiler(src)
         compiler.contextAt(4, 10)!!.importsInFile().should.contain.elements(
            QualifiedName.from("foo.bar.baz"),
            QualifiedName.from("fuzz.bizz.boz")
         )
         compiler.contextAt(8,14)!!.importsInFile().should.contain.elements(
            QualifiedName.from("foo.bar.baz"),
            QualifiedName.from("fuzz.bizz.boz")
         )
      }
      it("can import from another schema") {
         val sourceA = """
namespace test {
    type FirstName inherits String
}
        """.trimIndent()
         val schemaA = Compiler(sourceA).compile()
         val sourceB = """
import test.FirstName

namespace foo {
    type Customer {
        name : test.FirstName
    }
}
        """.trimIndent()

         val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
         val customer = schemaB.type("foo.Customer") as ObjectType
         expect(customer.field("name").type.qualifiedName).to.equal("test.FirstName")
      }
      // Not sure if this is a good idea or not, that imported types
      // become present in the final document
      // May need to split these to distinguish between declaredTypes and importedTypes.
      // However, at the moment, primitives are present in the types collection, which indicates
      // that may not be needed.
      it("includes imported types in the compiled schema") {
         val sourceA = """
namespace test {
    type alias FirstName as String
}
        """.trimIndent()
         val schemaA = Compiler(sourceA).compile()
         val sourceB = """
import test.FirstName

namespace foo {
    type Customer {
        name : test.FirstName
    }
}
        """.trimIndent()

         val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
         expect(schemaB.containsType("test.FirstName")).to.be.`true`
      }

      it("imports types that are referenced by other imported types") {
         val srcA = """
namespace foo

type Customer {
    name : FirstName inherits String
}
        """.trimIndent()
         val schemaA = Compiler(srcA).compile()

         val srcB = """
import foo.Customer

type Thing {
    customer : foo.Customer
}
        """.trimIndent()
         val schemaB = Compiler(srcB, importSources = listOf(schemaA)).compile()
         expect(schemaB.containsType("foo.Customer")).to.be.`true`
         expect(schemaB.containsType("foo.FirstName")).to.be.`true`
      }


      it("creates and error if importing a type that doesn't exist") {
         val sourceB = """
import test.FirstName

namespace foo {
    type Customer {
        name : test.FirstName
    }
}
        """.trimIndent()
         val errors = Compiler(sourceB).validate()
         expect(errors.filter { it.severity == Severity.ERROR }).to.have.size(2)
         expect(errors.first().detailMessage).to.equal("Cannot import test.FirstName as it is not defined")
      }

      it("can declare an inline type on an annotation") {

      }

      it("when two types with same name exist but one is explicitly imported then type resolution is unambiguous") {
         val sourceA = """
namespace foo {
   type alias Name as String
}

namespace bar {
   type alias Name as String
}
      """.trimIndent()
         val sourceB = """
import foo.Name
namespace car {
   type Person {
      name : Name
   }
}
      """.trimIndent()
         val schemaA = Compiler(sourceA).compile()
         val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()
         schemaB.objectType("car.Person").field("name").type.qualifiedName.should.equal("foo.Name")
      }

      it("can import a type defined inline") {
         val sourceA = """
namespace foo {
   model Person {
      firstName : FirstName inherits String
      lastName : LastName inherits String
   }
}
      """.trimIndent()
         val sourceB = """
import foo.FirstName
namespace bar {
   model Pet {
      name : FirstName
   }
}
      """.trimIndent()
         val schemaA = Compiler(sourceA).compile()
         val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()

         schemaB.model("bar.Pet").field("name").type.qualifiedName.shouldBe("foo.FirstName")
      }

      it("can import a type defined inline when compiler created with a list of sources") {
         val sourceA = """
namespace foo {
   model Person {
      firstName : FirstName inherits String
      lastName : LastName inherits String
   }
}
      """.trimIndent()
         val sourceB = """
import foo.FirstName
namespace bar {
   model Pet {
      name : FirstName
   }
}
      """.trimIndent()
         // This is the test... rather than declaring import sources,
         // we're constructing the compiler as a list of sources.
         // That seems to break imports
         val sources = listOf(sourceA,sourceB).mapIndexed { index, content ->
            SourceCode("source$index", content)
         }
         val schema = Compiler(sources).compile()
         schema.model("bar.Pet").field("name").type.qualifiedName.shouldBe("foo.FirstName")
      }

      it("can import a type defined without a namespace") {
         val sourceA = """
   model Person {
      firstName : FirstName inherits String
      lastName : LastName inherits String
   }
      """.trimIndent()
         val sourceB = """
import FirstName
namespace bar {
   model Pet {
      name : FirstName
   }
}
      """.trimIndent()
         val schemaA = Compiler(sourceA).compile()
         val schemaB = Compiler(sourceB, importSources = listOf(schemaA)).compile()

         schemaB.model("bar.Pet").field("name").type.qualifiedName.shouldBe("FirstName")
      }
   }
})

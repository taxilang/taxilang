package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.PrimitiveType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object EnumSpec : Spek({

   describe("enum syntax") {
      describe("basic syntax") {
         it("should parse enums with value of Int correctly") {
            val src = """
enum Foo {
   One(1),
   Two(2)
}
      """.trimIndent()
            val document = Compiler(src).compile()
            document.enumType("Foo").value("One").value.should.equal(1)
            document.enumType("Foo").value("One").qualifiedName.should.equal("Foo.One")
            document.enumType("Foo").value("Two").value.should.equal(2)
         }

         it("should parse enums with value of String correctly") {
            val src = """
enum Foo {
   One("A"),
   Two("B")
}
      """.trimIndent()
            Compiler(src).compile().enumType("Foo").value("One").value.should.equal("A")
            Compiler(src).compile().enumType("Foo").value("Two").value.should.equal("B")
         }

         it("should use the name of an enum as its value if not provided") {
            val src = """
enum Foo {
   One,
   Two
}
      """.trimIndent()
            val doc = Compiler(src).compile()
            doc.enumType("Foo").value("One").value.should.equal("One")
            doc.enumType("Foo").value("Two").value.should.equal("Two")
            doc.enumType("Foo").basePrimitive.should.equal(PrimitiveType.STRING)
            doc.enumType("Foo").valueType.should.equal(PrimitiveType.STRING)

         }

         it("should the infer the type as Int if all the values are ints") {
            val src = """
enum Foo {
   One(1),
   Two(2)
}
      """.trimIndent()
            Compiler(src).compile().enumType("Foo").basePrimitive.should.equal(PrimitiveType.INTEGER)
            Compiler(src).compile().enumType("Foo").valueType.should.equal(PrimitiveType.INTEGER)
         }

         it("should infer the type as String if all the values are strings") {
            val src = """
enum Foo {
   ONE('One'),
   TWO("Two") // Intentionally mixing the character declaration
}"""
            Compiler(src).compile().enumType("Foo").basePrimitive.should.equal(PrimitiveType.STRING)
         }

         it("should infer the type as String if the content types are mixed") {
            val src = """
enum Foo {
   One('One'),
   TWO(2)
}"""
            Compiler(src).compile().enumType("Foo").basePrimitive.should.equal(PrimitiveType.STRING)
            Compiler(src).compile().enumType("Foo").valueType!!.should.equal(PrimitiveType.STRING)
         }
      }

      it("should allow the content type to be explicitly declared") {
         val src = """
            type CountryCode inherits Int
            enum Country(CountryCode) {
               Australia(63),
               UnitedKingdom(44),
               NewZealand(64)
            }
         """.trimIndent()

         Compiler(src).compile().enumType("Country").basePrimitive.should.equal(PrimitiveType.INTEGER)
         Compiler(src).compile().enumType("Country").valueType!!.qualifiedName.should.equal("CountryCode")
      }
      it("should report compiler error if the value type is not defined") {
         val src = """
            enum Country(CountryCode) {
               Australia(63),
               UnitedKingdom(44),
               NewZealand(64)
            }
         """.trimIndent()

         val errors  = Compiler(src).validate()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("CountryCode is not defined as a type")
      }
      describe("extensions") {
         it("should apply docs added on an extension to the definition") {
            val src = """
enum Foo {
   One,
   Two
}
[[ A foo ]]
enum extension Foo {
 [[ One foo ]]
 One,
 [[ Two foos ]]
 Two
}
""".trimIndent()
            Compiler(src).compile().enumType("Foo").value("One").typeDoc.should.equal("One foo")
         }
      }
      describe("synonyms") {
         describe("basic syntax") {
            val src = """
enum English {
   One,
   Two
}
enum French {
   Un synonym of English.One,
   Deux synonym of English.Two
}
            """.trimIndent()
            it("should compile") {
               val doc = Compiler(src).compile()
            }
            it("should map synonyms in both directions") {
               val doc = Compiler(src).compile()
               doc.enumType("English").value("One").synonyms.should.contain("French.Un")
               doc.enumType("French").value("Un").synonyms.should.contain("English.One")
            }
         }

         it("should apply synonyms transitively") {
            val src = """
enum English {
   One
}
enum French {
   Un synonym of English.One
}
enum Australian {
   One synonym of English.One
}
            """.trimIndent()
            Compiler(src).compile().enumType("English").value("One").synonyms.should.contain.elements("Australian.One", "French.Un")
            Compiler(src).compile().enumType("French").value("Un").synonyms.should.contain.elements("Australian.One", "English.One")
            Compiler(src).compile().enumType("Australian").value("One").synonyms.should.contain.elements("French.Un", "English.One")
         }
         it("should allow lists of synonyms") {
            val src = """
enum English { One }
enum French { Un }
enum Australian {
   One synonym of [English.One, French.Un]
}
            """.trimIndent()
            Compiler(src).compile().enumType("English").value("One").synonyms.should.contain.elements("Australian.One", "French.Un")
            Compiler(src).compile().enumType("French").value("Un").synonyms.should.contain.elements("Australian.One", "English.One")
            Compiler(src).compile().enumType("Australian").value("One").synonyms.should.contain.elements("French.Un", "English.One")
         }
         it("should allow declaration of synonyms with an imported source") {
            val srcA = """
namespace language {
   enum English { One }
}
"""
            val srcB = """
import language.English
enum Australian {
   One synonym of English.One
}
            """.trimIndent()
            val docA = Compiler(srcA).compile()
            val docB = Compiler(srcB, importSources = listOf(docA)).compile()
            docB.enumType("language.English").value("One").synonyms.should.contain.elements("Australian.One")
         }
         it("should throw an error if the referenced type cannot be found") {
            val src = """
import language.English
enum Australian {
   One synonym of English.One
}"""
            val errors = Compiler(src).validate()
            errors.should.satisfy { it.any { error -> error.detailMessage.contains("language.English is not defined as a type") } }
         }
         it("should throw an error if the reference value is not a value on the type") {
            val src = """
enum English { One }
enum Australian {
   One synonym of English.Single
}
            """.trimIndent()
            val errors = Compiler(src).validate()
            errors.should.have.size(1)
            errors.first().detailMessage.should.equal("Single is not defined on type English")
         }
         it("should throw an error if the reference value is not an enum") {
            val src = """
enum English { One }
type Word
enum Australian {
   One synonym of Word.Single
}
            """.trimIndent()
            val errors = Compiler(src).validate()
            errors.should.have.size(1)
            errors.first().detailMessage.should.equal("Word is not an Enum")

         }
         it("should support referencing enum synonyms before they have been defined") {
            val src = """
enum French {
   Un synonym of English.One,
   Deux synonym of English.Two
}
enum English {
   One,
   Two
}
            """.trimIndent()
            val enumType = Compiler(src).compile().enumType("French")
            enumType.value("Un").synonyms.should.have.size(1)
         }
         it("should support circular references in enum synonyms") {
            val src = """
enum French {
   Un synonym of English.One,
   Deux synonym of English.Two
}
enum English {
   One synonym of French.Un,
   Two synonym of French.Deux
}
            """.trimIndent()
            Compiler(src).compile().enumType("French").value("Un").synonyms.should.have.size(1)
         }
         it("should support defining synonyms in extensions") {

         }
      }
   }
})

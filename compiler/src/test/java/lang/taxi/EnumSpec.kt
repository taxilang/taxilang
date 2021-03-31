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
            Compiler(src).compile().enumType("Foo").value("One").value.should.equal("One")
            Compiler(src).compile().enumType("Foo").value("Two").value.should.equal("Two")
         }

         it("should the infer the type as Int if all the values are ints") {
            val src = """
enum Foo {
   One(1),
   Two(2)
}
      """.trimIndent()
            Compiler(src).compile().enumType("Foo").basePrimitive.should.equal(PrimitiveType.INTEGER)
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
         }
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
         it("should allow a synonym to use a fully qualified name") {
            val taxi = """
               namespace calendars {
                  enum Days {
                     Monday,
                     Tuesday,
                     Wednesday
                  }
                  enum AbbreviatedDays {
                     Mon synonym of calendars.Days.Monday,
                     Tue synonym of calendars.Days.Tuesday,
                     Wed synonym of calendars.Days.Wednesday
                  }
               }
            """.compiled()
            taxi.enumType("calendars.AbbreviatedDays").ofValue("Mon").synonyms.should.have.size(1)
         }
         it("should allow a synonym to use a fully qualified name before the target is declared") {
            val taxi = """
               namespace calendars {
                  enum AbbreviatedDays {
                     Mon synonym of calendars.Days.Monday,
                     Tue synonym of calendars.Days.Tuesday,
                     Wed synonym of calendars.Days.Wednesday
                  }
                  enum Days {
                     Monday,
                     Tuesday,
                     Wednesday
                  }
               }
            """.compiled()
            taxi.enumType("calendars.AbbreviatedDays").ofValue("Mon").synonyms.should.have.size(1)
         }
         it("should throw an error if the referenced type cannot be found") {
            val src = """
import language.English
enum Australian {
   One synonym of English.One
}"""
            val errors = Compiler(src).validate()
            errors.should.satisfy { it.any { error -> error.detailMessage.contains("language.English is not defined") } }
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
type Word inherits String
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

         describe("enum default values") {
            it("will match default if no other option matches") {
               val src = """
               enum Country {
                  NZ("New Zealand"),
                  AUS("Australia"),
                  default UNKNOWN("Unknown")
               }
            """.trimIndent()
               val enum = Compiler(src).compile().enumType("Country")
               enum.ofValue("New Zealand").name.should.equal("NZ")
               enum.ofValue("Australia").name.should.equal("AUS")
               // No match, so use default
               enum.ofValue("United Kingdom").name.should.equal("UNKNOWN")

               // Not lenient, so shouldn't match
               enum.of("nz").name.should.equal("UNKNOWN")
               enum.of("UK").name.should.equal("UNKNOWN")
               enum.hasName("UK").should.be.`true` //should match default
               enum.hasValue("UK").should.be.`true` //should match default
            }
            it("is invalid to declare more than one default") {
               val src = """
               enum Country {
                  NZ("New Zealand"),
                  default AUS("Australia"),
                  default UNKNOWN("Unknown")
               }
            """.trimIndent()
               val errors = Compiler(src).validate()
               errors.size.should.equal(1)
               errors.first().detailMessage.should.equal("Cannot declare multiple default values - found AUS, UNKNOWN")
            }
            it("resolves to default if no other enum matches, and defaults are enabled") {
               val src = """
               enum Country {
                  NZ("New Zealand"),
                  default AUS("Australia"),
                  UNKNOWN("Unknown")
               }
            """.trimIndent()
               val enumType = Compiler(src).compile().enumType("Country")
               enumType.resolvesToDefault("uk").should.be.`true`
               enumType.resolvesToDefault("nz").should.be.`true`
               enumType.resolvesToDefault("NZ").should.be.`false`
            }
            it("does not resolve to default when no default is enabled, if no other enum matches") {
               val src = """
               enum Country {
                  NZ("New Zealand"),
                  AUS("Australia"),
                  UNKNOWN("Unknown")
               }
            """.trimIndent()
               val enumType = Compiler(src).compile().enumType("Country")
               enumType.resolvesToDefault("uk").should.be.`false`
               enumType.resolvesToDefault("nz").should.be.`false`
               enumType.resolvesToDefault("NZ").should.be.`false`
            }
         }
         it("is case insensitive when resolving defaults if enum is lenient") {
            val src = """
               lenient enum Country {
                  NZ("New Zealand"),
                  AUS("Australia"),
                  default UNKNOWN("Unknown")
               }
            """.trimIndent()
            val enumType = Compiler(src).compile().enumType("Country")
            enumType.resolvesToDefault("nz").should.be.`false`
            enumType.resolvesToDefault("NZ").should.be.`false`
            enumType.of("nz").name.should.equal("NZ")
            enumType.of("uk").name.should.equal("UNKNOWN")
         }
         it("is case insensitive with special characters") {
            val src = """
               lenient enum DayCountConvention {
                  ACT_360("ACT/360")
               }
            """.trimIndent()
            val enumType = Compiler(src).compile().enumType("DayCountConvention")
            enumType.of("Act/360").name.should.equal("ACT_360")
            enumType.of("ACT/360").name.should.equal("ACT_360")
         }
         describe("case insensitive enums") {
            it("is case sensitive by default") {
               val src = """
               enum Country {
                  NZ("New Zealand"),
                  AUS("Australia")
               }
            """.trimIndent()
               val enum = Compiler(src).compile().enumType("Country")
               enum.isLenient.should.be.`false`
               enum.hasName("nz").should.be.`false`
               enum.hasName("NZ").should.be.`true`
               enum.hasValue("new zealand").should.be.`false`
               enum.hasValue("New Zealand").should.be.`true`
            }

            it("supports lenient enums which match on case insensitive values") {
               val src = """
               lenient enum Country {
                  NZ("New Zealand"),
                  AUS("Australia")
               }
            """.trimIndent()
               val enum = Compiler(src).compile().enumType("Country")
               enum.isLenient.should.be.`true`
               enum.of("nz").name.should.equal("NZ")
               enum.ofValue("new zealand").name.should.equal("NZ")
               enum.ofName("nz").name.should.equal("NZ")
            }
         }


      }
   }
})

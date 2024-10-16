package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.LiteralArray
import lang.taxi.query.FactValue
import lang.taxi.query.Parameter
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TypedValue
import lang.taxi.utils.Benchmark
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class EnumSpec : DescribeSpec({

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
            document.enumType("Foo").value("One").enumValueQualifiedName.should.equal("Foo.One")
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
            Compiler(src).compile().enumType("English").value("One").synonyms.should.contain.elements(
               "Australian.One",
               "French.Un"
            )
            Compiler(src).compile().enumType("French").value("Un").synonyms.should.contain.elements(
               "Australian.One",
               "English.One"
            )
            Compiler(src).compile().enumType("Australian").value("One").synonyms.should.contain.elements(
               "French.Un",
               "English.One"
            )
         }
         it("should allow lists of synonyms") {
            val src = """
enum English { One }
enum French { Un }
enum Australian {
   One synonym of [English.One, French.Un]
}
            """.trimIndent()
            Compiler(src).compile().enumType("English").value("One").synonyms.should.contain.elements(
               "Australian.One",
               "French.Un"
            )
            Compiler(src).compile().enumType("French").value("Un").synonyms.should.contain.elements(
               "Australian.One",
               "English.One"
            )
            Compiler(src).compile().enumType("Australian").value("One").synonyms.should.contain.elements(
               "French.Un",
               "English.One"
            )
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
            errors.shouldContainMessage("Cannot import language.English as it is not defined")
         }
         it("should throw an error if the reference value is not a value on the type") {
            val src = """
enum English { One }
enum Australian {
   One synonym of English.Single
}
            """.trimIndent()
            val errors = Compiler(src).validate()
               .shouldContainMessage("Single is not defined on type English")
         }
         it("should throw an error if the reference value is not an enum") {
            val src = """
enum English { One }
type Word inherits String
enum Australian {
   One synonym of Word.Single
}
            """.trimIndent()
            Compiler(src).validate()
               .shouldContainMessage("Word.Single is not defined")
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

               enum.hasExplicitName("UK").shouldBeFalse()
               enum.hasExplicitValue("UK").shouldBeFalse()
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

      it("should allow selection of numeric values") {
         val taxi = """
            enum Numbers {
               One(1),
               Two(2)
            }
         """.compiled()
         taxi.enumType("Numbers").of(1).name.should.equal("One")
         taxi.enumType("Numbers").of("1").name.should.equal("One")
      }

      it("should parse string booleans as enum members") {
         val taxi = """
            enum Selected {
               `true`,
               `false`
            }
         """.compiled()
         taxi.enumType("Selected").of(true).name.should.equal("true")
         taxi.enumType("Selected").of("true").name.should.equal("true")
         taxi.enumType("Selected").of(false).name.should.equal("false")
         taxi.enumType("Selected").of("false").name.should.equal("false")

      }

      it("can parse an array of strings enum values to enums in an array literal") {
         val (a,b) = """
         enum Country {
            NZ("NZD"),
            AU("AUD")
         }
         """.compiledWithQuery("""
         given { c:Country[] = ["AUD","NZD"] }
         find {
            country : Country[]
         }
         """.trimIndent())
         val arrayMembers = b.facts.single().asA<Parameter>()
            .value.asA<FactValue.Expression>()
            .expression.asA<LiteralArray>()
            .members
         arrayMembers.shouldHaveSize(2)
      }
      it("can parse an array of string enum names to enums in an array literal") {
         val (a,b) = """
         enum Country {
            NZ("NZD"),
            AU("AUD")
         }
         """.compiledWithQuery("""
         given { c:Country[] = ["AU","NZ"] }
         find {
            country : Country[]
         }
         """.trimIndent())
         val arrayMembers = b.facts.single().asA<Parameter>()
            .value.asA<FactValue.Expression>()
            .expression.asA<LiteralArray>()
            .members
         arrayMembers.shouldHaveSize(2)
      }
      it("can parse an array of enum references to enums in an array literal") {
         val (a,b) = """
         enum Country {
            NZ("NZD"),
            AU("AUD")
         }
         """.compiledWithQuery("""
         given { c:Country[] = [Country.NZ, Country.AU] }
         find {
            country : Country[]
         }
         """.trimIndent())
         val arrayMembers = b.facts.single().asA<Parameter>()
            .value.asA<FactValue.Expression>()
            .expression.asA<LiteralArray>()
            .members
         arrayMembers.shouldHaveSize(2)
      }

      it("should allow selection of boolean values") {
         val taxi = """
            enum Selected {
               Yes(true),
               No(false)
            }
         """.compiled()
         taxi.enumType("Selected").of(true).name.should.equal("Yes")
         taxi.enumType("Selected").of("true").name.should.equal("Yes")
      }

      it("should have efficient hashcode implementations") {
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
         val taxiA = Compiler(src).compile()
         val taxiB = Compiler(src).compile()
         val frenchA = taxiA.enumType("French")
         val unA = frenchA.of("Un")
         val frenchB = taxiB.enumType("French")
         val unB = frenchB.of("Un")
         Benchmark.benchmark(
            "Enum Value hashcode",
            warmup = 100,
            iterations = 50_000,
            timeUnit = TimeUnit.NANOSECONDS
         ) {
            unA.hashCode()
            unB.hashCode()
            unA.should.equal(unB)
            unA.hashCode().should.equal(unB.hashCode())
         }
         Benchmark.benchmark("Enum hashcode", warmup = 100, iterations = 50_000, timeUnit = TimeUnit.NANOSECONDS) {
            val hash1 = frenchA.hashCode()
            val hash2 = frenchB.hashCode()
            hash1 == hash2
         }
      }
      it("can declare an enum with an object body") {
         val type = """
            model ErrorDetails {
               code : ErrorCode inherits Int
               message : ErrorMessage inherits String
            }
            enum Errors<ErrorDetails> {
               BadRequest({ code : 400, message : 'Bad Request' }),
               Unauthorized({ code : 401, message : 'Unauthorized' })
            }
         """.compiled()
            .enumType("Errors")
         type.valueType!!.qualifiedName.shouldBe("ErrorDetails")
         val typedValue = type.ofName("BadRequest").value.shouldBeInstanceOf<TypedValue>()
         typedValue.type.qualifiedName.shouldBe("ErrorDetails")
         typedValue.value.shouldBe(mapOf(
            "code" to 400,
            "message" to "Bad Request"
         ))
      }
      it("can reference a property of an enum object's body") {
         val (schema,query) = """
            model ErrorDetails {
               code : ErrorCode inherits Int
               message : ErrorMessage inherits String
            }
            enum Errors<ErrorDetails> {
               BadRequest({ code : 400, message : 'Bad Request' }),
               Unauthorized({ code : 401, message : 'Unauthorized' })
            }
         """.compiledWithQuery("""
            given { myError : Errors = Errors.BadRequest }
            find { "hello" } as {
               code : myError.code
            }
         """.trimIndent())
         query

      }
      it("can declare an enum with an object body with a default value") {
         val type = """
            model ErrorDetails {
               code : ErrorCode inherits Int
               message : ErrorMessage inherits String
            }
            enum Errors<ErrorDetails> {
               default BadRequest({ code : 400, message : 'Bad Request' }),
               Unauthorized({ code : 401, message : 'Unauthorized' })
            }
         """.compiled()
            .enumType("Errors")
         type.valueType!!.qualifiedName.shouldBe("ErrorDetails")
         val typedValue = type.ofName("Poopsy").value.shouldBeInstanceOf<TypedValue>()
         typedValue.type.qualifiedName.shouldBe("ErrorDetails")
         typedValue.value.shouldBe(mapOf(
            "code" to 400,
            "message" to "Bad Request"
         ))
      }
      it("is illegal to declare an enum with multiple type arguments") {
          """
            model ErrorDetails {
               code : ErrorCode inherits Int
               message : ErrorMessage inherits String
            }
            enum Errors<ErrorDetails, ErrorCode> {
               BadRequest({ code : 400, message : 'Bad Request' }),
               Unauthorized({ code : 401, message : 'Unauthorized' })
            }
         """.validated()
            .shouldContainMessage("An enum supports at most 1 type argument")
      }
      it("reports errors if an assigned field value of an enum is not assignable") {
         val errors = """ model ErrorDetails {
               code : ErrorCode inherits Int
               message : ErrorMessage inherits String
            }
            enum Errors<ErrorDetails> {
            // String is not assignable to int
               BadRequest({ code : "400", message : 'Bad Request' }),
               Unauthorized({ code : 401, message : 'Unauthorized' })
            }
         """.validated()
         errors.errors().shouldContainMessage("Type mismatch. Type of lang.taxi.String is not assignable to type ErrorCode")
      }
      it("reports errors if a required field value of an enum is not provided") {
         val errors = """ model ErrorDetails {
               code : ErrorCode inherits Int
               message : ErrorMessage inherits String
            }
            enum Errors<ErrorDetails> {
            // Message is missing
               BadRequest({ code : 400 }),
               Unauthorized({ code : 401, message : 'Unauthorized' })
            }
         """.validated()
         errors.errors().shouldContainMessage("Map is not assignable to type ErrorDetails as mandatory properties message are missing")
      }
      it("reports errors if the entire value is of wrong type") {
         val errors = """
            model ErrorDetails {
               code : ErrorCode inherits Int
               message : ErrorMessage inherits String
            }
            enum Errors<ErrorDetails> {
            // Wrong type
               BadRequest("Bad Request"),
               Unauthorized({ code : 401, message : 'Unauthorized' })
            }
         """.validated()
         errors.errors().shouldContainMessage("Value of Bad Request is not assignable to type ErrorDetails")
      }

      it("Can refer to a property of an enum") {
         """
model ErrorDetails {
   code : ErrorCode inherits Int
   message : ErrorMessage inherits String
}
enum Errors<ErrorDetails> {
   BadRequest({ code : 400, message : 'Bad Request' }),
   Unauthorized({ code : 401, message : 'Unauthorized' })
}
model Response {
  error: ErrorCode by Errors.BadRequest.code
}
         """.validated()
            .errors().shouldBeEmpty()
      }

      it("Can refer to a property of an enum") {
         """
model ErrorDetails {
   code : ErrorCode inherits Int
   message : ErrorMessage inherits String
   status : {
      severity : Severity inherits String
      resolution : {
         playbookSteps : PlaybookSteps inherits String
      }
   }
}
enum Errors<ErrorDetails> {
   BadRequest({ code : 400, message : 'Bad Request', status: { severity: 'Bad', resolution : { playbookSteps : 'Go nuts' } }  }),
   Unauthorized({ code : 401, message : 'Unauthorized', status: { severity: 'Bad', resolution : { playbookSteps : 'Go nuts' } }  })
}
model Response {
  playbookSteps: PlaybookSteps by Errors.BadRequest.status.resolution.playbookSteps
}
         """.validated()
            .errors().shouldBeEmpty()
      }

      it("Can refer to a property of an enum when using namespaces") {
         """
namespace com.foo.bar {
   model ErrorDetails {
      code : ErrorCode inherits Int
      message : ErrorMessage inherits String
      status : {
         severity : Severity inherits String
         resolution : {
            playbookSteps : PlaybookSteps inherits String
         }
      }
   }
   enum Errors<ErrorDetails> {
      BadRequest({ code : 400, message : 'Bad Request', status: { severity: 'Bad', resolution : { playbookSteps : 'Go nuts' } }  }),
      Unauthorized({ code : 401, message : 'Unauthorized', status: { severity: 'Bad', resolution : { playbookSteps : 'Go nuts' } }  })
   }
}

namespace com.baz {
   model Response {
     error: com.foo.bar.ErrorCode by com.foo.bar.Errors.BadRequest.status.resolution.playbookSteps
   }
}
         """.validated()
            .errors().shouldBeEmpty()
      }
   }
})

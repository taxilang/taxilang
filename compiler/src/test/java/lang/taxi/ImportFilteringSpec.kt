package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import lang.taxi.types.PrimitiveType
import org.antlr.v4.runtime.CharStreams

class ImportFilteringSpec : DescribeSpec ({

   /**
    * This feature exists because we now throw errors when redeclaring symbols
    * (which is correct), however Orbital requires the ability to edit a named
    * query and submit it.
    *
    * We want to allow this. The better fix is to replace CharStreams from the
    * compiler - but we're already working with compiled sources, which makes
    * working with CharStreams awkward
    *
    * ORB-1071
    */
   describe("Filtering members from imports") {
      it("should allow filtering of types to permit redeclaration") {
         val schemaA = """
         model Person {
            name : FirstName inherits String
         }
      """.compiled()
         val srcB = """model Person {
              name : Name inherits String
              age: Age inherits Int
            }
         """
         // Now try again, excluding Person
         val compiledWithFilter = Compiler(
            listOf(CharStreams.fromString(srcB.trimMargin())),
            importSources = listOf(schemaA),
            importSymbolFilter = ImportSymbolFilters.excludeNamed(setOf("Person"))
         ).compile()
         compiledWithFilter.containsType("Person").shouldBeTrue()
         compiledWithFilter.model("Person").fields.shouldHaveSize(2)
      }

      it("should allow filtering of types in namespaces to permit redeclaration") {
         val schemaA = """
         namespace com.foo

         model Person {
            name : FirstName inherits String
         }
      """.compiled()
         val srcB = """
            namespace com.foo

            model Person {
              name : Name inherits String
              age: Age inherits Int
            }
         """

         // Now try again, excluding Person
         val compiledWithFilter = Compiler(
            listOf(CharStreams.fromString(srcB.trimMargin())),
            importSources = listOf(schemaA),
            importSymbolFilter = ImportSymbolFilters.excludeNamed(setOf("com.foo.Person"))
         ).compile()
         compiledWithFilter.containsType("com.foo.Person").shouldBeTrue()
         compiledWithFilter.model("com.foo.Person").fields.shouldHaveSize(2)
      }


      it("should allow filtering of queries in namespaces to permit redeclaration") {
         val schemaA = """
         namespace com.foo

         query HelloWorld { find { 1 } }
      """.compiled()
         val srcB = """
            namespace com.foo

            query HelloWorld { find { "Foo" } }
         """
         // Now try again, excluding query
         val compiledWithFilter = Compiler(
            listOf(CharStreams.fromString(srcB.trimMargin())),
            importSources = listOf(schemaA),
            importSymbolFilter = ImportSymbolFilters.excludeNamed(setOf("com.foo.HelloWorld"))
         ).compile()
         compiledWithFilter.containsQuery("com.foo.HelloWorld").shouldBeTrue()
         compiledWithFilter.query("com.foo.HelloWorld")
            .returnType.shouldBe(PrimitiveType.STRING)
      }

      it("should allow filtering of services in namespaces to permit redeclaration") {
         val schemaA = """
         namespace com.foo

         model Person {
            name : Name inherits String
         }

         service PersonService {
            operation getPerson(): Person
         }
      """.compiled()
         val srcB = """
            namespace com.foo

            service PersonService {
               operation getPersonById(id: String): Person
               operation listPersons(): Person[]
            }
         """
         // Now try again, excluding service
         val compiledWithFilter = Compiler(
            listOf(CharStreams.fromString(srcB.trimMargin())),
            importSources = listOf(schemaA),
            importSymbolFilter = ImportSymbolFilters.excludeNamed(setOf("com.foo.PersonService"))
         ).compile()
         compiledWithFilter.containsService("com.foo.PersonService").shouldBeTrue()
         compiledWithFilter.service("com.foo.PersonService").operations.shouldHaveSize(2)
      }

      it("should allow filtering of policies in namespaces to permit redeclaration") {
         val schemaA = """
         namespace com.foo

         model Film {
            title : Title inherits String
         }

         policy FilmsPolicy against Film {
            read external { Film }
         }
      """.compiled()
         val srcB = """
            namespace com.foo

            policy FilmsPolicy against Film {
               read { Film as {
                  title: Title = null
                  ...
                }
              }
            }
         """
         // Now try again, excluding policy
         val compiledWithFilter = Compiler(
            listOf(CharStreams.fromString(srcB.trimMargin())),
            importSources = listOf(schemaA),
            importSymbolFilter = ImportSymbolFilters.excludeNamed(setOf("com.foo.FilmsPolicy"))
         ).compile()
         compiledWithFilter.containsPolicy("com.foo.FilmsPolicy").shouldBeTrue()
         compiledWithFilter.policy("com.foo.FilmsPolicy").rules.shouldHaveSize(1)
      }
   }
})

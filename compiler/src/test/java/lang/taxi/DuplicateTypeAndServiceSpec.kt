package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize

class DuplicateTypeAndServiceSpec : DescribeSpec({

   describe("duplicate type detection") {

      it("should detect duplicates when importing another compiled doc") {
         val compiledSrcA = """type Person inherits String""".compiled()
         val validationMessages =
            Compiler("""type Person inherits String""", importSources = listOf(compiledSrcA))
               .validate()

         validationMessages.shouldContainMessageStartingWith("Symbol Person is already declared")
      }

      it("should allow importing another spec without erroring on stdlib types etc") {
         val compiledSrcA = """type Person inherits String""".compiled()
         compiledSrcA.containsFunction("taxi.stdlib.left").shouldBeTrue()
         val compositeCompiled =
            Compiler("""type AnotherPerson inherits String""", importSources = listOf(compiledSrcA))
               .compile()

         compositeCompiled.containsType("Person").shouldBeTrue()
         compositeCompiled.containsType("AnotherPerson").shouldBeTrue()
         compositeCompiled.containsFunction("taxi.stdlib.left").shouldBeTrue()
      }

      it("should detect duplicate type declarations in the same file") {
         val source = """
            type Person inherits String
            type Person inherits String
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol Person is already declared")
      }

      it("should detect duplicate type declarations in different namespaces in the same file") {
         val source = """
            namespace foo {
               type Person inherits String
            }
            namespace bar {
               type Person inherits String
            }
         """.trimIndent()

         // This should compile fine - different namespaces means different qualified names
         source.compiled()
      }

      it("should detect duplicate type declarations with the same qualified name") {
         val source = """
            namespace foo {
               type Person inherits String
               type Person inherits String
            }
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol foo.Person is already declared")
      }

      it("should detect duplicate type declarations across multiple files") {
         val source1 = """
            namespace foo {
               type Person inherits String
            }
         """.trimIndent()

         val source2 = """
            namespace foo {
               type Person inherits String
            }
         """.trimIndent()

         listOf(source1, source2).validated()
            .shouldContainMessageStartingWith("Symbol foo.Person is already declared")
      }

      it("should detect duplicate model declarations") {
         val source = """
            model Person {
               name : String
            }
            model Person {
               name : String
            }
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol Person is already declared")
      }

      it("should detect duplicate enum declarations") {
         val source = """
            enum Status {
               ACTIVE,
               INACTIVE
            }
            enum Status {
               PENDING
            }
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol Status is already declared")
      }

      it("should detect duplicate type alias declarations") {
         val source = """
            type alias PersonId as String
            type alias PersonId as Int
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol PersonId is already declared")
      }

      it("should detect duplicate annotation type declarations") {
         val source = """
            annotation MyAnnotation
            annotation MyAnnotation
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol MyAnnotation is already declared")
      }

      it("should not allow duplication of inline type and declared type") {
         """
            type Name inherits String
            model Person {
               name : Name inherits String
            }
         """.validated()
            .shouldContainMessageStartingWith("Symbol Name is already declared")
      }

      it("should allow the same type name in different namespaces") {
         val source = """
            namespace foo {
               type Person inherits String
            }
            namespace bar {
               type Person inherits String
            }
         """.trimIndent()

         val doc = source.compiled()
         doc.containsType("foo.Person") shouldBe true
         doc.containsType("bar.Person") shouldBe true
      }

      it("should detect duplicates even when definitions differ") {
         val source = """
            type Person inherits String
            type Person inherits Int
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol Person is already declared")
      }

      it("should detect multiple duplicate declarations") {
         val source = """
            type Person inherits String
            type Person inherits String
            type Person inherits String
         """.trimIndent()

         val errors = source.validated()
         // Should have 2 errors (second and third declarations are duplicates)
         errors.filter { it.detailMessage.startsWith("Symbol Person is already declared") } shouldHaveSize 2
      }

      it("should detect duplicates of different type kinds") {
         val source = """
            type Person inherits String
            model Person {
               name : String
            }
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol Person is already declared")
      }
   }

   describe("duplicate service detection") {

      it("should detect duplicate service declarations in the same file") {
         val source = """
            service PersonService {
            }
            service PersonService {
            }
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol PersonService is already declared")
      }

      it("should detect duplicate service declarations with the same qualified name") {
         val source = """
            namespace foo {
               service PersonService {
               }
               service PersonService {
               }
            }
         """.trimIndent()

         source.validated()
            .shouldContainMessageStartingWith("Symbol foo.PersonService is already declared")
      }

      it("should detect duplicate service declarations across multiple files") {
         val source1 = """
            namespace foo {
               service PersonService {
               }
            }
         """.trimIndent()

         val source2 = """
            namespace foo {
               service PersonService {
               }
            }
         """.trimIndent()

         listOf(source1, source2).validated()
            .shouldContainMessageStartingWith("Symbol foo.PersonService is already declared")
      }

      it("should allow the same service name in different namespaces") {
         val source = """
            namespace foo {
               service PersonService {
               }
            }
            namespace bar {
               service PersonService {
               }
            }
         """.trimIndent()

         val doc = source.compiled()
         doc.containsService("foo.PersonService") shouldBe true
         doc.containsService("bar.PersonService") shouldBe true
      }

      it("should detect multiple duplicate service declarations") {
         val source = """
            service PersonService {
            }
            service PersonService {
            }
            service PersonService {
            }
         """.trimIndent()

         val errors = source.validated()
         // Should have 2 errors (second and third declarations are duplicates)
         errors.filter { it.detailMessage.contains("Symbol PersonService is already declared") } shouldHaveSize 2
      }
   }

   describe("mixed duplicate detection") {

      it("should detect both duplicate types and services in the same compilation") {
         val source = """
            type Person inherits String
            type Person inherits String

            service PersonService {
            }
            service PersonService {
            }
         """.trimIndent()

         val errors = source.validated()
         errors.shouldContainMessageStartingWith("Symbol Person is already declared")
         errors.shouldContainMessageStartingWith("Symbol PersonService is already declared")
      }

      it("should not declaring a service and type with the same name") {
         """
            type Foo inherits String
            service Foo {
            }
         """.validated()
            .shouldContainMessageStartingWith("Symbol Foo is already declared")
      }
   }

   describe("configurable duplicate definition severity") {

      it("should report duplicates as errors by default") {
         val source = """
            type Person inherits String
            type Person inherits String
         """.trimIndent()

         val errors = source.validated()
         errors.filter { it.detailMessage.startsWith("Symbol Person is already declared") }.let { duplicateErrors ->
            duplicateErrors shouldHaveSize 1
            duplicateErrors.first().severity shouldBe lang.taxi.messages.Severity.ERROR
         }
      }

      it("should report duplicates as warnings when configured") {
         val source = """
            type Person inherits String
            type Person inherits String
         """.trimIndent()

         val config = CompilerConfig(
            compilerOptions = lang.taxi.packages.CompilerOptions(
               duplicateDefinitionSeverity = lang.taxi.messages.Severity.WARNING
            )
         )

         val errors = Compiler(source, config = config).validate()
         errors.filter { it.detailMessage.startsWith("Symbol Person is already declared") }.let { duplicateErrors ->
            duplicateErrors shouldHaveSize 1
            duplicateErrors.first().severity shouldBe lang.taxi.messages.Severity.WARNING
         }
      }

      it("should report duplicates as info when configured") {
         val source = """
            type Person inherits String
            type Person inherits String
         """.trimIndent()

         val config = CompilerConfig(
            compilerOptions = lang.taxi.packages.CompilerOptions(
               duplicateDefinitionSeverity = lang.taxi.messages.Severity.INFO
            )
         )

         val errors = Compiler(source, config = config).validate()
         errors.filter { it.detailMessage.startsWith("Symbol Person is already declared") }.let { duplicateErrors ->
            duplicateErrors shouldHaveSize 1
            duplicateErrors.first().severity shouldBe lang.taxi.messages.Severity.INFO
         }
      }

      it("should allow compilation to succeed when duplicates are warnings") {
         val source = """
            type Person inherits String
            type Person inherits String
         """.trimIndent()

         val config = CompilerConfig(
            compilerOptions = lang.taxi.packages.CompilerOptions(
               duplicateDefinitionSeverity = lang.taxi.messages.Severity.WARNING
            )
         )

         // Should compile successfully (not throw) even with duplicates
         val (errors,doc) = Compiler(source, config = config).compileWithMessages()
         doc.containsType("Person") shouldBe true
         errors.shouldContainMessageStartingWith("Symbol Person is already declared")
      }

      it("should apply configured severity to service duplicates as well") {
         val source = """
            service PersonService {
            }
            service PersonService {
            }
         """.trimIndent()

         val config = CompilerConfig(
            compilerOptions = lang.taxi.packages.CompilerOptions(
               duplicateDefinitionSeverity = lang.taxi.messages.Severity.WARNING
            )
         )

         val errors = Compiler(source, config = config).validate()
         errors.filter { it.detailMessage.contains("Symbol PersonService is already declared") }.let { duplicateErrors ->
            duplicateErrors shouldHaveSize 1
            duplicateErrors.first().severity shouldBe lang.taxi.messages.Severity.WARNING
         }
      }
   }
})

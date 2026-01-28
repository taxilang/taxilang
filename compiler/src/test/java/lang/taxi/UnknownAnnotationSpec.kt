package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import lang.taxi.messages.Severity
import lang.taxi.packages.CompilerOptions

class UnknownAnnotationSpec : DescribeSpec({

   describe("unknown annotation handling") {

      describe("when unknownAnnotationSeverity is ERROR (default)") {

         it("should fail compilation for unknown annotation on type") {
            val source = """
               @UnknownAnnotation
               type Person {
                  name: String
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 1
            result.errors.first().detailMessage shouldContain "Cannot resolve annotation type: UnknownAnnotation"
            result.errors.first().severity shouldBe Severity.ERROR
         }

         it("should fail compilation for unknown annotation on field") {
            val source = """
               type Person {
                  @UnknownAnnotation
                  name: String
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 1
            result.errors.first().detailMessage shouldContain "Cannot resolve annotation type: UnknownAnnotation"
         }

         it("should fail compilation for unknown annotation on service") {
            val source = """
               @UnknownAnnotation
               service PersonService {
                  operation getPerson(): Person
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 1
            result.errors.first().detailMessage shouldContain "Cannot resolve annotation type: UnknownAnnotation"
         }

         it("should fail compilation for unknown annotation on operation") {
            val source = """
               type Person { name : String }
               service PersonService {
                  @UnknownAnnotation
                  operation getPerson(): Person
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 1
            result.errors.first().detailMessage shouldContain "Cannot resolve annotation type: UnknownAnnotation"
         }

         it("should fail compilation for unknown annotation on parameter") {
            val source = """
               type Person { name : String }
               service PersonService {
                  operation getPerson(@UnknownAnnotation id: String): Person
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 1
            result.errors.first().detailMessage shouldContain "Cannot resolve annotation type: UnknownAnnotation"
         }

         it("should succeed for known annotations") {
            val source = """
               annotation MyAnnotation

               @MyAnnotation
               type Person {
                  name: String
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors.shouldBeEmpty()
            val person = result.taxi.type("Person")
            person.annotations shouldHaveSize 1
            person.annotations.first().name shouldBe "MyAnnotation"
            person.annotations.first().type shouldBe result.taxi.type("MyAnnotation")
         }

         it("should report multiple unknown annotations") {
            val source = """
               @UnknownAnnotation1
               @UnknownAnnotation2
               type Person {
                  @UnknownAnnotation3
                  name: String
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 3
            result.errors[0].detailMessage shouldContain "UnknownAnnotation1"
            result.errors[1].detailMessage shouldContain "UnknownAnnotation2"
            result.errors[2].detailMessage shouldContain "UnknownAnnotation3"
         }

         it("should fail for unknown qualified annotation names") {
            val source = """
               @com.example.UnknownAnnotation
               type Person {
                  name: String
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 1
            result.errors.first().detailMessage shouldContain "com.example.UnknownAnnotation"
         }
      }

      describe("when unknownAnnotationSeverity is WARNING") {

         it("should compile successfully but report warnings") {
            val source = """
               @UnknownAnnotation
               type Person {
                  name: String
               }
            """.trimIndent()

            val config = CompilerConfig(
               compilerOptions = CompilerOptions(
                  unknownAnnotationSeverity = Severity.WARNING
               )
            )

            val result = Compiler(source, config).compile()
            result.errors shouldHaveSize 1
            result.errors.first().severity shouldBe Severity.WARNING
            result.errors.first().detailMessage shouldContain "Cannot resolve annotation type: UnknownAnnotation"

            // Should still succeed in producing output despite warning
            result.taxi.types shouldHaveSize 1
         }

         it("should allow multiple warnings") {
            val source = """
               @UnknownAnnotation1
               @UnknownAnnotation2
               type Person {
                  name: String
               }
            """.trimIndent()

            val config = CompilerConfig(
               compilerOptions = CompilerOptions(
                  unknownAnnotationSeverity = Severity.WARNING
               )
            )

            val result = Compiler(source, config).compile()
            result.errors shouldHaveSize 2
            result.errors.all { it.severity == Severity.WARNING } shouldBe true
         }
      }

      describe("when unknownAnnotationSeverity is INFO") {

         it("should compile successfully with info-level messages") {
            val source = """
               @UnknownAnnotation
               type Person {
                  name: String
               }
            """.trimIndent()

            val config = CompilerConfig(
               compilerOptions = CompilerOptions(
                  unknownAnnotationSeverity = Severity.INFO
               )
            )

            val result = Compiler(source, config).compile()
            result.errors shouldHaveSize 1
            result.errors.first().severity shouldBe Severity.INFO
            result.errors.first().detailMessage shouldContain "Cannot resolve annotation type: UnknownAnnotation"

            // Should produce the type normally
            result.taxi.types shouldHaveSize 1
         }
      }

      describe("backward compatibility") {

         it("should still resolve annotations from imports correctly") {
            val source = """
               import com.example.MyAnnotation

               @MyAnnotation
               type Person {
                  name: String
               }
            """.trimIndent()

            val importSource = """
               namespace com.example
               annotation MyAnnotation
            """.trimIndent()

            val result = Compiler(source, importSources = listOf(importSource)).compile()
            result.errors.shouldBeEmpty()
            val person = result.taxi.type("Person")
            person.annotations shouldHaveSize 1
            person.annotations.first().name shouldBe "MyAnnotation"
         }

         it("should handle the Id annotation edge case") {
            // This tests the backward compatibility case where a type name
            // is used as an annotation (line 1173-1185 in TokenProcessor)
            val source = """
               namespace foo {
                  type Id inherits String
                  model Thing {
                     @Id
                     id : Id
                  }
               }
            """.trimIndent()

            val config = CompilerConfig(
               compilerOptions = CompilerOptions(
                  unknownAnnotationSeverity = Severity.ERROR
               )
            )

            val result = Compiler(source, config).compile()
            // Should NOT error because Id resolves to a type (not an annotation)
            // and we maintain backward compatibility for this case
            result.errors.shouldBeEmpty()
         }
      }

      describe("edge cases") {

         it("should handle annotation on annotation definition") {
            val source = """
               @UnknownAnnotation
               annotation MyAnnotation
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 1
            result.errors.first().detailMessage shouldContain "UnknownAnnotation"
         }

         it("should handle unknown annotation with parameters") {
            val source = """
               @UnknownAnnotation(key = "value", count = 42)
               type Person {
                  name: String
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 1
            result.errors.first().detailMessage shouldContain "UnknownAnnotation"
         }

         it("should handle mixed known and unknown annotations") {
            val source = """
               annotation KnownAnnotation

               @KnownAnnotation
               @UnknownAnnotation
               type Person {
                  name: String
               }
            """.trimIndent()

            val result = Compiler(source).compile()
            result.errors shouldHaveSize 1
            result.errors.first().detailMessage shouldContain "UnknownAnnotation"
         }
      }

      describe("interaction with other compiler options") {

         it("should work alongside duplicate definition severity") {
            val source = """
               @UnknownAnnotation
               type Person { name: String }
               type Person { age: Int }
            """.trimIndent()

            val config = CompilerConfig(
               compilerOptions = CompilerOptions(
                  unknownAnnotationSeverity = Severity.WARNING,
                  duplicateDefinitionSeverity = Severity.ERROR
               )
            )

            val result = Compiler(source, config).compile()
            result.errors shouldHaveSize 2
            result.errors.count { it.severity == Severity.WARNING } shouldBe 1
            result.errors.count { it.severity == Severity.ERROR } shouldBe 1
         }
      }
   }
})

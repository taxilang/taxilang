package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize

class DuplicateTypeAndServiceSpec : DescribeSpec({

   describe("duplicate type detection") {

      it("should detect duplicate type declarations in the same file") {
         val source = """
            type Person inherits String
            type Person inherits String
         """.trimIndent()

         source.validated()
            .shouldContainMessage("Type Person is already defined")
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
            .shouldContainMessage("Type foo.Person is already defined")
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
            .shouldContainMessage("Type foo.Person is already defined")
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
            .shouldContainMessage("Type Person is already defined")
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
            .shouldContainMessage("Type Status is already defined")
      }

      it("should detect duplicate type alias declarations") {
         val source = """
            type alias PersonId as String
            type alias PersonId as Int
         """.trimIndent()

         source.validated()
            .shouldContainMessage("Type PersonId is already defined")
      }

      it("should detect duplicate annotation type declarations") {
         val source = """
            annotation MyAnnotation
            annotation MyAnnotation
         """.trimIndent()

         source.validated()
            .shouldContainMessage("Type MyAnnotation is already defined")
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
            .shouldContainMessage("Type Person is already defined")
      }

      it("should detect multiple duplicate declarations") {
         val source = """
            type Person inherits String
            type Person inherits String
            type Person inherits String
         """.trimIndent()

         val errors = source.validated()
         // Should have 2 errors (second and third declarations are duplicates)
         errors.filter { it.detailMessage == "Type Person is already defined" } shouldHaveSize 2
      }

      it("should detect duplicates of different type kinds") {
         val source = """
            type Person inherits String
            model Person {
               name : String
            }
         """.trimIndent()

         source.validated()
            .shouldContainMessage("Type Person is already defined")
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
            .shouldContainMessage("Service PersonService is already defined. Services may be extended (using an extension), but not redefined")
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
            .shouldContainMessage("Service foo.PersonService is already defined. Services may be extended (using an extension), but not redefined")
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
            .shouldContainMessage("Service foo.PersonService is already defined. Services may be extended (using an extension), but not redefined")
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
         errors.filter { it.detailMessage.contains("Service PersonService is already defined") } shouldHaveSize 2
      }

      it("should allow service extensions") {
         val source = """
            service PersonService {
            }
            service extension PersonService {
               operation getAll() : Person[]
            }
         """.trimIndent()

         // Service extensions should be allowed - this should compile fine
         source.compiled()
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
         errors.shouldContainMessage("Type Person is already defined")
         errors.shouldContainMessage("Service PersonService is already defined. Services may be extended (using an extension), but not redefined")
      }
   }
})

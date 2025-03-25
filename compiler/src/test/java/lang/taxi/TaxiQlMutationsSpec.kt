package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import lang.taxi.expressions.FunctionExpression
import lang.taxi.services.OperationScope

class TaxiQlMutationsSpec : DescribeSpec ({
   describe("declaring mutations") {
      val src = """
         model Person {
            personId : PersonId inherits String
         }

         model VipPerson inherits Person

         service PersonService {
            operation findAllPeople():Person[]
            write operation updatePerson(Person):Person
          }"""
      it("parses the scope of an operation") {
         src.compiled()
            .service("PersonService")
            .operation("updatePerson")
            .scope.shouldBe(OperationScope.MUTATION)
      }
      it("a query may declare a mutation following a find statement") {
         val query = """
             $src

             query UpdatePeople {
               find { Person }
               call PersonService::updatePerson
             }
         """.compiled()
            .query("UpdatePeople")

         query.mutation!!.service.qualifiedName.shouldBe("PersonService")
         query.mutation!!.operation.qualifiedName.shouldBe("updatePerson")
      }

      it("a query may declare a mutation on its own") {
         val query = """
             $src

             // Note: This is illogical as no inputs are provided,
             // but syntax is correct
             query UpdatePeople {
               call PersonService::updatePerson
             }
         """.compiled()
            .query("UpdatePeople")

         query.mutation!!.service.qualifiedName.shouldBe("PersonService")
         query.mutation!!.operation.qualifiedName.shouldBe("updatePerson")
      }
      it("a query may declare a mutation following a given statement") {
         val query = """
             $src

             query UpdatePeople {
               given { person : PersonId = "123" }
               call PersonService::updatePerson
             }
         """.compiled()
            .query("UpdatePeople")

         query.facts.shouldHaveSize(1)
         query.mutation!!.service.qualifiedName.shouldBe("PersonService")
         query.mutation!!.operation.qualifiedName.shouldBe("updatePerson")
      }

      it("is invalid to reference an operation that isn't explicitly write in a call statement") {
         """$src
            |
            |query UpdatePeople {
            |  find { Person }
            |  call PersonService::findAllPeople
            |}
         """.trimMargin()
            .validated()
            .shouldContainMessage("Call statements are only valid with write operations.  Operation PersonService::findAllPeople is not a write operation")
      }

      it("can parse a query from a precompiled schema using an explicit service name reference") {
         val taxi = """
            namespace com.foo.test

            $src
         """.compiled()

         val query = Compiler(
            source = """
               given { person : PersonId = "123" }
               call com.foo.test.PersonService::updatePerson""",
            importSources = listOf(taxi)
         ).queries().first()
         query.mutation.shouldNotBeNull()
         query.mutation!!.service.qualifiedName.shouldBe("com.foo.test.PersonService")
      }

      it("can parse a query from a precompiled schema using an imported service name reference") {
         val taxi = """
            namespace com.foo.test

            $src
         """.compiled()

         val query = Compiler(
            source = """
               import com.foo.test.PersonService

               given { person : PersonId = "123" }
               call PersonService::updatePerson""",
            importSources = listOf(taxi)
         ).queries().first()
         query.mutation.shouldNotBeNull()
         query.mutation!!.service.qualifiedName.shouldBe("com.foo.test.PersonService")
      }

      it("can parse a query from a precompiled schema") {
         val taxi = src.compiled()

         val query = Compiler(
            source = """given { person : PersonId = "123" }
               call PersonService::updatePerson""",
            importSources = listOf(taxi)
         ).queries().first()
         query.mutation.shouldNotBeNull()
      }

       it ("a mutation can have a typed projection") {
           val taxi = src.compiled()

           val query = Compiler(
               source = """given { person : PersonId = "123" }
               call PersonService::updatePerson as VipPerson""",
               importSources = listOf(taxi)
           ).queries().first()
           query.mutation.shouldNotBeNull()
           query.mutation!!.projectedType.shouldNotBeNull()
           query.mutation!!.projectedType!!.first.qualifiedName.shouldBe("VipPerson")
       }

       it ("a mutation can have an anonymous projection") {
           val taxi = src.compiled()

           val query = Compiler(
               source = """given { person : PersonId = "123" }
               call PersonService::updatePerson as {
                 vipId: PersonId
               }""",
               importSources = listOf(taxi)
           ).queries().first()
           query.mutation.shouldNotBeNull()
           query.mutation!!.projectedType.shouldNotBeNull()
           query.mutation!!.projectedType?.first?.anonymous.shouldBe(true)
       }

       it ("A mutation-with-projection against a non-existent service should generate a compilation error") {
           val taxi = src.compiled()

           val (compilationError, _) = Compiler(
               source = """given { person : PersonId = "123" }
               call FooService::updatePerson as {
                 vipId: PersonId
               }""",
               importSources = listOf(taxi)
           ).compileWithMessages()

           compilationError.shouldNotBeEmpty()
           compilationError.first().detailMessage.should.equal("FooService is not defined")
       }

       it ("A query-with-projection can have a mutation-with-projection") {
           val taxi = src.compiled()

           val query = Compiler(
               source = """given { person : PersonId = "123" }
               find { Person }  as {
                 cid: PersonId
               }
               call PersonService::updatePerson as {
                 vipId: PersonId
               }""",
               importSources = listOf(taxi)
           ).queries().first()
           query.mutation.shouldNotBeNull()
           query.mutation!!.projectedType.shouldNotBeNull()
           query.mutation!!.projectedType?.first?.anonymous.shouldBe(true)
       }

       it ("A mutation-with-projection that defines scoped variables") {
           val taxi = src.compiled()

           val query = Compiler(
               source = """
               find { Person[] }
               call PersonService::updatePerson as (person: first(Person[])) -> {
                 vipId: PersonId
               }""",
               importSources = listOf(taxi)
           ).queries().first()
           query.mutation.shouldNotBeNull()
           query.mutation!!.projectedType.shouldNotBeNull()
           query.mutation!!.projectedType?.first?.anonymous.shouldBe(true)
           val projectionFunctionScope = query.mutation!!.projectedType!!.second.first()
           projectionFunctionScope.name.should.equal("person")
           projectionFunctionScope.type.qualifiedName.should.equal("Person")
           val functionExpression = projectionFunctionScope.expression.shouldBeTypeOf<FunctionExpression>()
           functionExpression.function.qualifiedName.should.equal("taxi.stdlib.first")
       }
   }
})

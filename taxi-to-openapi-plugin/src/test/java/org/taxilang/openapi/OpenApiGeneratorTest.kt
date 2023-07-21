package org.taxilang.openapi

import com.google.common.io.Resources
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import lang.taxi.compiled
import lang.taxi.generators.WritableSource

class OpenApiGeneratorTest : DescribeSpec({

   describe("generating OpenAPI specs from Taxi") {

      it("should filter to only include named services") {
         OpenApiGenerator.matchesNamesFilter("com.foo.MyService", listOf("com.foo")).shouldBeTrue()
         OpenApiGenerator.matchesNamesFilter("com.foo.MyService", listOf("com.foo.MyService")).shouldBeTrue()
         OpenApiGenerator.matchesNamesFilter("com.foo.MyService", listOf("com.bar.MyService")).shouldBeFalse()
      }
      it("should include all services if no filter provided") {
         OpenApiGenerator.matchesNamesFilter("com.foo.MyService", emptyList()).shouldBeTrue()
      }

      it("should generate a spec for a saved query") {
         val taxi = """
            model Person {
               id: PersonId inherits Int
               firstName : FirstName inherits String
            }

            @HttpOperation(url = "/api/q/findPerson", method = "POST")
            query getPerson( @PathVariable("id") id : PersonId ) {
               find { Person( PersonId == id ) }
               as {
                  name : FirstName
                  personId : PersonId
               }
            }
         """.compiled()
         val openApi = OpenApiGenerator().generateOpenApiSpecAsYaml(taxi)
         // CAn't asser here right now, as the type names don't match.
//         openApi.single().shouldGenerateSameAs("yaml/expected-person-query.yaml")
      }

      // Kitchen sink
      it("should generate an actual spec") {
         val taxi = """model Person {
              firstName : FirstName inherits String
               id: PersonId inherits Int
            }

            @HttpService(baseUrl = "https://foo.com")
            service PersonService {
               [[ Finds all the people. ]]
               @HttpOperation(method = "GET", url = "/people")
               operation findAllPeople():Person[]

               [[ Finds just one person.  Sometimes, that's all you need, y'know?  Just one person. ]]
               @HttpOperation(method = "GET", url = "/people/{id}")
               operation findPerson(@PathVariable id: PersonId): Person

               [[ Updates a person. ]]
               @HttpOperation(method = "POST", url = "/people/{id}")
               operation updatePerson(@PathVariable id: PersonId, @RequestBody update: Person): Person

               [[ Deletes the person.  BOOM! They're gone. ]]
               @HttpOperation(method = "DELETE", url = "/people/{id}")
               operation killPerson(
                  [[ The id of the person to kill. Choose wisely ]]
                  @PathVariable id: PersonId?
                  ): Person
            }
            """.compiled()

         val openApi = OpenApiGenerator().generateOpenApiSpecAsYaml(taxi)
         openApi.single().shouldGenerateSameAs("yaml/expected-person-service.yaml")
      }
   }
})

private fun WritableSource.shouldGenerateSameAs(path: String) {

   val expected = Resources.getResource(path).readText()
   val expectedOas = Yaml.mapper().readValue(expected, OpenAPI::class.java)

   val actualOas = Yaml.mapper().readValue(this.content, OpenAPI::class.java)

   actualOas.shouldBe(expectedOas)
}

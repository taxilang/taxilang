package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class TaxiQlServiceRestrictionsSpec : DescribeSpec({
   describe("service restrictions in queries") {
      val schemaSrc = """
            model Film {
               title : FilmTitle inherits String
            }
            service FilmService {
               operation getFilms():Film[]
               operation getBlockbusters():Film[]

               table films : Film[]
            }
            service NetflixService {
               operation getFilms():Film[]
               operation getBlockbusters():Film[]
            }
         """.trimIndent()

      it("is possible to specify services to include in the query plan") {
         val (schema,query) = schemaSrc.compiledWithQuery("""
            find { Film[] }
            using { FilmService::getFilms }
         """.trimIndent())

         query.serviceRestrictions.inclusions.shouldHaveSize(1)
         val inclusion = query.serviceRestrictions.inclusions.single()
         inclusion.service.qualifiedName.shouldBe("FilmService")
         inclusion.members.shouldHaveSize(1)
         inclusion.members.single().qualifiedName.shouldBe("getFilms")
      }
      it("is possible to specify multiple services to include in the query plan") {
         val (schema,query) = schemaSrc.compiledWithQuery("""
            find { Film[] }
            using { FilmService::getFilms, FilmService::getBlockbusters }
         """.trimIndent())

         query.serviceRestrictions.inclusions.shouldHaveSize(1)
         val inclusion = query.serviceRestrictions.inclusions[0]
         inclusion.service.qualifiedName.shouldBe("FilmService")
         inclusion.members.shouldHaveSize(2)
         inclusion.members.map { it.name }
            .shouldContainAll("getFilms", "getBlockbusters")
      }

      it("is possible to exclude a table operation") {
         val (schema,query) = schemaSrc.compiledWithQuery("""
            find { Film[] }
            excluding { FilmService::films }
         """.trimIndent())
         query.serviceRestrictions.exclusions.shouldHaveSize(1)
      }

      it("is possible to mix service and operations to include in the query plan") {
         val (schema,query) = schemaSrc.compiledWithQuery("""
            find { Film[] }
            using { FilmService::getBlockbusters , FilmService::getFilms, NetflixService }
         """.trimIndent())

         query.serviceRestrictions.inclusions.shouldHaveSize(2)
         val firstInclusion = query.serviceRestrictions.inclusions[0]
         firstInclusion.members.map { it.name }
            .shouldContainAll("getFilms", "getBlockbusters")
         val secondInclusion = query.serviceRestrictions.inclusions[1]
         secondInclusion.members.shouldBeEmpty()
      }

      it("is an error to specify a service that does not exist") {
         val compilationException = schemaSrc.compiledWithQueryProducingCompilationException("""
            find { Film[] }
            using { FilmService2, FilmService::getBlockbusters }
         """.trimIndent())
         compilationException.errors.shouldContainMessage("FilmService2 is not defined")
      }
      it("is an error to specify an operation that does not exist") {
         val compilationException = schemaSrc.compiledWithQueryProducingCompilationException("""
            find { Film[] }
            using { FilmService, FilmService::getBlockbusters2 }
         """.trimIndent())
         compilationException.errors.shouldContainMessage("Service FilmService does not declare an operation getBlockbusters2")
      }
   }
})

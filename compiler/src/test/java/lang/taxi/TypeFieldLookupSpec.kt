package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSingleElement

class TypeFieldLookupSpec : DescribeSpec({
   describe("looking up fields within object types") {
      it("should match if an object type contains a desscendant of a type") {
         val schema = """
         model NamePart inherits String
         model Name {
            firstName : FirstName inherits NamePart
            lastName : LastName inherits NamePart
         }
         model InkLevels inherits Int
         model Person {
            name : Name
            friends : Person[]
         }
      """.compiled()
         schema.objectType("Person")
            .getDescendantPathsOfType(schema.type("FirstName")).shouldHaveSingleElement("name.firstName")
         schema.objectType("Person")
            .getDescendantPathsOfType(schema.type("NamePart"))
            .shouldContainExactlyInAnyOrder("name.firstName", "name.lastName")

         schema.objectType("Person")
            .getDescendantPathsOfType(schema.type("InkLevels"))
            .shouldBeEmpty()

         schema.objectType("Person")
            .getDescendantPathsOfType(schema.type("Person"))
            .shouldContainExactlyInAnyOrder("friends")
      }

      it("should return the expected path when it contains an array") {
         val schema = """

      model Film {
        imdbScore : ImdbScore inherits Decimal
      }

      model Catalog {
         films : Film[]
      }""".compiled()
         val path = schema.objectType("Catalog").getDescendantPathsOfType(schema.type("ImdbScore"))
         path.shouldContainExactlyInAnyOrder("films.imdbScore")
      }

      it("should find multiple collections") {
         val schema = """
            model Person {
               name : Name inherits String
            }
            model FilmStudio {
               films : Film[]
               videos : Film[]
            }
            model Film {
               team : {
                  actors : Person[]
                  crew : Person[]
               }
            }
         """.compiled()
         val paths = schema.objectType("FilmStudio").getDescendantPathsOfType(schema.type("Person[]"))
         paths.shouldContainExactlyInAnyOrder(
            "films.team.actors",
            "films.team.crew",
            "videos.team.actors",
            "videos.team.crew"
         )
      }

   }
})

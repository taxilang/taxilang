package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.nulls.shouldNotBeNull

class TypeFieldLookupSpec : DescribeSpec({
   describe("looking up fields within object types") {
      it("should match if an object type contains a desscendant of a type")  {
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
            .getDescendantPathsOfType(schema.type("NamePart")).shouldContainExactlyInAnyOrder("name.firstName", "name.lastName")

         schema.objectType("Person")
            .getDescendantPathsOfType(schema.type("InkLevels"))
            .shouldBeEmpty()

         schema.objectType("Person")
            .getDescendantPathsOfType(schema.type("Person"))
            .shouldContainExactlyInAnyOrder("friends")
      }

   }
})

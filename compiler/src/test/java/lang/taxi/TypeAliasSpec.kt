package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class TypeAliasSpec : DescribeSpec({
   describe("type alias") {
      it("is scalar if the aliased type is scalar") {
         val schema = """
            model Person {
               name : Name inherits String
            }
            type alias HumanName as Name
            type alias Human as Person

         """.trimIndent()
            .compiled()
         schema.type("Human").isScalar.shouldBeFalse()
         schema.type("HumanName").isScalar.shouldBeTrue()
      }
   }
})

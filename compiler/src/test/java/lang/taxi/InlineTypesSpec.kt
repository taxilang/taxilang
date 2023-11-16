package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// See also: GrammarTest
class InlineTypesSpec : DescribeSpec({
   describe("Declarining inline types") {
      it("can reference an inline type before it has been processed") {
         val schema = """
            model Pet {
               name : Name
            }
            model Person {
               name : Name inherits String
            }

         """.compiled()
         schema.model("Pet").field("name").type.qualifiedName.shouldBe("Name")
         schema.model("Person").field("name").type.qualifiedName.shouldBe("Name")
      }
   }
}) {
}

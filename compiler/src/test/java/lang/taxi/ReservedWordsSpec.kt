package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull

class ReservedWordsSpec :  DescribeSpec({
   describe("using reserved words") {
      it("can use a reserved word as a field name") {
         """
            model Foo {
               `table` : String
            }
         """.compiled()
            .model("Foo").field("table").shouldNotBeNull()

      }
   }
})

package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class InlineTypeSpec : DescribeSpec({
   describe("declaring an inline type") {
      it("can define an inline type on an annotation") {
         val doc = """
               annotation Table {
                  table : TableName inherits String
                  schema: SchemaName inherits String

               }
         """.compiled()
         doc.annotation("Table").field("table").type.qualifiedName.shouldBe("TableName")
      }
   }
})

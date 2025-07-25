package lang.taxi

import io.kotest.core.spec.style.DescribeSpec

class ObjectSpec : DescribeSpec({
   describe("declaring objects") {
      // ORB-990
      it("can declare an object using a reserved word") {
         """
            model Entity {
              `type` : String
            }
         """.compiledWithQuery("""
            given { e: Entity = {
                `type` : "Person"
            }}
            find { Entity }
         """.trimIndent())
      }
   }
})

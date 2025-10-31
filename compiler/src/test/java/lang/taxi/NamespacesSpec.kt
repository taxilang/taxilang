package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class NamespacesSpec : DescribeSpec({
   describe("namespaces") {
      it("can declare a namespace using a reserved word") {
         val schema = """
            namespace com.type {
               type Hello inherits String
            }
            namespace com.bar {
               type World inherits com.type.Hello
            }
         """.compiled()
         schema.type("com.type.Hello")
            .shouldNotBeNull()
         val world = schema.type("com.bar.World")
            .shouldNotBeNull()
         world.inheritsFrom.single().qualifiedName.shouldBe("com.type.Hello")

      }
   }
})

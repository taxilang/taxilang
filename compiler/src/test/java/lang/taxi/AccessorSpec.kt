package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class AccessorSpec : DescribeSpec({
   fun schemaWithAccessor(accessor:String) = """
       type Name inherits String
            model Foo {
               name : Name $accessor
            }
   """.trimIndent()
   describe("jsonPath accessor") {
      it("should infer the return type from the field") {
        schemaWithAccessor("""by jsonPath("$.name")""").compiled()
            .objectType("Foo")
            .field("name")
            .accessor!!
            .returnType
            .qualifiedName.should.equal("Name")
      }
   }


   describe("xpath accessors") {
      it("should infer the return type from the field") {
         schemaWithAccessor("""by xpath("/name")""").compiled()
            .objectType("Foo")
            .field("name")
            .accessor!!
            .returnType
            .qualifiedName.should.equal("Name")
      }
   }

   describe("column accessors") {
      it("should infer the return type from the field") {
         schemaWithAccessor("""by column(1)""").compiled()
            .objectType("Foo")
            .field("name")
            .accessor!!
            .returnType
            .qualifiedName.should.equal("Name")
      }
   }
})

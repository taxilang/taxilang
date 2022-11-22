package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import lang.taxi.types.ArrayType

class ArraysSpec : DescribeSpec({
   describe("arrays") {
      it("should allow defining an array using short hand and long hand") {
         val model = """
            model Foo {
               a : String[]
               b: lang.taxi.Array<String>
            }
         """.compiled()
            .model("Foo")
         model.field("a").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<lang.taxi.String>")
         model.field("b").type.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<lang.taxi.String>")
      }
   }
})

package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.describeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.types.PrimitiveType

class CoalesceSpec : DescribeSpec({
  describe("Coalesce operator") {
     it("infers the correct type coalescing two strings") {
        """given { a: String = "hello" , b: String = "Foo" }
           |find { a ?: b }
           |
        """.trimMargin()
           .compiled()
           .queries.single()
           .returnType.shouldBe(PrimitiveType.STRING)
     }

     it("infers the correct type coalescing two INTs") {
        """given { a: Int = 1 , b: Int = 2 }
           |find { a ?: b }
           |
        """.trimMargin()
           .compiled()
           .queries.single()
           .returnType.shouldBe(PrimitiveType.INTEGER)
     }

     it("Sets return type to ANY when types mismatch") {
        """given { a: Int = 1 , b: String = "2" }
           |find { a ?: b }
           |
        """.trimMargin()
           .compiled()
           .queries.single()
           .returnType.shouldBe(PrimitiveType.ANY)
     }
     // This isn't possible, because the type checker only has references to the
     // declared types, not the values. The declared type of null is ANY
     xit("Sets uses the second type when the first value is null") {
        """given { a: Int = 1 , b: String = "2" }
           |find { null ?: b }
           |
        """.trimMargin()
           .compiled()
           .queries.single()
           .returnType.shouldBe(PrimitiveType.STRING)
     }
     it("Selects the correct type when the inputs are arrays") {
        """given { a: String[] = ["1"] , b: String[] = ["2"] }
           |find { a ?: b }
           |
        """.trimMargin()
           .compiled()
           .queries.single()
           .returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<lang.taxi.String>")
     }
  }
})

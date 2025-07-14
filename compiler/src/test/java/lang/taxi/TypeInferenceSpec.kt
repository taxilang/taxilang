package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.types.PrimitiveType

class TypeInferenceSpec : DescribeSpec({

   describe("Type inference") {
      describe("inference of coalesced types") {
         it("A(String) ?: A(String) == A(String)") {
            val (_,query) =  """
               type A inherits String
               type B inherits Int
            """.compiledWithQuery("""find { A ?: B }""")
            query.returnType
         }
         it("A(String) ?: A(String) == A(String)") {
            val (_,query) =  """
               type A inherits String
            """.compiledWithQuery("""find { A ?: A }""")
            query.returnType.qualifiedName.shouldBe("A")
         }
         it("A(String) ?: B(String) == String") {
            val (_,query) =  """
               type A inherits String
               type B inherits String
            """.compiledWithQuery("""find { A ?: B }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.STRING.qualifiedName)
         }

      }

   }
})

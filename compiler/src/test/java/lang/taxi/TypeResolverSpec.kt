package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.types.PrimitiveType

class TypeResolverSpec : DescribeSpec({

   describe("Type inference") {

      describe("inference of coalesced types") {

         it("A(String) ?: A(String) == A(String)") {
            val (_, query) = """
               type A inherits String
            """.compiledWithQuery("""find { A ?: A }""")
            query.returnType.qualifiedName.shouldBe("A")
         }

         it("A(String) ?: B(String) == String") {
            val (_, query) = """
               type A inherits String
               type B inherits String
            """.compiledWithQuery("""find { A ?: B }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.STRING.qualifiedName)
         }

         it("A(String) ?: B(Int) == Any") {
            val (_, query) = """
               type A inherits String
               type B inherits Int
            """.compiledWithQuery("""find { A ?: B }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.ANY.qualifiedName)
         }

         it("A[] ?: B[] where A, B inherit C => C[]") {
            val (_, query) = """
               type C inherits String
               type A inherits C
               type B inherits C
            """.compiledWithQuery("""find { A[] ?: B[] }""")
            query.returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<C>")
         }



         it("A[] ?: B[] where A = String, B = Int => Any[]") {
            val (_, query) = """
               type A inherits String
               type B inherits Int
            """.compiledWithQuery("""find { A[] ?: B[] }""")
            query.returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<lang.taxi.Any>")
         }

         it("A[] ?: B[] where A = String, B = String => String[]") {
            val (_, query) = """
               type A inherits String
               type B inherits String
            """.compiledWithQuery("""find { A[] ?: B[] }""")
            query.returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<lang.taxi.String>")
         }

         it("Map<String, Int> ?: Map<Int, Int> => Map<Any, Int>") {
            val (_, query) = """
            """.compiledWithQuery("""find { Map<String,Int> ?: Map<Int,Int> }""")
            query.returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Map<lang.taxi.Any,lang.taxi.Int>")
         }

         it("Map<String, Int> ?: String => Any") {
            val (_, query) = """
            """.compiledWithQuery("""find { Map<String,Int> ?: String }""")
            query.returnType.toQualifiedName().parameterizedName.shouldBe(PrimitiveType.ANY.qualifiedName)
         }

         // In theory, this should work, but doesn't currently compile
         xit("null ?: A(String) => A") {
            val (_, query) = """
               type A inherits String
            """.compiledWithQuery("""find { null ?: A }""")
            query.returnType.qualifiedName.shouldBe("A")
         }

         it("A(String)? ?: A(String) => A") {
            val (_, query) = """
               type A inherits String
            """.compiledWithQuery("""find { A? ?: A }""")
            query.returnType.qualifiedName.shouldBe("A")
         }

         it("A(String)? ?: B(String)? => String") {
            val (_, query) = """
               type A inherits String
               type B inherits String
            """.compiledWithQuery("""find { A? ?: B? }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.STRING.qualifiedName)
         }
      }
      describe("arithmetic type promotion") {

         it("Int + Int => Int") {
            val (_, query) = "".compiledWithQuery("""find { 1 + 2 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.INTEGER.qualifiedName)
         }

         // Long isn't a thing
         xit("Int + Long => Long") {
            val (_, query) = "".compiledWithQuery("""find { 1 + 2L }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.LONG.qualifiedName)
         }

         it("Int + Decimal => Decimal") {
            val (_, query) = "".compiledWithQuery("""find { 1 + 2.0 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.DECIMAL.qualifiedName)
         }
         it("Int ?: Decimal => Decimal") {
            val (_, query) = "".compiledWithQuery("""find { 1 ?: 2.0 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.DECIMAL.qualifiedName)
         }
         it("Decimal + Int => Decimal") {
            val (_, query) = "".compiledWithQuery("""find { 1.5 + 1 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.DECIMAL.qualifiedName)
         }

         it("Decimal + Decimal => Decimal") {
            val (_, query) = "".compiledWithQuery("""find { 1.5 + 2.0 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.DECIMAL.qualifiedName)
         }

         it("Int / Int => Decimal") {
            val (_, query) = "".compiledWithQuery("""find { 4 / 2 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.DECIMAL.qualifiedName)
         }

         it("Double / Int => Double") {
            val (_, query) = "".compiledWithQuery("""find { 4.0 / 2 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.DECIMAL.qualifiedName)
         }
      }

      describe("logical operators") {

         it("true && false => Boolean") {
            val (_, query) = "".compiledWithQuery("""find { true && false }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.BOOLEAN.qualifiedName)
         }

         it("true || false => Boolean") {
            val (_, query) = "".compiledWithQuery("""find { true || false }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.BOOLEAN.qualifiedName)
         }
      }

      describe("comparison operators") {

         it("1 > 0 => Boolean") {
            val (_, query) = "".compiledWithQuery("""find { 1 > 0 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.BOOLEAN.qualifiedName)
         }

         it("1 < 0 => Boolean") {
            val (_, query) = "".compiledWithQuery("""find { 1 < 0 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.BOOLEAN.qualifiedName)
         }

         it("1 == 1 => Boolean") {
            val (_, query) = "".compiledWithQuery("""find { 1 == 1 }""")
            query.returnType.qualifiedName.shouldBe(PrimitiveType.BOOLEAN.qualifiedName)
         }
      }
   }
})

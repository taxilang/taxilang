package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class CompilerSymbolsSpec : DescribeSpec({

   describe("Can list names of symbols without compiling") {
      val compiler = Compiler("""
         namespace com.foo

         // This can't compile, as it references symbols (Name) that don't exist
         // But we need to be able to get the names of symbols it declares
         query Foo {
            given { name : Name = "Marty" }
            find { Name }
         }
      """.trimIndent())
      val queries = compiler.tokens.namedQueries
      queries.shouldHaveSize(1)
      queries.single().first.shouldBe("com.foo.Foo")
   }
})

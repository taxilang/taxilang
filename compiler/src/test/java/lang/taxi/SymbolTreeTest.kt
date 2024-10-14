package lang.taxi

import arrow.core.Either
import arrow.core.right
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.services.Service
import lang.taxi.sources.SourceCode
import lang.taxi.types.EnumType
import lang.taxi.types.EnumValue
import lang.taxi.types.NamespaceToken
import lang.taxi.types.ObjectType
import org.junit.Test
import org.mockito.kotlin.mock

class SymbolTreeTest {
   @Test
   fun `can register and find non-namespaced entries in a symbol tree`() {
      val compiler = Compiler(
         """
         model Person {
            firstName : FirstName inherits String
         }
      """
      )
      compiler.compile()
      val symbolTree = compiler.typeSystem.symbolTree
      val symbol = symbolTree.getSymbol("Person")
      symbol.isRight().shouldBeTrue()
      symbol.getOrNull()!!.single()
   }

   @Test
   fun `can get enum reference`() {
      val symbolTree = """namespace com.foo.bar

         enum Country {
            NZ,
            UK,
            AUS
         }""".symbolTree()
      symbolTree.getSymbol("Country.NZ", "com.foo.bar")
         .getOrNull()!!
         .shouldHaveSize(2)

      symbolTree.getSymbol("com.foo.bar.Country.NZ", "com.foo.bar")
         .getOrNull()!!
         .shouldHaveSize(2).let { matched ->
            val list = matched.toList()
            list[0].text.shouldBe("com.foo.bar.Country")
            list[0].value.shouldBeInstanceOf<EnumType>()
            list[1].text.shouldBe("NZ")
            list[1].value.shouldBeInstanceOf<EnumValue>()
         }

      symbolTree.getSymbol("Country.NZ", currentNamespace = "com.foo.bar")
         .getOrNull()!!
         .shouldHaveSize(2)
         .let { matched ->
            val list = matched.toList()
            list[0].text.shouldBe("Country")
            list[0].value.shouldBeInstanceOf<EnumType>()
            list[1].text.shouldBe("NZ")
            list[1].value.shouldBeInstanceOf<EnumValue>()
         }

      symbolTree.getSymbol(
         "Country.NZ",
         currentNamespace = "com.somethingElse",
         imports = listOf("com.foo.bar.Country")
      )
         .getOrNull()!!
         .shouldHaveSize(2).let { matched ->
            val list = matched.toList()
            list[0].text.shouldBe("Country")
            list[0].value.shouldBeInstanceOf<EnumType>()
            list[1].text.shouldBe("NZ")
            list[1].value.shouldBeInstanceOf<EnumValue>()
         }
   }

   @Test
   fun `can get a model by name`() {
      """namespace com.foo.bar

         enum Country {
            NZ,
            UK,
            AUS
         }
         model Person {
            firstName : FirstName inherits String
            country : Country
         }"""
         .symbolTree()
         .getSymbol("com.foo.bar.Person")
         .getOrNull()!!
         .single()
         .shouldBeInstanceOf<ObjectType>()
   }

   @Test
   fun `can get attribute of enum value by name`() {
      """
         namespace com.foo.bar

         model ErrorDetails {
            code : ErrorCode inherits Int
            message : ErrorMessage inherits String
         }
         enum Errors<ErrorDetails> {
            BadRequest({ code : 400, message : 'Bad Request' }),
            Unauthorized({ code : 401, message : 'Unauthorized' })
         }
      """.symbolTree()
         .getSymbol("com.foo.bar.Errors.BadRequest.code")
         .getOrNull()!!
         .single()
         .shouldBeInstanceOf<ObjectType>()
   }

   @Test
   fun `extension function applied against an unambiguous member`() {
      listOf(
         """namespace com.films
            |
            |type FilmTitle inherits String
         """.trimMargin(),
         """namespace com.funcs
            |
            |declare extension function shouty(String):String
         """.trimMargin()
      ).symbolTree()
         .getSymbol(
            // Neither FilmTitle nor shouty()
            // are in the current namespace,
            // making this *particularly* challenging.
            // However, FilmTitle should resolve against com.films.FilmTitle
            // as it's unambiguous.
            // shouty() should resolve against FilmTitle because it's a registered
            // extension function
            requestedName = "FilmTitle.shouty",
            currentNamespace = "com.foo.models"
         )

   }

   @Test
   fun `extension function applied against an full qualified named member`() {
      listOf(
         """namespace com.films
            |
            |type FilmTitle inherits String
         """.trimMargin(),
         """namespace com.funcs
            |
            |declare extension function shouty(String):String
         """.trimMargin()
      ).symbolTree()
         .getSymbol(
            // FilmTitle is fully qualified, but needs to
            // resolve shouty() as a registered member
            requestedName = "com.films.FilmTitle.shouty()",
            currentNamespace = "com.foo.models"
         )
   }

   @Test
   fun `extension function applied against an imported member`() {
      listOf(
         """namespace com.films
            |
            |type FilmTitle inherits String
         """.trimMargin(),
         """namespace com.documentaries
            |
            |type FilmTitle inherits String
            |type Price inherits Decimal
         """.trimMargin(),
         """namespace com.funcs
            |
            |declare extension function shouty(String):String
         """.trimMargin()
      ).symbolTree()
         .getSymbol(
            // FilmTitle is fully qualified, but needs to
            // resolve shouty() as a registered member
            requestedName = "FilmTitle.shouty()",
            currentNamespace = "com.foo.models",
            imports = listOf("com.documentaries.Price", "com.films.FilmTitle")
         )
   }

   @Test
   fun `can get a namespace by name`() {
      """ namespace com.foo.bar

         enum Country {
            NZ,
            UK,
            AUS
         }
         model Person {
            firstName : FirstName inherits String
            country : Country
         }"""
         .symbolTree()
         .getSymbol("com.foo")
         .shouldBeInstanceOf<NamespaceToken>()
   }


   @Test
   fun `can get a service by name`() {
      val fragment = """namespace com.foo

         model Film {}
         service FilmsApi {
            operation getFilms():Film
         }
      """.trimIndent()
         .symbolTree()
         .getSymbol("com.foo.FilmsApi")
         .getOrNull()
         .shouldNotBeNull()
         .single()
      fragment.value.shouldBeInstanceOf<Service>()
   }


}

private fun String.symbolTree(): SymbolTree {
   val compiler = Compiler(this)
   compiler.compile()
   return compiler.typeSystem.symbolTree
}
// Convenience method
private fun SymbolTree.getSymbol(requestedName: String, currentNamespace: String = "", imports: List<String> = emptyList()):Either<List<CompilationError>, List<TextFragmentWithCompiledToken>> {
   return this.getSymbol(
      requestedName, currentNamespace, imports, context = mock {  }, tokenProcessor = mock {}
   )
}
private fun List<String>.symbolTree(): SymbolTree {
   val compiler = Compiler(this.mapIndexed { idx, src -> SourceCode("SourceFile$idx", src) })
   compiler.compile()
   return compiler.typeSystem.symbolTree
}

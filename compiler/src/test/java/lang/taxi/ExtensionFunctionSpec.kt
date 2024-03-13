package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import lang.taxi.expressions.ExtensionFunctionExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.PrimitiveType

class ExtensionFunctionSpec : DescribeSpec({
   describe("extension functions") {
      it("can compile an extension function") {
         val function = """declare extension function toUpper(String):String"""
            .compiled()
            .function("toUpper")
         function.isExtension.shouldBeTrue()
         function.receiverType!!.qualifiedName.shouldBe(PrimitiveType.STRING.qualifiedName)
      }
      it("is invalid to declare an extension function that doesn't return anything") {
         """declare extension function toUpper(String)"""
            .validated()
            .shouldHaveSize(1) // turns out, the grammar rejects this, so not validating the message
      }
      it("is invalid to declare an extension function that doesn't take any args") {
         """declare extension function toUpper():String"""
            .validated()
            .shouldContainMessage("Extension functions must have at least one parameter, as this defines the type the function can operate against")
      }
      it("is valid to call an extension function on it's receiver") {
         val (doc, query) = """declare extension function toUpper(String):String"""
            .compiledWithQuery("""find { "hello".toUpper() }""")
         val expression = query.discoveryType!!.expression.shouldBeInstanceOf<ExtensionFunctionExpression>()
         expression.functionExpression.function.qualifiedName.shouldBe("toUpper")
         expression.receiverValue.shouldBeInstanceOf<LiteralExpression>()
      }
      it("is valid to call an extension function on a declared fact") {
         val (doc, query) = """declare extension function toUpper(String):String"""
            .compiledWithQuery(
               """
               given { message:String = "hello" }
               find { message.toUpper() }""".trimIndent()
            )
         val expression = query.discoveryType!!.expression.shouldBeInstanceOf<ExtensionFunctionExpression>()
         expression.receiverValue.shouldBeInstanceOf<ArgumentSelector>()
         expression.functionExpression.function.qualifiedName.shouldBe("toUpper")
      }
      it("is invalid to call an extension function on a declared fact of the wrong type") {
         val error = """declare extension function toUpper(String):String"""
            .compiledWithQueryProducingCompilationException(
               """
               given { message:Int = 123 }
               find { message.toUpper() }""".trimIndent()
            )
         error.errors.shouldContainMessage("Type mismatch. Type of lang.taxi.Int is not assignable to type lang.taxi.String")
      }
      it("is invalid to call an extension function on the wrong type") {
         val error = """
            declare extension function increment(Int):Int
            """.trimIndent()
            .compiledWithQueryProducingCompilationException("""find { "hello".increment() }""")
         error.errors.shouldContainMessage("Type mismatch. Type of lang.taxi.Int is not assignable to type lang.taxi.String")
      }
      it("is valid to call an chain extension functions on it's receiver") {
         val (doc, query) = """
            declare extension function toUpper(String):String
            declare extension function toLower(String):String
            """.trimIndent()
            .compiledWithQuery("""find { "hello".toUpper().toLower() }""")
         val expression = query.discoveryType!!.expression.shouldBeInstanceOf<ExtensionFunctionExpression>()
         expression.receiverValue.shouldBeInstanceOf<ExtensionFunctionExpression>()
      }
      it("is invalid to call an extension function on a type that's not it's receiver") {
         val error = """declare extension function toUpper(String):String"""
            .compiledWithQueryProducingCompilationException("""find { 123.toUpper() }""")
         error.errors.shouldContainMessage("Type mismatch. Type of lang.taxi.String is not assignable to type lang.taxi.Int")
      }


      it("is valid to use an extension function to filter a collection") {
         val (schema, query) = """
            model Movie {
               title : Title inherits String
            }
            service Movies {
               operation findAll():Movie[]
            }
         """.compiledWithQuery("""find { Movie[].filter( (Title) -> Title == "Jaws" ) }""")
         val expression = query.discoveryType?.expression.shouldBeInstanceOf<ExtensionFunctionExpression>()
         expression.receiverValue.shouldBeInstanceOf<TypeExpression>()
            .type.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<Movie>")
      }
      it("is valid to use an extension function to filter a stream") {
         val (schema, query) = """
            model Movie {
               title : Title inherits String
            }
            service Movies {
               operation findAll():Stream<Movie>
            }
         """.compiledWithQuery("""stream { Movie.filterEach( (Title) -> Title == "Jaws" ) }""")
         val expression = query.discoveryType?.expression.shouldBeInstanceOf<ExtensionFunctionExpression>()
         val receiver = expression.receiverValue
            .shouldBeInstanceOf<TypeExpression>()

         receiver.type.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Stream<Movie>")
         // Bug: We were parsing the (Title) -> Title == "Jaws" as a constraint.
         receiver.constraints.shouldBeEmpty()

         // Even though we declared the filterEach expression on a Stream<Movie>, the input should be Movie
         // Assert that the type was unwrapped:
         expression.functionExpression.inputs[0].shouldBeInstanceOf<TypeExpression>()
            .type.toQualifiedName().parameterizedName.shouldBe("Movie")
         expression.returnType.toQualifiedName().parameterizedName.shouldBe("Movie")

         expression.functionExpression.function.qualifiedName.shouldBe("taxi.stdlib.filterEach")
      }
   }
})

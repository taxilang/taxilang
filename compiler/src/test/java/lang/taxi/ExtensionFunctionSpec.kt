package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import lang.taxi.expressions.ExtensionFunctionExpression
import lang.taxi.expressions.LambdaExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TypeReference

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
         error.errors.shouldContainMessage("Type mismatch. Type of lang.taxi.String is not assignable to type lang.taxi.Int")
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
         error.errors.shouldContainMessage("Type mismatch. Type of lang.taxi.Int is not assignable to type lang.taxi.String")
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

      it("is valid to declare an extension function against a type") {
         """
         declare extension function <T> lookupEnumByName(enumType: lang.taxi.Type<T>, enumName: String): T
         model ErrorDetails {
            code : ErrorCode inherits Int
            message : ErrorMessage inherits String
         }
         enum Errors<ErrorDetails> {
            BadRequest({ code : 400, message : 'Bad Request' }),
            Unauthorized({ code : 401, message : 'Unauthorized' })
         }
         """.compiledWithQuery(
            """
            given { errorResponse : String = 'BadRequest' }
            find {
               error : ErrorDetails by Errors.lookupEnumByName(errorResponse)
            }
            """.trimIndent()
         )
      }
      it("can compile function with type reference") {
         val f ="""declare extension function <T> lookupEnumByName(enumType: lang.taxi.Type<T>): T"""
            .compiled()
            .function("lookupEnumByName")
         f.parameters.single().type.shouldBeInstanceOf<TypeReference>()
      }
      it("can call an expression function against an array") {
         val expression = """
            declare extension function <T> filter(collection:T[], callback: (T) -> Boolean):T[]
            model Film {
               title : Title inherits String
               minAge : Age inherits Int
            }
            type AllowedFilms by (Film[], viewerAge:Age) -> Film[].filter( (Film) -> Film::Age > viewerAge )
               .convert(Title)
         """.compiled()
            .type("AllowedFilms")
            .asA<ObjectType>()
            .expression!!
         expression.shouldBeInstanceOf<LambdaExpression>()
            .expression.shouldBeInstanceOf<ExtensionFunctionExpression>() // this is the .convert() part...
            .receiverValue.shouldBeInstanceOf<ExtensionFunctionExpression>() // this is the .filter() part..
            .receiverValue.shouldBeInstanceOf<TypeExpression>() // this is the Film[] party
            .type.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<Film>")
      }

      it("is valid to call an extesnion function on a named parameter") {
         val (schema,query) = """
            declare extension function <T> filter(collection:T[], callback: (T) -> Boolean):T[]
            model Movie {
               cast : Actor[]
            }
            model Actor {
               name : Name inherits String
            }
         """.compiledWithQuery("""
            find { Movie } as (cast:Actor[]) -> {
               starring : Actor[] by cast.filter( (Name) -> Name == 'Mark' )
            }
         """.trimIndent())
      }

      it("is valid to call an extension function against a property accessed") {
val (schema,query) = """
   model User {
      username: String
      roles: String[]
   }
""".compiledWithQuery("""
given {
   user: User = {
      username: 'AdminUser',
      roles: ['USER', 'EDITOR', 'ADMIN']
   }
}
find {
   username: user.username
   isAdmin: Boolean = user.roles.contains("ADMIN")
   isGuest: Boolean = user.roles.contains("GUEST")
}
""".trimIndent())
      }


   }
})

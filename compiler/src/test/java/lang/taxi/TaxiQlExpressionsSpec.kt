package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.expressions.FieldReferenceExpression
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.ObjectType
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class TaxiQlExpressionsSpec : DescribeSpec({
   describe("expressions constraints on types in queries") {
      val src = """
               model Person {
                  name : PersonName inherits String
               }
               model Pet {
                  name : PetName inherits String
               }
            """

      it("is possible to define a field with a no-arg function expression") {
         val schemaSrc = src + """declare function buyPet():Pet"""
         val querySrc = """find { Person } as {
               | name : PersonName
               | pet : buyPet()
               |}
            """.trimMargin()
         val (schema, query) = (schemaSrc).compiledWithQuery(querySrc)
         val field = query.projectedObjectType.field("pet")
         field.type.qualifiedName.should.equal("Pet")
         val accessor = field.accessor!!.shouldBeInstanceOf<FunctionAccessor>()
         accessor.function.qualifiedName.shouldBe("buyPet")
      }
      it("is possible to define a field with a function expression") {
         val schemaSrc = src + """declare function calculatePetAge(Int,Int):Int"""
         val querySrc = """find { Person } as {
               | name : PersonName
               | pet : calculatePetAge(2,3)
               |}
            """.trimMargin()
         val (schema, query) = (schemaSrc).compiledWithQuery(querySrc)
         val field = query.projectedObjectType.field("pet")
         field.type.qualifiedName.should.equal("lang.taxi.Int")
         val accessor = field.accessor!!.shouldBeInstanceOf<FunctionExpression>()
         accessor.inputs.should.have.size(2)
         accessor.inputs[0].asA<LiteralAccessor>().value.shouldBe(2)
         accessor.inputs[1].asA<LiteralAccessor>().value.shouldBe(3)
      }
      it("is possible to declare an expression and infer the type") {
         val schemaSrc = src + """declare function petName():String"""
         val querySrc = """find { Person } as {
               | petName : "" + petName()
               |}
            """.trimMargin()
         val (schema, query) = (schemaSrc).compiledWithQuery(querySrc)
         query.projectedObjectType.field("petName").type.qualifiedName.should.equal("lang.taxi.String")
      }
      it("is possible to declare an expression on a discovery type") {
         val (schema, query) = src.compiledWithQuery("find { Person(PersonName == 'Jimmy') }")
         query.typesToFind.single().constraints.shouldHaveSize(1)
      }

      it("is possible to declare an expression on a projected return type") {
         val (schema, query) = src.compiledWithQuery("find { Person(true == true) }")
      }
   }
   describe("filtering using functions") {
      val src = """model Person {
           id : PersonId inherits Int
          }
          model Movie {
            title : MovieTitle inherits String
            cast : Person[]
         }
          service PersonService {
            operation getAll():Movie[]
         }"""
      it("should be possible to query filtering on a field by name ") {
         val (_, query) = src.compiledWithQuery(
            """find { Movie[] } as {
            |cast : Person[]
            |name : single(this.cast, (Person) -> PersonId == 1 )
            |}[]
         """.trimMargin()
         )
         val accessor = query.projectedObjectType
            .field("name")
            .accessor!! as FunctionExpression
         val input = accessor.inputs[0].asA<FieldReferenceExpression>()
         input.path.shouldBe("cast")
      }
      it("should be possible to query filtering on a field by type") {
         val (_, query) = src.compiledWithQuery(
            """find { Movie[] } as {
            |cast : Person[]
            |name : single(Person[], (Person) -> PersonId == 1 )
            |}[]
         """.trimMargin()
         )
         val accessor = query.projectedObjectType
            .field("name")
            .accessor!! as FunctionExpression
         val input = accessor.inputs[0].asA<TypeExpression>()
         input.type.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<Person>")
      }

      it("generates an error when filtering on a field that doesn't exist") {
         val error = assertFailsWith<CompilationException> {
            val (_, query) = src.compiledWithQuery(
               """find { Movie[] } as {
            |cast : Person[]
            |// Below: this.actors is not a real field
            |name : single(this.actors, (Person) -> PersonId == 1 )
            |}[]
         """.trimMargin()
            )
         }
         error.message!!.shouldContain("Field actors does not exist")
      }


      it("is possible to use nested dot-syntax to navigate fields") {
         val (_, query) = src.compiledWithQuery(
            """find { Movie[] } as {
            |cast : Person[]
            |movie : Movie
            |// This is the test:  using multiple dots -- this.movie.title
            |movieTitle : this.movie.title
            |}[]
         """.trimMargin()
         )
         val field = query.projectedObjectType.field("movieTitle")
         val expression = field.accessor as FieldReferenceExpression
         expression.fieldNames.should.have.elements("movie", "title")
      }
   }
})

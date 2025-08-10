package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.CastExpression
import lang.taxi.expressions.LiteralArray
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.ObjectLiteralExpression
import lang.taxi.types.PrimitiveType

class LiteralsSpec : DescribeSpec({
   describe("literal expressions") {
      it("should parse a literal number") {
         val expression = "1".compiled()
            .expressions.single()
            .shouldBeInstanceOf<LiteralExpression>()
         expression.value.shouldBe(1)
         expression.returnType.shouldBe(PrimitiveType.INTEGER)
      }

      it("should parse a literal string") {
         val expression = """"hello"""".compiled()
            .expressions.single()
            .shouldBeInstanceOf<LiteralExpression>()
         expression.value.shouldBe("hello")
         expression.returnType.shouldBe(PrimitiveType.STRING)
      }

      it("should parse a literal boolean") {
         val expression = "true".compiled()
            .expressions.single()
            .shouldBeInstanceOf<LiteralExpression>()
         expression.value.shouldBe(true)
         expression.returnType.shouldBe(PrimitiveType.BOOLEAN)
      }
      it("should parse an object") {
         val expression = """{ name : "Jimmy" }""".compiled()
            .expressions.single()
            .shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = expression.expressionMap
         map["name"]!!.returnType.shouldBe(PrimitiveType.STRING)
          (map["name"]!! as LiteralExpression).value.shouldBe("Jimmy")
      }
      it("should parse an object using string double-quoted names") {
         val expression = """{ "name" : "Jimmy" }""".compiled()
            .expressions.single()
            .shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = expression.expressionMap
         map["name"]!!.returnType.shouldBe(PrimitiveType.STRING)
         (map["name"]!! as LiteralExpression).value.shouldBe("Jimmy")
      }
      it("should parse an object using string single-quoted names") {
         val expression = """{ 'name' : "Jimmy" }""".compiled()
            .expressions.single()
            .shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = expression.expressionMap
         map["name"]!!.returnType.shouldBe(PrimitiveType.STRING)
         (map["name"]!! as LiteralExpression).value.shouldBe("Jimmy")
      }
      it("is valid to declare an empty array") {
         val expression = """{ 'names' : [] }""".compiled()
            .expressions.single()
            .shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = expression.expressionMap
         map["names"]!!.returnType.qualifiedName.shouldBe("lang.taxi.Array")
         (map["names"]!! as LiteralArray).members.shouldBeEmpty()
      }
      it("is valid to declare a single element string array") {
         val expression = """{ 'names' : ['jimmy'] }""".compiled()
            .expressions.single()
            .shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = expression.expressionMap
         map["names"]!!.returnType.qualifiedName.shouldBe("lang.taxi.Array")
         (map["names"]!! as LiteralArray).members.shouldHaveSize(1)
         (map["names"]!! as LiteralArray).members[0].shouldBeInstanceOf<LiteralExpression>()
            .value.shouldBe("jimmy")
      }
      it("is valid to declare a string array") {
         val expression = """{ 'names' : ['jimmy','schmitt'] }""".compiled()
            .expressions.single()
            .shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = expression.expressionMap
         map["names"]!!.returnType.qualifiedName.shouldBe("lang.taxi.Array")
         (map["names"]!! as LiteralArray).members.shouldHaveSize(2)
         (map["names"]!! as LiteralArray).members[0].shouldBeInstanceOf<LiteralExpression>()
            .value.shouldBe("jimmy")
         (map["names"]!! as LiteralArray).members[1].shouldBeInstanceOf<LiteralExpression>()
            .value.shouldBe("schmitt")
      }
      it("is valid to declare a number array") {
         val expression = """{ 'names' : [1,2] }""".compiled()
            .expressions.single()
            .shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = expression.expressionMap
         (map["names"]!! as LiteralArray).members.shouldHaveSize(2)
         (map["names"]!! as LiteralArray).members[0].shouldBeInstanceOf<LiteralExpression>()
            .value.shouldBe(1)
         (map["names"]!! as LiteralArray).members[1].shouldBeInstanceOf<LiteralExpression>()
            .value.shouldBe(2)
      }
      it("is valid to declare an object array") {
         val expression = """{ people : [{ name: "jimmy" }, { "name" : "jack" } ]}""".compiled()
            .expressions.single()
            .shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = expression.expressionMap
         val array = map["people"]!!.shouldBeInstanceOf<LiteralArray>()
         array.members[0].shouldBeInstanceOf<ObjectLiteralExpression>()
         array.members[1].shouldBeInstanceOf<ObjectLiteralExpression>()
      }
      it("should assign nested types based on target type") {
         val cast = """
            model Film {
               id : FilmId inherits Int
               title : Title inherits String
            }

            (Film) { id : 1 , title : "Star Wars" }
         """.compiled()
            .expressions.single()
            .shouldBeInstanceOf<CastExpression>()
         val objectLiteralExpression = cast.expression.shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = objectLiteralExpression.expressionMap
         map["id"]!!.returnType.qualifiedName.shouldBe("FilmId")
          (map["id"]!! as LiteralExpression).value.shouldBe(1)
         map["title"]!!.returnType.qualifiedName.shouldBe("Title")
          (map["title"]!! as LiteralExpression).value.shouldBe("Star Wars")
      }

      it("should infer type of array from members") {
         """[ 'a' , 'b']""".compiled().expressions.single()
            .returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<lang.taxi.String>")

         """[ 1, 2 ]""".compiled().expressions.single()
            .returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<lang.taxi.Int>")

         // Mixed arrays become Any
         """[ "a", 1 ]""".compiled().expressions.single()
            .returnType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<lang.taxi.Any>")

      }

      it("should assign nested types based on target type within an array") {
         val cast = """
            model Film {
               id : FilmId inherits Int
               title : Title inherits String
            }

            (Film[]) [{ id : 1 , title : "Star Wars" }]
         """.compiled()
            .expressions.single()
            .shouldBeInstanceOf<CastExpression>()
         val literalArray = cast.expression.shouldBeInstanceOf<LiteralArray>()

         val objectLiteralExpression =literalArray.members.single().shouldBeInstanceOf<ObjectLiteralExpression>()
         val map = objectLiteralExpression.expressionMap
         map["id"]!!.returnType.qualifiedName.shouldBe("FilmId")
          (map["id"]!! as LiteralExpression).value.shouldBe(1)
         map["title"]!!.returnType.qualifiedName.shouldBe("Title")
          (map["title"]!! as LiteralExpression).value.shouldBe("Star Wars")
      }
      it("should fail to assign incompatible values in literal types") {
         val errors = """
            model Film {
               id : FilmId inherits Int
               title : Title inherits String
            }

            (Film) { id : "foo" , title : "Star Wars" }
         """.validated()
         errors.shouldNotBeEmpty()
         errors.shouldContainMessage("Type mismatch. Type of lang.taxi.String is not assignable to type FilmId")
      }
   }


})

fun LiteralExpression.asLiteralMap():Map<String,LiteralExpression> {
   return this.literal.value as Map<String, LiteralExpression>
}

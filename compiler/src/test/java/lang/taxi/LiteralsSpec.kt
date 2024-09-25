package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.CastExpression
import lang.taxi.expressions.LiteralArray
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.ObjectExpression
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
            .shouldBeInstanceOf<ObjectExpression>()
         val map = expression.expressionMap
         map["name"]!!.returnType.shouldBe(PrimitiveType.STRING)
          (map["name"]!! as LiteralExpression).value.shouldBe("Jimmy")
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
         val objectExpression = cast.expression.shouldBeInstanceOf<ObjectExpression>()
         val map = objectExpression.expressionMap
         map["id"]!!.returnType.qualifiedName.shouldBe("FilmId")
          (map["id"]!! as LiteralExpression).value.shouldBe(1)
         map["title"]!!.returnType.qualifiedName.shouldBe("Title")
          (map["title"]!! as LiteralExpression).value.shouldBe("Star Wars")
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

         val objectExpression =literalArray.members.single().shouldBeInstanceOf<ObjectExpression>()
         val map = objectExpression.expressionMap
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

package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.FieldReferenceExpression
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.functions.stdlib.Convert
import lang.taxi.functions.stdlib.StdLib

class ReflectionSpec : DescribeSpec({
   describe("Using type references") {
      it("should compile a function with a type reference") {
         val convertFunction = Convert.taxi.compiled()
            .function("convert")
         convertFunction.typeArguments!!.shouldHaveSize(1)
         convertFunction.parameters.shouldHaveSize(2)
      }

      it("should compile a model using a function that has a type reference") {
         val field = """
${Convert.taxi}

model HitFilm inherits Film
model Film {
   title : FilmTitle inherits String
   hit : convert(this.title, HitFilm)
}
         """.trimIndent().compiled()
            .model("Film")
            .field("hit")
         field.accessor.shouldNotBeNull()
         field.type.qualifiedName.shouldBe("HitFilm")
         val functionAccessor = field.accessor.shouldBeInstanceOf<FunctionExpression>()
         functionAccessor.returnType.qualifiedName.shouldBe("HitFilm")
         functionAccessor.inputs[0].shouldBeInstanceOf<FieldReferenceExpression>()

         // NOTE: I'm surprised that this is resolving to TypeExpression, and not TypeReference<T>.
         // If this becomes a problem later on, it's possible this test was wrong.
         val typeExpression = functionAccessor.inputs[1].shouldBeInstanceOf<TypeExpression>()
         typeExpression.type.qualifiedName.shouldBe("HitFilm")
      }

      it("should resolve functions that infer type from assignment") {
         val fieldType = """
            // Unlike convert, cast (a dummy function) doesn't take a
            // type as an argument, so should resolve via assignment site
            declare function <T> cast(input:Any):T
            model HitFilm inherits Film
            model Film {
               title : FilmTitle inherits String
               hit : HitFilm = cast(this.title)
            }
         """.compiled()
            .model("Film")
            .field("hit")
            .type
         fieldType.qualifiedName.shouldBe("HitFilm")
      }
      it("should give compilation error when functions that infer return type from assignment if no assignment type is delared") {
         """
            // Unlike convert, cast (a dummy function) doesn't take a
            // type as an argument, so should resolve via assignment site
            declare function <T> cast(input:Any):T
            model HitFilm inherits Film
            model Film {
               title : FilmTitle inherits String
               hit :cast(this.title) // this is an error, as cast requires an assignment type
            }
         """.validated()
            .shouldContainMessage("Function cast expects 1 parameters, but none were provided")
      }

      it("should not compile a model using a function that has a type reference where return type is not compatibile") {
         val messages = """
${Convert.taxi}

model HitFilm inherits Film
model Film {
   title : FilmTitle inherits String
   hit : String = convert(this.title, HitFilm)
}
         """.validated()
         messages.shouldContainMessage("Type mismatch. Type of HitFilm is not assignable to type lang.taxi.String")
      }
   }
})

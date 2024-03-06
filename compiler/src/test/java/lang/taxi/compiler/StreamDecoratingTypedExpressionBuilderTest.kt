package lang.taxi.compiler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import lang.taxi.compiled
import lang.taxi.compiledWithQuery
import lang.taxi.expressions.ExtensionFunctionExpression
import lang.taxi.expressions.LambdaExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.query.DiscoveryType
import lang.taxi.query.TaxiQlQuery
import lang.taxi.types.ObjectType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StreamDecoratingTypedExpressionBuilderTest : DescribeSpec({

   describe("stream decorating typed expression builder") {
      fun queryOf(query: String): DiscoveryType {
         return """
         model Film {
            title : Title inherits String
            spanishTitle : SpanishTitle inherits String
         }
      """.compiledWithQuery(query)
            .second.discoveryType!!
      }
      it("rewrites streamed type") {
         queryOf("stream { Film }")
            .expression.shouldBeInstanceOf<TypeExpression>()
            .type.toQualifiedName().parameterizedName
            .shouldBe("lang.taxi.Stream<Film>")
      }

      it("rewrites streamed array type") {
         queryOf("stream { Film[] }")
            .expression.shouldBeInstanceOf<TypeExpression>()
            .type.toQualifiedName().parameterizedName
            .shouldBe("lang.taxi.Stream<lang.taxi.Array<Film>>")
      }

      it("does not rewrite find type") {
         queryOf("find { Film }")
            .expression.shouldBeInstanceOf<TypeExpression>()
            .type.toQualifiedName().parameterizedName
            .shouldBe("Film")
      }

      it("does not rewrite find array type") {
         queryOf("find { Film[] }")
            .expression.shouldBeInstanceOf<TypeExpression>()
            .type.toQualifiedName().parameterizedName
            .shouldBe("Film")
      }

      it("only rewrites the primary type in a stream statement with filter") {
         val expression = queryOf("""stream { Film.filterEach( (Title) -> Title == "Jaws" ) }""")
            .expression
            .shouldBeInstanceOf<ExtensionFunctionExpression>()
         expression.receiverValue
            .shouldBeInstanceOf<TypeExpression>()
            .type.toQualifiedName().parameterizedName
            .shouldBe("lang.taxi.Stream<Film>")
         val functionExpression = expression.functionExpression

         functionExpression.function.inputs[0]
            .shouldBeInstanceOf<TypeExpression>()
            .type.toQualifiedName().parameterizedName
            .shouldBe("lang.taxi.Stream<Film>")

         // Make sure the Title == Jaws wasn't modified
         val lambda = functionExpression.function.inputs[1].shouldBeInstanceOf<LambdaExpression>()
         lambda.inputs.single().shouldBeInstanceOf<ObjectType>()
            .toQualifiedName().parameterizedName.shouldBe("Title")

         val operatorExpression = lambda.expression.shouldBeInstanceOf<OperatorExpression>()
         operatorExpression.lhs
            .shouldBeInstanceOf<TypeExpression>()
            .type.toQualifiedName().parameterizedName
            .shouldBe("Title")
//         expression.type.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Stream<Film>")
//         expression.constraints.single()

      }


   }

})

package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.accessors.Accessor
import lang.taxi.expressions.Expression
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.NegatedExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.ObjectType

class NegateSpec : DescribeSpec({
   describe("negate operator") {
      it("can use a negate operator") {
         val (taxi,query) = """
            function alwaysFalse():Boolean -> false
         """.trimIndent().compiledWithQuery(
            """ given { b: Boolean = true }
               |find {
               | justB : Boolean = b
               | notB: Boolean = !b
               | notTrue : Boolean = !true
               | notFalse : Boolean = !false
               | withFunction : Boolean = !alwaysFalse()
               | withExpression : Boolean = !(2 == 3)
               |}
            """.trimMargin()
         )
         val type = query.discoveryType!!.type as ObjectType

         type.field("notB").accessor.shouldBeNegated<ArgumentSelector>()
         type.field("notTrue").accessor.shouldBeNegated<LiteralExpression>()
         type.field("withFunction").accessor.shouldBeNegated<FunctionExpression>()
         type.field("withExpression").accessor.shouldBeNegated<OperatorExpression>()
      }
      it("cannot use negate on non-boolean types") {
         val exception = "".compiledWithQueryProducingCompilationException("""
            find {
              a: String = !""
            }
         """.trimIndent())
         exception.errors.shouldContainMessage("Cannot use ! operator against type lang.taxi.String - ! is supported against Boolean types only")
      }
   }
})

inline fun <reified T : Any> Accessor?.shouldBeNegated():T {
   return this.shouldBeInstanceOf<NegatedExpression>()
      .expression.shouldBeInstanceOf<T>()
}

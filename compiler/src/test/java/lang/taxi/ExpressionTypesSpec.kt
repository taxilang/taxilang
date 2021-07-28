package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.types.FormulaOperator
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ExpressionTypesSpec : Spek({
   describe("Expression types") {
      it("should compile an expression type") {
         val doc = """
            type Height inherits Int
            type Width inherits Int
            type Area inherits Int by Height * Width
         """.compiled()
         val area = doc.objectType("Area").expression as OperatorExpression
         (area.lhs as TypeExpression).let {
            it.type.should.equal(doc.type("Height"))
         }
         (area.rhs as TypeExpression).let {
            it.type.should.equal(doc.type("Width"))
         }
         area.operator.should.equal(FormulaOperator.Multiply)
      }
      it("should apply brackets before unbracketed") {
         val doc = """
            type Height inherits Int
            type Width inherits Int
            type Bracketed inherits Int by (Height + Width) * Height
            type MultiExpression inherits Int by Height + Width * Height
         """.compiled()
         val expression = doc.objectType("Bracketed").expression as OperatorExpression
         (expression.lhs as OperatorExpression).let { lhs ->
            (lhs.lhs.asA<OperatorExpression>()).let {
               it.lhs
            }
         }
//         (area.lhs as TypeExpression).let {
//            it.type.should.equal(doc.type("Height"))
//         }
//         (area.rhs as TypeExpression).let {
//            it.type.should.equal(doc.type("Width"))
//         }
//         area.operator.should.equal(FormulaOperator.Multiply)
         TODO()
      }
      it("should compile nested expression types") {
         """
            type Width inherits Int
            type Height inherits Int
            type Area by Height * Width
            type Square by Area * Area
         """.trimIndent()
      }
      it("should be possible to use number literals in expression types") {
         val expression = """
            type Height inherits Int
            type ABitBigger inherits Int by Height + 1
         """.compiled()
            .objectType("ABitBigger")
            .expression
         TODO()

      }
      it("can declare a expression type on a model") {
         """
            type Height inherits Int
         type Width inherits Int
//       type Area inherits Int by Height * Width
         model Rectangle {
            height : Height
            width : Width
            area : Area
         }
         """.compiled()
            .model("Rectangle")
            .field("area")
            .type.qualifiedName.should.equal("Area")
      }
      it("can use functions in expression types") {
         """
            declare function squared(Int):Int

            type Height inherits Int
            type SingleFunction inherits Int by squared(Height)
            type MultipleFunction inherits Int by squared(squared(Height))

            type Another inherits Int by Height * squared(Height)
         """.compiled()
         val foo = emptyList<String>()
         TODO()
      }
   }
})

inline fun <reified T : Any> Any.asA():T  {
   return this as T
}

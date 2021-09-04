package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.FormulaOperator
import lang.taxi.types.TypeReferenceSelector
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
         expression.lhs.compilationUnits[0].source.content.should.equal("Height + Width")
         expression.rhs.compilationUnits[0].source.content.should.equal("Height")
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
            .expression as OperatorExpression
         val rhs = expression.rhs as LiteralExpression
         rhs.literal.value.should.equal(1)
      }
      it("can declare a expression type on a model") {
         """
            type Height inherits Int
         type Width inherits Int
       type Area inherits Int by Height * Width
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
         val expressionType = """
            declare function squared(Int):Int

            type Height inherits Int

            type MultipleFunction inherits Int by squared(squared(Height))
         """.compiled()
            .objectType("MultipleFunction")
         val expression = expressionType.expression as FunctionExpression
         expression.function.function.qualifiedName.should.equal("squared")
         expression.function.inputs.should.have.size(1)
         val firstInput = expression.function.inputs.first() as FunctionAccessor
         firstInput.inputs.should.have.size(1)
         val firstNestedInput = firstInput.inputs.first() as TypeReferenceSelector
         firstNestedInput.type.qualifiedName.should.equal("Height")
      }

      it("can use functions on rhs of expression types") {
         val expressionType = """
            declare function squared(Int):Int

            type Height inherits Int

            type MyExpression inherits Int by Height * squared(Height)
         """.compiled()
            .objectType("MyExpression")
         val expression = expressionType.expression as OperatorExpression
         val rhs = expression.rhs as FunctionExpression
         rhs.function.function.qualifiedName.should.equal("squared")
         rhs.function.inputs.should.have.size(1)
         val firstInput = rhs.function.inputs.first() as TypeReferenceSelector
         firstInput.type.qualifiedName.should.equal("Height")
      }
   }


})

inline fun <reified T : Any> Any.asA():T  {
   return this as T
}

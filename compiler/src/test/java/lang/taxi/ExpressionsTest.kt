package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.TypeReferenceSelector
import org.junit.jupiter.api.Test

// Writing junit style tests here until a new version of Spek is released.
class ExpressionsTest {

   @Test
   fun `can use functions in expression types`() {
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

   @Test
   fun `can use functions on rhs of expression types`() {
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

package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LambdaExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.FormulaOperator
import lang.taxi.types.PrimitiveType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ExpressionTypesSpec : DescribeSpec({
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
      it("can infer return type of expression types") {
         val type = """
            type Height inherits Int
            type Width inherits Int
            type Area by (Height + Width) * Height
         """.compiled()
            .objectType("Area")
         type.basePrimitive!!.should.equal(PrimitiveType.INTEGER)
      }
      it("infers return types correctly") {
         val doc = """
            type ExpectedInt by Int + Int
            type ExpectedDecimal by Int + Decimal
            type ExpectedDouble by Int + Double
            type ExpectedString by String + String
         """.compiled()
         doc.type("ExpectedInt").basePrimitive!!.should.equal(PrimitiveType.INTEGER)
         doc.type("ExpectedDecimal").basePrimitive!!.should.equal(PrimitiveType.DECIMAL)
         doc.type("ExpectedDouble").basePrimitive!!.should.equal(PrimitiveType.DOUBLE)
         doc.type("ExpectedString").basePrimitive!!.should.equal(PrimitiveType.STRING)
      }
      it("detects return types of logical expressions") {
         val doc = """
            type Height inherits Int
            type Width inherits Int
            type A by Height > Width
            type B by Height >= Width
            type C by Height < Width
            type D by Height <= Width
            type E by Height == Width
            type F by Height != Width
         """.compiled()
         listOf("A","B","C","D","E","F").forEach { typeName ->
            doc.objectType(typeName).basePrimitive!!.should.equal(PrimitiveType.BOOLEAN)
         }
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
         val firstInput = expression.function.inputs.first() as FunctionExpression
         firstInput.inputs.should.have.size(1)
         val firstNestedInput = firstInput.function.inputs.first() as TypeExpression
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
         val firstInput = rhs.function.inputs.first() as TypeExpression
         firstInput.type.qualifiedName.should.equal("Height")
      }
      it("can use expressions within function inputs") {
         val expression = """
            declare function hasAvailableStock(Boolean):Int

            type RequestedStock inherits Int
            type AvailableStock inherits Int

            type MyExpression by hasAvailableStock(AvailableStock > RequestedStock)
         """.compiled()
            .objectType("MyExpression")
            .expression as FunctionExpression
         val input = expression.function.inputs[0] as OperatorExpression
         input.returnType.should.equal(PrimitiveType.BOOLEAN)
         input.lhs.asA<TypeExpression>().type.qualifiedName.should.equal("AvailableStock")
         input.rhs.asA<TypeExpression>().type.qualifiedName.should.equal("RequestedStock")
         input.operator.should.equal(FormulaOperator.GreaterThan)
      }
   }
   describe("Lambda types") {
      it("should compile simple lambda type") {
         val expressionType = """
            type MinimumAcceptableCalories inherits Int
            type MaximumAcceptableCalories inherits Int
            type ProductCalories inherits Int
            type AcceptableCalorieRange by (MinimumAcceptableCalories , MaximumAcceptableCalories) -> ProductCalories > MinimumAcceptableCalories && ProductCalories < MaximumAcceptableCalories
         """.trimIndent()
            .compiled()
            .objectType("AcceptableCalorieRange")
            .expression!! as LambdaExpression

         expressionType.inputs.map { it.qualifiedName }.should.equal(listOf("MinimumAcceptableCalories","MaximumAcceptableCalories"))
         val lambdaExpression = expressionType.expression as OperatorExpression
         lambdaExpression.asTaxi().should.equal("ProductCalories > MinimumAcceptableCalories && ProductCalories < MaximumAcceptableCalories")
         lambdaExpression.operator.should.equal(FormulaOperator.LogicalAnd)
      }

   }


})

inline fun <reified T : Any> Any.asA(): T {
   return this as T
}

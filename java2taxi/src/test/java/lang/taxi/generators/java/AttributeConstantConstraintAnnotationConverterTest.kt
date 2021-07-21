package lang.taxi.generators.java

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import lang.taxi.annotations.ConstraintAnnotationModel
import lang.taxi.annotations.ResponseConstraint
import lang.taxi.annotations.ResponseContract
import lang.taxi.services.operations.constraints.ConstantValueExpression
import lang.taxi.services.operations.constraints.PropertyFieldNameIdentifier
import org.junit.jupiter.api.Test

class AttributeConstantConstraintAnnotationConverterTest {

   @Test
   fun detectsSyntaxCorrectly() {
      val converter = AttributeConstantConstraintAnnotationConverter()
      val constraint = mockConstraint("a = 'b'")
      expect(converter.canProvide(constraint)).to.be.`true`
      val result = converter.provide(constraint)
      (result.propertyIdentifier as PropertyFieldNameIdentifier).name.path.should.equal("a")
      (result.expectedValue as ConstantValueExpression).value.should.equal("b")
   }

   @Test
   fun doesntConvertInvalid() {
      val converter = AttributeConstantConstraintAnnotationConverter()
      val invalidStrings = listOf(
         "a > b", "a in b", "a == b", "a != b"
      )
      invalidStrings.forEach {
         expect(converter.canProvide(mockConstraint(it))).to.be.`false`
      }
   }

   private fun mockConstraint(constraint: String): ConstraintAnnotationModel {
      return ConstraintAnnotationModel(mock<ResponseConstraint> {
         on { this.value } doReturn constraint
      })
   }

   private fun mockContract(basedOn: String, vararg constraints: String): ResponseContract {
      val response = mock<ResponseContract> {
         on { this.basedOn } doReturn basedOn
         on { this.constraints } doReturn constraints.map { constraint ->
            mock<ResponseConstraint> {
               on { this.value } doReturn constraint
            }
         }.toTypedArray()
      }
      return response
   }


}

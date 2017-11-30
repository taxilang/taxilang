package lang.taxi.generators.java

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.winterbe.expekt.expect
import lang.taxi.annotations.ConstraintAnnotationModel
import lang.taxi.annotations.ResponseConstraint
import lang.taxi.annotations.ResponseContract
import org.junit.Test

class AttributeConstantConstaintAnnotationCoverterTest {

    @Test
    fun detectsSyntaxCorrectly() {
        val converter = AttributeConstantConstaintAnnotationCoverter()
        val constraint = mockConstraint("a = 'b'")
        expect(converter.canProvide(constraint)).to.be.`true`
        val result = converter.provide(constraint)
        expect(result.fieldName).to.equal("a")
        expect(result.expectedValue).to.equal("b")
    }

    @Test
    fun doesntConvertInvalid() {
        val converter = AttributeConstantConstaintAnnotationCoverter()
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

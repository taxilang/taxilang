package lang.taxi

import com.winterbe.expekt.expect
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import org.junit.jupiter.api.Test

class ConstraintsSpec : DescribeSpec({
   describe("Constraints") {
      it("can declare constraints on types") {
         val source = """
type Money {
   amount : Amount as Decimal
   currency : Currency as String
}
type SomeServiceRequest {
   amount : Money(this.currency == 'GBP')
   clientId : ClientId as String
}
"""
         val doc = Compiler(source).compile()
         val request = doc.objectType("SomeServiceRequest")

         val amountField = request.field("amount")
         expect(amountField.constraints).to.have.size(1)
         expect(amountField.constraints[0]).to.be.instanceof(PropertyToParameterConstraint::class.java)

      }
   }
})

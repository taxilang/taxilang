package lang.taxi.generators.openApi

import com.winterbe.expekt.expect
import org.junit.jupiter.api.Test
import v2.io.swagger.models.HttpMethod
import v2.io.swagger.models.Operation as SwaggerOperation

class OperationIdProviderTest {

    @Test
    fun generatesNameIfNoId() {
        val operation = SwaggerOperation()
        val operationId = OperationIdProvider.getOperationId(operation, "https://localhost:8080/foo/bar", HttpMethod.GET)
        expect(operationId).to.equal("GetFooBar")
    }

   @Test
   fun generatesNameIfNoIdAndPathHasVariable() {
      val operation = SwaggerOperation()
      val operationId = OperationIdProvider.getOperationId(operation, "/pets/{id}", HttpMethod.GET)
      expect(operationId).to.equal("GetPetsId")
   }
}

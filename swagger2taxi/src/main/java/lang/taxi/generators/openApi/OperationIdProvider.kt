package lang.taxi.generators.openApi

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import lang.taxi.generators.NamingUtils.removeIllegalCharacters
import lang.taxi.generators.NamingUtils.replaceIllegalCharacters
import java.net.URL

object OperationIdProvider {

   private fun getOperationId(operationId: String?, pathMapping: String, methodName: String) =
      operationId?.replaceIllegalCharacters() ?: generateOperationId(pathMapping, methodName.toLowerCase())

   private fun generateOperationId(pathMapping: String, methodName: String): String {
      val path = pathMapping.urlPath().split("/")
      val words = listOf(methodName) + path
      return words.joinToString("") { it.removeIllegalCharacters().capitalize() }
   }

   fun getOperationId(operation: Operation, pathMapping: String, method: PathItem.HttpMethod): String =
      getOperationId(operation.operationId, pathMapping, method.name)

}


private fun String.urlPath(): String {
   return try {
      URL(this).path
   } catch (exception: Exception) {
      this
   }
}

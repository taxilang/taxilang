package lang.taxi.generators.openApi.v3

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import lang.taxi.services.OperationScope

private const val EXTENSION_KEY = "x-taxi-operation-kind"

// Extension to determine the operation kind based on HTTP method or extension
fun Operation.getOperationKind(method: PathItem.HttpMethod): OperationScope {
   // Check if there's an explicit operation kind defined in the extensions
   val explicitOperationKind = this.extensions?.get(EXTENSION_KEY)?.toString()
   if (explicitOperationKind != null) {
      return OperationScope.forToken(explicitOperationKind)
   }
   
   // Default behavior based on HTTP method
   return when (method) {
      PathItem.HttpMethod.GET, PathItem.HttpMethod.HEAD, PathItem.HttpMethod.OPTIONS -> OperationScope.READ_ONLY
      PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE -> OperationScope.MUTATION
      else -> OperationScope.READ_ONLY // Default to READ_ONLY for any other methods
   }
}

// Keep the original property for backward compatibility, but default to READ_ONLY
val Operation.taxiOperationKind: OperationScope
   get() {
      val operationKind = this.extensions?.get(EXTENSION_KEY)?.toString() ?: return OperationScope.READ_ONLY
      return OperationScope.forToken(operationKind)
   }

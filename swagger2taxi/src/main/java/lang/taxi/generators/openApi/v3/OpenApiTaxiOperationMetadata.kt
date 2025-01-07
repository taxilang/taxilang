package lang.taxi.generators.openApi.v3

import io.swagger.v3.oas.models.Operation
import lang.taxi.services.OperationScope

private const val EXTENSION_KEY = "x-taxi-operation-kind"
val Operation.taxiOperationKind: OperationScope
   get() {
      val operationKind = this.extensions?.get(EXTENSION_KEY)?.toString() ?: return OperationScope.READ_ONLY
      return OperationScope.forToken(operationKind)
   }

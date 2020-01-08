package lang.taxi.generators.openApi.v3

import io.swagger.oas.models.OpenAPI
import io.swagger.oas.models.media.Schema

object RefEvaluator {
   fun navigate(api: OpenAPI, ref: String): Schema<Any> {
      if (!ref.startsWith("#")) {
         error("External refs are not supported")
      }

      return when {
         ref.startsWith("#/components/schemas/") -> {
            val schemaName = ref.removePrefix("#/components/schemas/")
            api.components.schemas[schemaName] ?: error("Ref $ref refers to schema $schemaName which does not exist")
         }
         else -> TODO("Not yet supported.  Add use cases as you hit this point")
      }
   }
}

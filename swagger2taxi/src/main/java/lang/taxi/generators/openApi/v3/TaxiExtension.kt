package lang.taxi.generators.openApi.v3

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.models.media.Schema
import lang.taxi.generators.openApi.v3.TaxiExtension.Companion.extensionKey

data class TaxiExtension(
   val name: String,
   val create: Boolean?,

   @field:JsonInclude(JsonInclude.Include.NON_EMPTY)
   val inherits: List<String> = emptyList()
) {
   companion object {
      const val extensionKey = "x-taxi-type"

      const val createModelByDefault = true
      const val createTypeByDefault = false
   }

   fun shouldGenerateFor(schema: Schema<*>): Boolean = when {
      schema.isModel() && (create ?: createModelByDefault) -> true
      schema.isType() && (create ?: createTypeByDefault) -> true
      else -> false
   }
}

private val objectMapper = jacksonObjectMapper()
   .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
   .configure(ALLOW_COERCION_OF_SCALARS, false)

val Schema<*>.taxiExtension: TaxiExtension?
   get() = extensions?.get(extensionKey)?.let { extensionObject ->
      objectMapper.convertValue(extensionObject, TaxiExtension::class.java)
   }

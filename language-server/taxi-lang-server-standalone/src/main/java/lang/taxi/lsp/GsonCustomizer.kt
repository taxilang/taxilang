package lang.taxi.lsp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.orbitalhq.query.history.DiagramNodeKind
import com.orbitalhq.query.history.HandleKind
import com.orbitalhq.schemas.RemoteOperation
import com.orbitalhq.schemas.SchemaMember
import lang.taxi.lsp.notebook.ListOperationsResponse
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime

object GsonCustomizer {
   fun configureGson(builder: GsonBuilder) {
      // Register enum serializer to use names instead of ordinals
      listOf(HandleKind::class, DiagramNodeKind::class).forEach {
         builder.registerTypeAdapter(it.java, EnumTypeAdapter(it.java))
      }

      builder.registerTypeHierarchyAdapter(ZonedDateTime::class.java, ZonedDateTypeAdapter)
      builder.registerTypeHierarchyAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter)
      builder.registerTypeHierarchyAdapter(Instant::class.java, InstantAdapter)

      // Disable HTML escaping and enable pretty printing for debugging
      builder.disableHtmlEscaping()
   }
}

/**
 * Type adapter for enums that uses name() for serialization and valueOf() for deserialization
 */
class EnumTypeAdapter<T : Enum<T>>(private val enumClass: Class<T>) : TypeAdapter<T>() {
   override fun write(out: JsonWriter, value: T?) {
      if (value == null) {
         out.nullValue()
      } else {
         out.value(value.name)
      }
   }

   override fun read(`in`: JsonReader): T? {
      if (`in`.peek() == JsonToken.NULL) {
         `in`.nextNull()
         return null
      }
      val name = `in`.nextString()
      return java.lang.Enum.valueOf(enumClass, name)
   }
}

object LocalDateTimeTypeAdapter : TypeAdapter<LocalDateTime>() {
   override fun write(out: JsonWriter, value: LocalDateTime?) {
      if (value == null) {
         out.nullValue()
      } else {
         out.value(value.toString())
      }
   }
   override fun read(`in`: JsonReader?): LocalDateTime? {
      TODO("Not yet implemented")
   }

}
object ZonedDateTypeAdapter : TypeAdapter<ZonedDateTime>() {
   override fun write(out: JsonWriter, value: ZonedDateTime?) {
      if (value == null) {
         out.nullValue()
      } else {
         out.value(value.toInstant().toString())
      }
   }

   override fun read(`in`: JsonReader?): ZonedDateTime? {
      TODO("Not yet implemented")
   }

}

object InstantAdapter : TypeAdapter<Instant>() {
   override fun write(out: JsonWriter, value: Instant?) {
      if (value == null) {
         out.nullValue()
      } else {
         out.value(value.toString())
      }
   }

   override fun read(`in`: JsonReader?): Instant? {
      TODO("Not yet implemented")
   }

}

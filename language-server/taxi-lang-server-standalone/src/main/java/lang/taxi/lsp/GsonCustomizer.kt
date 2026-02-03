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
import com.orbitalhq.models.json.Jackson
import com.orbitalhq.query.history.DiagramNodeKind
import com.orbitalhq.query.history.HandleKind
import com.orbitalhq.schemas.Parameter
import com.orbitalhq.schemas.RemoteOperation
import com.orbitalhq.schemas.SchemaMember
import com.orbitalhq.schemas.Service
import com.orbitalhq.schemas.asVyneTypeReference
import lang.taxi.TaxiDocument
import lang.taxi.TaxiParser
import lang.taxi.lsp.notebook.ListOperationsResponse
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.reflect.KClass

object GsonCustomizer {
   fun configureGson(builder: GsonBuilder) {
      // Register enum serializer to use names instead of ordinals
      listOf(HandleKind::class, DiagramNodeKind::class).forEach {
         builder.registerTypeAdapter(it.java, EnumTypeAdapter(it.java))
      }

      // Serialize orbital types with jackson
      listOf(com.orbitalhq.schemas.Type::class, Service::class, RemoteOperation::class, Parameter::class).forEach {
         builder.registerTypeHierarchyAdapter(it.java, JacksonAdapter(it))
      }


      // Gson equivalent of TaxiJAcksonModule
      builder.registerTypeAdapter(TaxiDocument::class.java, TaxiDocumentNoopAdapter)
      builder.registerTypeAdapter(TaxiParser.DocumentContext::class.java, TaxiDocumentContextNoopAdapter)
      builder.registerTypeHierarchyAdapter(lang.taxi.types.Type::class.java, TaxiTypeAsVyneQualifiedNameTypeAdapter)

      builder.registerTypeHierarchyAdapter(ZonedDateTime::class.java, ZonedDateTypeAdapter)
      builder.registerTypeHierarchyAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter)
      builder.registerTypeHierarchyAdapter(Instant::class.java, InstantAdapter)

      // Disable HTML escaping and enable pretty printing for debugging
      builder.disableHtmlEscaping()
   }
}

// Gson equivalent of TaxiDocumentNoopSerializer etc.
object TaxiDocumentContextNoopAdapter : NoOpTypeAdapter<TaxiParser.DocumentContext>()
object TaxiDocumentNoopAdapter : NoOpTypeAdapter<TaxiDocument>()
abstract class NoOpTypeAdapter<T> : TypeAdapter<T>() {
   override fun write(out: JsonWriter?, value: T?) {
      // Write null for these types - they shouldn't be serialized
      out?.nullValue()
   }

   override fun read(`in`: JsonReader?): T? {
      TODO("Not yet implemented")
   }
}

class JacksonAdapter<T : Any>(clazz: KClass<T>) : TypeAdapter<T>() {
   override fun write(out: JsonWriter, value: T?) {
      if (value == null) {
         out.nullValue()
      } else {
         val qualifiedNameJson = Jackson.defaultObjectMapper.writeValueAsString(value)
         out.jsonValue(qualifiedNameJson)
      }
   }

   override fun read(`in`: JsonReader?): T? {
      TODO("Not yet implemented")
   }

}

// Gson equivalent of TaxiTypeAsVyneQualifiedNameSerializer
object TaxiTypeAsVyneQualifiedNameTypeAdapter : TypeAdapter<lang.taxi.types.Type>() {
   override fun write(out: JsonWriter, value: lang.taxi.types.Type?) {
      if (value == null) {
         out.nullValue()
      } else {
         val qualifiedNameJson = Jackson.defaultObjectMapper.writeValueAsString(value.asVyneTypeReference().name)
         out.jsonValue(qualifiedNameJson)
      }
   }

   override fun read(`in`: JsonReader?): lang.taxi.types.Type? {
      TODO("Not yet implemented")
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

package lang.taxi.lsp

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.Instant
import java.time.ZonedDateTime

object GsonCustomizer {
   fun configureGson(builder: GsonBuilder) {
      builder.registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTypeAdapter)
      builder.registerTypeAdapter(Instant::class.java, InstantAdapter)
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

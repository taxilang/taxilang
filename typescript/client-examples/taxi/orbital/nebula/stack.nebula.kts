import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.orbitalhq.nebula.utils.duration
import io.ktor.http.*
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Paths
import kotlin.random.Random

/*
 This Nebula stack provides:
   - a database that contains film data
   - 5 http API's for retrieving reviews, streaming providers and correlating film id's
   - a kafka broker that broadcasts on a "release" topic every 5 seconds
 */

stack {
   fun loadSqlSchema(filename: String): String {
      return try {
         // Attempt to load the file from the container path
         File("/nebula/$filename").readText()
      } catch (e: Exception) {
         // If it fails, fall back to the local file path (used when running Nebula locally for dev)
         val scriptPath = Paths.get("./orbital/nebula").toAbsolutePath().toFile()
         println("scriptPath $scriptPath")
         val localFile = File(scriptPath, filename)
         if (!localFile.exists()) {
            throw IllegalArgumentException("Schema file $filename not found in either /nebula/ or the local directory.")
         }
         localFile.readText()
      }
   }

   postgres(componentName = "demo-postgres") {
      // Load SQL from external file
      val schemaSql = loadSqlSchema("sql-import.sql")
      table(
         "table-import", schemaSql, emptyList()
      )
   }

   data class IdResolution(
      val filmId: Int,
      val netflixId: Int = filmId + 1000,
      val squashedTomatoesFilmId: String = "squashed-$filmId"
   )

   fun lookupFromId(id: String): IdResolution {
      val filmId = id.split("-").last().toInt()
      return IdResolution(filmId)
   }

   data class StreamingProvider(
      val name: String,
      val pricePerMonth: BigDecimal
   )

   val streamingProviders: List<StreamingProvider> = listOf(
      StreamingProvider(name = "Netflix", pricePerMonth = 9.99.toBigDecimal()),
      StreamingProvider(name = "Disney Plus", pricePerMonth = 7.99.toBigDecimal()),
      StreamingProvider(name = "Now TV", pricePerMonth = 13.99.toBigDecimal()),
      StreamingProvider(name = "Hulu", pricePerMonth = 8.99.toBigDecimal()),
   )

   val reviewText = listOf(
      "An aggressively fine and mostly enjoyable romp that does some things well and others things less so. It’s the epitome of just OK.",
      "For a while it seems it wants to be the franchise’s ‘Mission: Impossible.’ Instead, it’s the anti–‘Top Gun: Maverick’.My co-worker Ali has one of these. He says it looks towering.",
      "An ugly franchise entry that feels like a contractual obligation by an employee full of hatred. Easily the worst film in the JP universe.",
      "This is not one of those awful dark, depressing films about an impending genetic apocalypse, although it could have easily been turned into that with a few minor tweaks. This is an entertaining romp, loaded with action, nostalgia and special effects.",
      "A death-metal ode to honor, retribution and sacrifice that casts payback in a surprisingly, and thrillingly, positive light.",
      "A new oddity in which horror, revenge and Norse mythology go hand in hand in one of the most suggestive and visceral shows that we've been able to enjoy on the big screen in the last years.",
      "Gratuitously extenuating retina trips... A nightmare in its own right that portrays the tragedy and the end of a treacherous and ultra-masculinized world. [Full review in Spanish]",
      "The veteran actor does nuanced work as usual, but the FX series takes too many ridiculous turns.",
      "The Old Man is more captivating when dealing with universal real-world concerns than with its spy stuff, no matter that its slam-bang action is crisp and its twists and turns are energized.",
      "Much of the show’s success comes down to Bridges, who anchors a rickety character visibly battered by the past yet able to shapeshift in the present.",
   )

   data class FilmReview(
      val filmId: String,
      val score: BigDecimal,
      val filmReview: String
   )

   val mapper = jacksonObjectMapper()


   http {
      get("/reviews/{filmId}") { call ->
         val filmId = call.parameters["filmId"] ?: throw IllegalArgumentException("Missing 'filmId' parameter")
         call.respondText(
            mapper.writeValueAsString(FilmReview(
               filmId,
               Random.nextDouble(2.3, 5.0).toBigDecimal().setScale(1, RoundingMode.HALF_EVEN),
               reviewText.random()
            )),
            ContentType.parse("application/json")
         )
      }

      get("/films/{filmId}/streamingProviders") { call ->
         val filmId = call.parameters["filmId"]?.toIntOrNull() ?: throw IllegalArgumentException("Missing 'filmId' parameter")
         call.respondText(
            mapper.writeValueAsString(streamingProviders[filmId % streamingProviders.size]),
            ContentType.parse("application/json")
         )
      }

      get("/ids/squashed/{id}") { call ->
         val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing 'id' parameter")
         call.respondText(
            mapper.writeValueAsString(lookupFromId(id)),
            ContentType.parse("application/json")
         )
      }

      get("/ids/netflix/{id}") { call ->
         val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing 'id' parameter")
         call.respondText(
            mapper.writeValueAsString(lookupFromId(id)),
            ContentType.parse("application/json")
         )
      }

      get("/ids/internal/{id}") { call ->
         val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing 'id' parameter")
         call.respondText(
            mapper.writeValueAsString(lookupFromId(id)),
            ContentType.parse("application/json")
         )
      }
   }

   kafka {
      producer("1s".duration(), "releases") {
         jsonMessage {
            mapOf(
               "filmId" to Random.nextInt(0, 1000),
               "announcement" to "Today, Netflix announced the reboot of yet another classic franchise"
            )
         }
      }
   }

}


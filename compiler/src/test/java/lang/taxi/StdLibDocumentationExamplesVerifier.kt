package lang.taxi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import lang.taxi.docs.StubQueryMessage
import lang.taxi.functions.stdlib.DocsSnippet
import lang.taxi.functions.stdlib.FunctionApi
import lang.taxi.functions.stdlib.HasRunnableExamples
import lang.taxi.functions.stdlib.StdLib
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

class StdLibDocumentationExamplesVerifierSpec : DescribeSpec({
   val client = OkHttpClient.Builder()
      .callTimeout(10L, TimeUnit.SECONDS)
      .build()
   val jsonMediaType = "application/json; charset=utf-8".toMediaType()
   val playgroundHost = "https://playground.taxilang.org"
//   val playgroundHost = "http://localhost:9500"
   val queryEndpoint = "$playgroundHost/api/query"

   describe("StdLib functions with runnable examples") {
      val runnableExamples: List<Pair<String, List<DocsSnippet>>> = StdLib.functions
         .filterIsInstance<HasRunnableExamples>()
         .map {
            val function = it as FunctionApi
            val functionName = function.name.fullyQualifiedName
            functionName to it.examples
         }

      // Generate a test for each example
      runnableExamples.forEach { (functionName, examples) ->
         examples.forEachIndexed { index, example ->
            it("$functionName example ${index + 1} should return expected JSON") {
               // Skip test if expectedJson is null
               if (example.query.expectedJson == null) {
                  println("Skipping test for $functionName example ${index + 1} as expectedJson is null")
                  return@it
               }

               // Prepare the request
               val requestJson = jacksonObjectMapper().writeValueAsString(example.query)
               val requestBody = requestJson.toRequestBody(jsonMediaType)
               val request = Request.Builder()
                  .url(queryEndpoint)
                  .post(requestBody)
                  .build()

               // Execute the request
               val response = runBlocking {
                  try {
                     client.newCall(request).execute()
                  } catch (e: IOException) {
                     throw AssertionError("Failed to execute query: ${e.message}")
                  }
               }

               // Validate the response
               if (!response.isSuccessful) {
                  // try to parse
                  val responseBody = response.body?.string()
                  if (responseBody != null) {
                     try {
                        val errorResponse = jacksonObjectMapper().readValue<Map<String,Any>>(responseBody)
                        val compilerError = errorResponse["message"]
                        val playgroundUrl = getPlaygroundUrl(example.query, playgroundHost)
                        fail("The query failed to execute: $compilerError\nPlaygroundUrl: $playgroundUrl")
                     } catch (e: Exception) {
                        val playgroundUrl = getPlaygroundUrl(example.query, playgroundHost)
                        fail("The query failed to execute - ${response.message}\nPlaygroundUrl: $playgroundUrl")
                     }

                  }

               }
               withClue("Http query failed: ${response.code} - ${response.message}") {
                  response.isSuccessful shouldBe true
               }

               val responseBody = response.body?.string() ?: ""

               // Use JSONAssert to compare the JSON responses
               try {
                  JSONAssert.assertEquals(
                     example.query.expectedJson,
                     responseBody,
                     JSONCompareMode.LENIENT
                  )
               } catch (e: Throwable) {
                  val playgroundUrl = getPlaygroundUrl(example.query, playgroundHost)
                  fail("${e.message}\nPlayground url: $playgroundUrl")
               }

            }
         }
      }
   }
})

@OptIn(ExperimentalEncodingApi::class)
fun getPlaygroundUrl(queryMessage: StubQueryMessage, playgroundHost: String): String {
   val json = jacksonObjectMapper().writeValueAsString(queryMessage)

   // Compress the JSON with GZIP
   val gzipOutput = ByteArrayOutputStream()
   GZIPOutputStream(gzipOutput).use {
      it.write(json.toByteArray())
   }
   val compressed = gzipOutput.toByteArray()

   // Base64 encode:
   val base64Encoded = Base64.encode(compressed)

   return "$playgroundHost/?enableDevTools=true#pako:$base64Encoded"
}

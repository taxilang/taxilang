package lang.taxi.annotations

import lang.taxi.types.Annotation
import lang.taxi.types.AnnotationProvider
import lang.taxi.types.BuiltIn
import lang.taxi.types.QualifiedName


data class HttpService(val baseUrl: String) : AnnotationProvider {
   companion object : BuiltIn {
      const val NAME = "taxi.http.HttpService"
      override fun asTaxi(): String = """
         namespace taxi.http {
            annotation HttpService {
               baseUrl : String
            }
            enum HttpMethod {
               GET,
               POST,
               PUT,
               DELETE,
               PATCH
            }

            annotation HttpOperation {
               method : HttpMethod
               url : String
            }

            annotation RequestBody {}
            annotation PathVariable { value : String }
         }

      """

      override val name: QualifiedName = QualifiedName.from("taxi.http.HttpService")

      fun fromAnnotation(annotation: Annotation): HttpService {
         val parameters = annotation.parameters
         return fromParams(annotation.parameters)
      }

      fun fromParams(parameters: Map<String, Any?>): HttpService {
         require(parameters.containsKey("baseUrl")) { "@$NAME requires a baseUrl parameter" }
         return HttpService(parameters["baseUrl"]!!.toString())
      }
   }


   override fun toAnnotation(): Annotation {
      return Annotation(NAME, mapOf("baseUrl" to baseUrl))
   }


}

data class HttpOperation(val method: String, val url: String) : AnnotationProvider {
   companion object {
      const val NAME = "taxi.http.HttpOperation"
      fun fromAnnotation(annotation: Annotation): HttpOperation {
         // TODO : We should just define the bloody annotation in taxi.  Then this would be handled
         // at the compiler level!!!
         val parameters = annotation.parameters
         require(parameters.containsKey("method")) { "@HttpOperation requires a method parameter" }
         require(parameters.containsKey("url")) { "@HttpOperation requires a url parameter" }
         return HttpOperation(parameters["method"]!!.toString(), parameters["url"]!!.toString())
      }
   }

   override fun toAnnotation(): Annotation = Annotation(NAME, mapOf("method" to method, "url" to url))
}

object HttpRequestBody : AnnotationProvider {
   override fun toAnnotation(): Annotation {
      return Annotation(NAME)
   }

   const val NAME = "taxi.http.RequestBody"
}

@Deprecated("This is no longer required")
data class ServiceDiscoveryClient(val serviceName: String) : AnnotationProvider {
   override fun toAnnotation(): Annotation {
      return lang.taxi.types.Annotation("ServiceDiscoveryClient", mapOf("serviceName" to serviceName))
   }
}

data class HttpPathVariable(val value: String) : AnnotationProvider {
   companion object {
      const val NAME = "PathVariable"
   }

   override fun toAnnotation() = Annotation(NAME, mapOf("value" to value))
}

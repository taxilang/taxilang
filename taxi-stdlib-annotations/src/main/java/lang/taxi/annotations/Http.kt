package lang.taxi.annotations

import lang.taxi.query.TaxiQlQuery
import lang.taxi.types.Annotation
import lang.taxi.types.AnnotationProvider
import lang.taxi.types.BuiltIn
import lang.taxi.types.QualifiedName


data class HttpService(val baseUrl: String) : AnnotationProvider {
   companion object : BuiltIn {
      const val RESPONSE_CODE_TYPE_NAME = "taxi.http.ResponseCode"
      const val RESPONSE_BODY_TYPE_NAME = "taxi.http.ResponseBody"
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

            annotation WebsocketOperation {
               path : String
            }


            annotation HttpHeader {
               name : String
               [[ Pass a value when using as an annotation on an operation.
               For parameters, it's valid to allow the value to be populated from the parameter. ]]
               value : String?
               prefix : String?
               suffix : String?
            }

            annotation RequestBody {}
            annotation PathVariable { value : String }

            annotation ResponseBody{}

            annotation ResponseCode {
               value : Int
            }
            
            annotation HttpQueryVariable { value: String }
            
            annotation HttpResponseHeader { value: String }
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

data class HttpHeader(val name: String, val value: String? = null, val prefix: String? = null, val suffix: String? = null): AnnotationProvider {
   companion object {
      const val NAME = "taxi.http.HttpHeader"
      fun fromMap(map: Map<String, Any?>): HttpHeader {
         return HttpHeader(
            name = map["name"]?.toString() ?: error("Name is required"),
            value = map["value"]?.toString(),
            prefix = map["prefix"]?.toString(),
            suffix = map["suffix"]?.toString(),

            )
      }
   }

   fun asValue(value: String = ""):String {
      val valueToUse = this.value ?: value
      return "${prefix.orEmpty()}$valueToUse${suffix.orEmpty()}"
   }

   override fun toAnnotation(): Annotation {
      val valueMap = value?.let { mapOf("value" to value) } ?: mapOf()
      val prefixMap = prefix?.let { mapOf("prefix" to prefix) } ?: mapOf()
      val suffixMap = suffix?.let { mapOf("suffix" to suffix) } ?: mapOf()
      return Annotation(HttpHeader.NAME, mapOf("name" to name) + valueMap + prefixMap + suffixMap)
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

      fun fromQuery(query: TaxiQlQuery): HttpOperation? {
         val httpAnnotation = query.annotations.singleOrNull { annotation -> annotation.name == HttpOperation.NAME }
         return if (httpAnnotation != null) {
            fromAnnotation(httpAnnotation)
         } else null
      }
   }

   override fun toAnnotation(): Annotation = Annotation(NAME, mapOf("method" to method, "url" to url))
}

data class WebsocketOperation(val path: String) : AnnotationProvider {
   companion object {
      const val NAME = "taxi.http.WebsocketOperation"
      fun fromAnnotation(annotation: Annotation): WebsocketOperation {
         // TODO : We should just define the bloody annotation in taxi.  Then this would be handled
         // at the compiler level!!!
         val parameters = annotation.parameters
         require(parameters.containsKey("path")) { "@WebsocketOperation requires a path parameter" }
         return WebsocketOperation(parameters["path"]!!.toString())
      }

      fun fromQuery(query: TaxiQlQuery): WebsocketOperation? {
         val httpAnnotation = query.annotations.singleOrNull { annotation -> annotation.name == WebsocketOperation.NAME }
         return if (httpAnnotation != null) {
            fromAnnotation(httpAnnotation)
         } else null
      }
   }

   override fun toAnnotation(): Annotation = Annotation(NAME, mapOf("path" to path))
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
      const val NAME = "taxi.http.PathVariable"
   }

   override fun toAnnotation() = Annotation(NAME, mapOf("value" to value))
}

data class HttpQueryVariable(val value: String) : AnnotationProvider {
   companion object {
      const val NAME = "taxi.http.QueryVariable"
   }

   override fun toAnnotation() = Annotation(NAME, mapOf("value" to value))
}

data class HttpResponseHeader(val value: String) : AnnotationProvider {
   companion object {
      const val NAME = "taxi.http.ResponseHeader"
   }

   override fun toAnnotation() = Annotation(NAME, mapOf("value" to value))
}



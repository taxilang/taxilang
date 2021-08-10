package lang.taxi.annotations

import lang.taxi.types.Annotation
import lang.taxi.types.AnnotationProvider

data class HttpOperation(val method: String, val url: String) : AnnotationProvider {
   override fun toAnnotation(): Annotation = Annotation("HttpOperation", mapOf("method" to method, "url" to url))
}

object HttpRequestBody : AnnotationProvider {
   override fun toAnnotation(): Annotation {
      return Annotation("RequestBody")
   }
}

data class ServiceDiscoveryClient(val serviceName: String) : AnnotationProvider {
   override fun toAnnotation(): Annotation {
      return lang.taxi.types.Annotation("ServiceDiscoveryClient", mapOf("serviceName" to serviceName))
   }
}

data class HttpPathVariable(val value: String) : AnnotationProvider {
   override fun toAnnotation() = Annotation("PathVariable", mapOf("value" to value))
}

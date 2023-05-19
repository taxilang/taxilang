package lang.taxi.generators.java.spring

import lang.taxi.generators.java.TaxiGeneratorExtension
import org.reflections8.Reflections
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.Method


class SpringMvcExtension(private val baseUrl: String) : TaxiGeneratorExtension {

   override fun getClassesToScan(reflections: Reflections, packageName: String): List<Class<*>> {
      val annotationTypes = listOf(
         Component::class.java,
         RestController::class.java
      )

      val annotatedClasses = annotationTypes.flatMap { annotationClass ->
         reflections
            .getTypesAnnotatedWith(annotationClass)
      }
         .filter { it.`package`.name.startsWith(packageName) }


      return annotatedClasses
   }

   override val isServiceType: (Class<*>) -> Boolean
      get() = { clazz -> clazz.isAnnotationPresent(RestController::class.java) }

   companion object {
      internal fun forBaseUrl(baseUrl: String): TaxiGeneratorExtension {
         return SpringMvcExtension(baseUrl)
      }

   }
}


fun String.joinEnsuringOnlyOneSeperator(other: String, seperator: String): String {
   return this.removeSuffix(seperator) + seperator + other.removePrefix(seperator)
}

class MetadataParser : RequestMappingHandlerMapping() {

   public override fun getMappingForMethod(method: Method, handlerType: Class<*>): RequestMappingInfo {
      return super.getMappingForMethod(method, handlerType)
   }
}

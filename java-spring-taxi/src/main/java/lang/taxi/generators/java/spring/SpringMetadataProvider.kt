package lang.taxi.generators.java.spring

import lang.taxi.utils.log
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.Method

object SpringMetadataProvider {
   fun getSpringMethodMetadata(method: Method, javaClass: Class<*>): SpringMethodMetadata {
      val hasSpringMvc = try {
         Class.forName("org.springframework.web.servlet.mvc.method.RequestMappingInfo")
         true
      } catch (e: ClassNotFoundException) {
         false
      }
      val hasSpringFlux = try {
         Class.forName("org.springframework.web.reactive.result.method.RequestMappingInfo")
         true
      } catch (e: ClassNotFoundException) {
         false
      }
      return when {
         hasSpringFlux && hasSpringMvc -> {
            log().info("Found both SpringMVC and SpringFlux on the classpath.  Using SpringMVC dependencies for metadata parsing")
            SpringMvcSpringMetadataProvider.getSpringMethodMetadata(method, javaClass)
         }

         hasSpringMvc -> SpringMvcSpringMetadataProvider.getSpringMethodMetadata(method, javaClass)
         hasSpringFlux -> SpringFluxMetadataProvider.getSpringMethodMetadata(method, javaClass)
         else -> error("Neither SpringMVC or SpringFlux were detected on the classpath.")
      }
   }
}

private object SpringMvcSpringMetadataProvider : RequestMappingHandlerMapping() {
   fun getSpringMethodMetadata(method: Method, handlerType: Class<*>): SpringMethodMetadata {
      val requestMappingInfo = super.getMappingForMethod(method, handlerType)
         ?: error("getMappingForMethod returned null for type ${handlerType.simpleName} and method ${method.name}")
      require(requestMappingInfo.patternValues.isNotEmpty()) { "Expected to find patternValues populated.  This suggests a usage of Spring annotations we haven't considered" }
      val url = requestMappingInfo.patternValues.first()
      return SpringMethodMetadata(
         url,
         requestMappingInfo.methodsCondition.methods.first()
      )
   }
}

private object SpringFluxMetadataProvider :
   org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping() {
   fun getSpringMethodMetadata(method: Method, javaClass: Class<*>): SpringMethodMetadata {
      val mappingInfo = getMappingForMethod(method, javaClass)
         ?: error("No RequestMappingInfo was produced for method ${method.name} on class ${javaClass.simpleName}")
      val url = mappingInfo.patternsCondition.patterns.first().patternString
      val method = mappingInfo.methodsCondition.methods.first()
      return SpringMethodMetadata(url, method)
   }
}

data class SpringMethodMetadata(
   val url: String,
   val requestMethod: RequestMethod
)

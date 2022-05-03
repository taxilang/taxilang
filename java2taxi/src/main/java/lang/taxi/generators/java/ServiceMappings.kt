package lang.taxi.generators.java

import lang.taxi.AnnotatedElementWrapper
import lang.taxi.TypeNames
import lang.taxi.annotations.Operation
import lang.taxi.annotations.ResponseContract
import lang.taxi.annotations.declaresName
import lang.taxi.annotations.qualifiedName
import lang.taxi.services.OperationContract
import lang.taxi.services.Parameter
import lang.taxi.services.Service
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Type
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction

interface ServiceMapper {
   fun getTaxiServices(javaClass: Class<*>, typeMapper: TypeMapper, mappedTypes: MutableSet<Type>): Set<Service>
   fun addServiceExtensions(serviceExtensions: List<ServiceMapperExtension>): ServiceMapper
   fun addOperationExtensions(operationExtensions: List<OperationMapperExtension>): ServiceMapper
}


data class DefaultServiceMapper(
   private val constraintAnnotationMapper: ConstraintAnnotationMapper = ConstraintAnnotationMapper(),
   private val serviceExtensions: List<ServiceMapperExtension> = emptyList(),
   private val operationExtensions: List<OperationMapperExtension> = emptyList()
) : ServiceMapper {

   override fun addServiceExtensions(serviceExtensions: List<ServiceMapperExtension>): DefaultServiceMapper {
      return this.copy(
         serviceExtensions = this.serviceExtensions + serviceExtensions
      )
   }

   override fun addOperationExtensions(operationExtensions: List<OperationMapperExtension>): DefaultServiceMapper {
      return this.copy(
         operationExtensions = this.operationExtensions + operationExtensions
      )
   }

   override fun getTaxiServices(type: Class<*>, typeMapper: TypeMapper, mappedTypes: MutableSet<Type>): Set<Service> {
      val namespace = TypeNames.deriveNamespace(type)
      val serviceName = deriveServiceName(type, namespace)
      val operations = type.methods.filter {
         it.isAnnotationPresent(Operation::class.java)
      }.map { method ->
         val func = method.kotlinFunction
            ?: TODO("I refactored this to use Kotlin functions, must've broken some scenarios where there are no kotlin functions - maybe java?")
         val operationAnnotation = method.getAnnotation(Operation::class.java)
         val name = operationAnnotation.value.orDefault(method.name)

         val params = method.parameters.mapIndexed { index, param ->
            val kotlinParameter = func.valueParameters[index]
            val paramType = typeMapper.getTaxiType(param, mappedTypes, namespace, method)
            val paramAnnotation = param.getAnnotation(lang.taxi.annotations.Parameter::class.java)
            Parameter(
               annotations = emptyList(), // todo,
               type = paramType,
               name = paramAnnotation?.name?.orDefaultNullable(kotlinParameter.name) ?: kotlinParameter.name,
               constraints = parseConstraints(paramAnnotation)
            )
         }
         val returnType = typeMapper.getTaxiType(KTypeWrapper(func.returnType), mappedTypes, namespace, method)
         val operation = lang.taxi.services.Operation(
            name,
            parameters = params,
            annotations = emptyList(), // TODO
            returnType = returnType,
            typeDoc = method.findTypeDoc(),
            contract = OperationContract(
               returnType = returnType,
               returnTypeConstraints = parseConstraints(method.getAnnotation(ResponseContract::class.java))
            ),
            scope = operationAnnotation.scope,
            compilationUnits = listOf(CompilationUnit.unspecified())
         )
         operationExtensions.fold(
            operation,
            { operation, extension -> extension.update(operation, type, method, typeMapper, mappedTypes) })
      }

      val service = serviceExtensions.fold(Service(
         serviceName,
         operations,
         annotations = emptyList(),
         typeDoc = type.findTypeDoc(),
         compilationUnits = listOf(CompilationUnit.unspecified())
      ), { service, extension -> extension.update(service, type, typeMapper, mappedTypes) })
      return setOf(service)
   }

   private fun parseConstraints(contract: ResponseContract?): List<Constraint> {
      if (contract == null) {
         return emptyList()
      }
      return constraintAnnotationMapper.convert(contract)
   }

   private fun parseConstraints(paramAnnotation: lang.taxi.annotations.Parameter?): List<Constraint> {
      if (paramAnnotation == null) {
         return emptyList()
      }
      return constraintAnnotationMapper.convert(paramAnnotation.constraints.toList())
   }


   fun String.orDefaultNullable(default: String?): String? {
      return if (this.isEmpty()) default else this
   }

   fun String.orDefault(default: String): String {
      return if (this.isEmpty()) default else this
   }

   fun deriveServiceName(element: Class<*>, defaultNamespace: String): String {
      if (element.isAnnotationPresent(lang.taxi.annotations.Service::class.java)) {
         val annotation = element.getAnnotation(lang.taxi.annotations.Service::class.java)
         if (annotation.declaresName()) {
            return annotation.qualifiedName(defaultNamespace)
         }
      }

      // If it's an inner class, trim the qualifier
      // This may cause problems with duplicates, but let's encourage
      // peeps to solve that via the DataType annotation.
      val typeName = element.simpleName.split("$").last()
      return "$defaultNamespace.$typeName"
   }
}


/*
This is a wrapper for times when we need to use KType's for discoverying Taxi types
In most scenarios, Kotlin provides a way to get from the Java type to a Kotlin type.
However, some cases this isn't possible (eeg., return types from functions),
so we need to use this special class
 */
class KTypeWrapper(val ktype: KType) : AnnotatedElement, AnnotatedElementWrapper {
   val arguments = ktype.arguments
   private val klass = ktype.classifier!! as KClass<*>
   override val delegate: AnnotatedElement = klass.java

   override fun getAnnotations(): Array<Annotation> {
      return delegate.annotations
   }

   override fun <T : Annotation> getAnnotation(p0: Class<T>): T? {
      return delegate.getAnnotation(p0)
   }

   override fun getDeclaredAnnotations(): Array<Annotation> {
      return delegate.declaredAnnotations
   }


}

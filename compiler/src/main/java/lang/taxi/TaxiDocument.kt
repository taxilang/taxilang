package lang.taxi

import com.google.common.collect.Multimaps
import lang.taxi.functions.Function
import lang.taxi.policies.Policy
import lang.taxi.services.Service
import lang.taxi.types.Annotation
import lang.taxi.types.AnnotationType
import lang.taxi.types.ArrayType
import lang.taxi.types.Arrays
import lang.taxi.types.AttributePath
import lang.taxi.types.CompilationUnit
import lang.taxi.types.EnumType
import lang.taxi.types.ImportableToken
import lang.taxi.types.Named
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias
import lang.taxi.types.View
import lang.taxi.utils.log


fun TaxiParser.QualifiedNameContext.toAttributePath(): AttributePath {
   return AttributePath(this.Identifier().map { it.text })
}

class NamespacedTaxiDocument(
   val namespace: String,
   types: Set<Type>,
   services: Set<Service>,
   policies: Set<Policy>,
   functions: Set<Function>
) : TaxiDocument(types, services, policies, functions)

// Note:  Changed types & services from List<> to Set<>
// as ordering shouldn't matter, only content.
// However, I suspect there was a reason these were Lists, so leaving this note here to remind me
open class TaxiDocument(
   val types: Set<Type>,
   val services: Set<Service>,
   val policies: Set<Policy> = emptySet(),
   val functions: Set<Function> = emptySet(),
   val annotations: Set<Annotation> = emptySet(),
   val views: Set<View> = emptySet()
) {
   private val equality = ImmutableEquality(this, TaxiDocument::types, TaxiDocument::services)
   private val typeMap = types.associateBy { it.qualifiedName }
   private val servicesMap = services.associateBy { it.qualifiedName }
   private val policiesMap = policies.associateBy { it.qualifiedName }
   private val functionsMap = functions.associateBy { it.qualifiedName }
   private val viewMap = views.associateBy { it.qualifiedName }

   /**
    * Collects a list of annotations that were used throughout, but have not been declared.
    * We return annotation qualified names only, not full annotation instances, as the
    * contract for annotations is undefined, so the set of parameters being used is not likely
    * to be consistent.
    */
   val undeclaredAnnotationNames by lazy {
      val annotationsOnTypes = this.types
         .filterIsInstance<ObjectType>()
         .flatMap { type ->
            type.annotations + type.fields.flatMap { it.annotations }
         }
      val annotationsOnServices = this.services
         .flatMap { service ->
            service.annotations + service.operations.flatMap { it.annotations }
         }
      (annotationsOnServices + annotationsOnTypes)
         // find the annotations that don't have types
         .filter { annotation -> annotation.type == null }
         .map { QualifiedName.from(it.qualifiedName) }
         .distinct()
   }

   companion object {
      fun empty(): TaxiDocument {
         return TaxiDocument(emptySet(), emptySet())
      }
   }

   fun importableToken(qualifiedName: String): ImportableToken {
      return when {
         containsType(qualifiedName) -> type(qualifiedName)
         containsFunction(qualifiedName) -> function(qualifiedName)
         else -> error("Importable token $qualifiedName is not defined")
      }
   }

   fun type(qualifiedName: String): Type {
      return type(QualifiedName.from(qualifiedName))
   }

   fun type(qualifiedName: QualifiedName): Type {
      if (Arrays.isArray(qualifiedName)) {
         return when {
            qualifiedName.parameters.isEmpty() -> {
               log().warn("Requested raw array.  This is strongly discouraged.  Tsk Tsk Tsk.")
               ArrayType.untyped()
            }

            qualifiedName.parameters.size == 1 -> {
               val innerType = type(qualifiedName.parameters.first())
               ArrayType(innerType, CompilationUnit.unspecified())
            }

            else -> error("Cannot construct an array with multiple type parameters")
         }
      }
      if (StreamType.isStreamTypeName(qualifiedName)) {
         return when {
            qualifiedName.parameters.isEmpty() -> {
               log().warn("Requested raw stream.  This is strongly discouraged.  Tsk Tsk Tsk.")
               StreamType.untyped()
            }

            qualifiedName.parameters.size == 1 -> {
               val innerType = type(qualifiedName.parameters.first())
               StreamType(innerType, CompilationUnit.unspecified())
            }

            else -> error("Cannot construct an array with multiple type parameters")
         }
      }

      if (PrimitiveType.isPrimitiveType(qualifiedName.toString())) {
         return PrimitiveType.fromDeclaration(qualifiedName.toString())
      }

      return typeMap[qualifiedName.toString()] ?: throw error("No type named $qualifiedName defined")

   }

   // This is a placeholder for when we start to seperate models and types
   fun model(name: String) = objectType(name)

   fun view(name: String) = viewMap[name]

   fun containsImportable(tokenName: String): Boolean {
      return typeMap.containsKey(tokenName) || functionsMap.containsKey(tokenName)
   }

   fun containsType(typeName: String) = typeMap.containsKey(typeName)
   fun containsService(serviceName: String) = servicesMap.containsKey(serviceName)
   fun containsFunction(functionName: String) = functionsMap.containsKey(functionName)

   override fun hashCode() = equality.hash()
   override fun equals(other: Any?) = equality.isEqualTo(other)

   fun toNamespacedDocs(): List<NamespacedTaxiDocument> {
      val typesByNamespace = Multimaps.index(types) { it!!.toQualifiedName().namespace }
      val servicesByNamespace = Multimaps.index(services) { it!!.toQualifiedName().namespace }
      val policiesByNamespace = Multimaps.index(policies) { it!!.toQualifiedName().namespace }
      val functionsByNamespace = Multimaps.index(functions) { it!!.toQualifiedName().namespace }
      val namespaces = typesByNamespace.keySet() + servicesByNamespace.keySet()

      return namespaces.map { namespace ->
         NamespacedTaxiDocument(
            namespace,
            types = typesByNamespace.get(namespace)?.toSet() ?: emptySet(),
            services = servicesByNamespace.get(namespace)?.toSet() ?: emptySet(),
            policies = policiesByNamespace.get(namespace)?.toSet() ?: emptySet(),
            functions = functionsByNamespace.get(namespace)?.toSet() ?: emptySet()
         )
      }
   }

   fun objectType(name: String): ObjectType {
      return type(name) as ObjectType
   }

   fun typeAlias(name: String): TypeAlias {
      return type(name) as TypeAlias
   }

   fun enumType(qualifiedName: String): EnumType {
      return type(qualifiedName) as EnumType
   }

   fun service(qualifiedName: String): Service {
      return servicesMap[qualifiedName] ?: error("Service $qualifiedName is not defined")
   }

   fun function(qualifiedName: String): Function {
      return functionsMap[qualifiedName] ?: error("Function $qualifiedName is not defined")
   }

   fun annotation(qualifiedName: String): AnnotationType {
      return type(qualifiedName) as AnnotationType
   }


   fun policy(qualifiedName: String): Policy {
      return policiesMap[qualifiedName] ?: error("Policy $qualifiedName is not defined")
   }

   fun containsPolicy(qualifiedName: String): Boolean {
      return policiesMap.containsKey(qualifiedName)
   }


   private fun Iterable<CompilationUnit>.declarationSites(): String {
      return this.joinToString { it.source.sourceName }
   }

   fun merge(other: TaxiDocument): TaxiDocument {
      val conflicts: List<Named> = collectConflictingTypes(other) + collectDuplicateServices(other)
      val errors = conflicts.map {
         val site1 = this.type(it.qualifiedName).compilationUnits.declarationSites()
         val site2 = other.type(it.qualifiedName).compilationUnits.declarationSites()
         DocumentStrucutreError("Attempted to redefine types with conflicting definition - ${it.qualifiedName} is defined in the following locations: $site1 which conflicts with the definition at $site2")
      }
      if (errors.isNotEmpty()) {
         throw DocumentMalformedException(errors)
      }

      // TODO : We should be merging where there are extensions in otherwise
      // equal type definitions.
      val duplicateNames = this.types.filter { other.containsType(it.qualifiedName) }.map { it.qualifiedName }

      return TaxiDocument(
         this.types + other.types.filterNot { duplicateNames.contains(it.qualifiedName) },
         this.services + other.services,
         this.policies + other.policies,
         this.functions + other.functions
      )
   }

   private fun collectDuplicateServices(other: TaxiDocument): List<Service> {
      val duplicateServices = this.services.filter { other.containsService(it.qualifiedName) }
      return duplicateServices.filter { it != other.service(it.qualifiedName) }

   }

   private fun collectConflictingTypes(other: TaxiDocument): List<Type> {
      val duplicateTypes = this.types.filter { other.containsType(it.qualifiedName) }
      // TODO : This should consider extensions.
      // If the underlying type definitions are the same, but one adds extensions,
      // that's valid.
      return duplicateTypes.filter { it != other.type(it.qualifiedName) }

   }
}

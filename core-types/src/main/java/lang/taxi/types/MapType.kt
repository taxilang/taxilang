package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.types.PrimitiveType.Companion.INHERITS_FROM_ANY

data class MapType(
   val keyType: Type, val valueType: Type, val source: CompilationUnit,
   override val annotations: List<Annotation> = emptyList()
) : GenericType {

   private val lazyLoadingWrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type> by lazy { getInheritanceGraph() }
   override val inheritsFrom: List<Type> = INHERITS_FROM_ANY
   override val inheritsFromPrimitive: Boolean = false
   override val basePrimitive: PrimitiveType? = null
   override val definitionHash: String? by lazy { lazyLoadingWrapper.definitionHash }
   override val compilationUnits: List<CompilationUnit> = listOf(source)
   override val typeDoc: String = "A collection that holds pairs of objects (keys and values) "
   override val qualifiedName: String = NAME
   override val parameters: List<Type> = listOf(keyType, valueType)
   override fun resolveTypes(typeSystem: TypeProvider): GenericType {
      return this.copy(keyType = typeSystem.getType(keyType.qualifiedName), valueType = typeSystem.getType(valueType.qualifiedName))
   }
   companion object {
      const val NAME = "lang.taxi.Map"
      val qualifiedName = QualifiedName.from(NAME)

      fun isMapTypeName(requestedTypeName: String): Boolean {
         return requestedTypeName == qualifiedName.fullyQualifiedName || requestedTypeName == qualifiedName.typeName
      }
      // Note: Intentionally using fullyQualifiedName, not parameterized name here, since
      // the parameters will affect the name
      fun isMapTypeName(requestedTypeName: QualifiedName):Boolean = isMapTypeName(requestedTypeName.fullyQualifiedName)

      fun untyped(source:CompilationUnit = CompilationUnit.unspecified()) = MapType(PrimitiveType.ANY, PrimitiveType.ANY, source)
   }

   override fun withParameters(parameters: List<Type>): Either<InvalidNumberOfParametersError, GenericType> {
      return if (parameters.size != 2) {
         InvalidNumberOfParametersError.forTypeAndCount(this.toQualifiedName(), 2).left()
      } else {
         this.copy(keyType = parameters[0], valueType = parameters[1]).right()
      }
   }

   override val formatAndZoneOffset: FormatsAndZoneOffset? = null
   override val format: List<String>? = null
   override val offset: Int? = null
   override val typeKind: TypeKind = TypeKind.Type
}

fun Type.isMapType():Boolean {
   return this.allInheritedTypes.any { MapType.isMapTypeName(it.qualifiedName) }
      || this is MapType
}
fun ObjectType.getUnderlyingMapType(): MapType {
   require (this.isMapType()) { "${this.qualifiedName} is not a Map"}
   return this.inheritsFrom.single { MapType.isMapTypeName(it.qualifiedName) } as MapType
}

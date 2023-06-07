package lang.taxi.generators.java

import lang.taxi.TypeNames
import lang.taxi.annotations.*
import lang.taxi.generators.kotlin.AnnotatedTypeAlias
import lang.taxi.generators.kotlin.TypeAliasRegister
import lang.taxi.jvm.common.PrimitiveTypes
import lang.taxi.sources.SourceCode
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.types.Modifier
import lang.taxi.types.Type
import org.jetbrains.annotations.NotNull
import java.lang.reflect.*
import java.lang.reflect.Field
import java.lang.reflect.Parameter
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.jvm.kotlinProperty

interface TypeMapper {
   fun getTaxiType(
      sourceType: Class<*>,
      existingTypes: MutableSet<Type>,
      containingMember: AnnotatedElement? = null
   ): Type {
      val namespace = TypeNames.deriveNamespace(sourceType)
      return getTaxiType(sourceType, existingTypes, namespace)
   }

   fun getTaxiType(
      source: AnnotatedElement,
      existingTypes: MutableSet<Type>,
      defaultNamespace: String,
      containingMember: AnnotatedElement? = null
   ): Type
}

class DefaultTypeMapper(
   private val constraintAnnotationMapper: ConstraintAnnotationMapper = ConstraintAnnotationMapper(),
   private val typeAliasRegister: TypeAliasRegister = TypeAliasRegister.forRegisteredPackages()
) :
   TypeMapper {

   fun MutableSet<Type>.findByName(qualifiedTypeName: String): Type? {
      return this.firstOrNull { it.qualifiedName == qualifiedTypeName }
   }

   // A containingMember is typically a function, which has declared a returnType.
   // Since the annotations can't go directly on the return type, they go on the function itself,
   // meaning we need to evaluate the function when considering the type.
   override fun getTaxiType(
      element: AnnotatedElement,
      existingTypes: MutableSet<Type>,
      defaultNamespace: String,
      containingMember: AnnotatedElement?
   ): Type {
      val elementType = TypeNames.typeFromElement(element)

      val declaresTypeAlias = declaresTypeAlias(element)

      if (isTaxiPrimitiveWithoutAnnotation(element) && !declaresTypeAlias) {
         if (containingMember == null) return PrimitiveTypes.getTaxiPrimitive(elementType.typeName)
         if (isTaxiPrimitiveWithoutAnnotation(containingMember)) {
            // If the type has a DataType annotation, we use that
            // Otherwise, return the primitive
            return PrimitiveTypes.getTaxiPrimitive(elementType.typeName)
         }
      }

      if (declaresTypeAlias) {

         val typeAliasName = getTypeNameOnTypeResolvedToNamespace(element, defaultNamespace)!!
         if (declaredAsImport(element)) {
            return getOrCreateImport(typeAliasName, existingTypes)
         }
      }

      // was: getTargetTypeName(element, defaultNamespace, containingMember)
      // But this is inconsistent, and seemed buggy.
      val targetTypeName = if (containingMember != null) {
         getTypeNameOnTypeResolvedToNamespace(containingMember, defaultNamespace)
      } else {
         getTypeNameOnTypeResolvedToNamespace(element, defaultNamespace)
      } ?: getTargetTypeName(element, defaultNamespace, containingMember)

      if (isCollection(element)) {
         val collectionType = findCollectionType(element, existingTypes, defaultNamespace, containingMember)
         return ArrayType(collectionType, exportedCompilationUnit(element))
      }

      if (containingMember != null && declaresTypeAlias(containingMember)) {
         val typeAliasName = getTypeNameOnTypeResolvedToNamespace(containingMember, defaultNamespace)!!
         return getOrCreateScalarType(containingMember, typeAliasName, existingTypes)
      }

      if (PrimitiveTypes.isClassTaxiPrimitive(elementType)) {
         return if (declaresTypeAlias) {
            getOrCreateScalarType(element, targetTypeName, existingTypes)
         } else {
            PrimitiveTypes.getTaxiPrimitive(elementType)
         }
      }


      val existing = existingTypes.findByName(targetTypeName)
      if (existing != null) {
         return existing
      }

      if (isEnum(element)) {
         val enumType = mapEnumType(element, targetTypeName)
         // Note: We have to mutate the collection of types within the parent
         // loop, as adding objectTypes needs to be able to pre-emptively add empty types
         // before the definition is encountered.
         // This feels ugly, but it works for now.
         existingTypes.add(enumType)
         return enumType
      }
      return mapNewObjectType(element, defaultNamespace, existingTypes)
   }


   private fun getOrCreateImport(typeAliasName: String, existingTypes: MutableSet<Type>): Type {

      val existingType = existingTypes.findByName(typeAliasName)
      return if (existingType != null) {
         existingType
      } else {
         val importedType = UnresolvedImportedType(typeAliasName)
         existingTypes.add(importedType)
         importedType
      }
   }

   private fun declaredAsImport(element: AnnotatedElement): Boolean {
      val dataType =
         getDataTypeFromKotlinTypeAlias(element)?.annotation
            ?: element.getAnnotation(DataType::class.java) ?: return false
      return dataType.imported
   }

   private fun mapEnumType(element: AnnotatedElement, targetTypeName: String): EnumType {
      require(targetTypeName.isNotEmpty()) { "Was passed an empty type name, cannot generate an enum" }
      val enum = TypeNames.typeFromElement(element)
      val enumValues = enum.getDeclaredField("\$VALUES").let {
         // This is a hack, as for some reason in tests enum.enumConstants is returning null.
         it.isAccessible = true
         it.get(null) as Array<Enum<*>>
      }.map {
         EnumValue(
            name = it.name,
            annotations = emptyList(), // TODO : Support annotations on EnumValues when exporting
            qualifiedName = Enums.enumValue(QualifiedName.from(targetTypeName), it.name),
            synonyms = emptyList()

         )
      }

      return EnumType(
         targetTypeName,
         EnumDefinition(
            values = enumValues,
            annotations = emptyList(), // TODO : Support annotations on Enums when exporting
            compilationUnit = exportedCompilationUnit(element),
            basePrimitive = PrimitiveType.STRING // TODO
         )
      )
   }

   private fun isEnum(element: AnnotatedElement) = TypeNames.typeFromElement(element).isEnum

   private fun findCollectionType(
      element: AnnotatedElement,
      existingTypes: MutableSet<Type>,
      defaultNamespace: String,
      containingMember: AnnotatedElement?
   ): Type {
      if (element is KTypeWrapper) {
         val argments = element.arguments
         require(argments.size == 1) { "expected type ${element.ktype} to have exactly 1 argument, but found ${argments.size}" }
         val typeArg = argments.first()
         val argumentType = typeArg.type
            ?: error("Unhandled: The argument type for ${element.ktype} did not return a type - unsure how this can happen")
         return getTaxiType(KTypeWrapper(argumentType), existingTypes, defaultNamespace)
      }


      val collectionType: Class<*> = when (element) {
         is Field -> {
            // returns T of List<T> / Set<T>
            (element.genericType as ParameterizedType).actualTypeArguments[0] as Class<*>
         }

         else -> TODO("Collection types that aren't fields not supported yet - (got $element)")
      }
      return getTaxiType(collectionType, existingTypes, defaultNamespace)
   }

   private fun isCollection(element: AnnotatedElement): Boolean {
      val clazz = TypeNames.typeFromElement(element)
      return (clazz.isArray) || Collection::class.java.isAssignableFrom(clazz)
   }

//    // This is typically for functions who's parameters are
//    // a primitive type (eg., String).  These
//    private fun isImplicitAliasForPrimitiveType(targetTypeName: String, element: AnnotatedElement): Boolean {
//
//    }

   private fun isTaxiPrimitiveWithoutAnnotation(element: AnnotatedElement?): Boolean {
      if (element == null)
         return false
      return (!TypeNames.declaresDataType(element) && PrimitiveTypes.isClassTaxiPrimitive(
         TypeNames.typeFromElement(
            element
         )
      ))
   }

   private fun getOrCreateScalarType(
      element: AnnotatedElement,
      typeName: String,
      existingTypes: MutableSet<Type>
   ): Type {
      val existingAlias = existingTypes.findByName(typeName)
      if (existingAlias != null) {
         return existingAlias
      }
      val compilationUnit = CompilationUnit.ofSource(SourceCode("Exported from annotation", "Annotation"))

      val type = if (element.isCollection()) {
         val collectionType = findCollectionType(
            element,
            existingTypes,
            TypeNames.deriveNamespace(TypeNames.typeFromElement(element)),
            null
         )
         ArrayType(collectionType, compilationUnit)
      } else {
         val underlyingType = TypeNames.typeFromElement(element)
         val inheritsFrom = getInheritedTypes(underlyingType, existingTypes, "")
            .filterNot { it.qualifiedName == typeName } // Don't inherit from ourselves
            .toSet()
         ObjectType(
            typeName,
            ObjectTypeDefinition(
               compilationUnit = compilationUnit,
               inheritsFrom = inheritsFrom
            )
         )
      }
      existingTypes.add(type)
      return type
   }


   private fun declaresTypeAlias(element: AnnotatedElement): Boolean {
      return getTypeNameOnTypeResolvedToNamespace(element, "") != null
   }

   fun getDeclaredTypeName(element: AnnotatedElement): AnnotatedTypeAlias? {
      return getDataTypeFromKotlinTypeAlias(element)
   }
//   fun getDataTypeAnnotation(element: AnnotatedElement): DataType? {
//      return getDataTypeFromKotlinTypeAlias(element)
//         ?: element.getAnnotation(DataType::class.java) ?: return null
//   }

   private fun getTypeNameOnTypeResolvedToNamespace(element: AnnotatedElement, defaultNamespace: String): String? {

      // MP: 5-Jun-23: Swapped the order of evaluation.
      // Look to see if there's a type alias before checking for the data type annotation.
      // Found that on type aliases for enums, we were getting the underlying enum, ignoring
      // the typealias.
      val dataTypeFromKotlinTypeAlias = getDataTypeFromKotlinTypeAlias(element)
      if (dataTypeFromKotlinTypeAlias != null) {
         return if (dataTypeFromKotlinTypeAlias.annotation.declaresName()) {
            dataTypeFromKotlinTypeAlias.annotation.qualifiedName(defaultNamespace)
         } else {
            dataTypeFromKotlinTypeAlias.qualifiedName
         }
      }

      val elementAnnotation = element.getAnnotation(DataType::class.java)
      return if (elementAnnotation != null && elementAnnotation.declaresName()) {
         elementAnnotation.qualifiedName(defaultNamespace)
      } else null
   }

   private fun getDataTypeFromKotlinTypeAlias(element: Any): AnnotatedTypeAlias? {
      val dataType = when (element) {

         is KTypeWrapper -> typeAliasRegister.findDataType(element.ktype)
         is Field -> typeAliasRegister.findDataType(element.kotlinProperty)
         is Method -> typeAliasRegister.findDataType(element.kotlinFunction)
         is Parameter -> typeAliasRegister.findDataType(element.kotlinParam)
         is Class<*> -> {
            element.getAnnotation(DataType::class.java)?.let { dataType ->
               if (dataType.declaresName()) {
                  val nameFromAnnotation = dataType.qualifiedName("")
                  // Design choice:
                  // Previously, we used to prepend the java package name
                  // to the name declared in the value, if there was no namespace present.
                  // This was an attempt to be convenient for the user and reduce boilerplate.
                  // However, in practice, this seems to violate the "least surprise" principal,
                  // so I'm reverting to "Just use what the annotation told you" until there's a compelling
                  // reason not to.
                  AnnotatedTypeAlias(dataType.value, dataType)
               } else null

            }
         }

         else -> null
      }
      return dataType
   }

   private fun mapNewObjectType(
      element: AnnotatedElement,
      defaultNamespace: String,
      existingTypes: MutableSet<Type>
   ): ObjectType {
      val name = getTargetTypeName(element, defaultNamespace)
      val fields = mutableSetOf<lang.taxi.types.Field>()
      val modifiers = getTypeLevelModifiers(element)

      val inheritance =
         getInheritedTypes(TypeNames.typeFromElement(element), existingTypes, defaultNamespace) // TODO
      val definition = ObjectTypeDefinition(
         fields = fields,
         annotations = emptySet(),
         modifiers = modifiers,
         inheritsFrom = inheritance,
         formatAndOffset = null,
         typeDoc = element.findTypeDoc(),
         compilationUnit = exportedCompilationUnit(element)
      )
      val objectType = ObjectType(name, definition)

      // Note: Add the type while it's empty, and then collect the fields.
      // This allows circular references to resolve
      existingTypes.add(objectType)
      fields.addAll(this.mapTaxiFields(lang.taxi.TypeNames.typeFromElement(element), defaultNamespace, existingTypes))
      return objectType
   }

   private fun getTypeLevelModifiers(element: AnnotatedElement): List<Modifier> {
      val modifiers = mutableListOf<Modifier>();
      if (element.getAnnotation(ParameterType::class.java) != null) {
         modifiers.add(Modifier.PARAMETER_TYPE)
      }
      // Odd boolean comparison to account for nulls
      if (element.getAnnotation(DataType::class.java)?.closed == true) {
         modifiers.add(Modifier.CLOSED)
      }

      val parentModifiers = if (element is Parameter) {
         getTypeLevelModifiers(element.type)
      } else {
         emptyList()
      }
      return modifiers + parentModifiers
   }

   private fun getInheritedTypes(
      clazz: Class<*>,
      existingTypes: MutableSet<Type>,
      defaultNamespace: String
   ): Set<Type> {
      val inheritedTypes = (clazz.interfaces.toList() + listOf(clazz.superclass)).filterNotNull()

      val inheritedTaxiTypes = inheritedTypes
         .filter { it.isAnnotationPresent(DataType::class.java) }
         .map { inheritedType -> getTaxiType(inheritedType, existingTypes, defaultNamespace) as ObjectType }
         .toSet()
      return if (inheritedTaxiTypes.isEmpty() && PrimitiveTypes.isClassTaxiPrimitive(clazz)) {
         setOf(PrimitiveTypes.getTaxiPrimitive(clazz))
      } else {
         inheritedTaxiTypes
      }
   }

   private fun exportedCompilationUnit(element: AnnotatedElement) =
      CompilationUnit.ofSource(SourceCode("Exported from type $element", "Exported"))

   private fun getTargetTypeName(
      element: AnnotatedElement,
      defaultNamespace: String,
      containingMember: AnnotatedElement? = null
   ): String {
      val rawType = TypeNames.typeFromElement(element)

      if (containingMember != null && TypeNames.declaresDataType(containingMember)) {
         val resolvedName = getTypeNameOnTypeResolvedToNamespace(element, defaultNamespace)
         if (resolvedName != null) {
            return resolvedName
         }
      }
      if (!TypeNames.declaresDataType(element) && PrimitiveTypes.isClassTaxiPrimitive(rawType)) {
         return PrimitiveTypes.getTaxiPrimitive(rawType.typeName).qualifiedName
      }
      return TypeNames.deriveTypeName(element, defaultNamespace)
   }


   fun mapTaxiFields(
      javaClass: Class<*>,
      defaultNamespace: String,
      existingTypes: MutableSet<Type>
   ): List<lang.taxi.types.Field> {
      val kClass = javaClass.kotlin
      val mappedProperties = kClass.memberProperties
         .filter { !propertyIsInheritedFromMappedSupertype(kClass, it) }
         .map { property ->
            val annotatedElement = when {
               property.javaField != null -> property.javaField
               property.javaGetter != null -> property.javaGetter
               else -> TODO()
            } as AnnotatedElement

            // Not sure what's going on here.
            // Sometimes the annotations appear on the Kotlin property,
            // sometimes they're on the underlying java type.
            // (Always for data class with a val).
            // For now, check both.  This might result in duplicates,
            // but I'll cross that bridge when that happens
            val constraints = listOf(property.findAnnotation<Constraint>()).filterNotNull() +
               annotatedElement.getAnnotationsByType(Constraint::class.java).distinct().toList()


            val mappedConstraints = constraintAnnotationMapper.convert(constraints)
            val fieldType = getTaxiType(
               KTypeWrapper(property.returnType, delegate = annotatedElement),
               existingTypes,
               defaultNamespace
            )
            lang.taxi.types.Field(
               name = property.name,
               typeDoc = annotatedElement.findTypeDoc(),
               type = fieldType,
               nullable = property.returnType.isMarkedNullable,
               annotations = mapAnnotations(property),
               constraints = mappedConstraints,
               compilationUnit = CompilationUnit.unspecified()
            )
         }
      return mappedProperties
//
//        return javaClass.declaredFields.map { field ->
//
//            val constraints = field.getAnnotationsByType(Constraint::class.java).toList()
//
//            val mappedConstraints = constraintAnnotationMapper.convert(constraints)
//            lang.taxi.types.Field(name = field.name,
//                    type = getTaxiType(field, existingTypes, defaultNamespace),
//                    nullable = isNullable(field),
//                    annotations = mapAnnotations(field),
//                    constraints = mappedConstraints)
//        }
   }

   // Indicates if the field is actually present as an inherited / overridden
   // member from an superType.  (ie., a field declared in an interface, that's overridden on a class)
   // In Taxi, we want those types to be declared on the supertype (since they're all innherited).
   private fun propertyIsInheritedFromMappedSupertype(
      kClass: KClass<out Any>,
      property: KProperty1<out Any, Any?>
   ): Boolean {
      val isDeclaredOnSupertype = kClass.allSupertypes.filter { superType ->
         val classifier = superType.classifier
         when (classifier) {
            is KClass<*> -> classifier.findAnnotation<DataType>() != null
            else -> false
         }
      }.any { superType ->
         val classifer = superType.classifier as KClass<*>
         classifer.declaredMemberProperties.any { it.name == property.name }
      }
      return isDeclaredOnSupertype
   }

   private fun mapAnnotations(property: KProperty<*>): List<Annotation> {
      // TODO -- as with mapAnnotations(field)
      return emptyList();
   }

   private fun mapAnnotations(field: java.lang.reflect.Field): List<Annotation> {
      // TODO
      // Note: When I do implement this, consider that fields
      // will have @Constraint annotations, which shouldn't be handled here,
      // but are instead handled in the constraint section
      return emptyList()
   }

   private fun isNullable(field: java.lang.reflect.Field): Boolean {
      val isNotNull = field.isAnnotationPresent(NotNull::class.java) ||
         return field.isAnnotationPresent(javax.validation.constraints.NotNull::class.java)
      return !isNotNull
   }
}

fun AnnotatedElement.findTypeDoc(): String? {
   return when {
      this.isAnnotationPresent(DataType::class.java) -> this.getAnnotation(DataType::class.java).documentation
      this.isAnnotationPresent(lang.taxi.annotations.Parameter::class.java) -> this.getAnnotation(lang.taxi.annotations.Parameter::class.java).documentation
      this.isAnnotationPresent(Operation::class.java) -> this.getAnnotation(Operation::class.java).documentation
      this.isAnnotationPresent(Service::class.java) -> this.getAnnotation(Service::class.java).documentation
      else -> null
   }
}

private val Parameter.kotlinParam: KParameter?
   get() {
      val method = this.declaringExecutable as Method
      val paramIndex = method.parameters.indexOf(this)
      val function = method.kotlinFunction ?: return null
      val param = function.parameters[paramIndex + 1] // Note that Kotlin functions have the call site at index 0
      return param
   }

private fun AnnotatedElement.isCollection(): Boolean {
   val type = TypeNames.typeFromElement(this)
   return Collection::class.java.isAssignableFrom(type)
}


fun KProperty<*>.toAnnotatedElement(): KTypeWrapper {
   return KTypeWrapper(this.returnType)
}

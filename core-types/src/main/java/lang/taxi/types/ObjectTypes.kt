package lang.taxi.types

import arrow.core.Either
import lang.taxi.ImmutableEquality
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.AccessorWithDefault
import lang.taxi.expressions.Expression
import lang.taxi.expressions.TypeExpression
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.ConstraintTarget
import lang.taxi.utils.quotedIfNecessary
import kotlin.reflect.KProperty1

data class FieldExtension(
   val name: String,
   override val annotations: List<Annotation>,
   val refinedType: Type?,
   val defaultValue: Any?
) : Annotatable

data class ObjectTypeExtension(
   val annotations: List<Annotation> = emptyList(),
   val fieldExtensions: List<FieldExtension> = emptyList(),
   val typeDoc: String? = null,
   override val compilationUnit: CompilationUnit
) : TypeDefinition {
   fun fieldExtensions(name: String): List<FieldExtension> {
      return this.fieldExtensions.filter { it.name == name }
   }
}

data class ObjectTypeDefinition(
   val fields: Set<Field> = emptySet(),
   val annotations: Set<Annotation> = emptySet(),
   val modifiers: List<Modifier> = emptyList(),
   val inheritsFrom: Set<Type> = emptySet(),
   val format: List<String>? = null,
   val pattern: String? = null,
   val formattedInstanceOfType: Type? = null,
   val calculatedInstanceOfType: Type? = null,
//   @Deprecated("Formulas are replaced by functions and expressions")
//   val calculation: Formula? = null,
   val offset: Int? = null,
   val isAnonymous: Boolean = false,
   val typeKind: TypeKind = TypeKind.Type,
   val expression: Expression? = null,
   override val typeDoc: String? = null,
   override val compilationUnit: CompilationUnit
) : TypeDefinition, Documented {
   private val equality = ImmutableEquality(
      this,
      ObjectTypeDefinition::fields,
      ObjectTypeDefinition::annotations,
      ObjectTypeDefinition::modifiers,
      ObjectTypeDefinition::inheritsFrom,
   )

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

internal fun <T, R> KProperty1<T, Collection<R>>.toSet(): T.() -> Set<R>? {
   val prop = this
   return {
      prop.get(this).toSet()
   }
}

enum class FieldModifier(val token: String) {
   CLOSED("closed")
}

enum class Modifier(val token: String) {

   // A Parameter type indicates that the object
// is used when constructing requests,
// and that frameworks should freely construct
// these types based on known values.
   PARAMETER_TYPE("parameter"),

   /**
    * Closed types can not be decomponsed into their individual parts,
    * they only make sense as a single, cohesive unit.
    */
   CLOSED("closed");

   companion object {
      fun fromToken(token: String): Modifier {
         return Modifier.values().first { it.token == token }
      }
   }

}

data class ObjectType(
   override val qualifiedName: String,
   override var definition: ObjectTypeDefinition?,
   override val extensions: MutableList<ObjectTypeExtension> = mutableListOf()
) : UserType<ObjectTypeDefinition, ObjectTypeExtension>, Annotatable, Documented {
   companion object {
      fun undefined(name: String): ObjectType {
         return ObjectType(name, definition = null)
      }
   }

   val isUnionTypeWrapper: Boolean
      get() {
         return if (isDefined && definition!!.expression is TypeExpression) {
            val typeExpression = definition!!.expression as TypeExpression
            return typeExpression.type is UnionType
         } else {
            false
         }
      }
   val wrappedUnionType: UnionType?
      get() {
         return if (isUnionTypeWrapper) {
            val typeExpression = definition!!.expression as TypeExpression
            return typeExpression.type as UnionType
         } else {
            null
         }

      }

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type>
      get() {
         return if (isDefined) wrapper.allInheritedTypes else emptySet()
      }

   override val inheritsFromPrimitive: Boolean
      get() {
         return if (isDefined) wrapper.inheritsFromPrimitive else false
      }
   override val basePrimitive: PrimitiveType?
      get() {
         return if (isDefined) wrapper.basePrimitive else null
      }
   override val definitionHash: String?
      get() {
         return if (isDefined) wrapper.definitionHash else null
      }

   val expression: Expression?
      get() {
         return definition?.expression
      }

   /**
    * Indicates if this type (excluding any inherited types)
    * declares a format.
    *
    * Note that the format property will return formats
    * from inherited types.
    */
   override val declaresFormat: Boolean
      get() {
         return this.definition?.format != null
      }

   override val format: List<String>?
      get() {
         return if (this.definition?.format != null) {
            this.definition?.format
         } else {
            val inheritedFormats = this.inheritsFrom.filter { it.format != null }
            when {
               inheritedFormats.isEmpty() -> null
               inheritedFormats.size == 1 -> inheritedFormats.first().format
               else -> error("Multiple formats found in inheritence - this is an error")
            }
         }
      }

   override val anonymous: Boolean
      get() = this.definition?.isAnonymous ?: false

   override val formattedInstanceOfType: Type?
      get() = this.definition?.formattedInstanceOfType

   val calculatedInstanceOfType: Type?
      get() = this.definition?.calculatedInstanceOfType

   override val referencedTypes: List<Type>
      get() {
         val fieldTypes = this.allFields.map { it.type }
         val inheritedTypes = this.definition?.inheritsFrom?.toList() ?: emptyList()
         return (fieldTypes + inheritedTypes).filterIsInstance<UserType<*, *>>()
      }

   override fun addExtension(extension: ObjectTypeExtension): Either<ErrorMessage, ObjectTypeExtension> {
      val error = verifyMaxOneTypeRefinementPerField(extension.fieldExtensions)
      if (error != null) return Either.left(error)
      this.extensions.add(extension)

      return Either.right(extension)
   }

//   override val calculation: Formula?
//      get() = definition?.calculation

   private fun verifyMaxOneTypeRefinementPerField(fieldExtensions: List<FieldExtension>): ErrorMessage? {
      fieldExtensions.filter { it.refinedType != null }
         .forEach { proposedExtension ->
            val existingRefinedType = fieldExtensions(proposedExtension.name).mapNotNull { it.refinedType }
            if (existingRefinedType.isNotEmpty() && proposedExtension.refinedType != null) {
               return "Cannot refinement field ${proposedExtension.name} to ${proposedExtension.refinedType!!.qualifiedName} as it has already been refined to ${existingRefinedType.first().qualifiedName}"
            }
         }
      return null;
   }

   override val inheritsFrom: Set<Type>
      get() {
         return definition?.inheritsFrom ?: emptySet()
      }

   val inheritsFromNames: List<String>
      get() {
         return inheritsFrom.map { it.qualifiedName }
      }

   override fun toString(): String {
      return qualifiedName
   }

   val modifiers: List<Modifier>
      get() {
         return this.definition?.modifiers ?: emptyList()
      }

   val inheritedFields: List<Field>
      get() {
         return allInheritedTypes
            .filterIsInstance<ObjectType>()
            .flatMap { it.fields }
      }

   val allFields: List<Field>
      get() {
         return inheritedFields + fields
      }

   override val typeDoc: String?
      get() {
         return listOfNotNull(this.definition?.typeDoc).plus(this.extensions.mapNotNull { it.typeDoc })
            .joinToString("\n")
      }
   val fields: List<Field>
      get() {
         return this.definition?.fields?.map { field ->
            val fieldExtensions = fieldExtensions(field.name)
            val collatedAnnotations = field.annotations + fieldExtensions.annotations()
            val (refinedType, defaultValue) = fieldExtensions
               .asSequence()
               .mapNotNull { refinement -> refinement.refinedType?.let { refinement.refinedType to refinement.defaultValue } }
               .firstOrNull() ?: field.type to null
            field.copy(annotations = collatedAnnotations, type = refinedType, defaultValue = defaultValue)
         } ?: emptyList()
      }

   override val annotations: List<Annotation>
      get() {
         val collatedAnnotations = this.extensions.flatMap { it.annotations }.toMutableList()
         definition?.annotations?.forEach { collatedAnnotations.add(it) }
         return collatedAnnotations.toList()
      }

   private fun fieldExtensions(fieldName: String): List<FieldExtension> {
      return this.extensions.flatMap { it.fieldExtensions(fieldName) }
   }

   fun hasField(name: String): Boolean = allFields.any { it.name == name }
   fun field(name: String): Field = allFields.first { it.name == name }
   fun annotation(name: String): Annotation = annotations.first { it.qualifiedName == name }

   /**
    * Returns a list of field references for any fields on this type, or
    * any of it's fields which are assignable to the requested type.
    *
    * Assignable indicates that the field either is the requested type, or is a superclass
    * of the requested type
    *
    * FieldReferences indicate the path from the base object (this) through fields
    * to access the available field.
    */
   fun fieldReferencesAssignableTo(
      type: Type,
      considerTypeParameters: Boolean = true,
      typeChecker: TypeChecker = TypeChecker.DEFAULT,
      includePrimitives: Boolean = false
   ): List<FieldReference> {
      return this.fieldReferencesAssignableTo(
         type,
         considerTypeParameters,
         typeChecker,
         baseObject = this,
         parentFields = emptyList(),
         includePrimitives = includePrimitives
      )
   }

   private fun fieldReferencesAssignableTo(
      type: Type,
      considerTypeParameters: Boolean = true,
      typeChecker: TypeChecker = TypeChecker.DEFAULT,
      baseObject: ObjectType,
      parentFields: List<Field>,
      includePrimitives: Boolean = false
   ): List<FieldReference> {
      return this.fields
         .filter { field ->
            // Generally, when asking "is this field assignable to that type?",
            // we don't want to consider fields that have primitive types, as they're
            // not really good candidates.
            if (field.type is PrimitiveType) {
               includePrimitives
            } else {
               true
            }
         }
         .flatMap { field ->
            when {
               // Top-level fields
               field.type.isAssignableTo(type, considerTypeParameters, typeChecker) -> listOf(
                  FieldReference(
                     baseObject,
                     parentFields + field
                  )
               )
               field.type is ObjectType -> {
                  field.type.fieldReferencesAssignableTo(
                     type,
                     considerTypeParameters,
                     typeChecker,
                     baseObject,
                     parentFields = parentFields + field,
                     includePrimitives = includePrimitives
                  )
               }
               else -> emptyList()
            }
         }
   }

   @Deprecated("Use fieldReferencesAssignableTo instead", replaceWith = ReplaceWith("fieldReferencesAssignableTo"))
   fun fieldsWithType(typeName: QualifiedName): List<Field> {
      return this.fields.filter { applicableQualifiedNames(it.type).contains(typeName) }
   }

   @Deprecated("Use fieldReferencesAssignableTo instead", replaceWith = ReplaceWith("fieldReferencesAssignableTo"))
   fun applicableQualifiedNames(type: Type): List<QualifiedName> {
      return type.inheritsFrom.map { it.toQualifiedName() }.plus(type.toQualifiedName())
   }

   override val offset: Int?
      get() {
         return if (this.definition?.offset != null) {
            this.definition?.offset
         } else {
            val inheritedOffsets = this.inheritsFrom.filter { it.offset != null }
            when {
               inheritedOffsets.isEmpty() -> null
               inheritedOffsets.size == 1 -> inheritedOffsets.first().offset
               else -> error("Multiple formats found in inheritence - this is an error")
            }
         }
      }

   override val typeKind: TypeKind?
      get() = definition?.typeKind

}


interface AnnotationProvider {
   fun toAnnotation(): Annotation
}

fun Iterable<AnnotationProvider>.toAnnotations(): List<Annotation> {
   return this.map { it.toAnnotation() }
}

interface NameTypePair {
   // Relaxed to Nullable to support Parameters, which don't mandate names
   val name: String?
   val type: Type

}

data class Annotation(
   val name: String,
   val parameters: Map<String, Any?> = emptyMap(),
   val type: AnnotationType? = null
) : TaxiStatementGenerator {
   constructor(type: AnnotationType, parameters: Map<String, Any?>) : this(type.qualifiedName, parameters, type)

   // For compatability.  Should probably migrate to using qualifiedName in
   // the constructor to be consistent.
   val qualifiedName: String = name

   fun parameter(name: String): Any? {
      return parameters[name]
   }

   override fun asTaxi(): String {
      val parameterTaxi = parameters.map { (name, value) ->
         if (value != null) {
            "$name = ${value.quotedIfNecessary()}"
         } else {
            name
         }
      }.joinToString(", ")
      return if (parameterTaxi.isNotEmpty()) {
         """@$name($parameterTaxi)"""
      } else {
         """@$name"""
      }
   }
}

/**
 * A path to a specific attribute, which may be any arbitrary number
 * of fields dep
 */
data class FieldReference(
   val baseType: ObjectType,
   val path: List<Field>
) {
   val description = "${baseType.toQualifiedName().typeName}." + path.joinToString(".") { it.name }
}

data class Field(
   override val name: String,
   override val type: Type,
   val nullable: Boolean = false,
   val modifiers: List<FieldModifier> = emptyList(),
   override val annotations: List<Annotation> = emptyList(),
   override val constraints: List<Constraint> = emptyList(),
   // TODO : Can we fold readCondition into accessor?
   // exploring with ConditionalAccessor
   val accessor: Accessor? = null,
   val readExpression: FieldSetExpression? = null,
   override val typeDoc: String? = null,
   // TODO : This feels wrong - what's the relationship between this and the
   //  defaults served by accessors?
   // These default values are set by field extensions.
   // Need to standardise.
   val defaultValue: Any? = null,
//   val formula: Formula? = null,
   // populated for Anonymous field definitions used in views. Example:
   // orderId: Order::SentOrderId
   // For above, field name is 'orderId', field type is 'SentOrderId' and memberSource is 'Order'
   // it is set to null for all other cases.
   val memberSource: QualifiedName? = null,
   // Defines types that should be used to limit the scope when
   // projecting this field in a query.
   // eg:
   //             findAll { Transaction[] } as {
   //               items : TransactionItem -> {     // <-----This thing.  Only use a TransactionItem to generate this field
   //                  sku : ProductSku
   //                  size : ProductSize
   //               }[]
   //            }[]
//   val projectionScopeTypes: List<Type> = emptyList(),
   override val compilationUnit: CompilationUnit
) : Annotatable, ConstraintTarget, Documented, NameTypePair, TokenDefinition {

   override val description: String = "field $name"

   // This needs to be stanrdardised with the defaultValue above, which comes from
   // extensions
   val accessorDefault = if (accessor is AccessorWithDefault) accessor.defaultValue else null


   // For equality - don't compare on the type (as this can cause stackOverflow when the type is an Object type)
   private val typeName = type.qualifiedName
   private val equality =
      ImmutableEquality(this, Field::name, Field::typeName, Field::nullable, Field::annotations, Field::constraints)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()


}



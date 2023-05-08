package lang.taxi.services.operations.constraints

import lang.taxi.ImmutableEquality
import lang.taxi.Operator
import lang.taxi.types.*
import lang.taxi.utils.prependIfAbsent
import lang.taxi.utils.quotedIfNecessary


/**
 * Indicates that an attribute of a parameter (which is an Object type)
 * must have a relationship to a specified value.
 *
 * The property may be identified either by it's type (preferred), or it's field name.
 * Use of field names is discouraged, as it leads to tightly coupled models.
 * eg:
 * Given Money(amount:Decimal, currency:String),
 * could express that Money.currency must have a value of 'GBP'
 *
 * Note - it's recommended to use TypedFieldConstantValueConstraint,
 * as that allows expressions which aren't bound to field name contracts,
 * making them more polymorphic
 */
@Deprecated("Migrate to using ExpressionConstraint instead, which is the more generic form of this concept.  It's not implemented yet, but it's the direction of travel")
open class PropertyToParameterConstraint(
   val propertyIdentifier: PropertyIdentifier,
   val operator: Operator = Operator.EQUAL,
   val expectedValue: ValueExpression,
   override val compilationUnits: List<CompilationUnit>
) : Constraint, FilterExpression {
   override fun asTaxi(): String {
      return "${propertyIdentifier.taxi} ${operator.symbol} ${expectedValue.taxi}"
   }

   private val equality = ImmutableEquality(
      this,
      PropertyToParameterConstraint::propertyIdentifier,
      PropertyToParameterConstraint::operator,
      PropertyToParameterConstraint::expectedValue
   )

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

}

sealed class PropertyIdentifier(val description: String, val taxi: String) {

   /**
    * Indicates if this identifier resolves to the same field as the other
    */
   fun resolvesTheSameAs(other: PropertyIdentifier, target: ObjectType): Boolean {
      return this.resolveAgainst(target) == other.resolveAgainst(target)
   }

   abstract fun resolveAgainst(target: ObjectType): Field?
}

// Identifies a property by it's name
data class PropertyFieldNameIdentifier(val name: AttributePath) :
   PropertyIdentifier("field ${name.path}", name.path.prependIfAbsent("this.")) {
   constructor(fieldName: String) : this(AttributePath.from(fieldName))

   override fun resolveAgainst(target: ObjectType): Field? {
      // When we come to implement this, we'll need to build navigation within a typed object using dot-paths
      // eg: foo.bar.baz
      // Things to cosndier:
      // We produce a path using ObjectType.getDescendantPathsOfType()
      // The path should be compatible.
      // Also, there's already path-property navigation implemented in Vyne's
      // TypedInstance.  We should use the same implementation.
      require(name.parts.size == 1) { "Path ${name.path} is not supported - support for multiple lenght parts is not yet implemented" }
      return if (target.hasField(name.path)) {
         target.field(name.path)
      } else {
         null
      }
   }
}

// TODO : Syntax here is still up for discussion.  See OperationContextSpec
data class PropertyTypeIdentifier(val type: Type) :
   PropertyIdentifier("type ${type.toQualifiedName().parameterizedName}", type.toQualifiedName().parameterizedName) {
   override fun resolveAgainst(target: ObjectType): Field? {
      val candidates = target.fieldReferencesAssignableTo(type)
      fun fieldFromReference(fieldReference: FieldReference):Field {
         // See PropertyFieldNameIdentifier.resolveAgainst for considerations when implementing this.
         // (The two should be implemented together)
         require(fieldReference.path.size == 1) { "Nested paths are not yet supported (found ${fieldReference.path.joinToString(".")})" }
         return fieldReference.path.single()
      }
      return when (candidates.size) {
         0 -> null
         1 -> fieldFromReference(candidates.single())
         else -> {
            // Do any of the candidates match exactly?
            val exactMatch = candidates.singleOrNull { fieldReference -> fieldFromReference(fieldReference).type == target }
            if (exactMatch != null) {
               fieldFromReference(exactMatch)
            } else {
               error("${type.toQualifiedName().parameterizedName} is ambiguous on type ${target.toQualifiedName().parameterizedName} - found ${candidates.size} matches: ${candidates.joinToString { it.description }}")
            }
         }
      }
   }
}

sealed class ValueExpression(val taxi: String)

// TODO : This won't work with numbers - but neither does the parsing.  Need to fix that.
data class ConstantValueExpression(val value: Any) : ValueExpression(value.quotedIfNecessary())
data class RelativeValueExpression(val path: AttributePath) : ValueExpression(path.path) {
   constructor(attributeName: String) : this(AttributePath.from(attributeName))
}

data class ArgumentExpression(val argument: ArgumentSelector) : ValueExpression(argument.asTaxi())

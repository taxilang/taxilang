package lang.taxi.types

/**
 * A set of fields with additional conditional mapping logic
 * applied
 */
data class ConditionalFieldSet(val fields: List<Field>, val expression: FieldSetExpression)

interface FieldSetExpression : TaxiStatementGenerator

data class CalculatedFieldSetExpression(
   val operand1: FieldReferenceSelector,
   val operand2: FieldReferenceSelector,
   val operator: FormulaOperator
) : FieldSetExpression {
   override fun asTaxi(): String = "${operand1.asTaxi()} ${operator.symbol} ${operand2.asTaxi()}"
}

data class WhenFieldSetCondition(
   val selectorExpression: WhenSelectorExpression,
   val cases: List<WhenCaseBlock>
) : FieldSetExpression {
   override fun asTaxi(): String {
      return """when (${selectorExpression.asTaxi()}) {
   ${cases.joinToString("\n") { it.asTaxi() }}
}
      """.trimMargin()
   }
}

interface WhenSelectorExpression : TaxiStatementGenerator

data class AccessorExpressionSelector(
   val accessor: Accessor,
   val declaredType: Type
) : WhenSelectorExpression {
   override fun asTaxi(): String {
      TODO("Not yet implemented")
   }
}

data class FieldReferenceSelector(val fieldName: String) : WhenSelectorExpression {
   override fun asTaxi(): String = "this.$fieldName"
}

data class WhenCaseBlock(
   val matchExpression: WhenCaseMatchExpression,
   val assignments: List<AssignmentExpression>
) : TaxiStatementGenerator {
   override fun asTaxi(): String {
      val assignmentStatement = if (assignments.size > 1) {
         """{
   ${assignments.joinToString("\n") { it.asTaxi() }}
         }""".trimMargin()
      } else assignments.first().asTaxi()
      return "${matchExpression.asTaxi()} -> $assignmentStatement"
   }

   fun getAssignmentFor(fieldName: String): FieldAssignmentExpression {
      return assignments
         .filterIsInstance<FieldAssignmentExpression>()
         .firstOrNull { it.fieldName == fieldName } ?: error("No assignment exists for field $fieldName")
   }

   /**
    * Expects that a single InlineAssignmentExpression is present,
    * and will error if not.
    */
   fun getSingleAssignment(): InlineAssignmentExpression {
      require(assignments.size == 1) { "Cannot call getSingleAssignment() when there are multiple assignments present.  You should call getAssignmentFor(..) instead" }
      require(assignments[0] is InlineAssignmentExpression) { "Expected single assignment to be an InlineAssignmentExpression, but found ${assignments[0]::class.simpleName}" }
      return assignments[0] as InlineAssignmentExpression
   }
}

sealed class AssignmentExpression : TaxiStatementGenerator {
   abstract val assignment: ValueAssignment
}

/**
 * An assignment for a specific field.
 * Used when mapping a when { } block for mulitple fields, to indicate which field is going to
 * be assigned
 */
data class FieldAssignmentExpression(val fieldName: String, override val assignment: ValueAssignment) : AssignmentExpression() {
   override fun asTaxi(): String = "$fieldName ${assignment.asTaxi()}"
}

/**
 * An assignment where the field is implied
 * Used when mapping a when block for a single field, and the field name is handled higher
 * in the AST.
 */
data class InlineAssignmentExpression(override val assignment: ValueAssignment) : AssignmentExpression() {
   override fun asTaxi(): String = assignment.asTaxi()
}


interface WhenCaseMatchExpression : TaxiStatementGenerator
class ReferenceCaseMatchExpression(val reference: String) : WhenCaseMatchExpression {
   override fun asTaxi(): String = reference // I don't think this is right...
}

class LiteralCaseMatchExpression(val value: Any) : WhenCaseMatchExpression {
   override fun asTaxi(): String {
      return when (value) {
         is String -> value.quoted()
         else -> value.toString()
      }
   }
}

internal fun String.quoted(): String {
   return "\"$this\""
}

object ElseMatchExpression : WhenCaseMatchExpression {
   override fun asTaxi(): String = "else"
}

interface ValueAssignment : TaxiStatementGenerator
data class ScalarAccessorValueAssignment(val accessor: Accessor) : ValueAssignment {
   override fun asTaxi(): String = ""
}

data class DestructuredAssignment(val assignments: List<FieldAssignmentExpression>) : ValueAssignment {
   override fun asTaxi(): String {
      return assignments.joinToString("\n") { it.asTaxi() }
   }
}

data class ReferenceAssignment(val reference: String) : ValueAssignment {
   override fun asTaxi(): String = reference
}

data class LiteralAssignment(val value: Any) : ValueAssignment {
   override fun asTaxi(): String {
      return when (value) {
         is String -> value.quoted()
         else -> value.toString()
      }
   }
}

data class EnumValueAssignment(val enum: EnumType, val enumValue: EnumValue) : ValueAssignment {
   override fun asTaxi(): String = "${enum.qualifiedName}.${enumValue.name}"
}

object NullAssignment : ValueAssignment {
   override fun asTaxi(): String = "null"
}

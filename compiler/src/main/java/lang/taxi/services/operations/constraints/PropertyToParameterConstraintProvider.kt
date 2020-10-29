package lang.taxi.services.operations.constraints

import arrow.core.Either
import arrow.core.flatMap
import lang.taxi.*
import lang.taxi.services.Operation
import lang.taxi.services.OperationContract
import lang.taxi.services.Parameter
import lang.taxi.types.*
import lang.taxi.utils.leftOr
import lang.taxi.utils.log
import org.antlr.v4.runtime.ParserRuleContext

/**
 * We use an AST for parsing constraints as we need to support these in both
 * Taxi and VyneQL.  However, the Antlr generation tree makes using the same code
 * in both contexts very difficult, as it regenerates the classes in a different namespace.
 * This means even though the classes are generated from the exact same source,
 * we can't pass them into the constraint provider code.
 * Therefore, we parse a simple AST, which makes reuse possible
 */
data class PropertyToParameterConstraintAst(
   val lhs: AstLhs,
   val operator: String,
   val rhs: AstRhs
) {
   data class AstRhs(
      val literal: Any?,
      val attributePath: AttributePath?
   )

   data class AstLhs(
      val lhsPropertyFieldNameQualifier: String?,
      val typeOrFieldQualifiedName: String
   )
}

class PropertyToParameterConstraintProvider : ValidatingConstraintProvider {
   override fun applies(constraint: Constraint): Boolean {
      return constraint is PropertyToParameterConstraint
   }

   override fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget, constraintDeclarationSite: Compiled): List<CompilationError> {
      val attributeConstraint = constraint as PropertyToParameterConstraint
      val validationResult = getUnderlyingType(when (target) {
         is Field -> target.type
         is Parameter -> target.type
         is OperationContract -> target.returnType
         else -> throw IllegalArgumentException("Cannot validate constraint on target of type ${target.javaClass.name}")
      }, constraint, target)
         .mapLeft { listOf(it) }
         .flatMap { constrainedType ->
            val lhsValidationError = when (attributeConstraint.propertyIdentifier) {
               is PropertyFieldNameIdentifier -> validate(constraint.propertyIdentifier as PropertyFieldNameIdentifier, constrainedType, constraint)
               is PropertyTypeIdentifier -> validate(constraint.propertyIdentifier as PropertyTypeIdentifier, constrainedType, constraint)
            }

            val rhsValidationError = when (attributeConstraint.expectedValue) {
               is ConstantValueExpression -> null // Not sure that to validate here?
               is RelativeValueExpression -> validate(constraint.expectedValue as RelativeValueExpression, target, constraint, constraintDeclarationSite)
            }

            val errors = listOfNotNull(lhsValidationError, rhsValidationError)
            if (errors.isEmpty()) {
               Either.right(constraint)
            } else {
               Either.left(errors)
            }
         }

      return validationResult.leftOr(emptyList())
   }

   private fun validate(relativeValueExpression: RelativeValueExpression, target: ConstraintTarget, constraint: PropertyToParameterConstraint, constraintDeclarationSite: Compiled): CompilationError? {
      return if (target !is OperationContract) {
         // TODO :  Need to understand what other scnearios would look like
         CompilationError(constraint, "These types of constraints can only be applied to return types of operations at present")
      } else {
         if (constraintDeclarationSite !is Operation) {
            CompilationError(constraint, "Expected that constraint would be declared in an operation.  This is likely a bug in the compiler")
         } else {
            if (relativeValueExpression.path.canResolve(constraintDeclarationSite.parameters)) {
               null
            } else {
               CompilationError(constraint, "Operation ${constraintDeclarationSite.name} does not declare a parameter with name ${relativeValueExpression.path.path}")
            }
         }
      }
   }

   private fun validate(property: PropertyTypeIdentifier, constrainedType: ObjectType, constraint: Constraint): CompilationError? {
      val fields = constrainedType.fieldsWithType(property.type)
      return when {
         fields.isEmpty() -> CompilationError(constraint, "Type ${constrainedType.qualifiedName} does not have a field with type ${property.type}")
         fields.size > 1 -> {
            log().warn(fields.joinToString { it.name })
            CompilationError(constraint, "Type ${constrainedType.qualifiedName} has multiple fields with type ${property.type}.  This is ambiguous, and the constraint is invalid.")
         }
         else -> null
      }
   }

   private fun validate(propertyName: PropertyFieldNameIdentifier, constrainedType: ObjectType, constraint: Constraint): CompilationError? {
      val propertyPath = propertyName.name.path
      return if (!constrainedType.hasField(propertyPath)) { // TODO : This should take the path directly, and evaluate nested paths
         CompilationError(constraint, "Type ${constrainedType.qualifiedName} does not contain a property $propertyPath")
      } else {
         null
      }
   }

   // Unwraps type aliases, if present
   private fun getUnderlyingType(type: Type, constraint: PropertyToParameterConstraint, target: ConstraintTarget): Either<CompilationError, ObjectType> {
      return when (type) {
         is TypeAlias -> getUnderlyingType(type.aliasType!!, constraint, target)
         is ArrayType -> getUnderlyingType(type.type, constraint, target)
         is ObjectType -> Either.right(type)
         else -> Either.left(MalformedConstraint.from("Constraint for ${constraint.propertyIdentifier.description} on ${target.description} is malformed - constraints are only supported on Object types.", constraint))
      }
   }

   override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
      return constraint.propertyToParameterConstraintExpression() != null
   }

   fun build(ast: PropertyToParameterConstraintAst, type: Type, typeResolver: NamespaceQualifiedTypeResolver, context: ParserRuleContext): Either<CompilationError, Constraint> {
      // Could be either a type name or a field name
      val operator = Operator.parse(ast.operator)

      return parseLhs(ast.lhs, typeResolver, context).flatMap { propertyIdentifier ->
         parseRhs(ast.rhs).map { valueExpression ->
            PropertyToParameterConstraint(propertyIdentifier, operator, valueExpression, context.toCompilationUnits())
         }
      }
   }

   override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<CompilationError, Constraint> {
      val ast = parseToAst(constraint.propertyToParameterConstraintExpression())
      return build(ast,type,typeResolver,constraint)
   }

   /**
    * We use an AST for parsing constraints as we need to support these in both
    * Taxi and VyneQL.  However, the Antlr generation tree makes using the same code
    * in both contexts very difficult, as it regenerates the classes in a different namespace.
    * This means even though the classes are generated from the exact same source,
    * we can't pass them into the constraint provider code.
    * Therefore, we parse a simple AST, which makes reuse possible
    */
   private fun parseToAst(constraint: TaxiParser.PropertyToParameterConstraintExpressionContext): PropertyToParameterConstraintAst {
      val astLhs = constraint.propertyToParameterConstraintLhs().let { lhs ->
         val typeOrFieldQualifiedName = lhs.qualifiedName().asDotJoinedPath()
         val propertyFieldNameQualifier = lhs.propertyFieldNameQualifier()?.text
         PropertyToParameterConstraintAst.AstLhs(propertyFieldNameQualifier, typeOrFieldQualifiedName)
      }

      val astRhs = constraint.propertyToParameterConstraintRhs().let { rhs ->
         val literal = rhs.literal()?.value()
         val attributePath = rhs.qualifiedName()?.asDotJoinedPath()?.let { AttributePath.from(it) }
         PropertyToParameterConstraintAst.AstRhs(literal,attributePath)
      }

      val operator = constraint.comparisonOperator().text
      return PropertyToParameterConstraintAst(astLhs,operator,astRhs)
   }

   private fun parseLhs(lhs: PropertyToParameterConstraintAst.AstLhs, typeResolver: NamespaceQualifiedTypeResolver, context: ParserRuleContext): Either<CompilationError, PropertyIdentifier> {
      return when {
         lhs.lhsPropertyFieldNameQualifier != null -> {
            Either.right(PropertyFieldNameIdentifier(AttributePath.from(lhs.typeOrFieldQualifiedName.removePrefix("this."))))
         }
         else -> typeResolver.resolve(lhs.typeOrFieldQualifiedName, context).map { propertyType -> PropertyTypeIdentifier(propertyType.toQualifiedName()) }
      }
   }

   private fun parseRhs(rhs: PropertyToParameterConstraintAst.AstRhs): Either<CompilationError, ValueExpression> {
      return when {
         rhs.literal != null -> Either.right(ConstantValueExpression(rhs.literal))
         rhs.attributePath != null -> Either.right(RelativeValueExpression(rhs.attributePath))
         else -> error("Unhandled scenario parsing rhs of constraint")
      }
   }

}

private fun TaxiParser.QualifiedNameContext.asDotJoinedPath(): String {
   return this.Identifier().joinToString(".") { it.text }
}

package lang.taxi.compiler

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.functions.FunctionAccessor
import lang.taxi.functions.FunctionModifiers
import lang.taxi.types.Accessor
import lang.taxi.types.AndExpression
import lang.taxi.types.AssignmentExpression
import lang.taxi.types.CalculatedModelAttributeFieldSetExpression
import lang.taxi.types.ComparisonExpression
import lang.taxi.types.ComparisonOperand
import lang.taxi.types.ConditionalAccessor
import lang.taxi.types.ConstantEntity
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.EnumType
import lang.taxi.types.Field
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.LiteralAssignment
import lang.taxi.types.LogicalExpression
import lang.taxi.types.ModelAttributeFieldReferenceEntity
import lang.taxi.types.ModelAttributeReferenceSelector
import lang.taxi.types.ModelAttributeTypeReferenceAssignment
import lang.taxi.types.NullAssignment
import lang.taxi.types.ObjectType
import lang.taxi.types.OrExpression
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.ScalarAccessorValueAssignment
import lang.taxi.types.Type
import lang.taxi.types.ViewBodyDefinition
import lang.taxi.types.WhenCaseBlock
import lang.taxi.types.WhenFieldSetCondition

/**
 * As our current view resolution strategy is handled through casks (cask generates a new model and a 'database view' per view)
 * We need a very strict validator to avoid run-time cask issues.
 */
class ViewValidator(private val viewName: String) {
   private var currentViewBodyType: ObjectType? = null
   fun validateViewBodyDefinitions(
      bodyDefinitions: List<Pair<ViewBodyDefinition, TaxiParser.FindBodyContext>>,
      viewCtx: TaxiParser.ViewDeclarationContext): Either<List<CompilationError>, List<ViewBodyDefinition>> {
      if (bodyDefinitions.size == 1 && bodyDefinitions.first().first.joinType == null) {
         bodyDefinitions.right()
      }

      val compilationErrors = mutableListOf<CompilationError>()
      val viewFieldTypes = mutableListOf<List<PrimitiveType>>()
      bodyDefinitions.forEach { (bodyDefinition, bodyCtx) ->
         val viewBodyType = bodyDefinition.viewBodyType
         if (viewBodyType == null) {
            compilationErrors.add(CompilationError(bodyCtx.start, "Invalid View Definition - find with an empty body (as {} block is missing"))
         } else {
            val fields = (viewBodyType as ObjectType).fields
            if (fields.isEmpty()) {
               compilationErrors.add(CompilationError(bodyCtx.start, "Invalid View Definition - empty as {} block"))
            }

            val nonQueryableFunctions = fields.mapNotNull { it.accessor }
               .filterIsInstance(FunctionAccessor::class.java)
               .filter { functionAccessor -> !functionAccessor.function.modifiers.contains(FunctionModifiers.Query) }

            if (nonQueryableFunctions.isNotEmpty()) {
               compilationErrors.add(CompilationError(bodyCtx.start, "Invalid View Definition. Functions, ${nonQueryableFunctions.joinToString { it.function.qualifiedName }}, must be queryable"))
            }
            viewFieldTypes.add(fromViewBodyFieldDefinitionToPrimitiveFields(fields, bodyDefinition, bodyCtx, compilationErrors))
         }
      }

      if (viewFieldTypes.size > 1) {
         val firstViewDefinitionPrimitiveFields = viewFieldTypes.removeAt(0)
         if (viewFieldTypes.any { firstViewDefinitionPrimitiveFields != it }) {
            compilationErrors.add(CompilationError(viewCtx.start, "Invalid View Definition - individual find expressions should have compatible 'as' blocks."))
         }
      }
      val typesInViewFindDefinitions = mutableSetOf<Type>()
      bodyDefinitions.map { (bodyDefinitions, _) ->
         typesInViewFindDefinitions.add(bodyDefinitions.bodyType)
         bodyDefinitions.joinType?.let { typesInViewFindDefinitions.add(it) }
      }


      val fieldValidations = if (compilationErrors.isEmpty()) validateAccessors(bodyDefinitions, typesInViewFindDefinitions) else emptyList()

      return if (compilationErrors.isEmpty() && fieldValidations.isEmpty()) {
         bodyDefinitions.map { it.first }.right()
      } else {
         compilationErrors.plus(fieldValidations).left()
      }
   }

   private fun validateAccessors(
      bodyDefinitions: List<Pair<ViewBodyDefinition, TaxiParser.FindBodyContext>>,
      typesInViewFindDefinitions: Set<Type>): List<CompilationError> {
      val compilationErrors = mutableListOf<CompilationError>()
      bodyDefinitions.forEach { (viewBodyDefinition, findBodyContext) ->
         currentViewBodyType = (viewBodyDefinition.viewBodyType!! as ObjectType)
         currentViewBodyType!!
            .fields
            .map { field -> validateField(field.accessor, findBodyContext, compilationErrors, typesInViewFindDefinitions) }
      }
      return compilationErrors.toList()
   }

   private fun validateField(
      accessor: Accessor?,
      ctx: TaxiParser.FindBodyContext, compilationErrors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>):
      List<CompilationError> {
      if (accessor != null) {
         when (accessor) {
            is ConditionalAccessor -> {
               when (val accessorExpression = accessor.expression) {
                   is WhenFieldSetCondition -> {
                      accessorExpression.cases.forEach { caseBlock ->
                         processWhenCaseMatchExpression(caseBlock, ctx, compilationErrors, typesInViewFindDefinitions)
                      }
                   }
                  is CalculatedModelAttributeFieldSetExpression -> {
                     val op1 = accessorExpression.operand1
                     val op2 = accessorExpression.operand2
                     validateValidateSourceAndField(op1.memberSource, op1.memberType, ctx, compilationErrors, typesInViewFindDefinitions)
                     validateValidateSourceAndField(op2.memberSource, op2.memberType, ctx, compilationErrors, typesInViewFindDefinitions)
                  }
                  else -> compilationErrors.add(
                     CompilationError(
                        ctx.start,
                        "Invalid Find Body in View Definition -  is not valid to use!")
                  )
               }

            }

            is FunctionAccessor -> {
               validateFunction(accessor, ctx, compilationErrors, typesInViewFindDefinitions)
            }

            else -> {
               compilationErrors.add(
                  CompilationError(
                     ctx.start,
                     "unexpected accessor for view field: $accessor")
               )
            }
         }
      }
      return compilationErrors
   }

   private fun processWhenCaseMatchExpression(
      caseBlock: WhenCaseBlock,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>) {
      val caseExpression = caseBlock.matchExpression
      val assignments = caseBlock.assignments
      when (caseExpression) {
         is ComparisonExpression -> processComparisonExpression(caseExpression, ctx, errors, typesInViewFindDefinitions)
         is AndExpression -> validateLogicalExpressions(caseExpression.left, caseExpression.right, ctx, errors, typesInViewFindDefinitions)
         is OrExpression -> validateLogicalExpressions(caseExpression.left, caseExpression.right, ctx, errors, typesInViewFindDefinitions)
         is ElseMatchExpression -> processAssignments(assignments, ctx, errors, typesInViewFindDefinitions)
         // this is also covered by compiler.
         else -> errors.add(
            CompilationError(
               ctx.start,
               "Invalid When Case!")
         )
      }
   }

   private fun validateLogicalExpressions(
      left: LogicalExpression,
      right: LogicalExpression,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>) {
      if (left !is ComparisonExpression && right !is ComparisonExpression) {
         errors.add(
            CompilationError(
               ctx.start,
               "Both left and right hand-side of AND / OR should be a comparison expression!")
         )
      } else {
         processComparisonExpression(left as ComparisonExpression, ctx, errors, typesInViewFindDefinitions)
         processComparisonExpression(right as ComparisonExpression, ctx, errors, typesInViewFindDefinitions)
      }
   }

   private fun processComparisonExpression(
      caseExpression: ComparisonExpression,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>) {
      processComparisonOperand(caseExpression.left, ctx, errors, typesInViewFindDefinitions)
      processComparisonOperand(caseExpression.right, ctx, errors, typesInViewFindDefinitions)
   }

   private fun processComparisonOperand(
      operand: ComparisonOperand,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>) {
      when (operand) {
         is ModelAttributeFieldReferenceEntity -> validateValidateSourceAndField(operand.source, operand.fieldType, ctx, errors, typesInViewFindDefinitions, false)
         is ConstantEntity -> {
         }
         else -> errors.add(
            CompilationError(
               ctx.start,
               "Invalid When Case!")
         )
      }
   }

   private fun processAssignments(
      assignments: List<AssignmentExpression>,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>) {
      if (assignments.size != 1) {
         errors.add(CompilationError(
            ctx.start,
            "only 1 assignment is supported for a when case!"))
      }

      val assignment = assignments.first()

      if (assignment !is InlineAssignmentExpression) {
         errors.add(CompilationError(
            ctx.start,
            "only inline assignment is supported for a when case!"))
      }

      when (val expression = assignment.assignment) {
         is ModelAttributeTypeReferenceAssignment -> validateModelAttributeTypeReference(expression, ctx, errors, typesInViewFindDefinitions)
         is LiteralAssignment -> { }
         is NullAssignment -> { }
         is ScalarAccessorValueAssignment -> {
            when (val accessor = expression.accessor) {
               is ConditionalAccessor -> validateField(accessor, ctx, errors, typesInViewFindDefinitions)
               is FunctionAccessor -> {
                  validateFunction(accessor, ctx, errors, typesInViewFindDefinitions)
               }
               else -> errors.add(CompilationError(
                  ctx.start,
                  "Unsupported Assignment for a when case!"))

            }
         }
         // This is also covered by the compiler.
         else -> errors.add(CompilationError(
            ctx.start,
            "Unsupported Assignment for a when case!"))
      }
   }

   private fun validateModelAttributeTypeReference(
      expression: ModelAttributeTypeReferenceAssignment,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>) {
      return validateValidateSourceAndField(expression.source, expression.type, ctx, errors, typesInViewFindDefinitions)
   }

   private fun validateValidateSourceAndField(
      source: QualifiedName,
      type: Type,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      canUseViewName: Boolean = true) {
      if (!canUseViewName && (source.fullyQualifiedName == viewName)) {
         errors.add(
            CompilationError(
               ctx.start,
               "Invalid context for ${source.typeName}::${type.toQualifiedName().typeName}. You can not use a reference to View on the left hand side of a case when expression.")
         )
      }
      if (source.fullyQualifiedName != viewName &&
         !typesInViewFindDefinitions.map { it.toQualifiedName() }.contains(source)) {
         errors.add(
            CompilationError(
               ctx.start,
               "A Reference to ${source.fullyQualifiedName} is invalid in this view definition context")
         )
      }
      val sourceFields = if (source.fullyQualifiedName == viewName) {
         currentViewBodyType?.fields
      } else {
         (typesInViewFindDefinitions
            .firstOrNull { it.toQualifiedName() == source } as? ObjectType?)?.fields
      }

      if (!hasFieldWithGivenType(sourceFields, type)) {
         errors.add(
            CompilationError(
               ctx.start,
               "${source.typeName}::${type.toQualifiedName().typeName} is invalid as ${source.typeName} does not have a field with type ${type.toQualifiedName().typeName}")
         )
      }
   }

   private fun hasFieldWithGivenType(fields: List<Field>?, fieldType: Type): Boolean {
     return  fields?.firstOrNull { field ->
         val actualFieldType = field.type.formattedInstanceOfType ?: field.type
         actualFieldType == fieldType
      } != null
   }


   private fun validateFunction(accessor: FunctionAccessor,
                                ctx: TaxiParser.FindBodyContext,
                                errors: MutableList<CompilationError>,
                                typesInViewFindDefinitions: Set<Type>) {
      if (!accessor.function.modifiers.contains(FunctionModifiers.Query)) {
         errors.add(CompilationError(
            ctx.start,
            "Only query function are allowed."))
      }
      val inputs = accessor.inputs
      inputs.forEach { input ->
         if (input !is ModelAttributeReferenceSelector) {
            errors.add(CompilationError(
               ctx.start,
               "Function input must in SourceType::FieldType format."))
         } else {
            validateValidateSourceAndField(input.memberSource, input.memberType, ctx, errors, typesInViewFindDefinitions)
         }
      }
   }

   private fun fromViewBodyFieldDefinitionToPrimitiveFields(
      fields: List<Field>,
      bodyDefinition: ViewBodyDefinition,
      bodyCtx: TaxiParser.FindBodyContext,
      compilationErrors: MutableList<CompilationError>): List<PrimitiveType> {
      return fields.mapNotNull { viewBodyFieldDefinition ->
         when (val res = validateViewBodyFieldDefinition(viewBodyFieldDefinition, bodyDefinition, bodyCtx)) {
            is Either.Left -> {
               compilationErrors.add(res.a)
               null
            }
            is Either.Right -> res.b
         }
      }
   }

   private fun validateViewBodyFieldDefinition(
      viewBodyField: Field,
      bodyDefinition: ViewBodyDefinition,
      findBodyCtx: TaxiParser.FindBodyContext): Either<CompilationError, PrimitiveType> {

      return when {
         // orderDate: OrderDateTime
         viewBodyField.memberSource == null -> getPrimitiveTypeForField(viewBodyField.type, viewBodyField.name, findBodyCtx)
         // orderDate: Order::OrderDateTime
         (viewBodyField.memberSource != null &&
            (viewBodyField.memberSource == bodyDefinition.bodyType.toQualifiedName()) ||
            (viewBodyField.memberSource == bodyDefinition.joinType?.toQualifiedName())) -> getPrimitiveTypeForField(viewBodyField.type, viewBodyField.name, findBodyCtx)

         else -> CompilationError(findBodyCtx.start, "Invalid View Definition - ${viewBodyField.memberSource} is not valid to use!")
            .left()
      }
   }

   // ensure that field type can be mapped to a database column
   // as currently we restrict view to sql Views. Remove this when it is relaxed.
   private fun getPrimitiveTypeForField(fieldType: Type, fieldName: String, findBodyCtx: TaxiParser.FindBodyContext):
      Either<CompilationError, PrimitiveType> {
      return when {
         PrimitiveType.isAssignableToPrimitiveType(fieldType) -> {
            PrimitiveType.getUnderlyingPrimitive(fieldType).right()
         }
         fieldType is EnumType -> {
            PrimitiveType.STRING.right()
         }
         fieldType.inheritsFrom.size == 1 -> {
            getPrimitiveTypeForField(fieldType.inheritsFrom.first(), fieldName, findBodyCtx)
         }
         else -> CompilationError(findBodyCtx.start, "type ${fieldType.qualifiedName} for field $fieldName is not allowed in view definitions").left()
      }
   }
}

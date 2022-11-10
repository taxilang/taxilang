package lang.taxi.compiler

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.ConditionalAccessor
import lang.taxi.expressions.Expression
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.functions.FunctionModifiers
import lang.taxi.functions.stdlib.Coalesce
import lang.taxi.functions.vyne.aggregations.SumOver
import lang.taxi.types.AssignmentExpression
import lang.taxi.types.CalculatedModelAttributeFieldSetExpression
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.EnumType
import lang.taxi.types.Field
import lang.taxi.types.FormulaOperator
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.ModelAttributeReferenceSelector
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
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
      viewCtx: TaxiParser.ViewDeclarationContext
   ): Either<List<CompilationError>, List<ViewBodyDefinition>> {
      if (bodyDefinitions.size == 1 && bodyDefinitions.first().first.joinType == null) {
         bodyDefinitions.right()
      }

      val compilationErrors = mutableListOf<CompilationError>()
      val viewFieldTypes = mutableListOf<List<PrimitiveType>>()
      bodyDefinitions.forEach { (bodyDefinition, bodyCtx) ->
         val viewBodyType = bodyDefinition.viewBodyType
         if (viewBodyType == null) {
            compilationErrors.add(
               CompilationError(
                  bodyCtx.start,
                  "Invalid View Definition - find with an empty body (as {} block is missing"
               )
            )
         } else {
            val fields = (viewBodyType as ObjectType).fields
            if (fields.isEmpty()) {
               compilationErrors.add(CompilationError(bodyCtx.start, "Invalid View Definition - empty as {} block"))
            }

            val invalidFunctions = fields.mapNotNull { it.accessor }
               .filterIsInstance(FunctionAccessor::class.java)
               .filter { functionAccessor -> !isValidFunctionForViewField(functionAccessor) }

            if (invalidFunctions.isNotEmpty()) {
               compilationErrors.add(
                  CompilationError(
                     bodyCtx.start,
                     "Invalid View Definition. Functions, only ${validViewFieldFunctionNames()} are allowed."
                  )
               )
            }
            viewFieldTypes.add(
               fromViewBodyFieldDefinitionToPrimitiveFields(
                  fields,
                  bodyDefinition,
                  bodyCtx,
                  compilationErrors
               )
            )
         }
      }

      if (viewFieldTypes.size > 1) {
         val firstViewDefinitionPrimitiveFields = viewFieldTypes.removeAt(0)
         if (viewFieldTypes.any { firstViewDefinitionPrimitiveFields != it }) {
            compilationErrors.add(
               CompilationError(
                  viewCtx.start,
                  "Invalid View Definition - individual find expressions should have compatible 'as' blocks."
               )
            )
         }
      }
      val typesInViewFindDefinitions = mutableSetOf<Type>()
      bodyDefinitions.map { (bodyDefinitions, _) ->
         typesInViewFindDefinitions.add(bodyDefinitions.bodyType)
         bodyDefinitions.joinType?.let { typesInViewFindDefinitions.add(it) }
      }


      val fieldValidations = if (compilationErrors.isEmpty()) validateAccessors(
         bodyDefinitions,
         typesInViewFindDefinitions
      ) else emptyList()

      return if (compilationErrors.isEmpty() && fieldValidations.isEmpty()) {
         bodyDefinitions.map { it.first }.right()
      } else {
         compilationErrors.plus(fieldValidations).left()
      }
   }

   private fun validateAccessors(
      bodyDefinitions: List<Pair<ViewBodyDefinition, TaxiParser.FindBodyContext>>,
      typesInViewFindDefinitions: Set<Type>
   ): List<CompilationError> {
      val compilationErrors = mutableListOf<CompilationError>()
      bodyDefinitions.forEach { (viewBodyDefinition, findBodyContext) ->
         currentViewBodyType = (viewBodyDefinition.viewBodyType!! as ObjectType)
         currentViewBodyType!!
            .fields
            .map { field ->
               validateField(
                  field.accessor,
                  findBodyContext,
                  compilationErrors,
                  typesInViewFindDefinitions,
                  viewBodyDefinition.viewBodyType
               )
            }
      }
      return compilationErrors.toList()
   }

   private fun validateField(
      accessor: Accessor?,
      ctx: TaxiParser.FindBodyContext, compilationErrors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      viewBodyType: Type?
   ):
      List<CompilationError> {
      if (accessor != null) {
         when {
            accessor is OperatorExpression && accessor.lhs is ModelAttributeReferenceSelector && accessor.rhs is ModelAttributeReferenceSelector -> {
               val op1 = accessor.lhs as ModelAttributeReferenceSelector
               val op2 = accessor.rhs as ModelAttributeReferenceSelector
               validateValidateSourceAndField(
                  op1.memberSource,
                  op1.targetType,
                  ctx,
                  compilationErrors,
                  typesInViewFindDefinitions,
                  viewBodyType
               )
               validateValidateSourceAndField(
                  op2.memberSource,
                  op2.targetType,
                  ctx,
                  compilationErrors,
                  typesInViewFindDefinitions,
                  viewBodyType
               )
            }

            accessor is ConditionalAccessor -> {
               when (val accessorExpression = accessor.expression) {
                  is WhenFieldSetCondition -> {
                     accessorExpression.cases.forEach { caseBlock ->
                        processWhenCaseMatchExpression(
                           caseBlock,
                           ctx,
                           compilationErrors,
                           typesInViewFindDefinitions,
                           viewBodyType
                        )
                     }
                  }

                  is CalculatedModelAttributeFieldSetExpression -> {
                     val op1 = accessorExpression.operand1
                     val op2 = accessorExpression.operand2
                     validateValidateSourceAndField(
                        op1.memberSource,
                        op1.targetType,
                        ctx,
                        compilationErrors,
                        typesInViewFindDefinitions,
                        viewBodyType
                     )
                     validateValidateSourceAndField(
                        op2.memberSource,
                        op2.targetType,
                        ctx,
                        compilationErrors,
                        typesInViewFindDefinitions,
                        viewBodyType
                     )
                  }

                  else -> compilationErrors.add(
                     CompilationError(
                        ctx.start,
                        "Invalid Find Body in View Definition -  is not valid to use!"
                     )
                  )
               }

            }

            accessor is FunctionAccessor -> {
               validateFunction(accessor, ctx, compilationErrors, typesInViewFindDefinitions, viewBodyType)
            }

            else -> {
               compilationErrors.add(
                  CompilationError(
                     ctx.start,
                     "unexpected accessor for view field: $accessor"
                  )
               )
            }
         }
      }
      return compilationErrors
   }

   private fun validateOperatorExpression(
      caseExpression: OperatorExpression,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      viewBodyType: Type?
   ) {
      when {
         caseExpression.operator.isComparisonOperator() -> processComparisonExpression(
            caseExpression,
            ctx,
            errors,
            typesInViewFindDefinitions,
            viewBodyType
         )


         caseExpression.operator.isLogicalOperator() -> validateLogicalExpressions(
            caseExpression.lhs,
            caseExpression.rhs,
            ctx,
            errors,
            typesInViewFindDefinitions,
            viewBodyType
         )

         else -> errors.add(
            CompilationError(
               ctx.start,
               "Invalid When Case!"
            )
         )
      }
   }

   private fun processWhenCaseMatchExpression(
      caseBlock: WhenCaseBlock,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      viewBodyType: Type?
   ) {
      val caseExpression = caseBlock.matchExpression
      val assignments = caseBlock.assignments
      when (caseExpression) {
         is OperatorExpression -> {
            validateOperatorExpression(caseExpression, ctx, errors, typesInViewFindDefinitions, viewBodyType)
         }

         is ElseMatchExpression -> processAssignments(
            assignments,
            ctx,
            errors,
            typesInViewFindDefinitions,
            viewBodyType
         )
         // this is also covered by compiler.
         else -> errors.add(
            CompilationError(
               ctx.start,
               "Invalid When Case!"
            )
         )
      }
   }

   private fun validateLogicalExpressions(
      left: Expression,
      right: Expression,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      viewBodyType: Type?
   ) {
      val lhsOperator = (left as? OperatorExpression)?.operator
      val rhsOperator = (right as? OperatorExpression)?.operator
      when {
         left is OperatorExpression && rhsOperator == FormulaOperator.LogicalAnd -> {
            processComparisonExpression(left, ctx, errors, typesInViewFindDefinitions, viewBodyType)
            validateLogicalExpressions(right.lhs, right.rhs, ctx, errors, typesInViewFindDefinitions, viewBodyType)
         }

         left is OperatorExpression && rhsOperator == FormulaOperator.LogicalOr -> {
            processComparisonExpression(left, ctx, errors, typesInViewFindDefinitions, viewBodyType)
            validateLogicalExpressions(right.lhs, right.rhs, ctx, errors, typesInViewFindDefinitions, viewBodyType)
         }

         right is OperatorExpression && lhsOperator == FormulaOperator.LogicalAnd -> {
            processComparisonExpression(right, ctx, errors, typesInViewFindDefinitions, viewBodyType)
            validateLogicalExpressions(left.lhs, left.rhs, ctx, errors, typesInViewFindDefinitions, viewBodyType)
         }

         right is OperatorExpression && lhsOperator == FormulaOperator.LogicalOr -> {
            processComparisonExpression(right, ctx, errors, typesInViewFindDefinitions, viewBodyType)
            validateLogicalExpressions(left.lhs, left.rhs, ctx, errors, typesInViewFindDefinitions, viewBodyType)
         }

         left is OperatorExpression && right is OperatorExpression -> {
            processComparisonExpression(left, ctx, errors, typesInViewFindDefinitions, viewBodyType)
            processComparisonExpression(right, ctx, errors, typesInViewFindDefinitions, viewBodyType)
         }


         else -> errors.add(
            CompilationError(
               ctx.start,
               "Both left and right hand-side of AND / OR should be a comparison expression!"
            )
         )
      }
   }

   private fun processComparisonExpression(
      caseExpression: OperatorExpression,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      viewBodyType: Type?
   ) {
      processComparisonOperand(caseExpression.lhs, ctx, errors, typesInViewFindDefinitions, viewBodyType)
      processComparisonOperand(caseExpression.rhs, ctx, errors, typesInViewFindDefinitions, viewBodyType)
   }

   private fun processComparisonOperand(
      operand: Expression,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      viewBodyType: Type?
   ) {
      when (operand) {
         is ModelAttributeReferenceSelector -> validateValidateSourceAndField(
            operand.memberSource,
            operand.targetType,
            ctx,
            errors,
            typesInViewFindDefinitions,
            viewBodyType,
            false
         )

         is LiteralExpression -> {
         }

         else -> errors.add(
            CompilationError(
               ctx.start,
               "Invalid When Case!"
            )
         )
      }
   }

   private fun processAssignments(
      assignments: List<AssignmentExpression>,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      viewBodyType: Type?
   ) {
      if (assignments.size != 1) {
         errors.add(
            CompilationError(
               ctx.start,
               "only 1 assignment is supported for a when case!"
            )
         )
      }

      val assignment = assignments.first()

      if (assignment !is InlineAssignmentExpression) {
         errors.add(
            CompilationError(
               ctx.start,
               "only inline assignment is supported for a when case!"
            )
         )
      }

      when (val expression = assignment.assignment) {
         is LiteralExpression -> {}
         is OperatorExpression -> {
            // Example: (OrderSent::RequestedQuantity - OrderView::CumulativeQty)

         }

         is ModelAttributeReferenceSelector -> validateValidateSourceAndField(
            expression.memberSource,
            expression.targetType,
            ctx,
            errors,
            typesInViewFindDefinitions,
            viewBodyType
         )

         is FunctionExpression -> validateFunction(
            expression.function,
            ctx,
            errors,
            typesInViewFindDefinitions,
            viewBodyType
         )
         //validateModelAttributeTypeReference(expression, ctx, errors, typesInViewFindDefinitions, viewBodyType)
//         is LiteralAssignment -> { }
//         is NullAssignment -> { }
//         is ScalarAccessorValueAssignment -> {
//            when (val accessor = expression.accessor) {
//               // Example: (OrderSent::RequestedQuantity - OrderView::CumulativeQty)
//               is ConditionalAccessor -> validateField(accessor, ctx, errors, typesInViewFindDefinitions, viewBodyType)
//               is FunctionAccessor -> {
//                  validateFunction(accessor, ctx, errors, typesInViewFindDefinitions, viewBodyType)
//               }
//               else -> errors.add(CompilationError(
//                  ctx.start,
//                  "Unsupported Assignment for a when case!"))
//
//            }
//         }
//         is EnumValueAssignment -> { }
         // This is also covered by the compiler.
         else -> errors.add(
            CompilationError(
               ctx.start,
               "Unsupported Assignment for a when case!"
            )
         )
      }
   }
//
//   private fun validateModelAttributeTypeReference(
//      expression: ModelAttributeTypeReferenceAssignment,
//      ctx: TaxiParser.FindBodyContext,
//      errors: MutableList<CompilationError>,
//      typesInViewFindDefinitions: Set<Type>,
//      viewBodyType: Type?) {
//      return validateValidateSourceAndField(expression.source, expression.type, ctx, errors, typesInViewFindDefinitions, viewBodyType)
//   }

   private fun validateValidateSourceAndField(
      source: QualifiedName,
      type: Type,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      viewBodyType: Type?,
      canUseViewName: Boolean = true
   ) {
      if (!canUseViewName && (source.fullyQualifiedName == viewName)) {
         if (viewBodyType == null) {
            errors.add(
               CompilationError(
                  ctx.start,
                  "Invalid context for ${source.typeName}::${type.toQualifiedName().typeName}. You can not use a reference to View on the left hand side of a case when expression."
               )
            )
         } else {
            val referencedViewField = getFieldWithGivenType((viewBodyType as? ObjectType)?.fields, type)
            if (referencedViewField == null) {
               errors.add(
                  CompilationError(
                     ctx.start,
                     "Invalid context for ${source.typeName}::${type.toQualifiedName().typeName}. You can not use a reference to View on the left hand side of a case when expression."
                  )
               )
            } else {
               when (val accessor = referencedViewField.accessor) {
                  is FunctionAccessor -> if (!accessor.function.modifiers.contains(FunctionModifiers.Query)) {
                     errors.add(
                        CompilationError(
                           ctx.start,
                           "Invalid context for ${source.typeName}::${type.toQualifiedName().typeName}. You can not use a reference to View on the left hand side of a case when expression."
                        )
                     )
                  }

                  is ConditionalAccessor -> {}
                  else -> errors.add(
                     CompilationError(
                        ctx.start,
                        "Invalid context for ${source.typeName}::${type.toQualifiedName().typeName}. You can not use a reference to View on the left hand side of a case when expression."
                     )
                  )
               }
            }
         }
      }

      if (source.fullyQualifiedName != viewName &&
         !typesInViewFindDefinitions.map { it.toQualifiedName() }.contains(source)
      ) {
         errors.add(
            CompilationError(
               ctx.start,
               "A Reference to ${source.fullyQualifiedName} is invalid in this view definition context"
            )
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
               "${source.typeName}::${type.toQualifiedName().typeName} is invalid as ${source.typeName} does not have a field with type ${type.toQualifiedName().typeName}"
            )
         )
      }
   }

   private fun validateFunction(
      accessor: FunctionAccessor,
      ctx: TaxiParser.FindBodyContext,
      errors: MutableList<CompilationError>,
      typesInViewFindDefinitions: Set<Type>,
      viewBodyType: Type?
   ) {
      if (!isValidFunctionForViewField(accessor)) {
         errors.add(
            CompilationError(
               ctx.start,
               "Only ${validViewFieldFunctionNames()} function are allowed."
            )
         )
      }
      val inputs = accessor.inputs
      inputs.forEach { input ->
         if (input !is ModelAttributeReferenceSelector) {
            errors.add(
               CompilationError(
                  ctx.start,
                  "Function input must in SourceType::FieldType format."
               )
            )
         } else {
            validateValidateSourceAndField(
               input.memberSource,
               input.targetType,
               ctx,
               errors,
               typesInViewFindDefinitions,
               viewBodyType
            )
         }
      }

      if (accessor.function.toQualifiedName() == Coalesce.name) {
         if (inputs.size < 2) {
            errors.add(
               CompilationError(
                  ctx.start,
                  "${Coalesce.name.typeName} requires at least two arguments"
               )
            )
            return
         }
         val firstCoalesceArgumentType = (inputs.first() as ModelAttributeReferenceSelector).targetType
         if (!inputs.all { (it as ModelAttributeReferenceSelector).targetType == firstCoalesceArgumentType }) {
            errors.add(
               CompilationError(
                  ctx.start,
                  "${Coalesce.name.typeName} arguments must be of same type"
               )
            )
         }
      }


   }

   /**
    * We only Allow sumOver and coalesce for view fields as we map these function to their postgres counterparts in Vyne.
    */
   private fun isValidFunctionForViewField(accessor: FunctionAccessor): Boolean {
      val functionQualifiedName = accessor.function.toQualifiedName()
      return (functionQualifiedName == SumOver.name || functionQualifiedName == Coalesce.name)
   }

   private fun validViewFieldFunctionNames() = "${SumOver.name} and ${Coalesce.name}"

   private fun fromViewBodyFieldDefinitionToPrimitiveFields(
      fields: List<Field>,
      bodyDefinition: ViewBodyDefinition,
      bodyCtx: TaxiParser.FindBodyContext,
      compilationErrors: MutableList<CompilationError>
   ): List<PrimitiveType> {
      return fields.mapNotNull { viewBodyFieldDefinition ->
         when (val res = validateViewBodyFieldDefinition(viewBodyFieldDefinition, bodyDefinition, bodyCtx)) {
            is Either.Left -> {
               compilationErrors.add(res.value)
               null
            }

            is Either.Right -> res.value
         }
      }
   }

   private fun validateViewBodyFieldDefinition(
      viewBodyField: Field,
      bodyDefinition: ViewBodyDefinition,
      findBodyCtx: TaxiParser.FindBodyContext
   ): Either<CompilationError, PrimitiveType> {

      return when {
         // orderDate: OrderDateTime
         viewBodyField.memberSource == null -> getPrimitiveTypeForField(
            viewBodyField.type,
            viewBodyField.name,
            findBodyCtx
         )
         // orderDate: Order::OrderDateTime
         (viewBodyField.memberSource != null &&
            (viewBodyField.memberSource == bodyDefinition.bodyType.toQualifiedName()) ||
            (viewBodyField.memberSource == bodyDefinition.joinType?.toQualifiedName())) -> getPrimitiveTypeForField(
            viewBodyField.type,
            viewBodyField.name,
            findBodyCtx
         )

         else -> CompilationError(
            findBodyCtx.start,
            "Invalid View Definition - ${viewBodyField.memberSource} is not valid to use!"
         )
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

         else -> CompilationError(
            findBodyCtx.start,
            "type ${fieldType.qualifiedName} for field $fieldName is not allowed in view definitions"
         ).left()
      }
   }

   companion object {
      fun hasFieldWithGivenType(fields: List<Field>?, fieldType: Type): Boolean {
         return fields?.firstOrNull { field ->
            val actualFieldType = field.type
            actualFieldType == fieldType
         } != null
      }

      fun getFieldWithGivenType(fields: List<Field>?, fieldType: Type): Field? {
         return fields?.firstOrNull { field ->
            val actualFieldType = field.type
            actualFieldType == fieldType
         }
      }
   }
}

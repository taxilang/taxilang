package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Operator
import lang.taxi.TaxiParser
import lang.taxi.findNamespace
import lang.taxi.query.asDotJoinedPath
import lang.taxi.services.operations.constraints.ConstantValueExpression
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.services.operations.constraints.PropertyToParameterConstraintProvider
import lang.taxi.services.operations.constraints.PropertyTypeIdentifier
import lang.taxi.types.AndFilterExpression
import lang.taxi.types.FilterExpression
import lang.taxi.types.FilterExpressionInParenthesis
import lang.taxi.types.InFilterExpression
import lang.taxi.types.LikeFilterExpression
import lang.taxi.types.NotInFilterExpression
import lang.taxi.types.ObjectType
import lang.taxi.types.OrFilterExpression
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.value
import org.antlr.v4.runtime.Token

class ViewCriteriaFilterProcessor(private val tokenProcessor: TokenProcessor) {
   /*
    * Parses the filter expression for the given target Type
    * Example:
    * find { OrderSent (OrderStatus = 'Filled') }
    *
    * filter Expression -> OrderStatus = 'Filled'
    * Target Type -> OrderSent
    */
   fun processFilterExpressionContext(targetType: Type, ctx: TaxiParser.FilterExpressionContext): Either<List<CompilationError>, FilterExpression> {
      return when (ctx) {
         // case for: OrderStatus = 'Filled'
         is TaxiParser.AtomExpContext -> validatePropertyConstraint(
            PropertyToParameterConstraintProvider().build(ctx.propertyToParameterConstraintExpression(), tokenProcessor.typeResolver(ctx.findNamespace())),
            ctx,
         targetType)
         // case for:  (OrderStatus = 'Filled')
         is TaxiParser.ParenExpContext -> processFilterExpressionContext(targetType, ctx.filterExpression()).map { FilterExpressionInParenthesis(it) }
         // case for  ( OrderStatus in ['Filled', 'PartiallyFilled']
         is TaxiParser.InExprContext -> processInExpressionContext(ctx, targetType)
         // case for ( OrderStatus like '%Fill%'
         is TaxiParser.LikeExprContext -> processLikeExpressionContext(ctx, targetType)
         // case for  (OrderStatus = 'Filled' or OrderStatus = 'PartiallyFilled')
         is TaxiParser.OrBlockContext -> processOrBlockExpressionContext(ctx, targetType)
         // case for ( OrderStatus != 'Cancelled' and OrderStatus != 'Rejected')
         is TaxiParser.AndBlockContext -> processAndBlockExpressionContext(ctx, targetType)
         // case for  ( OrderStatus not in ['Filled', 'PartiallyFilled'])
         is TaxiParser.NotInExprContext -> processNotInExpressionContext(ctx, targetType)
         else -> listOf(CompilationError(ctx.start, "Expected that constraint would be declared in an operation.  This is likely a bug in the compiler")).left()
      }
   }

   /**
    * Processes 'or' filter expression, e.g.    OrderStatus = 'Filled' or OrderStatus = 'Partially Filled'
    * @param ctx Or Parser context
    * @param targetType The type for which the filter expression is defined.
    */
   private fun processOrBlockExpressionContext(ctx: TaxiParser.OrBlockContext, targetType: Type): Either<List<CompilationError>, FilterExpression> {
      val left = ctx.filterExpression(0)
      val right = ctx.filterExpression(1)
      return processFilterExpressionContext(targetType, left).flatMap { leftExpr ->
         processFilterExpressionContext(targetType, right).map { rightExpr ->
            OrFilterExpression(leftExpr, rightExpr)
         }
      }
   }

   /**
    * Processes 'and' filter expression, e.g.    OrderStatus = 'Filled' and OrderStatus = 'Partially Filled'
    * @param ctx Corresponding Parser Context
    * @param targetType The type for which the filter expression is defined.
    */
   private fun processAndBlockExpressionContext(ctx: TaxiParser.AndBlockContext, targetType: Type): Either<List<CompilationError>, FilterExpression> {
      val left = ctx.filterExpression(0)
      val right = ctx.filterExpression(1)
      return processFilterExpressionContext(targetType, left).flatMap { leftExpr ->
         processFilterExpressionContext(targetType, right).map { rightExpr ->
            AndFilterExpression(leftExpr, rightExpr)
         }
      }
   }

   /**
    * Processes like filter expression, e.g. OrderStatus like '%Filled'
    * @param ctx Corresponding Parser Context
    * @param targetType The type for which the filter expression is defined.
    */
   private fun processLikeExpressionContext(ctx: TaxiParser.LikeExprContext, targetType: Type): Either<List<CompilationError>, FilterExpression> {
      val qfnm = ctx.like_exprs().qualifiedName().asDotJoinedPath()
      val typeResolver = tokenProcessor.typeResolver(ctx.findNamespace())
      return typeResolver.resolve(qfnm, ctx).flatMap { type ->
         hasFieldWithType(type, targetType, ctx.start, PrimitiveType.STRING).flatMap {
            val value = ctx.like_exprs().literal()
            if (value.StringLiteral() == null) {
               listOf(CompilationError(ctx.start, "in expects a String argument, ${value.value()} must be string")).left()
            } else {
               LikeFilterExpression(value.value().toString(), type).right()
            }
         }
      }
   }

   /**
    * Parses the 'in' filter expression which is only applicable to 'String' based fields.
    * @param ctx Corresponding parser context
    * @param targetType The type for which the filter expression is defined.
    * Example:
    * Order (OrderId in ['Id1', 'Id2'])
    *
    * Order -> targetType
    * @return list of compilation errors or InFilterExpression.
    */
   private fun processInExpressionContext(ctx: TaxiParser.InExprContext, targetType: Type): Either<List<CompilationError>, FilterExpression> {
      val qfnm = ctx.in_exprs().qualifiedName().asDotJoinedPath()
      val typeResolver = tokenProcessor.typeResolver(ctx.findNamespace())
      return typeResolver.resolve(qfnm, ctx).flatMap { fieldType ->
         hasFieldWithType(fieldType, targetType, ctx.start).flatMap {
            val values = ctx.in_exprs().literalArray().value()
            val inArgumentErrors = validateLiteralArrayVals(values, fieldType, ctx)
            if (inArgumentErrors.isEmpty()) {
               InFilterExpression(values, fieldType).right()
            } else {
               inArgumentErrors.left()
            }
         }
      }
   }

   /**
    * Validates the given literal Array values.
    */
   private fun validateLiteralArrayVals(values: List<Any>, fieldType: Type, ctx: TaxiParser.FilterExpressionContext): List<CompilationError> {
      val firstValue = values.first()
      val firstValueClass = firstValue.javaClass
      return values.mapNotNull { value ->
         when {
            !firstValueClass.isInstance(value) -> CompilationError(ctx.start, "arguments of in must be of same type $firstValue is not compatible with $value")
            else -> validateArgumentValue(value, fieldType, ctx.start)
         }
      }
   }

   /**
    * Parses the 'not in' filter expression which is only applicable to 'String' based fields.
    * @param ctx Corresponding parser context
    * @param targetType The type for which the filter expression is defined.
    * Example:
    * Order (OrderId in ['Id1', 'Id2'])
    *
    * Order -> targetType
    * @return list of compilation errors or InFilterExpression.
    */
   private fun processNotInExpressionContext(ctx: TaxiParser.NotInExprContext, targetType: Type): Either<List<CompilationError>, FilterExpression> {
      val qfnm = ctx.not_in_exprs().qualifiedName().asDotJoinedPath()
      val typeResolver = tokenProcessor.typeResolver(ctx.findNamespace())
      return typeResolver.resolve(qfnm, ctx).flatMap { fieldType ->
         hasFieldWithType(fieldType, targetType, ctx.start).flatMap {
            val values = ctx.not_in_exprs().literalArray().value()
            val inArgumentErrors = validateLiteralArrayVals(values, fieldType, ctx)
            if (inArgumentErrors.isEmpty()) {
               NotInFilterExpression(values, fieldType).right()
            } else {
               inArgumentErrors.left()
            }
         }
      }
   }

   /**
    *  Make sure the the constraint is in  'FullyQualifiedName <operator> Literal' Format
    *  these are valid:
    *     OrderStatus = 'Filled'
    *     OrderStatus != 'Filled'
    *     TradeCount > 10
    * But this is not valid:
    *     this.orderStatus = 'Filled'
    * @param errorOrPropertyToParameterConstraint PropertyToParameterConstraint to be validated.
    * @param ctx Parser Context to instantiate CompilationErrors when required.
    * @return list of compilation errors if the given properyToParameterConstraint is valid otherwise the corresponding FilterExpression.
    */
   private fun validatePropertyConstraint(errorOrPropertyToParameterConstraint: Either<List<CompilationError>, PropertyToParameterConstraint>,
                                          ctx: TaxiParser.AtomExpContext,
                                          targetType: Type):
      Either<List<CompilationError>, FilterExpression> {
      val errors = mutableListOf<CompilationError>()
      return errorOrPropertyToParameterConstraint.flatMap { constraint ->
         if (constraint.propertyIdentifier !is PropertyTypeIdentifier) {
            errors.add(
               CompilationError(ctx.start, "Constraint must specify a type.")
            )
         }
         if  (constraint.expectedValue !is ConstantValueExpression) {
            errors.add(
               CompilationError(ctx.start, "Constraint's expected value must be a literal. e.g. 'foo' or 5 or true")
            )
         }

         val propTypeIdentifier = constraint.propertyIdentifier as PropertyTypeIdentifier
         val typeResolver = tokenProcessor.typeResolver(ctx.findNamespace())
         val expectedConstantValueExpression = constraint.expectedValue as ConstantValueExpression

          typeResolver.resolve(propTypeIdentifier.type.toQualifiedName().parameterizedName, ctx).flatMap { fieldType ->
            hasFieldWithType(fieldType, targetType, ctx.start).flatMap {
               argumentValueOrError(expectedConstantValueExpression.value, fieldType, ctx.start).flatMap {
                  validateOperator(fieldType, constraint.operator, ctx.start).flatMap {
                     constraint.right()
                  }
               }
            }
         }
      }
   }

   /**
    * Checks whether 'targetType' has a field with 'fieldType'
    * @param fieldType Type of the field.
    * @param targetType Type that needs to contain a field with fieldType
    * @param compilationToken Current compilation token - required for CompilationError instantiation.
    * @param validatedAgainstPrimitiveType When specified fieldType must be based on it.
    * @return either list of compilation errors or fieldType
    */
   private fun hasFieldWithType(
      fieldType: Type,
      targetType: Type,
      compilationToken: Token,
      validatedAgainstPrimitiveType: PrimitiveType? = null): Either<List<CompilationError>, Type> {
      val errors = mutableListOf<CompilationError>()
      val fields = (targetType as? ObjectType)?.fields
      if (!ViewValidator.hasFieldWithGivenType(fields, fieldType)) {
         errors.add(
            CompilationError(compilationToken, "${targetType.qualifiedName} does not have a field with type ${fieldType.qualifiedName}")
         )
      }

      if (validatedAgainstPrimitiveType != null && fieldType.basePrimitive != validatedAgainstPrimitiveType) {
         errors.add(
            CompilationError(compilationToken, "${fieldType.qualifiedName} must be a ${validatedAgainstPrimitiveType.qualifiedName} type")
         )
      }

      return if (errors.isEmpty()) {
         fieldType.right()
      } else {
         errors.left()
      }
   }

   /**
    * Checks whether given value is compatible with the base primitive of the given field type.
    * @param value A value to be validated against field type.
    * @param fieldType Type whose primitive type for compatibility check.
    * @param parseToken Token to instantiate CompilerError message when required.
    * @return CompilationError if there is an incompatibility otherwise null.
    */
   private fun validateArgumentValue(value: Any, fieldType: Type, parseToken: Token): CompilationError? {
      return when {
         fieldType.basePrimitive == PrimitiveType.STRING && value !is String -> CompilationError(parseToken, "$value is incorrect, it must be a string")
         fieldType.basePrimitive == PrimitiveType.DECIMAL && value !is Number -> CompilationError(parseToken, "$value is incorrect, it must be numeric")
         fieldType.basePrimitive == PrimitiveType.INTEGER && value !is Number -> CompilationError(parseToken, "$value is incorrect, it must be numeric")
         fieldType.basePrimitive == PrimitiveType.BOOLEAN && value !is Boolean -> CompilationError(parseToken, "$value is incorrect, it must be true or false")
         else -> null
      }
   }

   /**
    * Checks whether given value is compatible with the base primitive of the given field type.
    * @param value A value to be validated against field type.
    * @param fieldType Type whose primitive type for compatibility check.
    * @param parseToken Token to instantiate CompilerError message when required.
    * @return Either list of compilation errors or the passed value.
    */
   private fun argumentValueOrError(value: Any, fieldType: Type, parseToken: Token): Either<List<CompilationError>, Any> {
      val errorOrNull = validateArgumentValue(value, fieldType, parseToken)
      return if (errorOrNull == null) {
         value.right()
      } else {
         listOf(errorOrNull).left()
      }
   }

   /**
    * Checks whether the given operator compatible with the primitive type of provided fieldType.
    * @param fieldType Field Type whose primitive type will be used to validate the operator.
    * @param operator Operator to be validated.
    * @param  parseToken Token to create Compiler error when reuqired.
    * @return Either list of compilation errors or the passed operator.
    */
   private fun validateOperator(fieldType: Type, operator: Operator, parseToken: Token): Either<List<CompilationError>, Operator>  {
      val error =  when {
         fieldType.basePrimitive == PrimitiveType.STRING && (operator != Operator.EQUAL && operator != Operator.NOT_EQUAL) ->
            CompilationError(parseToken, "${operator.symbol} is not applicable for String based types. Use only = or !=")
         fieldType.basePrimitive == PrimitiveType.DECIMAL &&  (operator == Operator.IN || operator == Operator.LIKE)
         -> CompilationError(parseToken, "${operator.symbol} is not applicable for Numeric types. Use only =, !=, <, >, >=, <=")
         fieldType.basePrimitive == PrimitiveType.INTEGER &&  (operator == Operator.IN || operator == Operator.LIKE)
         -> CompilationError(parseToken, "${operator.symbol} is not applicable for Numeric types. Use only =, !=, <, >, >=, <=")
         fieldType.basePrimitive == PrimitiveType.BOOLEAN && (operator != Operator.EQUAL && operator != Operator.NOT_EQUAL) ->
            CompilationError(parseToken, "${operator.symbol} is not applicable for Boolean based types. Use only = or !=")
         else -> null
      }

      return if (error == null) {
         operator.right()
      } else {
         listOf(error).left()
      }
   }
}

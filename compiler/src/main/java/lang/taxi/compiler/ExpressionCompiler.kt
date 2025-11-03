package lang.taxi.compiler

import arrow.core.*
import lang.taxi.*
import lang.taxi.TaxiParser.ExpressionGroupContext
import lang.taxi.TaxiParser.QualifiedNameContext
import lang.taxi.TaxiParser.TypeMemberDeclarationContext
import lang.taxi.TaxiParser.TypeProjectionContext
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.Argument
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.compiler.fields.FieldCompiler
import lang.taxi.compiler.fields.FieldTypeSpec
import lang.taxi.expressions.*
import lang.taxi.functions.Function
import lang.taxi.query.ConstraintBuilder
import lang.taxi.query.DiscoveryType
import lang.taxi.services.ServiceMember
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.types.*
import lang.taxi.utils.*
import org.antlr.v4.runtime.ParserRuleContext

class ExpressionCompiler(
   private val tokenProcessor: TokenProcessor,
   val typeChecker: TypeChecker,
   private val errors: MutableList<CompilationError>,
   /**
    * Pass the fieldCompiler when the expression being compiled is within the field of a model / query result.
    * This allows field lookups by name in expressions
    */
   private val fieldCompiler: FieldCompiler? = null,
   private val scopes: List<Argument> = emptyList(),
   private val typedExpressionBuilder: TypedExpressionBuilder = DefaultTypedExpressionBuilder,
   // MP: 11-Mar-25
   // Only for when compiling a top-level projection expression (currently)
   // Trying to get top-level projection expressions using the same code
   // as field projections.
   // This isn't right long-term, but exploring
   private val discoveryType: DiscoveryType? = null
) : FunctionParameterReferenceResolver {
   private val functionCompiler = FunctionAccessorCompiler(
      tokenProcessor,
      typeChecker,
      errors,
      this,
   )

   fun withTypedExpressionBuilder(newBuilder: TypedExpressionBuilder): ExpressionCompiler {
      return ExpressionCompiler(
         tokenProcessor,
         typeChecker,
         errors,
         fieldCompiler,
         scopes,
         typedExpressionBuilder = newBuilder
      )
   }

   fun withParameters(arguments: List<Argument>): ExpressionCompiler {
      // TODO : In future, does it make sense to "nest" these, so that as we add arguments,
      // they form scopes / contexts?
      // For now, everything is flat.
      return ExpressionCompiler(
         tokenProcessor,
         typeChecker,
         errors,
         fieldCompiler,
         scopes + arguments,
         typedExpressionBuilder = typedExpressionBuilder
      )
   }

   fun forDiscoveryType(source: DiscoveryType): ExpressionCompiler {
      return ExpressionCompiler(
         tokenProcessor, typeChecker, errors, fieldCompiler, scopes, typedExpressionBuilder,
         discoveryType = source
      )
   }

   fun compile(
      expressionGroup: ExpressionGroupContext,
      // The type we'll be attempting to assign the result of this expression to.
      // This is a recent (26-09-23) addition to the signature, and hasn't yet been
      // updated in all call sites.
      targetType: Type? = null,

      /**
       * This should almost always be true.
       * false when parsing cast expressions, as the cast overrides
       */
      enforceTypeChecks: Boolean = true
   ): Either<List<CompilationError>, out Expression> {
      return when {
         expressionGroup.castExpression() != null -> compileCastExpression(expressionGroup, targetType)

         // 11-Mar:
         // Top-level projections
         // find { Foo } as (Thing[]) -> Bar[] as {
         // }[]
         expressionGroup.children.size == 1 && expressionGroup.expressionAtom()
            ?.typeProjection() != null && discoveryType != null -> {
            val anonymousTypeName = NameGenerator.generate()
            val fieldTypeSpec = FieldTypeSpec.forDiscoveryTypes(discoveryType)
            compileExpressionProjectionWithoutField(
               expressionGroup.expressionAtom()!!.typeProjection()!!,
               fieldTypeSpec,
               anonymousTypeName
            ).map { projectedTypeAndScope ->
               // TODO: Constraints?
               val projection = FieldProjection.forNullable(discoveryType.type, emptyList(), projectedTypeAndScope)!!
               ProjectingExpression(discoveryType.expression, projection)
            }
         }

         expressionGroup.children.size == 2 && expressionGroup.children.last() is TypeProjectionContext && expressionGroup.children.first() is ExpressionGroupContext -> {
            // This is an expression with a projection.
            // Compile the expression first.
            // We don't pass the targetType, since the expressionGroup is an input into the
            // projection
            return compile(expressionGroup.expressionGroup(0), targetType = null).flatMap { expression ->
               compileExpressionProjection( // ...then compile the projection
                  expression,
                  expressionGroup.typeProjection()!!
               ).flatMap { projectedTypeAndScope ->
                  // ...and stick it all together as a ProjectingExpression
                  val constraints = if (expression is TypeExpression) {
                     expression.constraints
                  } else emptyList()
                  val projection =
                     FieldProjection.forNullable(expression.returnType, constraints, projectedTypeAndScope)!!
                  val projectingExpression = ProjectingExpression(expression, projection)
                  if (enforceTypeChecks) {
                     typeChecker.ifAssignableOrErrorList(
                        projectingExpression.returnType,
                        targetType,
                        expressionGroup
                     ) { projectingExpression }
                  } else projectingExpression.right()

               }
            }
         }


         expressionGroup.LPAREN() != null && expressionGroup.RPAREN() != null -> {
            require(expressionGroup.children.size == 3) { "When handling an expression ${expressionGroup.text} expected exactly 3 children, including the parenthesis" }
            // There must be only one child not a bracket
            // ie., ( A + B ) should yeild LPAREN EXPRESSIONGROUP RPAREN
            require(expressionGroup.expressionGroup().size == 1) { "Expected only a single ExpressionGroup inside parenthesis" }
            compile(expressionGroup.expressionGroup(0), targetType)
         }

         expressionGroup.children.size == 2 && expressionGroup.expressionInputs() != null -> parseLambdaExpression(
            expressionGroup, targetType
         )

         // Might need to be more specific here -- this should be expressionGroup.functionCall()
         expressionGroup.children.size == 3 && expressionGroup.methodCall() != null -> parseExtensionFunctionCallExpression(
            expressionGroup,
            targetType
         )

         // Might need to be more specific here -- this should be expressionGroup.propertyName
         expressionGroup.children.size == 3 && expressionGroup.identifier() != null -> parseAccessorExpression(
            expressionGroup
         )

         expressionGroup.memberReference() != null -> {
            if (expressionGroup.expressionGroup().orEmpty().size != 1) {
               expressionGroup.createInternalError("Cannot have a member reference without a preceding expression group")
            } else {
               parseTypeMemberReference(expressionGroup.expressionGroup().single(), expressionGroup.memberReference())
            }
         }

         expressionGroup.children.size == 3 -> parseOperatorExpression(expressionGroup)
         expressionGroup.negated() != null && expressionGroup.expressionGroup().size == 1 -> compileNegatedExpression(
            expressionGroup.expressionGroup().single(),
            targetType
         )
         // lhs operator rhs
         expressionGroup.expressionGroup().isEmpty() -> compileSingleExpression(expressionGroup, targetType)
         else -> expressionGroup.createInternalError("Unhandled expression group scenario: ${expressionGroup.text}")
      }.flatMap { expression ->
         when (targetType) {
            null -> expression.right() // we weren't given a type, so can't do type checking
            else -> {
               val error = if (enforceTypeChecks) typeChecker.assertIsAssignable(
                  expression.returnType,
                  targetType,
                  expressionGroup
               ) else null
               if (error != null) {
                  listOf(error).left()
               } else {
                  expression.right()
               }
            }
         }
      }
   }

   private fun compileNegatedExpression(
      expressionGroup: ExpressionGroupContext,
      targetType: Type?
   ): Either<List<CompilationError>, Expression> {
      return compile(expressionGroup, targetType).flatMap { expression ->
         if (expression.returnType.basePrimitive !== PrimitiveType.BOOLEAN) {
            expressionGroup.createCompilationError("Cannot use ! operator against type ${expression.returnType.qualifiedName} - ! is supported against Boolean types only")
         } else {
            NegatedExpression(expression, expressionGroup.toCompilationUnits()).right()
         }
      }
   }

   private fun compileCastExpression(
      expressionGroup: ExpressionGroupContext,
      targetType: Type?
   ): Either<List<CompilationError>, CastExpression> {
      return tokenProcessor.typeOrError(expressionGroup.castExpression().typeReference())
         .flatMap { castType ->
            // We're about to perform casting, so disable the type check. We catch it below
            compile(expressionGroup.expressionGroup().single(), targetType = castType, enforceTypeChecks = false)
               .flatMap { uncastExpression ->

                  // now validate the type
                  val castExpression = CastExpression(
                     castType,
                     uncastExpression,
                     expressionGroup.castExpression().toCompilationUnits()
                  )
                  typeChecker.ifAssignableOrErrorList(
                     castExpression.returnType,
                     targetType,
                     expressionGroup
                  ) { castExpression }
               }
         }
   }

   private fun compileExpressionProjection(
      expression: Expression,
      typeProjection: TypeProjectionContext
   ): Either<List<CompilationError>, Pair<Type, List<ProjectionFunctionScope>>> {
      val projectionSourceType = FieldTypeSpec.forExpression(expression)
      return if (fieldCompiler != null) {
         // find the current field name
         val member = typeProjection.searchUpForRule<TypeMemberDeclarationContext>()
            ?: error("Exptected that we were projecting inside a field declaration.  Can't work out a suggested name for the anonymous type")
         val typeName = fieldCompiler.anonymousTypeNameForMember(member) + "$${NameGenerator.randomString(length = 5)}"
         fieldCompiler.parseFieldProjection(typeProjection, projectionSourceType, typeName, emptyList())
      } else {
         val anonymousTypeName = projectionSourceType.type.qualifiedName + "$${NameGenerator.randomString(length = 5)}"
         compileExpressionProjectionWithoutField(
            typeProjection,
            projectionSourceType,
            anonymousTypeName,
         )
      }
   }

   // This occurs when compiling a type projection expression outside of a field
   // eg: in a policy
   //
   private fun compileExpressionProjectionWithoutField(
      typeProjection: TypeProjectionContext,
      projectionSourceType: FieldTypeSpec,
      anonymousTypeName: String,
   ): Either<List<CompilationError>, Pair<Type, List<ProjectionFunctionScope>>> {
      return tokenProcessor.parseProjectionScope(typeProjection.expressionInputs(), projectionSourceType, this.scopes)
         .flatMap { projectionScope ->
            val projectedType = when {
               typeProjection.anonymousTypeDefinition() != null -> tokenProcessor.parseAnonymousType(
                  anonymousTypeName,
                  typeProjection.anonymousTypeDefinition(),
                  anonymousTypeName,
                  ResolutionContext(
                     // Implementing policies 2.0
                     // We need spread operator capabilities when compiling expressions,
                     // which requires a scope.
                     // MP: 11-Mar-25: Updated this -- why weren't we passing the projectionScope in here?
                     // It now breaks things as we're DRYing up expression compilation and sending more
                     // code down this path.
                     activeScopes = projectionScope,
                     //activeScopes = listOf(ProjectionFunctionScope.implicitThis(projectionSourceType.type)),
                     parameters = this.scopes,
                     typesToDiscover = listOfNotNull(discoveryType)
                  )
               )

               typeProjection.typeReference() != null -> tokenProcessor.typeOrError(typeProjection.typeReference())
               else -> error("Can't lookup type reference for projection from statement: ${typeProjection.source().content}")
            }
            projectedType.map { type -> type to projectionScope }
         }
   }

   /**
    * Parses an expression like
    * (A,B) -> A > B
    */
   private fun parseLambdaExpression(
      lambdaExpression: ExpressionGroupContext,
      targetType: Type?
   ): Either<List<CompilationError>, out Expression> {
      require(lambdaExpression.children.size == 2) { "Expected exactly 2 children in the lambda expression" }
      require(lambdaExpression.expressionGroup().size == 1) { "expected exactly 1 expression group on the rhs of the lambda" }
      return lambdaExpression.expressionInputs()
         .expressionInput().mapIndexed { index, expressionInput ->
            val parameterName = expressionInput.identifier()?.text ?: "p$index"
            when {
               // This is a standard type as a lambda input
               // eg: ( SomeType )
               expressionInput.nullableTypeReference() != null -> tokenProcessor.parseType(
                  expressionInput.findNamespace(),
                  expressionInput.nullableTypeReference().typeReference()
               ).map { type ->
                  ProjectionFunctionScope(parameterName, type)
               }
               // This is a type with constraints
               // eg: ( SomeType( Foo == Bar ) )
               expressionInput.expressionGroup() != null -> compile(
                  expressionInput.expressionGroup(),
                  // ORB-679
                  // targetType passed here should not be our targetType that was passed in as an arg.
                  // This is because this call is compiling an INPUT to an expression - not the expression itself.
                  // eg:
                  // type CustomerType inherits String by (customer:Customer(CustomerId == '123')) -> // impl. omitted
                  // We're compiling the Customer expression, which is not intended to be assignable to CustomerType
                  targetType = null
               )
                  .map { expression ->
                     ProjectionFunctionScope(parameterName, expression.returnType, expression)
                  }

               else -> return listOf(
                  CompilationError(
                     lambdaExpression.toCompilationUnit(),
                     "An internal error occurred: Unhandled branch in parseLambdaExpression with token ${lambdaExpression.text}"
                  )
               )
                  .left()
            }
         }.invertEitherList().flattenErrors()
         .flatMap { inputs ->
            withParameters(inputs)
               .compile(lambdaExpression.expressionGroup(0), targetType).flatMap { expression ->
                  typeChecker.ifAssignableOrErrorList(expression.returnType, targetType, lambdaExpression) {
                     LambdaExpression(inputs, expression, lambdaExpression.toCompilationUnits())
                  }
               }
         }

   }

   private fun compileSingleExpression(
      expression: ExpressionGroupContext,
      assignmentType: Type?
   ): Either<List<CompilationError>, Expression> {
      return when {
         expression.expressionAtom() != null -> compileExpressionAtom(expression.expressionAtom(), assignmentType)
         expression.whenBlock() != null -> {
// MP - 9-Aug-25
            // In order to improve type inference in expressions (ie., if a type isn't provided, look at the return
            // type of the expression, and infer it)
            // we need to relax this requirement.,
            // If the assignment type wasn't provided, it becomes Any.
            // In future, we might like to expand this to compile the when block, and
            // infer the return type if not provided.
            // However, that's beyond the current scope
//            require(assignmentType != null) { "Cannot compile a When Block as no assignment type has been provided" }
            val whenCompiler = WhenBlockCompiler(this)
            whenCompiler.compileWhenCondition(expression.whenBlock(), assignmentType ?: PrimitiveType.ANY)
         }

         else -> TODO("Unhandled single expression: ${expression.text}")
      }
   }

   private fun compileExpressionAtom(
      expressionAtom: TaxiParser.ExpressionAtomContext,
      assignmentType: Type?
   ): Either<List<CompilationError>, Expression> {
      return when {
         expressionAtom.typeExpression() != null -> parseTypeExpression(expressionAtom.typeExpression())
         expressionAtom.functionCall() != null -> parseFunctionExpressionOrTypeExpression(
            expressionAtom.functionCall(),
            assignmentType
         )

         expressionAtom.literal() != null -> parseLiteralExpression(expressionAtom.literal(), assignmentType)
         expressionAtom.valueArray() != null -> parseValueArray(expressionAtom.valueArray(), assignmentType)
         expressionAtom.fieldReferenceSelector() != null -> parseAttributeSelector(expressionAtom.fieldReferenceSelector())
         expressionAtom.objectValue() != null -> ValueExpressionCompiler(this).objectValueAsExpression(
            expressionAtom.objectValue(),
            assignmentType
         )

         expressionAtom.typeProjection() != null -> parseTypeProjectionExpression(
            expressionAtom.typeProjection(),
            assignmentType
         )

         expressionAtom.operationInvocation() != null -> parseOperationInvocationExpression(
            expressionAtom.operationInvocation(),
            assignmentType
         )

         else -> expressionAtom.createInternalError("Unhandled atom in expression: ${expressionAtom.text}")
      }
   }

   private fun parseAttributeSelector(fieldReferenceSelector: TaxiParser.FieldReferenceSelectorContext): Either<List<CompilationError>, Expression> {
      return when {
         fieldCompiler != null -> parseFieldReferenceSelector(fieldReferenceSelector)
         // This is used when we're parsing expressions on operation contracts
         // (ie., return contracts)
         scopes.isNotEmpty() -> parseAttributeFromScope(fieldReferenceSelector)
         else -> {
            return listOf(
               CompilationError(
                  fieldReferenceSelector.toCompilationUnit(),
                  "Attribute reference ${fieldReferenceSelector.text} cannot be resolved against anything"
               )
            ).left()
         }
      }
   }

   // Used when we're parsing expressions on operation contracts
   // (ie., return contracts)
   private fun parseAttributeFromScope(fieldReferenceSelector: TaxiParser.FieldReferenceSelectorContext): Either<List<CompilationError>, Expression> {

      // We're trying to build the scope path - eg: this.foo.bar
      // There's some tech debt here - see comments about "this" in fieldReferenceSelector
      // in grammar.
      // Net result is that "this" is treated specially, making building the path awkward,
      // as we read the "this" from one part, and the rest of the path elsewhere.
      val attributePath = fieldReferenceSelector.qualifiedName()
      val scopePath = listOf(fieldReferenceSelector.propertyFieldNameQualifier().text.removeSuffix(".")) +
         attributePath.identifier().map { it.text }
      return resolveScopePath(scopePath, context = fieldReferenceSelector)
   }

   private fun parseFieldReferenceSelector(fieldReferenceSelector: TaxiParser.FieldReferenceSelectorContext): Either<List<CompilationError>, Expression> {
      return requireFieldCompilerIsPresent(fieldReferenceSelector).flatMap {
         val fieldPath = fieldReferenceSelector.qualifiedName().identifier()

         val (firstPathElement, remainingPathElements) = fieldPath.takeHead()

         var error: CompilationMessage? = null
         return fieldCompiler!!.provideField(firstPathElement.text, fieldReferenceSelector)
            .flatMap { field ->
               val fieldSelectors = remainingPathElements
                  .asSequence()
                  .takeWhile { error == null }
                  // Cast to nullable type, as it allows us to return null when an error is thrown
                  .runningFold(FieldReferenceSelector.fromField(field) as FieldReferenceSelector?) { lastField, pathElement ->
                     val lastFieldReturnType = lastField!!.returnType

                     // Check that the type has properties
                     if (lastFieldReturnType !is ObjectType) {
                        error = CompilationError(
                           pathElement.toCompilationUnit(),
                           "${lastFieldReturnType.toQualifiedName().parameterizedName} does not expose properties"
                        )
                        null

                        // Check that the field exists on the type
                     } else if (!lastFieldReturnType.hasField(pathElement.text)) {
                        error = CompilationError(
                           pathElement.toCompilationUnit(),
                           "${lastFieldReturnType.toQualifiedName().parameterizedName} does not have a property ${pathElement.text}"
                        )
                        null
                     } else {
                        FieldReferenceSelector.fromField(lastFieldReturnType.field(pathElement.text))
                     }
                  }
                  .filterNotNull()
                  .toList()

               if (error != null) {
                  listOfNotNull(error).left()
               } else {
                  FieldReferenceExpression(
                     fieldSelectors,
                     fieldReferenceSelector.toCompilationUnits()
                  ).right()
               }

            }
      }
   }


   fun parseValueArray(
      literalArray: TaxiParser.ValueArrayContext,
      assignmentType: Type?
   ): Either<List<CompilationError>, Expression> {
      if (assignmentType != null && !Arrays.isArray(assignmentType) && assignmentType != PrimitiveType.ANY) {
         return listOf(
            CompilationError(
               literalArray.toCompilationUnit(),
               "An internal error occurred: Parsing an array, but the assignment type was not an array type - got ${assignmentType.qualifiedName}"
            )
         )
            .left()
      }
      val memberType = if (assignmentType != null) {
         Arrays.unwrapPossibleArrayType(assignmentType)
      } else PrimitiveType.ANY
      return literalArray.expressionGroup().map { expression ->
         compile(expression, memberType)
      }.invertEitherList().flattenErrors()
         .map { compiledExpressions ->
            val inferredAssignmentType = assignmentType ?: findCommonType(compiledExpressions.map { it.returnType })

            val arrayType = if (assignmentType != null && assignmentType != PrimitiveType.ANY) {
               assignmentType
            } else {
               Arrays.arrayOf(inferredAssignmentType)
            }
            LiteralArray(arrayType, compiledExpressions, literalArray.toCompilationUnits())
         }
//      return LiteralExpression(LiteralAccessor(literalArray.value()), literalArray.toCompilationUnits()).right()
   }

   private fun findCommonType(types: List<Type>): Type {
      return TypeResolver.findLowestCommonType(types)
   }

   private fun parseLiteralExpression(
      literal: TaxiParser.LiteralContext,
      assignmentType: Type?
   ): Either<List<CompilationError>, Expression> {
      // The raw accessor initially uses the raw primitive type.
      // We do this in order to first do assignment type checking, then we
      // upcast to the semantic type if provided.
      val rawAccessor = LiteralAccessor(literal.valueOrNullValue())

      val receiverType = assignmentType ?: PrimitiveType.ANY
      val returnType = try {
         TypeUtils.getMostSpecificType(assignmentType ?: PrimitiveType.ANY, rawAccessor.returnType)
      } catch (e: Exception) {
         // We can't go into the type checker (where these errors are normally reported,
         // as the types are incompatible, and we couldn't determine the return type.
         // So, report the error from here.
         return listOf(Errors.typeMismatch(rawAccessor.returnType, receiverType, literal)).left()
      }
      return typeChecker.ifAssignableOrErrorList(rawAccessor.returnType, receiverType, literal) {
         LiteralExpression(
            LiteralAccessor(
               literal.valueOrNullValue(),
               returnType
            ),
            literal.toCompilationUnits()
         )
      }
   }


   private fun parseOperatorExpression(expressionGroup: ExpressionGroupContext): Either<List<CompilationError>, out Expression> {

      val lhsOrError = expressionGroup.expressionGroup(0)?.let { compile(it) }
         ?: return listOf(
            CompilationError(
               expressionGroup.toCompilationUnit(),
               "An internal error occurred: Expected an expression group at index 0, but was null"
            )
         )
            .left()
      val rhsOrError = expressionGroup.expressionGroup(1)?.let { compile(it) }
         ?: return listOf(
            CompilationError(
               expressionGroup.toCompilationUnit(),
               "An internal error occurred: Expected an expression group at index 1, but was null"
            )
         )
            .left()
      val operatorSymbol = expressionGroup.children[1]
      val operatorOrError = when {
         FormulaOperator.isSymbol(operatorSymbol.text) -> FormulaOperator.forSymbol(operatorSymbol.text).right()
         else -> listOf(
            CompilationError(
               expressionGroup.toCompilationUnit(),
               "${operatorSymbol.text} is not a valid operator"
            )
         ).left()
      }
      val expressionComponents = listOf(lhsOrError, rhsOrError, operatorOrError)
      if (expressionComponents.allValid()) {
         val lhs = lhsOrError.getOrThrow()
         val operator = operatorOrError.getOrThrow()
         val rhs = rhsOrError.getOrThrow()
         // Don't love this, but we don't have access to the type in the null value at this point.
         // Thereofre, we basically disable operator support checks for null comparisons.
         // If we fix that problem, we can keep the operator checking here
         val isNullCheck = LiteralExpression.isNullExpression(lhs) || LiteralExpression.isNullExpression(rhs)

         val (coercedLhs, coercedRhs) = TypeCaster.coerceTypesIfRequired(lhs, rhs).getOrElse { error ->
            return listOf(CompilationError(expressionGroup.toCompilationUnit(), error)).left()
         }

         // MP 9-Aug-25:
         // We used to coalesce this to any if there was no primitive type
         // However, we need to support checks against array types
         // for 'in' and 'not in' types - so if there isn't a base primitive type, use the actual type.
         fun underlyingType(type: Type): Type {
            return if (type.isScalar) {
               type.basePrimitive ?: PrimitiveType.ANY
            } else {
               type
            }
         }

         val lhsType = underlyingType(coercedLhs.returnType)
         val rhsType = underlyingType(coercedRhs.returnType)

         return when {
            isNullCheck && !operator.supportsNullComparison() -> {
               listOf(
                  CompilationError(
                     expressionGroup.toCompilationUnit(),
                     "Operations with symbol '${operator.symbol}' is not supported when comparing against null"
                  )
               ).left()
            }

            !isNullCheck && !operator.supports(lhsType, rhsType) -> {
               listOf(
                  CompilationError(
                     expressionGroup.toCompilationUnit(),
                     "Operations with symbol '${operator.symbol}' is not supported on types ${lhsType.toQualifiedName().shortDisplayName} and ${rhsType.toQualifiedName().shortDisplayName}"
                  )
               ).left()
            }

            else -> {
               OperatorExpression(
                  lhs = coercedLhs,
                  operator = operator,
                  rhs = coercedRhs,
                  compilationUnits = expressionGroup.toCompilationUnits()
               ).right()
            }
         }
      } else {
         // Collect all the errors and bail out.
         return expressionComponents.invertEitherList().flattenErrors()
            .leftOr(emptyList())
            .left()
      }
   }

   private fun parseOperationInvocationExpression(
      operationInvocation: TaxiParser.OperationInvocationContext,
      assignmentType: Type?
   ): Either<List<CompilationError>, OperationInvocationExpression> {
      return tokenProcessor.resolveServiceAndOperation(
         operationInvocation.typeReference(),
         operationInvocation.memberReference()?.typeReference(),
         true,
         operationInvocation
      ).flatMap { (service, member: ServiceMember?) ->
         if (member == null) {
            return operationInvocation.createCompilationError("An operation reference is require here")
         }
         if (assignmentType != null) {
            val typeError = typeChecker.assertIsAssignable(member.returnType, assignmentType, operationInvocation)
            if (typeError != null) {
               return listOf(typeError).left()
            }
         }

         val argumentContexts = operationInvocation.argumentList()?.argument() ?: emptyList()
         functionCompiler.compileParameters(
            member,
            null,
            argumentContexts,
            operationInvocation,
            member.qualifiedName,
            operationInvocation.findNamespace()
         ).map { parameters ->
            OperationInvocationExpression(
               service,
               member,
               parameters,
               operationInvocation.toCompilationUnits()
            )
         }
      }
   }


   /**
    * Sometimes, the grammar gets it wrong, and parses a TypeExpression
    * as a FunctionCall.
    *
    * This is because gramatically, there's very little difference between
    *  - A type expression:  Actor(FirstName == 'Jimmy')
    *  - A function call:    uppercase(FirstName)
    *
    * Attempts to improve the grammar to refine the conditions
    * became a rabbit-hole of edge cases.
    *
    * Instead, parse the thing, and see what the symbols resolve to.
    */
   private fun parseFunctionExpressionOrTypeExpression(
      readFunction: TaxiParser.FunctionCallContext,
      assignmentType: Type?
   ): Either<List<CompilationError>, Expression> {
      return parseFunctionExpressionOrTypeExpression(readFunction.qualifiedName().text, readFunction, assignmentType)
   }

   /**
    * At this point, we have some tokens, but we don't know what they are.
    * They could be:
    *  - A type name
    *  - A function name
    *  - A function call on a type name (FirstName.toUppercase())
    *  - A function call on a scoped variable (myFirstName.toUppercase())
    */
   private fun parseFunctionExpressionOrTypeExpression(
      tokenName: String,
      readFunction: TaxiParser.FunctionCallContext,
      assignmentType: Type?,
      receiver: Expression? = null,
   ): Either<List<CompilationError>, Expression> {
      parseFunctionExpressionOrTypeExpressionUsingScopeName(tokenName, readFunction, assignmentType)?.let {
         return it
      }
      return tokenProcessor.findInSymbolTree(tokenName, readFunction)
         .flatMap { namedElements ->

            when {
               namedElements.size == 1 && namedElements.single().value is Type -> {
                  // The text we were passed exactly matched a Type
                  // So, parse it as such
                  parseFunctionExpressionAsTypeExpression(namedElements.single().value as Type, readFunction)
               }

               namedElements.size == 1 && namedElements.single().value is Function -> {
                  // The text we were passed exactly matched a function name
                  // (eg: filterEach(...)
                  // So, parse it as such
                  parseFunctionExpression(
                     readFunction = readFunction,
                     assignmentType = assignmentType,
                     receiver = receiver,
                     functionName = namedElements.single().text
                  )
               }

               namedElements.size == 2 -> {
                  if (namedElements[0].value is Type && namedElements[1].value is Function) {
                     // This is an extension function call
                     // eg: Movies.filterEach( ... )
                     // First parse the left-hand-side (Movies)
                     parseFunctionExpressionAsTypeExpression(
                        namedElements[0].value as Type,
                        readFunction
                     ).flatMap { lhs ->
                        parseFunctionExpression(
                           readFunction = readFunction,
                           assignmentType = assignmentType,
                           receiver = lhs,
                           functionName = namedElements[1].text
                        ).map { rhs ->
                           ExtensionFunctionExpression(
                              functionExpression = rhs,
                              receiverValue = lhs,
                              compilationUnits = readFunction.toCompilationUnits()
                           )
                        }
                     }
                  } else {
                     listOf(
                        CompilationError(
                           readFunction.toCompilationUnit(),
                           "An internal error occurred: Could not resolve token '$tokenName' as type or function correctly. Matched two nodes, but were of unexpected types: ${namedElements[0].value::class} and ${namedElements[1].value::class}"
                        )
                     ).left()
                  }
               }

               else -> listOf(
                  CompilationError(
                     readFunction.toCompilationUnit(),
                     "An internal error occurred: Could not resolve token '$tokenName' as type or function correctly. Expected 1-2 nodes, but found ${namedElements.size}"
                  )
               ).left()

            }
         }
   }

   /**
    * Attempts to resolve a token as an ExtensionFunctionExpression where the first
    * part of the call is a scoped variable.
    * If possible, returns either the extensionFunctionExpression, or the compilation errors.
    *
    * If the token is not a scope, then returns null.
    *
    * TODO :  Can we merge this functionality into SymbolTree somehow?
    */
   private fun parseFunctionExpressionOrTypeExpressionUsingScopeName(
      tokenName: String,
      readFunction: TaxiParser.FunctionCallContext,
      assignmentType: Type?
   ): Either<List<CompilationError>, Expression>? {
      val tokenParts = tokenName.split(".")
      val (scopePath, remainingTokens) = when (tokenParts.size) {
         0 -> error("Expected tokens defining a scope for calling an expression")
         1 -> listOf(tokenParts.single()) to emptyList<String>()
         else -> tokenParts.dropLast(1) to listOf(tokenParts.last())
      }

      return if (canResolveAsScopePath(scopePath)) {
         resolveScopePath(scopePath, readFunction)
            .flatMap { selector ->
               when {
                  remainingTokens.isEmpty() -> selector.right()
                  remainingTokens.size == 1 -> {
                     parseFunctionExpression(
                        readFunction,
                        assignmentType,
                        receiver = selector,
                        remainingTokens.single()
                     ).map { function ->
                        ExtensionFunctionExpression(function, selector, readFunction.toCompilationUnits())
                     }
                  }

                  else -> error("Unhandled branch - there were ${remainingTokens.size} tokens left to process, don't know how to handle this: ${readFunction.source().content}")
               }
            }
      } else {
         null
      }
   }

   /**
    * Takes a functionCall context which should've been parsed as a typeExpression,
    * and returns the typeExpression.
    *
    * This is for scenarios like Person(PersonName == 'Jimmy')
    * It should not be invoked for extension function calls on types, like Person.toUpperCase()
    *
    * I have a strong feeling this is gonna bite me in the butt.
    */
   private fun parseFunctionExpressionAsTypeExpression(
      type: Type,
      functionCall: TaxiParser.FunctionCallContext
   ): Either<List<CompilationError>, TypeExpression> {
      // In complex projects, we occasioanlly end up here where the type hasn't yet
      // been compiled properly. Haven't yet been able to reproduce in a test
      // If we hit that sitatuion, try to compile the type now.
      val forceCompiledType = if (type is ObjectType && !type.isDefined) {
         // Last ditch attempt.
         // The token hasn't been compiled, which stops us from proceeding.
         // Try to compile it now.
         // Note: The context we pass here is used for generating errors if the requested
         // token can't be compiled.
         // It's not the token we actually use to compile the type
         val recompiledType = tokenProcessor.compile(type, functionCall)
            .getOrElse {
               return it.left()
            }

         // Forcing compilation didn't work, so we have to give up.
         if (!recompiledType.isDefined) {
            return listOf(
               CompilationError(
                  functionCall.toCompilationUnit(),
                  "An internal error has occurred - attempted to use a type in an expression before the type was compiled"
               )
            )
               .left()
         }
         recompiledType
      } else {
         type
      }
      // Check to see if this type expression is part of an extension function call.
      // eg: Movie.filter( (Title) -> Title == "Jaws" )
      // If so, we can't treat the arguments as constraints.
      // Otherwise, it's a type expression with constraints
      // eg: Movie( Title == "Jaws" ), and we should include them in the parsed type.
      val typeExpressionIsFollowedByFunctionCall =
         !functionCall.qualifiedName().text.endsWith(forceCompiledType.qualifiedName.toQualifiedName().typeName)
      val parseArgumentAsConstraints = !typeExpressionIsFollowedByFunctionCall
      if (!parseArgumentAsConstraints) {
         // Defer to the typeExpressionBuilder to create the typed expression.
         // This allows an opportunity to decorate streamed types (in stream { Foo }) to
         // Stream<Foo>
         return typedExpressionBuilder.typedExpression(forceCompiledType, emptyList(), functionCall)
            .right()
      }

      // We're parsing a type with constraints
      // eg: Movie( Title == "Jaws" ).
      // Convert the remaining arguments to constraints
      val size = functionCall.argumentList()?.argument()?.size ?: 0
      if (size == 0) {
         return TypeExpression(forceCompiledType, emptyList(), functionCall.toCompilationUnits())
            .right()
      }
      if (size != 1) {
         return functionCall.createInternalError("Expected an argumentList with size of 1, but found $size: ${functionCall.source().content}")
      }
      val argument = functionCall.argumentList().argument().single()
      if (argument.scalarAccessorExpression() == null) {
         return argument.createInternalError("Expected a scalar expression, but did not find one")
      }
      return compileScalarAccessor(argument.scalarAccessorExpression()).map { accessor ->
         require(accessor is Expression) { "Expected to receive an expression when parsing functionExpression" }
         // Note: We're intentionally not deferring to the typeExpressionBuilder here.
         // This is because we're not constructing a typeExpression that we want to wrap for stream { Foo } -> Stream<Foo>
         // It got really hard to detect reliably if we're inside a place that should / shouldn't wrap when
         // inside the builder. So, for now, I'm just not calling the builder when I don't need wrapping.
         // Can revisit this when needed, but need to ensure that we improve the logic inside StreamDecoratingTypedExpressionBuilder
         TypeExpression(
            forceCompiledType, listOf(
               ExpressionConstraint(accessor)
            ),
            functionCall.toCompilationUnits()
         )
      }
   }

   /**
    * These are function calls that are invoked with a dot - eg:
    * find { "hello".toUpper() }
    */
   private fun parseExtensionFunctionCallExpression(
      expression: ExpressionGroupContext,
      targetType: Type?
   ): Either<List<CompilationError>, out Expression> {
      val lhsOrError = expression.expressionGroup(0)?.let { compile(it) }
         ?: error("Expected an expression group at index 0")
      return lhsOrError.flatMap { lhsExpression ->
         parseMethodInvocation(
            methodCall = expression.methodCall(),
            // MP: 14-Oct-24
            // Was: lhsExpression.returnType.
            // But, I don't think that actually is the targetType of the extension function - it's
            // whatever the declared type is that this function is being assigned to, or Any if that's not know
            // eg: Consider: [1,2,3].sum() ... the targetType isn't declared here (but is inferrable)
            targetType = targetType ?: PrimitiveType.ANY,
            receiver = lhsExpression
         )
//         tokenProcessor.resolveFunction(expression.functionCall().qualifiedName(), expression)
            .map { lhsExpression to it }
      }.flatMap { (lhsExpression, functionExpression) ->
         if (!functionExpression.function.function.isExtension) {
            return@flatMap listOf(
               CompilationError(
                  expression.methodCall().toCompilationUnit(),
                  "Function ${functionExpression.function.qualifiedName} is not an extension function, so cannot be called using the dot-syntax"
               )
            )
               .left()
         }
         ExtensionFunctionExpression(functionExpression, lhsExpression, expression.toCompilationUnits())
            .right()
      }
   }

   private fun parseAccessorExpression(expression: ExpressionGroupContext): Either<List<CompilationError>, out Expression> {
      val lhsOrError = expression.expressionGroup(0)?.let { compile(it) }
         ?: expression.createInternalError("Expected an expression group at index 0")
      return lhsOrError.flatMap { lhsExpression ->
         val memberIdentifier = expression.identifier()
            ?: return expression.createInternalError("Expected a qualifiedName, but none was found")
         getMemberReference(lhsExpression.returnType, memberIdentifier.text, expression).map { member ->
            MemberAccessExpression(lhsExpression, member, expression.toCompilationUnits())
         }
      }
   }

   private fun parseMethodInvocation(
      methodCall: TaxiParser.MethodCallContext,
      targetType: Type,
      receiver: Expression
   ): Either<List<CompilationError>, FunctionExpression> {
      return functionCompiler.buildFunctionAccessor(
         methodCall.findNamespace(),
         methodCall.identifier().text,
         methodCall,
         methodCall.argumentList()?.argument() ?: emptyList(),
         targetType,
         receiver
      ).map { functionAccessor ->
         FunctionExpression(functionAccessor, methodCall.toCompilationUnits())
      }
   }

   private fun parseFunctionExpression(
      readFunction: TaxiParser.FunctionCallContext,
      assignmentType: Type?,
      /**
       * Indicates if this function has been called with a receiver (ie., an extension function).
       * This is when calling an extension function as "hello".toUpperCase()
       * In this case, the first param is considered the receiver, which needs to be considered when
       * parsing the params of the function, moving their offset by 1.
       */
      receiver: Expression? = null,
      /**
       * Allows overriding the function name.
       * Needed when parsing a function with dot-syntax,
       * eg: PersonName.uppercase()
       *
       */
      functionName: String = readFunction.qualifiedName().identifier().text()
   ): Either<List<CompilationError>, FunctionExpression> {

      // It's not always required to declare a type for a variable.
      // eg :
      // model Foo {
      //   d : someFunction() // d is the return type of someFunction
      // }
      // However, sometimes, the return type of a function is defined by the call site.
      // eg:
      // declare function <T> anotherFunction():T
      // model Foo {
      //    d : String = anotherFunction() // anotherFunction should return String
      // }
      val declaredFunctionReturnType = assignmentType?.right() ?: tokenProcessor.getType(
         namespace = readFunction.findNamespace(),
         name = PrimitiveType.ANY.qualifiedName,
         context = readFunction
      )
      return declaredFunctionReturnType
         .flatMap { targetType ->
            functionCompiler.buildFunctionAccessor(readFunction, targetType, receiver, functionName)
         }.map { functionAccessor ->
            FunctionExpression(functionAccessor, readFunction.toCompilationUnits())
         }
   }


   private fun parseTypeProjectionExpression(
      typeProjection: TypeProjectionContext,
      assignmentType: Type?
   ): Either<List<CompilationError>, Expression> {
      TODO()
   }

   private fun parseTypeExpression(typeExpression: TaxiParser.TypeExpressionContext): Either<List<CompilationError>, Expression> {
      val typeReference = typeExpression.nullableTypeReference().typeReference()
      if (typeReference != null && canResolveAsScopePath(typeReference.qualifiedName())) {
         return resolveScopePath(typeReference.qualifiedName())
      }
      return tokenProcessor.parseTypeOrUnionType(typeExpression.nullableTypeReference())
         .flatMap { type ->
            ConstraintBuilder(this).build(
               typeExpression.parameterConstraint(),
               type
            ).map { constraints -> type to constraints }
         }
         .map { (type, constraints) -> typedExpressionBuilder.typedExpression(type, constraints, typeExpression) }
         .handleErrorWith { errors ->
            if (typeReference != null && Enums.isPotentialEnumMemberReference(
                  typeReference.qualifiedName().identifier().text()
               )
            ) {
               tokenProcessor.resolveEnumMember(typeReference.qualifiedName().identifier().text(), typeExpression)
                  .map { enumMember ->
                     LiteralExpression(
                        LiteralAccessor(enumMember.value, enumMember.enum),
                        typeExpression.toCompilationUnits()
                     )
                  }
            } else {
               errors.left()
            }
         }
      // MP: 15-Sep-25: The error handling here seems messed up.
      // If we restore it, we at least need to check that the error we're getting related
      // to services.
      // Otherwise, we are bascially swallowing real errors.
      // See test in ExpressionsSpec "should show a meaningful error when using the wrong operator for an array check",
      // which shows an error we were swallowing.
      // NOte: After commenting this block out, zero tests failed, which suggest it was doing more harm than good...
//         .handleErrorWith { errors ->
//            // This is possibly a reference to a service
//            if (typeReference != null) {
//               // TODO: MP 26-Mar-25: It'd be cleaner if we didn't have to specify the symbol kind here.
//               tokenProcessor.attemptToLookupSymbolByName(
//                  typeReference.findNamespace(),
//                  typeReference.qualifiedName().identifier().text(),
//                  typeReference,
//                  SymbolKind.SERVICE
//               ).wrapErrorsInList()
//                  // If this wasn't a service, stick with the previous set of errors, as they;re more relevant
//                  .mapLeft { errors }
//                  .flatMap {
//                     tokenProcessor.typeSystem.getTokenOrError(it, typeReference, SymbolKind.SERVICE).wrapErrorsInList()
//                        // If this wasn't a service, stick with the previous set of errors, as they;re more relevant
//                        .mapLeft { errors }
//                        .flatMap { importableToken ->
//                           when (importableToken) {
//                              is Service -> ServiceExpression(
//                                 importableToken,
//                                 typeReference.toCompilationUnits()
//                              ).right()
//
//
//                              else -> typeReference.createCompilationError("Expected a service reference here")
//                           }
//                        }
//                  }
//            } else {
//               errors.left()
//            }
//         }
   }

   fun canResolveAsScopePath(qualifiedName: QualifiedNameContext): Boolean {
      val identifierTokens = qualifiedName.identifier().map { it.text }
      return canResolveAsScopePath(identifierTokens)
   }

   private fun canResolveAsScopePath(identifierTokens: List<String>): Boolean {
      return scopes.any { it.matchesReference(identifierTokens) }
   }


   /**
    * Resolves a path declared using a scope, against the field name.
    * for example:
    *
    * find{ Foo[] } as (foo : Foo) {
    *    thing : foo.bar // <---resolves bar property against foo
    * }
    */
   fun resolveScopePath(qualifiedName: QualifiedNameContext): Either<List<CompilationError>, ArgumentSelector> {
      val identifierTokens = qualifiedName.identifier().map { it.text }
      return resolveScopePath(identifierTokens, context = qualifiedName)
   }

   private fun resolveScopePath(
      identifierTokens: List<String>,
      context: ParserRuleContext
   ): Either<List<CompilationError>, ArgumentSelector> {
      // if we can resolve it through a scope, do so.
      val resolvedScopeReference = scopes.firstOrNull { it.matchesReference(identifierTokens) }
         ?: error("Internal error : Failed to resolve identifier ${identifierTokens.joinToString(".")} - current scopes are ${scopes.joinToString { it.name }}")
      return resolveScopePath(
         resolvedScopeReference,
         resolvedScopeReference.pruneFieldPath(identifierTokens),
         context
      ).flatMap { fieldSelectors ->

         ArgumentSelector(
            resolvedScopeReference,
            resolvedScopeReference.pruneFieldSelectors(fieldSelectors),
            context.toCompilationUnits()
         ).right()
      }
   }

   private fun getMemberReference(
      sourceType: Type,
      fieldName: String,
      context: ParserRuleContext
   ): Either<List<CompilationError>, FieldReferenceSelector> {
      return when {
         sourceType is ObjectType && sourceType.hasField(fieldName) -> {
            FieldReferenceSelector(fieldName, sourceType.field(fieldName).type).right()
         }

         sourceType is EnumType && sourceType.valueType is ObjectType && (sourceType.valueType!! as ObjectType).hasField(
            fieldName
         ) -> {
            FieldReferenceSelector(
               fieldName,
               (sourceType.valueType as ObjectType).field(fieldName).type
            ).right()
         }

         else -> {
            listOf(
               CompilationError(
                  context.toCompilationUnit(),
                  "Cannot resolve reference $fieldName against type ${sourceType.toQualifiedName().parameterizedName}"
               )
            ).left()
         }
      }
   }

   /**
    * Maps the full scope path.
    * Callers are responsible for dropping the first path, if
    * that's already handled (ie., if the initial scope is "special" - "this")
    */
   private fun resolveScopePath(
      scope: Argument,
      path: List<String>,
      context: ParserRuleContext
   ): Either<List<CompilationError>, List<FieldReferenceSelector>> {
      val initial = FieldReferenceSelector(scope.name, scope.type)
      // We must return the initial scope, so that it can be consistently dropped
      return if (path.isEmpty()) {
         listOf(initial).right()
      } else {
         return path
            .runningFold(initial.right() as Either<List<CompilationError>, FieldReferenceSelector>) { resolvedType, fieldName ->
               resolvedType.flatMap { selector ->
                  val previousType = selector.declaredType
                  getMemberReference(previousType, fieldName, context)
               }
            }.invertEitherList().flattenErrors()
      }
   }

   override fun compileScalarAccessor(
      expression: TaxiParser.ScalarAccessorExpressionContext,
      targetType: Type
   ): Either<List<CompilationError>, Accessor> {
      return when {
         expression.jsonPathAccessorDeclaration() != null ||
            expression.xpathAccessorDeclaration() != null ||
//            expression.byFieldSourceExpression() != null ||
//            expression.conditionalTypeConditionDeclaration() != null ||
//            expression.collectionProjectionExpression() != null ||
            expression.columnDefinition() != null -> {
            if (this.fieldCompiler == null) {
               listOf(
                  CompilationError(
                     expression.toCompilationUnit(),
                     "Accessors are not supported in Expression Types outside of model declarations"
                  )
               ).left()
            } else {
               fieldCompiler.compileScalarAccessor(expression, targetType)
            }
         }

         expression.expressionGroup() != null -> {
            val compiled = compile(expression.expressionGroup(), targetType)
            compiled
         }
//         expression.functionCall() != null -> {
//            val functionContext = expression.functionCall()
//            functionCompiler.buildFunctionAccessor(functionContext, targetType)
//         }
         else -> error("Unhandled type of accessor expression at ${expression.source().content}")
      }
   }

   private fun requireFieldCompilerIsPresent(parserContext: ParserRuleContext): Either<List<CompilationError>, Boolean> {
      return if (this.fieldCompiler == null) {
         listOf(
            CompilationError(
               parserContext.start,
               "Cannot use field references when outside the scope of a model"
            )
         ).left()
      } else {
         true.right()
      }
   }

   override fun compileFieldReferenceAccessor(
      parameterContext: TaxiParser.ArgumentContext
   ): Either<List<CompilationError>, FieldReferenceSelector> {
      return requireFieldCompilerIsPresent(parameterContext).flatMap {
         fieldCompiler!!.provideField(
            parameterContext.fieldReferenceSelector().qualifiedName().identifier().text(),
            parameterContext
         )
            .map { field -> FieldReferenceSelector.fromField(field) }
      }
   }

   override fun parseTypeMemberReference(
      lhsExpressionGroup: ExpressionGroupContext,
      typeMemberReference: TaxiParser.MemberReferenceContext
   ): Either<List<CompilationError>, MemberTypeReferenceExpression> {
      val compiledLhsExpression = compile(lhsExpressionGroup)
      val sourceTypeReference =
         lhsExpressionGroup.expressionAtom()?.typeExpression()?.nullableTypeReference()?.typeReference()
      // The source is either a type, and sometimes with an argument selector
      // if the type is a reference to a scoped argument.
      // eg: (movie:Movie) -> {
      //   something : Something[]( MovieId == movie::MovieId )
      var sourceExpression: Expression? = null
      val source: Either<List<CompilationError>, Pair<Type, ArgumentSelector?>> =
         when {
            sourceTypeReference != null && canResolveAsScopePath(sourceTypeReference.qualifiedName()) -> {
               // Is the source actually a scoped variable?
               resolveScopePath(sourceTypeReference.qualifiedName()).map { selector ->
                  selector.returnType to selector
               }
            }
            // The LHS could be an expression that we're using as a type reference
            compiledLhsExpression.isRight() -> {
               compiledLhsExpression.map { lhsExpression ->
                  sourceExpression = lhsExpression
                  lhsExpression.returnType to null
               }
            }

            else -> {
               if (sourceTypeReference == null) {
                  lhsExpressionGroup.createCompilationError("Expected either a type reference, an expression returning a type, or a variable name here")
               } else {
                  tokenProcessor.typeOrError(sourceTypeReference).map { type -> type to null }
               }

            }
         }
      return source.flatMap { (sourceType, argumentSelector) ->
         tokenProcessor.typeOrError(typeMemberReference.typeReference()).map { targetType ->
            val returnType = if (typeMemberReference.arrayMarker() != null) {
               ArrayType.of(targetType, typeMemberReference.typeReference().toCompilationUnit())
            } else {
               targetType
            }
            MemberTypeReferenceExpression(
               sourceType.toQualifiedName(),
               targetType,
               returnType,
               argumentSelector,
               sourceExpression,
               typeMemberReference.toCompilationUnit()
            )
         }
      }
   }
}

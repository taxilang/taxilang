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
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.types.*
import lang.taxi.utils.*
import org.antlr.v4.runtime.ParserRuleContext

class ExpressionCompiler(
   private val tokenProcessor: TokenProcessor,
   private val typeChecker: TypeChecker,
   private val errors: MutableList<CompilationError>,
   /**
    * Pass the fieldCompiler when the expression being compiled is within the field of a model / query result.
    * This allows field lookups by name in expressions
    */
   private val fieldCompiler: FieldCompiler? = null,
   private val scopes: List<Argument> = emptyList(),
   private val typedExpressionBuilder: TypedExpressionBuilder = DefaultTypedExpressionBuilder
) : FunctionParameterReferenceResolver {
   private val functionCompiler = FunctionAccessorCompiler(
      tokenProcessor,
      typeChecker,
      errors,
      this,
   )

   fun withTypedExpressionBuilder(newBuilder: TypedExpressionBuilder):ExpressionCompiler {
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
                  val projection = FieldProjection.forNullable(expression.returnType, projectedTypeAndScope)!!
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
            expressionGroup
         )

         // Might need to be more specific here -- this should be expressionGroup.functionCall
         expressionGroup.children.size == 3 && expressionGroup.functionCall() != null -> parseExtensionFunctionCallExpression(
            expressionGroup
         )

         expressionGroup.children.size == 3 -> parseOperatorExpression(expressionGroup)          // lhs operator rhs
         expressionGroup.expressionGroup().isEmpty() -> compileSingleExpression(expressionGroup, targetType)

         else -> error("Unhandled expression group scenario: ${expressionGroup.text}")
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

   private fun compileCastExpression(
      expressionGroup: ExpressionGroupContext,
      targetType: Type?
   ) = if (expressionGroup.expressionGroup() == null || expressionGroup.expressionGroup().size != 1) {
      listOf(
         CompilationError(
            expressionGroup.toCompilationUnit(),
            "An internal error occurred - expected a single expressionGroup after a cast statement"
         )
      )
         .left()
   } else {
      tokenProcessor.typeOrError(expressionGroup.castExpression().typeReference())
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
         fieldCompiler.parseFieldProjection(typeProjection, projectionSourceType, typeName)
      } else {
         error("Expected we were parsing an expression with a projection inside a field.  Understand this usecase")
      }
   }

   private fun parseLambdaExpression(lambdaExpression: ExpressionGroupContext): Either<List<CompilationError>, out Expression> {
      require(lambdaExpression.children.size == 2) { "Expected exactly 2 children in the lambda expression" }
      require(lambdaExpression.expressionGroup().size == 1) { "expected exactly 1 expression group on the rhs of the lambda" }
      return lambdaExpression.expressionInputs()
         .expressionInput().map { expressionInput ->
            tokenProcessor.parseType(
               expressionInput.findNamespace(),
               expressionInput.nullableTypeReference().typeReference()
            )
         }.invertEitherList().flattenErrors()
         .flatMap { inputs ->
            compile(lambdaExpression.expressionGroup(0)).map { expression ->
               LambdaExpression(inputs, expression, lambdaExpression.toCompilationUnits())
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
            require(fieldCompiler != null) { "Cannot compile a When Block as the expression compiler was initialized without a field compiler" }
            require(assignmentType != null) { "Cannot compile a When Block as no assignment type has been provided" }
            val whenCompiler = WhenBlockCompiler(fieldCompiler, this)
            whenCompiler.compileWhenCondition(expression.whenBlock(), assignmentType)
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

         expressionAtom.literal() != null -> parseLiteralExpression(expressionAtom.literal())
         expressionAtom.fieldReferenceSelector() != null -> parseAttributeSelector(expressionAtom.fieldReferenceSelector())
         expressionAtom.modelAttributeTypeReference() != null -> parseModelAttributeTypeReference(expressionAtom.modelAttributeTypeReference())
         else -> error("Unhandled atom in expression: ${expressionAtom.text}")
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

   private fun parseLiteralExpression(literal: TaxiParser.LiteralContext): Either<List<CompilationError>, Expression> {
      return LiteralExpression(LiteralAccessor(literal.valueOrNullValue()), literal.toCompilationUnits()).right()
   }


   private fun parseOperatorExpression(expressionGroup: ExpressionGroupContext): Either<List<CompilationError>, out Expression> {

      val lhsOrError = expressionGroup.expressionGroup(0)?.let { compile(it) }
         ?: error("Expected an expression group at index 0")
      val rhsOrError = expressionGroup.expressionGroup(1)?.let { compile(it) }
         ?: error("Expected an expression group at index 1")
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

         val lhsType = coercedLhs.returnType.basePrimitive ?: PrimitiveType.ANY
         val rhsType = coercedRhs.returnType.basePrimitive ?: PrimitiveType.ANY

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
                     "Operations with symbol '${operator.symbol}' is not supported on types ${lhsType.declaration} and ${rhsType.declaration}"
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


      return tokenProcessor.resolveImportableToken(tokenName, readFunction.qualifiedName())
         .flatMap { token ->
            when (token) {
               is Type -> parseFunctionExpressionAsTypeExpression(token, readFunction)
               else -> parseFunctionExpression(
                  readFunction,
                  assignmentType,
                  functionName = tokenName,
                  receiver = receiver
               )
            }
         }
         .flatMapLeft { error ->
            if (!error.any { it.errorCode == ErrorCodes.UNRESOLVED_TYPE.errorCode }) {
               return@flatMapLeft error.left()
            }
            // At this point, we likely have an error like "Movie.filterEach is not defined".
            // Try to recover from a parse error, by checking if this is actually a function call
            // in the form of foo.bar(), indicating a call of bar() on foo.
            // Verify by trying to resolve BOTH foo and bar.
            if (readFunction.qualifiedName().identifier().size > 1) {
               val lhs = readFunction.qualifiedName().identifier()
                  .dropLast(1)
                  .joinToString(".") { it.text }
               parseFunctionExpressionOrTypeExpression(lhs, readFunction, assignmentType)
                  .flatMap { lhsExpression ->
                     val rhs = readFunction.qualifiedName().identifier().last().text
                     parseFunctionExpressionOrTypeExpression(
                        rhs,
                        readFunction,
                        assignmentType,
                        receiver = lhsExpression
                     ).map { rhsExpression ->
                        require(rhsExpression is FunctionExpression) { "Expected a FunctionExpression, but got ${rhsExpression::class.simpleName}" }
                        ExtensionFunctionExpression(
                           functionExpression = rhsExpression,
                           receiverValue = lhsExpression,
                           compilationUnits = readFunction.toCompilationUnits()
                        )
                     }
                  }
            } else {
               error.left()
            }
         }
   }

   /**
    * Attempts to resolve a token as an ExtensionFunctionExpression where the first
    * part of the call is a scoped variable.
    * If possible, returns either the extensionFunctionExpression, or the compilation errors.
    *
    * If the token is not a scope, then returns null.
    */
   private fun parseFunctionExpressionOrTypeExpressionUsingScopeName(
      tokenName: String,
      readFunction: TaxiParser.FunctionCallContext,
      assignmentType: Type?
   ): Either<List<CompilationError>, Expression>? {
      val tokenParts = tokenName.split(".")
      val firstToken = listOf(tokenParts.first())
      return if (canResolveAsScopePath(firstToken)) {
         resolveScopePath(firstToken, readFunction)
            .flatMap { selector ->
               val remainingTokens = tokenParts.drop(1)
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

      // Check to see if this type expression is part of an extension function call.
      // eg: Movie.filter( (Title) -> Title == "Jaws" )
      // If so, we can't treat the arguments as constraints.
      // Otherwise, it's a type expression with constraints
      // eg: Movie( Title == "Jaws" ), and we should include them in the parsed type.
      val typeExpressionIsFollowedByFunctionCall =
         !functionCall.qualifiedName().text.endsWith(type.qualifiedName.toQualifiedName().typeName)
      val parseArgumentAsConstraints = !typeExpressionIsFollowedByFunctionCall
      if (!parseArgumentAsConstraints) {
         // Defer to the typeExpressionBuilder to create the typed expression.
         // This allows an opportunity to decorate streamed types (in stream { Foo }) to
         // Stream<Foo>
         return typedExpressionBuilder.typedExpression(type, emptyList(), functionCall)
            .right()
      }

      // We're parsing a type with constraints
      // eg: Movie( Title == "Jaws" ).
      // Convert the remaining arguments to constraints
      val size = functionCall.argumentList().argument().size
      require(size == 1) { "Expected an argumentList with size of 1, but found $size: ${functionCall.source().content}" }
      val argument = functionCall.argumentList().argument().single()
      require(argument.scalarAccessorExpression() != null) { "Expected a scalar expression, but did not find one" }
      return compileScalarAccessor(argument.scalarAccessorExpression()).map { accessor ->
         require(accessor is Expression) { "Expected to receive an expression when parsing functionExpression" }
         // Note: We're intentionally not deferring to the typeExpressionBuilder here.
         // This is because we're not constructing a typeExpression that we want to wrap for stream { Foo } -> Stream<Foo>
         // It got really hard to detect reliably if we're inside a place that should / shouldn't wrap when
         // inside the builder. So, for now, I'm just not calling the builder when I don't need wrapping.
         // Can revisit this when needed, but need to ensure that we improve the logic inside StreamDecoratingTypedExpressionBuilder
         TypeExpression(
            type, listOf(
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
   private fun parseExtensionFunctionCallExpression(expression: ExpressionGroupContext): Either<List<CompilationError>, out Expression> {
      val lhsOrError = expression.expressionGroup(0)?.let { compile(it) }
         ?: error("Expected an expression group at index 0")
      return lhsOrError.flatMap { lhsExpression ->
         parseFunctionExpression(expression.functionCall(), lhsExpression.returnType, receiver = lhsExpression)
//         tokenProcessor.resolveFunction(expression.functionCall().qualifiedName(), expression)
            .map { lhsExpression to it }
      }.flatMap { (lhsExpression, functionExpression) ->
         if (!functionExpression.function.function.isExtension) {
            return@flatMap listOf(
               CompilationError(
                  expression.functionCall().toCompilationUnit(),
                  "Function ${functionExpression.function.qualifiedName} is not an extension function, so cannot be called using the dot-syntax"
               )
            )
               .left()
         }
         ExtensionFunctionExpression(functionExpression, lhsExpression, expression.toCompilationUnits())
            .right()
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
            if (Enums.isPotentialEnumMemberReference(typeReference.qualifiedName().identifier().text())) {
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
      val resolvedScopeReference = scopes.first { it.matchesReference(identifierTokens) }
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
                  if (previousType is ObjectType && previousType.hasField(fieldName)) {
                     FieldReferenceSelector(fieldName, previousType.field(fieldName).type).right()
                  } else {
                     listOf(
                        CompilationError(
                           context.toCompilationUnit(),
                           "Cannot resolve reference $fieldName against type ${previousType.toQualifiedName().parameterizedName}"
                        )
                     ).left()
                  }
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
      function: Function,
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

   override fun parseModelAttributeTypeReference(
      modelAttributeReferenceCtx: TaxiParser.ModelAttributeTypeReferenceContext
   ): Either<List<CompilationError>, ModelAttributeReferenceSelector> {

      val sourceTypeReference = modelAttributeReferenceCtx.typeReference().first()
      val targetTypeReference = modelAttributeReferenceCtx.typeReference()[1]

      return fieldCompiler!!.typeOrError(sourceTypeReference).flatMap { sourceType ->
         fieldCompiler.typeOrError(targetTypeReference).map { targetType ->
            val returnType = if (modelAttributeReferenceCtx.arrayMarker() != null) {
               ArrayType.of(targetType, targetTypeReference.toCompilationUnit())
            } else {
               targetType
            }
            ModelAttributeReferenceSelector(
               sourceType.toQualifiedName(),
               targetType,
               returnType,
               modelAttributeReferenceCtx.toCompilationUnit()
            )
         }
      }
   }
}

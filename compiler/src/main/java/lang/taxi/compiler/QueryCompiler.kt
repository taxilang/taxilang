package lang.taxi.compiler

import arrow.core.*
import lang.taxi.*
import lang.taxi.TaxiParser.ServiceOrMemberReferenceContext
import lang.taxi.TaxiParser.TypeReferenceContext
import lang.taxi.TaxiParser.ValueContext
import lang.taxi.accessors.Argument
import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.compiler.fields.FieldTypeSpec
import lang.taxi.expressions.Expression
import lang.taxi.expressions.TypeExpression
import lang.taxi.mutations.Mutation
import lang.taxi.query.*
import lang.taxi.services.Operation
import lang.taxi.services.OperationScope
import lang.taxi.services.Service
import lang.taxi.services.ServiceMember
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList
import lang.taxi.values.PrimitiveValues
import org.antlr.v4.runtime.ParserRuleContext

internal class QueryCompiler(
   private val tokenProcessor: TokenProcessor,
   private val expressionCompiler: ExpressionCompiler
) {
   fun parseQueryBody(
      name: QualifiedName,
      parameters: List<Parameter>,
      annotations: List<Annotation>,
      docs: String?,
      ctx: TaxiParser.QueryBodyContext,
      compilationUnit: CompilationUnit
   ): Either<List<CompilationError>, TaxiQlQuery> {
      val queryDirective = when {
         ctx.queryOrMutation().queryDirective() == null && ctx.queryOrMutation().mutation() != null -> QueryMode.MUTATE

//         ctx.queryDirective().FindAll() != null -> QueryMode.FIND_ALL
//         ctx.queryDirective().FindOne() != null -> QueryMode.FIND_ONE
         //Deprecating FindAll/FindOne in favour of Find which behaves the same as FindAll
         ctx.queryOrMutation().queryDirective()?.K_Find() != null -> QueryMode.FIND_ALL
         ctx.queryOrMutation().queryDirective()?.K_Stream() != null -> QueryMode.STREAM
         ctx.queryOrMutation().queryDirective()?.K_Map() != null -> QueryMode.MAP
         else -> error("Unhandled Query Directive")
      }
      val factsOrErrors = ctx.givenBlock()?.let { parseFacts(it, parameters) } ?: emptyList<Parameter>().right()
      val queryOrErrors = factsOrErrors.flatMap { facts ->

         parseQueryBody(ctx, facts + parameters, queryDirective).flatMap { typesToDiscover ->
            parseTypeToProject(
               ctx.queryOrMutation()?.typeProjection(),
               typesToDiscover,
               parameters
            ).flatMap { typeToProject ->
               parseMutation(ctx.queryOrMutation().mutation()).flatMap { mutation ->
                  parseServiceRestrictions(ctx.queryOrMutation().serviceRestrictions()).map { serviceRestrictions ->
                     TaxiQlQuery(
                        name = name,
                        facts = facts,
                        queryMode = queryDirective,
                        parameters = parameters,
                        discoveryType = typesToDiscover,
                        projectedType = typeToProject?.first,
                        projectionScopeVars = typeToProject?.second ?: emptyList(),
                        typeDoc = docs,
                        annotations = annotations,
                        mutation = mutation,
                        serviceRestrictions = serviceRestrictions,
                        compilationUnits = listOf(compilationUnit)
                     )
                  }
               }
            }
         }
      }
      return queryOrErrors
   }

   private fun parseMutation(mutationCtx: TaxiParser.MutationContext?): Either<List<CompilationError>, Mutation?> {
      if (mutationCtx == null) return Either.Right(null)
      val memberReference = mutationCtx.memberReference()

      return resolveServiceAndOperation(
         memberReference.typeReference(0),
         memberReference.typeReference(1),
         operationTokenRequired = true,
         memberReference
      )
         .flatMap { (service, operation) ->
            fun compilationError(message: String): Either<List<CompilationError>, Nothing> {
               return listOf(CompilationError(memberReference.start, message)).left()
            }
            if (operation !is Operation) return@flatMap compilationError("Mutations are only supported on operations")

            if (operation.scope != OperationScope.MUTATION) return@flatMap compilationError("Call statements are only valid with write operations.  Operation ${memberReference.text} is not a write operation")
            (service to operation).right()
         }
         .map { (service, operation) ->
            Mutation(
               service,
               operation,
               mutationCtx.toCompilationUnits()
            )
         }
   }

   /**
    * Given a service + operation call reference in the form of
    * ServiceName::OperationName or, just a service reference in the form of ServiceName,
    * will resolve both service and operation, returning compilation errors if encountered
    */
   private fun resolveServiceAndOperation(
      serviceToken: TypeReferenceContext,
      operationToken: TypeReferenceContext?,
      operationTokenRequired: Boolean,
      context: ParserRuleContext
   ): Either<List<CompilationError>, Pair<Service, ServiceMember?>> {
      return tokenProcessor.resolveImportableToken(
         serviceToken.qualifiedName(),
         context,
         SymbolKind.SERVICE
      )
         .flatMap { service ->
            fun compilationError(message: String): Either<List<CompilationError>, Nothing> {
               return listOf(CompilationError(serviceToken.start, message)).left()
            }

            if (service !is Service) return@flatMap compilationError("Expected a reference to a service.  ${service.qualifiedName} is not a service")
            if (operationToken == null && operationTokenRequired) {
               return@flatMap compilationError("Mutations require a reference to services and operations in the form of ServiceName::operationName. No operation name was provided")
            }
            val operation = if (operationToken != null) {
               if (!service.containsMember(operationToken.text)) return@flatMap compilationError("Service ${service.qualifiedName} does not declare an operation ${operationToken.text}")
               service.member(operationToken.text)
            } else null
            (service to operation).right()
         }
   }

   private fun parseQueryBody(
      queryBodyContext: TaxiParser.QueryBodyContext,
      parameters: List<Parameter>,
      queryDirective: QueryMode
   ): Either<List<CompilationError>, DiscoveryType?> {
      val namespace = queryBodyContext.findNamespace()
      val constraintBuilder =
         ConstraintBuilder(expressionCompiler.withParameters(parameters))

      /**
       * A query body can either be a concrete type:
       * findAll { foo.bar.Order[] }
       *
       * or an anonymous type
       *
       * findAll {
       *    field1: Type1
       *    field2: Type2
       * }
       */
      val typeExpression: Either<List<CompilationError>, Expression> = when {
         queryBodyContext.queryOrMutation().expressionGroup() != null -> {
            expressionCompiler
               // wrap stream { Foo } so that Foo becomes Stream<Foo>
               .withTypedExpressionBuilder(StreamDecoratingTypedExpressionBuilder)
               .withParameters(parameters)
               .compile(queryBodyContext.queryOrMutation().expressionGroup())
         }

         queryBodyContext.queryOrMutation()?.anonymousTypeDefinition() != null -> parseAnonymousTypesIfPresent(
            namespace,
            queryBodyContext.queryOrMutation().anonymousTypeDefinition(),
            constraintBuilder,
            parameters
         )

         queryBodyContext.queryOrMutation().mutation() != null -> {
            // at this stage, it's just a mutation, with no discovery types, so return null
            return (null).right()
         }


         else -> error("Unhandled code branch - expected to create a type expression by parsing query block")
      }
      return typeExpression
//         .map { expression ->
//            if (queryDirective == QueryMode.STREAM) {
//               // Wrap stream queries as Stream<T>
//               when (expression) {
//                  is TypeExpression -> expression.copy(type = StreamType.of(expression.type))
//                  is ExtensionFunctionExpression -> {
//                     if (expression.receiverValue is TypeExpression) {
//                        val receiverTypeExpression = expression.receiverValue as TypeExpression
//                        expression.copy(
//                           receiverValue = receiverTypeExpression.copy(
//                              type = StreamType.of(
//                                 receiverTypeExpression.type
//                              )
//                           )
//                        )
//                     } else {
//                        return listOf(
//                           CompilationError(
//                              expression.compilationUnits.first(),
//                              "This expression cannot be streamed."
//                           )
//                        )
//                           .left()
//                     }
//                  }
//
//                  else -> error("Unhandled branch in wrapping stream queries")
//               }
//            } else expression
//         }
         .map { expression ->
            DiscoveryType(
               expression, parameters
            )
         }
   }

   private fun parseAnonymousTypesIfPresent(
      namespace: String,
      anonymousTypeDefinition: TaxiParser.AnonymousTypeDefinitionContext,
      constraintBuilder: ConstraintBuilder,
      parameters: List<Parameter>
   ): Either<List<CompilationError>, TypeExpression> {

      return tokenProcessor.parseAnonymousType(
         namespace,
         anonymousTypeDefinition,
         resolutionContext = ResolutionContext(parameters = parameters)
      ).flatMap { anonymousType ->
         constraintBuilder.build(anonymousTypeDefinition.parameterConstraint(), anonymousType)
            .map { constraints ->
               TypeExpression(
                  anonymousType,
                  constraints,
                  anonymousTypeDefinition.toCompilationUnits()
               )
            }

      }
   }


   private fun parseFacts(
      givenBlock: TaxiParser.GivenBlockContext,
      parameters: List<Parameter>
   ): Either<List<CompilationError>, List<Parameter>> {
      val original: Either<List<CompilationError>, List<Parameter>> = parameters.right()
      return givenBlock.factList().fact().foldIndexed(original) { idx, paramsOrErrors, factCtx ->
         paramsOrErrors.flatMap { params ->
            parseFact(idx, factCtx, params).map {
               params + it
            }
         }
      }

   }

   private fun parseFact(
      index: Int,
      factCtx: TaxiParser.FactContext,
      parameters: List<Parameter>
   ): Either<List<CompilationError>, Parameter> {
      val variableName = factCtx.factDeclaration()?.variableName()?.identifier()?.text ?: "fact$index"
      val namespace = factCtx.findNamespace()

      return when {
         factCtx.variableName() != null && factCtx.factDeclaration() == null -> parseFactVariableReference(
            factCtx.variableName(),
            parameters
         )

         factCtx.variableName() == null && factCtx.factDeclaration() != null -> parseFactValueDeclaration(
            factCtx,
            namespace,
            variableName,
            parameters
         )

         else -> error("Expected either a variable name reference, or a fact declaration with a value, but got both (or neither): ${factCtx.parent.text}")
      }
   }

   private fun parseFactValueDeclaration(
      factCtx: TaxiParser.FactContext,
      namespace: String,
      variableName: String,
      parameters: List<Parameter>
   ): Either<List<CompilationError>, Parameter> {
      require(factCtx.factDeclaration() != null) { "Expected the declared fact to have a value" }
      val factDeclaration = factCtx.factDeclaration()
      return tokenProcessor.typeOrError(namespace, factDeclaration.typeReference()).flatMap { factType ->
         try {
            readValue(factDeclaration.value(), factType, false, parameters)
               .map { factValue ->
                  when {
                     factValue is Expression -> Parameter(
                        name = variableName,
                        value = FactValue.Expression(factType, factValue),
                        annotations = emptyList()
                     )

                     factValue != null -> Parameter(
                        name = variableName,
                        value = FactValue.Constant(TypedValue(factType, factValue)),
                        annotations = emptyList()
                     )

                     else -> {
                        error("It is illegal in the grammar to not decalre a factValue. You shouldn't hit this part")

                     }
                  }
               }
         } catch (e: Exception) {
            listOf(
               CompilationError(
                  factCtx.start,
                  "Failed to create TypedInstance - ${e.message ?: e::class.simpleName}"
               )
            ).left()
         }
      }
   }

   private fun parseFactVariableReference(
      variableName: TaxiParser.VariableNameContext,
      parameters: List<Parameter>
   ): Either<List<CompilationError>, Parameter> {
      val resolved = parameters.firstOrNull { it.name == variableName.identifier().text }
      @Suppress("IfThenToElvis")
      return if (resolved == null) {
         listOf(
            CompilationError(
               variableName.toCompilationUnit(),
               "Cannot resolve variable ${variableName.identifier().text}"
            )
         ).left()
      } else {
         resolved.right()
      }
   }

   private fun readValue(
      valueContext: ValueContext?,
      factType: Type,
      nullable: Boolean,
      parameters: List<Parameter> = emptyList()
   ): Either<List<CompilationError>, Any?> {
      if (valueContext == null) {
         return (null as Any?).right()
      }

      val result: Either<List<CompilationError>, Any?> = when {
         valueContext.literal() != null -> valueContext.literal().nullableValue().right()
         valueContext.objectValue() != null -> readObjectValue(valueContext.objectValue(), factType)
         valueContext.valueArray() != null -> readArray(valueContext.valueArray(), factType)
         valueContext.expressionGroup() != null -> readExpression(valueContext.expressionGroup(), factType, parameters)
         else -> null
      }?.flatMap { value -> verifyIsAssignable(factType, value, valueContext, nullable) }
         ?: (null as Any?).right()

      return result
   }

   private fun readExpression(
      expressionGroup: TaxiParser.ExpressionGroupContext,
      factType: Type,
      parameters: List<Parameter>
   ): Either<List<CompilationError>, Any?> {
      return expressionCompiler
         .withParameters(parameters)
         .compile(expressionGroup, factType)
   }

   private fun verifyIsAssignable(
      factType: Type,
      value: Any?,
      valueContext: ValueContext,
      nullable: Boolean
   ): Either<List<CompilationError>, Any?> {
      return when (value) {
         null -> {
            if (!nullable) {
               listOf(CompilationError(valueContext.toCompilationUnit(), "null values are not supported here")).left()
            } else {
               Either.Right(null)
            }
         }

         is Collection<*> -> {
            // We don't need to verify that the inner types of the array match,
            // as that was verified whilst parsing the array.
            if (factType is ArrayType) {
               value.right()
            } else {
               listOf(
                  CompilationError(
                     valueContext.toCompilationUnit(),
                     "An array is not assignable to type ${factType.qualifiedName}"
                  )
               ).left()
            }
         }

         is Map<*, *> -> {
            // TODO : We should be verifying the type contract here.
            if (factType !is ObjectType) {
               return listOf(
                  CompilationError(
                     valueContext.toCompilationUnit(),
                     "Map is not assignable to type ${factType.qualifiedName}"
                  )
               ).left()
            }
            val missingRequiredFields = factType.fields
               .filter { !it.nullable }
               .filter { field -> !value.containsKey(field.name) }
            if (missingRequiredFields.isNotEmpty()) {
               return listOf(
                  CompilationError(
                     valueContext.toCompilationUnit(),
                     "Map is not assignable to type ${factType.qualifiedName} as mandatory properties ${missingRequiredFields.joinToString { it.name }} are missing"
                  )
               ).left()
            } else {
               value.right()
            }
         }

         is Expression -> {
            tokenProcessor.typeChecker.ifAssignable(value.returnType, factType, valueContext) { value }
               .wrapErrorsInList()
         }

         else -> {
            val primitiveType = PrimitiveValues.getTaxiPrimitive(value)
            tokenProcessor.typeChecker.ifAssignable(primitiveType, factType, valueContext) { value }
               .wrapErrorsInList()
         }
      }
   }

   private fun readArray(
      valueArray: TaxiParser.ValueArrayContext,
      factType: Type
   ): Either<List<CompilationError>, List<Any?>> {
      if (factType !is ArrayType) {
         return listOf(
            CompilationError(
               valueArray.toCompilationUnit(),
               "An array is not assignable to type ${factType.qualifiedName}"
            )
         ).left()
      }
      val arrayMemberType = factType.memberType
      val readValue = valueArray.value().map { readValue(it, arrayMemberType, false) }
         .invertEitherList()
         .flattenErrors()
      return readValue
   }

   private fun readObjectValue(
      objectValue: TaxiParser.ObjectValueContext,
      factType: Type
   ): Either<List<CompilationError>, Map<String, Any?>> {
      val errors = mutableListOf<CompilationError>()
      if (factType !is ObjectType) {
         return listOf(
            CompilationError(
               objectValue.toCompilationUnit(),
               "Map is not assignable to ${factType.qualifiedName}"
            )
         ).left()
      }
      val mapResult = objectValue.objectField().map { objectField ->
         val fieldName = objectField.identifier().IdentifierToken().text
         val field = factType.field(fieldName)
         val fieldValue = readValue(objectField.value(), field.type, field.nullable)
            .getOrElse {
               errors.addAll(it)
               null
            }
         fieldName to fieldValue
      }.toMap()

      return if (errors.isEmpty()) {
         mapResult.right()
      } else {
         errors.left()
      }
   }

   private fun parseTypeToProject(
      queryProjection: TaxiParser.TypeProjectionContext?,
      typesToDiscover: DiscoveryType?,
      scopedArguments: List<Argument>
   ): Either<List<CompilationError>, Pair<Type, List<ProjectionFunctionScope>>?> {
      if (queryProjection == null || typesToDiscover == null) {
         return null.right()
      }

      val concreteProjectionTypeType = queryProjection.typeReference()
      val anonymousProjectionType = queryProjection.anonymousTypeDefinition()

      if (concreteProjectionTypeType != null && concreteProjectionTypeType.arrayMarker() == null && anonymousProjectionType == null && typesToDiscover.typeName.parameters.isNotEmpty()) {
         return listOf(
            CompilationError(
               queryProjection.start,
               "projection type is a list but the type to discover is not, both should either be list or single entity."
            )
         ).left()
      }


      if (anonymousProjectionType != null && anonymousProjectionType.arrayMarker() == null && typesToDiscover.typeName.parameters.isNotEmpty()) {
         return listOf(
            CompilationError(
               queryProjection.start,
               "projection type is a list but the type to discover is not, both should either be list or single entity."
            )
         ).left()
      }

      val baseTypeOrErrors =
         if (concreteProjectionTypeType != null) {
            this.tokenProcessor.parseType(queryProjection.findNamespace(), concreteProjectionTypeType)
         } else null.right()

      val projectionType = baseTypeOrErrors.flatMap { possibleBaseType: Type? ->

         tokenProcessor.parseProjectionScope(
            queryProjection.expressionInputs(),
            FieldTypeSpec.forDiscoveryTypes(typesToDiscover),
            scopedArguments
         ).flatMap { projectionScopedVariables ->
            if (possibleBaseType != null && anonymousProjectionType == null) {
               (possibleBaseType to projectionScopedVariables).right()
            } else {
               // Parse the anonymous projection type
               anonymousProjectionType.let { anonymousTypeDef ->
                  this
                     .tokenProcessor
                     .parseAnonymousType(
                        namespace = anonymousProjectionType.findNamespace(),
                        resolutionContext = ResolutionContext(
                           listOf(typesToDiscover),
                           concreteProjectionTypeType,
                           possibleBaseType,
                           projectionScopedVariables,
                           scopedArguments
                        ),
                        anonymousTypeDefinition = anonymousProjectionType
                     ).map { createdType ->
                        createdType to projectionScopedVariables
                     }
               }
            }
         }
      }

      return projectionType
   }

   private fun parseServiceRestrictions(serviceRestrictions: TaxiParser.ServiceRestrictionsContext?): Either<List<CompilationError>, ServiceRestrictions> {
      if (serviceRestrictions == null) {
         return ServiceRestrictions.EMPTY.right()
      }
      return serviceRestrictions.serviceOrMemberReferenceList().serviceOrMemberReference()
         .map { serviceOrMemberReferenceContext ->
            parseServiceOrMemberReference(serviceOrMemberReferenceContext)
         }
         .invertEitherList()
         .flattenErrors()
         .map { parsedRestrictions: List<ServiceRestriction> ->
            // Collapse the individual restrictions, so they're grouped by
            // the service they're restricting.

            val grouped = parsedRestrictions.groupBy { it.service }
               .map { (service, restrictions) ->
                  val allOperations = restrictions.flatMap { it.members }
                  ServiceRestriction(service, allOperations)
               }

            if (serviceRestrictions.K_Using() != null) {
               ServiceRestrictions(inclusions = grouped, exclusions = emptyList())
            } else {
               ServiceRestrictions(inclusions = emptyList(), exclusions = grouped)
            }
         }
   }

   private fun parseServiceOrMemberReference(serviceOrOperationContext: ServiceOrMemberReferenceContext): Either<List<CompilationError>, ServiceRestriction> {
      return if (serviceOrOperationContext.memberReference() != null) {
         // This is a call in the form of Service::Operation
         resolveServiceAndOperation(
            serviceToken = serviceOrOperationContext.memberReference().typeReference(0),
            operationToken = serviceOrOperationContext.memberReference().typeReference(1),
            operationTokenRequired = true,
            context = serviceOrOperationContext
         )
      } else {
         // This is just a service name
         resolveServiceAndOperation(
            serviceToken = serviceOrOperationContext.typeReference()!!,
            operationToken = null,
            operationTokenRequired = false,
            context = serviceOrOperationContext
         )
      }.map { (service, operation) ->
         ServiceRestriction(service, listOfNotNull(operation))
      }

   }
}

// Was called AnonymousTypesResolutionContext.
// Basically, things that will help us resolve tokens
// that are contextual (eg., "this" or similar in a function scope).
data class ResolutionContext(
   val typesToDiscover: List<DiscoveryType> = emptyList(),
   val concreteProjectionTypeContext: TaxiParser.TypeReferenceContext? = null,
   val baseType: Type? = null,
   val activeScopes: List<ProjectionFunctionScope> = emptyList(),
   val parameters: List<Argument> = emptyList()
) {
   fun appendScope(projectionScope: List<ProjectionFunctionScope>): ResolutionContext {
      return this.copy(activeScopes = activeScopes + projectionScope)
   }

   val argumentsInScope = activeScopes + parameters

}


package lang.taxi.compiler

import arrow.core.*
import lang.taxi.*
import lang.taxi.TaxiParser.ValueContext
import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.compiler.fields.FieldTypeSpec
import lang.taxi.mutations.Mutation
import lang.taxi.query.*
import lang.taxi.services.OperationScope
import lang.taxi.services.Service
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.utils.wrapErrorsInList
import lang.taxi.values.PrimitiveValues

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
            parseTypeToProject(ctx.queryOrMutation()?.typeProjection(), typesToDiscover).flatMap { typeToProject ->
               parseMutation(ctx.queryOrMutation().mutation()).map { mutation ->
                  TaxiQlQuery(
                     name = name,
                     facts = facts,
                     queryMode = queryDirective,
                     parameters = parameters,
                     typesToFind = typesToDiscover,
                     projectedType = typeToProject?.first,
                     projectionScope = typeToProject?.second,
                     typeDoc = docs,
                     annotations = annotations,
                     mutation = mutation,
                     compilationUnits = listOf(compilationUnit)
                  )
               }


            }
         }
      }
      return queryOrErrors
   }

   private fun parseMutation(mutationCtx: TaxiParser.MutationContext?): Either<List<CompilationError>, Mutation?> {
      if (mutationCtx == null) return Either.Right(null)
      val memberReference = mutationCtx.memberReference()

      return tokenProcessor.resolveImportableToken(
         memberReference.typeReference(0).qualifiedName(),
         mutationCtx,
         SymbolKind.SERVICE
      )
         .flatMap { token ->
            fun compilationError(message: String): Either<List<CompilationError>, Nothing> {
               return listOf(CompilationError(memberReference.start, message)).left()
            }

            if (token !is Service) return@flatMap compilationError("Mutations require a reference to services and operations in the form of ServiceName::operationName.  ${token.qualifiedName} is not a service")
            val operationName = memberReference.typeReference(1)
               ?: return@flatMap compilationError("Mutations require a reference to services and operations in the form of ServiceName::operationName. No operation name was provided")
            if (!token.containsOperation(operationName.text)) return@flatMap compilationError("Service ${token.qualifiedName} does not declare an operation ${operationName.text}")
            val operation = token.operation(operationName.text)
            if (operation.scope != OperationScope.MUTATION) return@flatMap compilationError("Call statements are only valid with write operations.  Operation ${memberReference.text} is not a write operation")
            (token to operation).right()
         }
         .map { (service, operation) ->
            Mutation(
               service,
               operation,
               mutationCtx.toCompilationUnits()
            )
         }
   }

   private fun parseQueryBody(
      queryBodyContext: TaxiParser.QueryBodyContext,
      parameters: List<Parameter>, queryDirective: QueryMode
   ): Either<List<CompilationError>, List<DiscoveryType>> {
      val namespace = queryBodyContext.findNamespace()
      val constraintBuilder =
         ConstraintBuilder(tokenProcessor.typeResolver(namespace), expressionCompiler.withParameters(parameters))

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
      val queryTypeList = queryBodyContext.queryOrMutation()?.queryTypeList()
      val anonymousTypeDefinition = queryBodyContext.queryOrMutation()?.anonymousTypeDefinition()
      return queryTypeList?.fieldTypeDeclaration()?.map { queryType ->
         tokenProcessor.parseType(namespace, queryType.nullableTypeReference().typeReference()).flatMap { type ->
            toDiscoveryType(
               type, queryType.parameterConstraint(), queryDirective, constraintBuilder, parameters
            )
         }
      }?.invertEitherList()?.flattenErrors() ?: parseAnonymousTypesIfPresent(
         namespace,
         anonymousTypeDefinition,
         queryDirective,
         constraintBuilder,
         parameters
      )

   }

   private fun parseAnonymousTypesIfPresent(
      namespace: String,
      anonymousTypeDefinition: TaxiParser.AnonymousTypeDefinitionContext?,
      queryDirective: QueryMode,
      constraintBuilder: ConstraintBuilder,
      parameters: List<Parameter>
   ): Either<List<CompilationError>, List<DiscoveryType>> {
      if (anonymousTypeDefinition == null) {
         return emptyList<DiscoveryType>().right()
      }
      return tokenProcessor.parseAnonymousType(
         namespace,
         anonymousTypeDefinition
      ).flatMap { anonymousType ->
         toDiscoveryType(
            anonymousType,
            anonymousTypeDefinition.parameterConstraint(),
            queryDirective,
            constraintBuilder,
            parameters
         )
      }.map { listOf(it) }
   }

   private fun toDiscoveryType(
      type: Type,
      parameterConstraint: TaxiParser.ParameterConstraintContext?,
      queryDirective: QueryMode,
      constraintBuilder: ConstraintBuilder,
      facts: List<Parameter>
   ): Either<List<CompilationError>, DiscoveryType> {
      val constraintsOrErrors =
         parameterConstraint?.let { constraintExpressionList ->
            constraintBuilder.build(constraintExpressionList, type)
         } ?: emptyList<Constraint>().right()
      return constraintsOrErrors.map { constraints ->
         // If we're building a streaming query, then wrap the requested type
         // in a stream
         val typeToDiscover = if (queryDirective == QueryMode.STREAM) {
            StreamType.of(type)
         } else {
            type
         }
         DiscoveryType(typeToDiscover, constraints, facts, if (type.anonymous) type else null)
      }
   }

   private fun parseFacts(
      givenBlock: TaxiParser.GivenBlockContext,
      parameters: List<Parameter>
   ): Either<List<CompilationError>, List<Parameter>> {
      return givenBlock.factList().fact().mapIndexed { idx, factCtx ->
         parseFact(idx, factCtx, parameters)
      }.invertEitherList().flattenErrors()

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
            variableName
         )

         else -> error("Expected either a variable name reference, or a fact declaration with a value, but got both (or neither): ${factCtx.parent.text}")
      }
   }

   private fun parseFactValueDeclaration(
      factCtx: TaxiParser.FactContext,
      namespace: String,
      variableName: String
   ): Either<List<CompilationError>, Parameter> {
      require(factCtx.factDeclaration() != null) { "Expected the declared fact to have a value" }
      val factDeclaration = factCtx.factDeclaration()
      return tokenProcessor.typeOrError(namespace, factDeclaration.typeReference()).flatMap { factType ->
         try {
            readValue(factDeclaration.value(), factType, false)
               .map { factValue ->
                  if (factValue != null) {
                     Parameter(
                        name = variableName,
                        value = FactValue.Constant(TypedValue(factType, factValue)),
                        annotations = emptyList()
                     )
                  } else {
                     error("It is illegal in the grammar to not decalre a factValue. You shouldn't hit this part")

                  }
               }
         } catch (e: Exception) {
            listOf(CompilationError(factCtx.start, "Failed to create TypedInstance - ${e.message}")).left()
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
               "Cannot resolve variable ${variableName.identifier().toString()}"
            )
         ).left()
      } else {
         resolved.right()
      }
   }

   private fun readValue(
      valueContext: ValueContext?,
      factType: Type,
      nullable: Boolean
   ): Either<List<CompilationError>, Any?> {
      if (valueContext == null) {
         return (null as Any?).right()
      }

      val result: Either<List<CompilationError>, Any?> = when {
         valueContext.literal() != null -> valueContext.literal().nullableValue().right()
         valueContext.objectValue() != null -> readObjectValue(valueContext.objectValue(), factType)
         valueContext.valueArray() != null -> readArray(valueContext.valueArray(), factType)
         else -> null
      }?.flatMap { value -> verifyIsAssignable(factType, value, valueContext, nullable) }
         ?: (null as Any?).right()

      return result
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
      typesToDiscover: List<DiscoveryType>
   ): Either<List<CompilationError>, Pair<Type, ProjectionFunctionScope?>?> {
      if (queryProjection == null) {
         return null.right()
      }

      val concreteProjectionTypeType = queryProjection.typeReference()
      val anonymousProjectionType = queryProjection.anonymousTypeDefinition()

      if (anonymousProjectionType != null && concreteProjectionTypeType == null && typesToDiscover.size > 1) {
         return listOf(
            CompilationError(
               queryProjection.start,
               "When anonymous projected type is defined without an explicit based discoverable type sizes should be 1"
            )
         ).left()
      }

      if (concreteProjectionTypeType != null && concreteProjectionTypeType.arrayMarker() == null && anonymousProjectionType == null && typesToDiscover.size == 1 && typesToDiscover.first().typeName.parameters.isNotEmpty()) {
         return listOf(
            CompilationError(
               queryProjection.start,
               "projection type is a list but the type to discover is not, both should either be list or single entity."
            )
         ).left()
      }


      if (anonymousProjectionType != null && anonymousProjectionType.arrayMarker() == null && typesToDiscover.size == 1 && typesToDiscover.first().typeName.parameters.isNotEmpty()) {
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
         if (possibleBaseType != null && anonymousProjectionType == null) {
            return@flatMap (possibleBaseType to null).right()
         }
         if (anonymousProjectionType == null) {
            return@flatMap listOf(
               CompilationError(
                  queryProjection.toCompilationUnit(),
                  "An internal error occurred.  Expected an anonymous type definition here."
               )
            ).left()
         }

         tokenProcessor.parseProjectionScope(
            queryProjection.expressionInputs(),
            FieldTypeSpec.forDiscoveryTypes(typesToDiscover)
         ).flatMap { projectionFunctionScope ->
            anonymousProjectionType.let { anonymousTypeDef ->
               val isList = anonymousTypeDef.arrayMarker() != null

               this
                  .tokenProcessor
                  .parseAnonymousType(
                     namespace = anonymousProjectionType.findNamespace(),
                     resolutionContext = ResolutionContext(
                        typesToDiscover,
                        concreteProjectionTypeType,
                        possibleBaseType,
                        listOf(projectionFunctionScope)
                     ),
                     anonymousTypeDefinition = anonymousProjectionType
                  ).map { createdType ->
                     val compiledType =
                        if (isList) ArrayType(createdType, anonymousProjectionType.toCompilationUnit()) else createdType
                     compiledType to projectionFunctionScope
                  }
            }

         }
      }

      return projectionType
   }
}

// Was called AnonymousTypesResolutionContext.
// Basically, things that will help us resolve tokens
// that are contextual (eg., "this" or similar in a function scope).
data class ResolutionContext(
   val typesToDiscover: List<DiscoveryType> = emptyList(),
   val concreteProjectionTypeContext: TaxiParser.TypeReferenceContext? = null,
   val baseType: Type? = null,
   val activeScopes: List<ProjectionFunctionScope> = emptyList()
) {
   fun appendScope(projectionScope: ProjectionFunctionScope): ResolutionContext {
      return this.copy(activeScopes = activeScopes + projectionScope)
   }

}


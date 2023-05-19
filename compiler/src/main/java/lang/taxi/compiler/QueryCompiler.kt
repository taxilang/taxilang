package lang.taxi.compiler

import arrow.core.*
import lang.taxi.*
import lang.taxi.TaxiParser.ValueContext
import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.compiler.fields.FieldTypeSpec
import lang.taxi.query.*
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
//         ctx.queryDirective().FindAll() != null -> QueryMode.FIND_ALL
//         ctx.queryDirective().FindOne() != null -> QueryMode.FIND_ONE
         //Deprecating FindAll/FindOne in favour of Find which behaves the same as FindAll
         ctx.queryDirective().K_Find() != null -> QueryMode.FIND_ALL
         ctx.queryDirective().K_Stream() != null -> QueryMode.STREAM
         else -> error("Unhandled Query Directive")
      }
      val factsOrErrors = ctx.givenBlock()?.let { parseFacts(it) } ?: emptyList<Parameter>().right()
      val queryOrErrors = factsOrErrors.flatMap { facts ->

         parseQueryBody(ctx, facts + parameters, queryDirective).flatMap { typesToDiscover ->
            parseTypeToProject(ctx.typeProjection(), typesToDiscover).map { typeToProject ->

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
                  compilationUnits = listOf(compilationUnit)
               )
            }
         }
      }
      return queryOrErrors
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
      val queryTypeList = queryBodyContext.queryTypeList()
      val anonymousTypeDefinition = queryBodyContext.anonymousTypeDefinition()
      return queryTypeList?.fieldTypeDeclaration()?.map { queryType ->
         tokenProcessor.parseType(namespace, queryType.optionalTypeReference().typeReference()).flatMap { type ->
            toDiscoveryType(
               type, queryType.parameterConstraint(), queryDirective, constraintBuilder, parameters
            )
         }
      }?.invertEitherList()?.flattenErrors() ?: listOf(
         tokenProcessor.parseAnonymousType(
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
         }).invertEitherList().flattenErrors()
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

   private fun parseFacts(givenBlock: TaxiParser.GivenBlockContext): Either<List<CompilationError>, List<Parameter>> {
      return givenBlock.factList().fact().mapIndexed { idx, factCtx ->
         parseFact(idx, factCtx)
      }.invertEitherList().flattenErrors()

   }

   private fun parseFact(index: Int, factCtx: TaxiParser.FactContext): Either<List<CompilationError>, Parameter> {
      val variableName = factCtx.variableName()?.identifier()?.text ?: "fact$index"
      val namespace = factCtx.findNamespace()

      return tokenProcessor.typeOrError(namespace, factCtx.typeReference()).flatMap { factType ->
         try {
            readValue(factCtx.value(), factType)
               .map { factValue ->
                  if (factValue != null) {
                     Parameter(
                        name = variableName,
                        value = FactValue.Constant(TypedValue(factType, factValue)),
                        annotations = emptyList()
                     )
                  } else {
                     Parameter(
                        name = variableName,
                        value = FactValue.Variable(factType, variableName),
                        annotations = emptyList()
                     )
                  }
               }
         } catch (e: Exception) {
            listOf(CompilationError(factCtx.start, "Failed to create TypedInstance - ${e.message}")).left()
         }

      }
   }

   private fun readValue(valueContext: ValueContext?, factType: Type): Either<List<CompilationError>, Any?> {
      if (valueContext == null) {
         return (null as Any?).right()
      }

      val result: Either<List<CompilationError>, Any?> = when {
         valueContext.literal() != null -> valueContext.literal().value().right()
         valueContext.objectValue() != null -> readObjectValue(valueContext.objectValue(), factType)
         valueContext.valueArray() != null -> readArray(valueContext.valueArray(), factType)
         else -> null
      }?.flatMap { value -> verifyIsAssignable(factType, value, valueContext) }
         ?: (null as Any?).right()

      return result
   }

   private fun verifyIsAssignable(
      factType: Type,
      value: Any?,
      valueContext: ValueContext
   ): Either<List<CompilationError>, Any?> {
      return when (value) {
         null -> listOf(CompilationError(valueContext.toCompilationUnit(), "Null values are not supported here")).left()
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
      val readValue = valueArray.value().map { readValue(it, arrayMemberType) }
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
         val fieldValue = readValue(objectField.value(), field.type)
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


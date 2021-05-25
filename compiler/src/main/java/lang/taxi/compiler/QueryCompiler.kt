package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.*
import lang.taxi.query.ConstraintBuilder
import lang.taxi.query.TaxiQlQuery
import lang.taxi.types.*
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList

internal class QueryCompiler(private val tokenProcessor: TokenProcessor) {
   fun parseQueryBody(
      name: String,
      parameters: Map<String, QualifiedName>,
      ctx: TaxiParser.QueryBodyContext
   ): Either<List<CompilationError>, TaxiQlQuery> {
      val queryDirective = when {
         ctx.queryDirective().FindAll() != null -> QueryMode.FIND_ALL
         ctx.queryDirective().FindOne() != null -> QueryMode.FIND_ONE
         else -> error("Unhandled Query Directive")
      }

      val factsOrErrors = ctx.givenBlock()?.let { parseFacts(it) } ?: Either.right(emptyMap())
      val queryOrErrors = factsOrErrors.flatMap { facts ->
         parseQueryTypeList(ctx.queryTypeList(), facts)
            .flatMap { typesToDiscover ->
               parseTypeToProject(ctx.queryProjection(), typesToDiscover)
                  .map { typeToProject ->
                     TaxiQlQuery(
                        name = name,
                        facts = facts,
                        queryMode = queryDirective,
                        parameters = parameters,
                        typesToFind = typesToDiscover,
                        projectedType = typeToProject
                     )
                  }
            }
      }
      return queryOrErrors
   }

   private fun parseQueryTypeList(
      queryTypeList: TaxiParser.QueryTypeListContext,
      facts: Map<String, TypedValue>
   ): Either<List<CompilationError>, List<DiscoveryType>> {
      val namespace = queryTypeList.findNamespace()
      val constraintBuilder = ConstraintBuilder(tokenProcessor.typeResolver(namespace))

      return queryTypeList.typeType().map { queryType ->
         tokenProcessor.parseType(namespace, queryType)
            .flatMap { type ->
               val constraintsOrErrors = queryType.parameterConstraint()?.parameterConstraintExpressionList()
                  ?.let { constraintExpressionList ->
                     constraintBuilder.build(constraintExpressionList, type)
                  } ?: Either.right(emptyList())
               constraintsOrErrors.map { constraints ->
                  DiscoveryType(type.toQualifiedName(), constraints, facts)
               }
            }
      }.invertEitherList().flattenErrors()
   }

   private fun parseFacts(givenBlock: TaxiParser.GivenBlockContext): Either<List<CompilationError>, Map<String, TypedValue>> {
      return givenBlock
         .factList()
         .fact()
         .map {
            parseFact(it)
         }.invertEitherList().flattenErrors()
         .map { it.toMap() }
   }

   private fun parseFact(factCtx: TaxiParser.FactContext): Either<List<CompilationError>, Pair<String, TypedValue>> {
      val variableName = factCtx.variableName().Identifier().text
      val namespace = factCtx.findNamespace()

      return tokenProcessor.typeOrError(namespace, factCtx.typeType())
         .flatMap { factType ->
            try {
               Either.right(variableName to TypedValue(factType.toQualifiedName(), factCtx.literal().value()))
            } catch (e: Exception) {
               Either.left(listOf(CompilationError(factCtx.start, "Failed to create TypedInstance - ${e.message}")))
            }

         }
   }

   private fun parseTypeToProject(
      queryProjection: TaxiParser.QueryProjectionContext?,
      typesToDiscover: List<DiscoveryType>
   ): Either<List<CompilationError>, ProjectedType?> {
      if (queryProjection == null) {
         return Either.right(null)
      }

      val concreteProjectionTypeType = queryProjection.typeType()
      val anonymousProjectionType = queryProjection.anonymousTypeDefinition()

      if (anonymousProjectionType != null && concreteProjectionTypeType == null && typesToDiscover.size > 1) {
         return Either.left(
            listOf(
               CompilationError(
                  queryProjection.start,
                  "When anonymous projected type is defined without an explicit based discoverable type sizes should be 1"
               )
            )
         )
      }

      if (concreteProjectionTypeType != null && concreteProjectionTypeType.listType() == null && anonymousProjectionType == null && typesToDiscover.size == 1 && typesToDiscover.first().type.parameters.isNotEmpty()) {
         return Either.left(
            listOf(
               CompilationError(
                  queryProjection.start,
                  "projection type is a list but the type to discover is not, both should either be list or single entity."
               )
            )
         )
      }


      if (anonymousProjectionType != null && anonymousProjectionType.listType() == null && typesToDiscover.size == 1 && typesToDiscover.first().type.parameters.isNotEmpty()) {
         return Either.left(
            listOf(
               CompilationError(
                  queryProjection.start,
                  "projection type is a list but the type to discover is not, both should either be list or single entity."
               )
            )
         )
      }

      return when {
         concreteProjectionTypeType == null && anonymousProjectionType != null -> {
            val typeBodyContext = anonymousProjectionType.typeBody()
            val isList = anonymousProjectionType.listType() != null
            this
               .tokenProcessor
               .parseAnonymousType(
                  namespace = typeBodyContext.findNamespace(),
                  anonymousTypeCtx = typeBodyContext,
                  anonymousTypeResolutionContext = AnonymousTypeResolutionContext(
                     typesToDiscover,
                     concreteProjectionTypeType
                  )
               )
               .map { createdType ->
                  val compiledType =
                     if (isList) ArrayType(createdType, anonymousProjectionType.toCompilationUnit()) else createdType
                  ProjectedType.fomAnonymousTypeOnly(compiledType)
               }
         }

         concreteProjectionTypeType != null && anonymousProjectionType != null -> {
            val concreteProjectionType =
               this.tokenProcessor.parseType(queryProjection.findNamespace(), concreteProjectionTypeType)
            val typeBodyContext = anonymousProjectionType.typeBody()
            val isList = anonymousProjectionType.listType() != null
            val anonymousType = this
               .tokenProcessor
               .parseAnonymousType(
                  namespace = typeBodyContext.findNamespace(),
                  anonymousTypeCtx = typeBodyContext,
                  anonymousTypeResolutionContext = AnonymousTypeResolutionContext(
                     typesToDiscover,
                     concreteProjectionTypeType
                  )
               )


            return when {
               concreteProjectionType is Either.Left -> concreteProjectionType.a.left()
               anonymousType is Either.Left -> anonymousType.a.left()
               else -> {
                  val createdAnonymousType = (anonymousType as Either.Right).b
                  val compiledType = if (isList) ArrayType(
                     createdAnonymousType,
                     anonymousProjectionType.toCompilationUnit()
                  ) else createdAnonymousType
                  ProjectedType((concreteProjectionType as Either.Right).b, compiledType).right()
               }
            }
         }


         concreteProjectionTypeType != null && anonymousProjectionType == null -> {
            this.tokenProcessor.parseType(queryProjection.findNamespace(), concreteProjectionTypeType)
               .map { ProjectedType.fromConcreteTypeOnly(it) }

         }
         else -> Either.left(
            listOf(
               CompilationError(
                  queryProjection.start,
                  "projection type is a list but the type to discover is not, both should either be list or single entity."
               )
            )
         )

      }

      /*return when {
         projectionType == null && anonymousProjectionType != null -> {
            anonymousProjectionType.typeDeclaration()
            val compilationErrorOrFields = anonymousProjectionType.anonymousField().map { toAnonymousFieldDefinition(it, queryProjection, typesToDiscover) }
            val fieldDefinitions = mutableListOf<AnonymousFieldDefinition>()
            compilationErrorOrFields.forEach { compilationErrorOrField ->
               when (compilationErrorOrField) {
                  is Either.Left -> {
                     return compilationErrorOrField.a.left()
                  }
                  is Either.Right -> fieldDefinitions.add(compilationErrorOrField.b)
               }
            }

            return ProjectedType.fomAnonymousTypeOnly(
               AnonymousTypeDefinition(anonymousProjectionType.listType() != null, fieldDefinitions.toList(), queryProjection.toCompilationUnit())).right()
         }

         projectionType != null && anonymousProjectionType == null -> {
            lookupType(projectionType).map { qualifiedName -> ProjectedType.fromConcreteTypeOnly(qualifiedName) }

         }

         projectionType != null && anonymousProjectionType != null -> {
            val concreteProjectionType = lookupType(projectionType)
            val compilationErrorOrFields = anonymousProjectionType.anonymousField().map { toAnonymousFieldDefinition(it, queryProjection, typesToDiscover) }
            val fieldDefinitions = mutableListOf<AnonymousFieldDefinition>()
            compilationErrorOrFields.forEach { compilationErrorOrField ->
               when (compilationErrorOrField) {
                  is Either.Left -> {
                     return compilationErrorOrField.a.left()
                  }
                  is Either.Right -> fieldDefinitions.add(compilationErrorOrField.b)
               }
            }

            if (concreteProjectionType is Either.Left) {
               return concreteProjectionType.a.left()
            }

            return concreteProjectionType.map {
               ProjectedType(it,
                  AnonymousTypeDefinition(anonymousProjectionType.listType() != null, fieldDefinitions.toList(), projectionType.toCompilationUnit()))
            }
         }

         else -> Either.left(CompilationError(queryProjection.start,
            "Unexpected as definition"))
      }*/
   }

}

data class AnonymousTypeResolutionContext(
   val typesToDiscover: List<DiscoveryType> = emptyList(),
   val concreteProjectionTypeContext: TaxiParser.TypeTypeContext? = null
)

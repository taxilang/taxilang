package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.findNamespace
import lang.taxi.query.ConstraintBuilder
import lang.taxi.query.TaxiQlQuery
import lang.taxi.toCompilationUnit
import lang.taxi.types.ArrayType
import lang.taxi.types.DiscoveryType
import lang.taxi.types.ProjectedType
import lang.taxi.types.QualifiedName
import lang.taxi.types.QueryMode
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypedValue
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.value

internal class QueryCompiler(private val tokenProcessor: TokenProcessor) {
   fun parseQueryBody(
      name: String,
      parameters: Map<String, QualifiedName>,
      ctx: TaxiParser.QueryBodyContext
   ): Either<List<CompilationError>, TaxiQlQuery> {
      val queryDirective = when {
         ctx.queryDirective().FindAll() != null -> QueryMode.FIND_ALL
         ctx.queryDirective().FindOne() != null -> QueryMode.FIND_ONE
         //Deprecating FindAll/FindOne in favour of Find which behaves the same as FindAll
         ctx.queryDirective().Find() != null -> QueryMode.FIND_ALL
         ctx.queryDirective().Stream() != null -> QueryMode.STREAM
         else -> error("Unhandled Query Directive")
      }

      val factsOrErrors = ctx.givenBlock()?.let { parseFacts(it) } ?: Either.right(emptyMap())
      val queryOrErrors = factsOrErrors.flatMap { facts ->

         parseQueryBody(ctx, facts, queryDirective)
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

   private fun parseQueryBody(
      queryBodyContext: TaxiParser.QueryBodyContext,
      facts: Map<String, TypedValue>,
      queryDirective: QueryMode
   ): Either<List<CompilationError>, List<DiscoveryType>> {
      val namespace = queryBodyContext.findNamespace()
      val constraintBuilder = ConstraintBuilder(tokenProcessor.typeResolver(namespace))
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
     return queryTypeList?.typeType()?.map { queryType ->
        tokenProcessor.parseType(namespace, queryType)
           .flatMap { type -> toDiscoveryType(type, queryType.parameterConstraint(), queryDirective, constraintBuilder, facts) }
     }?.invertEitherList()?.flattenErrors()
             ?: listOf(tokenProcessor
                .parseAnonymousType(namespace, anonymousTypeDefinition)
                .flatMap { anonymousType -> toDiscoveryType(
                   anonymousType,
                   anonymousTypeDefinition.parameterConstraint(),
                   queryDirective,
                   constraintBuilder,
                   facts) }
             ).invertEitherList().flattenErrors()
   }

   private fun toDiscoveryType(
      type: Type,
      parameterConstraint: TaxiParser.ParameterConstraintContext?,
      queryDirective: QueryMode,
      constraintBuilder: ConstraintBuilder,
      facts: Map<String, TypedValue>): Either<List<CompilationError>, DiscoveryType> {
      val constraintsOrErrors = parameterConstraint?.parameterConstraintExpressionList()
         ?.let { constraintExpressionList ->
            constraintBuilder.build(constraintExpressionList, type)
         } ?: Either.right(emptyList())
      return constraintsOrErrors.map { constraints ->
         // If we're building a streaming query, then wrap the requested type
         // in a stream
         val typeToDiscover = if (queryDirective == QueryMode.STREAM) {
            StreamType.of(type)
         } else {
            type
         }
         DiscoveryType(typeToDiscover.toQualifiedName(), constraints, facts, if (type.anonymous) type else null)
      }
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
            val isList = anonymousProjectionType.listType() != null
            this
               .tokenProcessor
               .parseAnonymousType(
                  namespace = anonymousProjectionType.findNamespace(),
                  anonymousTypeResolutionContext = AnonymousTypeResolutionContext(
                     typesToDiscover,
                     concreteProjectionTypeType
                  ),
                  anonymousTypeDefinition = anonymousProjectionType
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
            val isList = anonymousProjectionType.listType() != null
            val anonymousType = this
               .tokenProcessor
               .parseAnonymousType(
                  namespace = anonymousProjectionType.findNamespace(),
                  anonymousTypeResolutionContext = AnonymousTypeResolutionContext(
                     typesToDiscover,
                     concreteProjectionTypeType
                  ),
                  anonymousTypeDefinition = anonymousProjectionType
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

   }
}

data class AnonymousTypeResolutionContext(
   val typesToDiscover: List<DiscoveryType> = emptyList(),
   val concreteProjectionTypeContext: TaxiParser.TypeTypeContext? = null
)


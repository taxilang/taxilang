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
import lang.taxi.types.*
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.value

internal class QueryCompiler(private val tokenProcessor: TokenProcessor) {
   fun parseQueryBody(
      name: String, parameters: Map<String, QualifiedName>, ctx: TaxiParser.QueryBodyContext
   ): Either<List<CompilationError>, TaxiQlQuery> {
      val queryDirective = when {
         ctx.queryDirective().FindAll() != null -> QueryMode.FIND_ALL
         ctx.queryDirective().FindOne() != null -> QueryMode.FIND_ONE
         //Deprecating FindAll/FindOne in favour of Find which behaves the same as FindAll
         ctx.queryDirective().Find() != null -> QueryMode.FIND_ALL
         ctx.queryDirective().Stream() != null -> QueryMode.STREAM
         else -> error("Unhandled Query Directive")
      }

      val factsOrErrors = ctx.givenBlock()?.let { parseFacts(it) } ?: Either.right(emptyList())
      val queryOrErrors = factsOrErrors.flatMap { facts ->

         parseQueryBody(ctx, facts, queryDirective).flatMap { typesToDiscover ->
               parseTypeToProject(ctx.queryProjection(), typesToDiscover).map { typeToProject ->
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
      queryBodyContext: TaxiParser.QueryBodyContext, facts: List<Variable>, queryDirective: QueryMode
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
         tokenProcessor.parseType(namespace, queryType).flatMap { type ->
               toDiscoveryType(
                  type, queryType.parameterConstraint(), queryDirective, constraintBuilder, facts
               )
            }
      }?.invertEitherList()?.flattenErrors() ?: listOf(
         tokenProcessor.parseAnonymousType(
               namespace,
               anonymousTypeDefinition
            ).flatMap { anonymousType ->
               toDiscoveryType(
                  anonymousType, anonymousTypeDefinition.parameterConstraint(), queryDirective, constraintBuilder, facts
               )
            }).invertEitherList().flattenErrors()
   }

   private fun toDiscoveryType(
      type: Type,
      parameterConstraint: TaxiParser.ParameterConstraintContext?,
      queryDirective: QueryMode,
      constraintBuilder: ConstraintBuilder,
      facts: List<Variable>
   ): Either<List<CompilationError>, DiscoveryType> {
      val constraintsOrErrors =
         parameterConstraint?.parameterConstraintExpressionList()?.let { constraintExpressionList ->
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

   private fun parseFacts(givenBlock: TaxiParser.GivenBlockContext): Either<List<CompilationError>, List<Variable>> {
      return givenBlock.factList().fact().map {
            parseFact(it)
         }.invertEitherList().flattenErrors()

   }

   private fun parseFact(factCtx: TaxiParser.FactContext): Either<List<CompilationError>, Variable> {
      val variableName = factCtx.variableName()?.Identifier()?.text
      val namespace = factCtx.findNamespace()

      return tokenProcessor.typeOrError(namespace, factCtx.typeType()).flatMap { factType ->
            try {
               Either.right(Variable(variableName, TypedValue(factType.toQualifiedName(), factCtx.literal().value())))
            } catch (e: Exception) {
               Either.left(listOf(CompilationError(factCtx.start, "Failed to create TypedInstance - ${e.message}")))
            }

         }
   }

   private fun parseTypeToProject(
      queryProjection: TaxiParser.QueryProjectionContext?, typesToDiscover: List<DiscoveryType>
   ): Either<List<CompilationError>, Type?> {
      if (queryProjection == null) {
         return Either.right(null)
      }

      val concreteProjectionTypeType = queryProjection.typeType()
      val anonymousProjectionType = queryProjection.anonymousTypeDefinition()

      if (anonymousProjectionType != null && concreteProjectionTypeType == null && typesToDiscover.size > 1) {
         return listOf(
            CompilationError(
               queryProjection.start,
               "When anonymous projected type is defined without an explicit based discoverable type sizes should be 1"
            )
         ).left()
      }

      if (concreteProjectionTypeType != null && concreteProjectionTypeType.listType() == null && anonymousProjectionType == null && typesToDiscover.size == 1 && typesToDiscover.first().type.parameters.isNotEmpty()) {
         return listOf(
            CompilationError(
               queryProjection.start,
               "projection type is a list but the type to discover is not, both should either be list or single entity."
            )
         ).left()
      }


      if (anonymousProjectionType != null && anonymousProjectionType.listType() == null && typesToDiscover.size == 1 && typesToDiscover.first().type.parameters.isNotEmpty()) {
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
         } else Either.right<Type?>(null)

      val projectionType = baseTypeOrErrors.flatMap { possibleBaseType: Type? ->
         if (possibleBaseType != null && anonymousProjectionType == null) {
            return@flatMap possibleBaseType.right()
         }
         if (anonymousProjectionType == null) {
           return@flatMap listOf(CompilationError(queryProjection.toCompilationUnit(), "An internal error occurred.  Expected an anonymous type definition here.")).left()
         }
         anonymousProjectionType.let { anonymousTypeDef ->
            val isList = anonymousTypeDef.listType() != null

            this
               .tokenProcessor
               .parseAnonymousType(
                  namespace = anonymousProjectionType.findNamespace(),
                  anonymousTypeResolutionContext = AnonymousTypeResolutionContext(
                     typesToDiscover, concreteProjectionTypeType, possibleBaseType
                  ),
                  anonymousTypeDefinition = anonymousProjectionType
               ).map { createdType ->
                  val compiledType =
                     if (isList) ArrayType(createdType, anonymousProjectionType.toCompilationUnit()) else createdType
                  compiledType
               }
         }
      }

      return projectionType
   }
}

data class AnonymousTypeResolutionContext(
   val typesToDiscover: List<DiscoveryType> = emptyList(),
   val concreteProjectionTypeContext: TaxiParser.TypeTypeContext? = null,
   val baseType:Type? = null,
)


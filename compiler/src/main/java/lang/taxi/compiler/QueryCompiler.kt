package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.compiler.fields.FieldTypeSpec
import lang.taxi.findNamespace
import lang.taxi.query.ConstraintBuilder
import lang.taxi.query.DiscoveryType
import lang.taxi.query.FactValue
import lang.taxi.query.Parameter
import lang.taxi.query.QueryMode
import lang.taxi.query.TaxiQlQuery
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.toCompilationUnit
import lang.taxi.types.*
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import lang.taxi.value
import lang.taxi.valueOrNull

internal class QueryCompiler(
   private val tokenProcessor: TokenProcessor,
   private val expressionCompiler: ExpressionCompiler
) {
   fun parseQueryBody(
      name: String, parameters: List<Parameter>, ctx: TaxiParser.QueryBodyContext
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
                  projectionScope = typeToProject?.second
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
      val constraintBuilder = ConstraintBuilder(tokenProcessor.typeResolver(namespace), expressionCompiler.withParameters(parameters))

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
               anonymousType, anonymousTypeDefinition.parameterConstraint(), queryDirective, constraintBuilder, parameters
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
      return givenBlock.factList().fact().mapIndexed { idx,factCtx ->
         parseFact(idx,factCtx)
      }.invertEitherList().flattenErrors()

   }

   private fun parseFact(index: Int, factCtx: TaxiParser.FactContext): Either<List<CompilationError>, Parameter> {
      val variableName = factCtx.variableName()?.identifier()?.text ?: "fact$index"
      val namespace = factCtx.findNamespace()

      return tokenProcessor.typeOrError(namespace, factCtx.typeReference()).flatMap { factType ->
         try {
            val providedValue = factCtx.literal()?.valueOrNull()
            if (providedValue != null) {
               Parameter(variableName, FactValue.Constant(TypedValue(factType, factCtx.literal().value()))).right()
            } else {
               Parameter(variableName, FactValue.Variable(factType, variableName)).right()
            }
         } catch (e: Exception) {
            listOf(CompilationError(factCtx.start, "Failed to create TypedInstance - ${e.message}")).left()
         }

      }
   }

   private fun parseTypeToProject(
      queryProjection: TaxiParser.TypeProjectionContext?,
      typesToDiscover: List<DiscoveryType>
   ): Either<List<CompilationError>, Pair<Type,ProjectionFunctionScope?>?> {
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
   val activeScopes:List<ProjectionFunctionScope> = emptyList()
) {
   fun appendScope(projectionScope: ProjectionFunctionScope): ResolutionContext {
      return this.copy(activeScopes = activeScopes + projectionScope)
   }

}


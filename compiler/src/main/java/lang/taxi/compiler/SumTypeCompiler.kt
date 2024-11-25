package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser.IntersectionTypeContext
import lang.taxi.TaxiParser.TypeReferenceContext
import lang.taxi.TaxiParser.UnionTypeContext
import lang.taxi.findNamespace
import lang.taxi.toCompilationUnit
import lang.taxi.types.ArrayType
import lang.taxi.types.IntersectionType
import lang.taxi.types.ObjectType
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypeArgument
import lang.taxi.types.UnionType
import lang.taxi.utils.createCompilationError
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList
import org.antlr.v4.runtime.ParserRuleContext

/**
 * Processes the creation of sum types (Union Type or Intersection Type)
 */
class SumTypeCompiler(
   private val tokenProcessor: TokenProcessor
) {

   fun parseSumType(
      typeReferences: MutableList<TypeReferenceContext>,
      typeArgumentsInScope: List<TypeArgument>,
      declaringContext: ParserRuleContext
   ): Either<List<CompilationError>, Type> {
      return typeReferences
         .map { unionTypeMember ->
            tokenProcessor.parseType(
               declaringContext.findNamespace(),
               unionTypeMember,
               typeArgumentsInScope
            )
         }.invertEitherList()
         .flattenErrors()
         .flatMap { types ->
            parseSumType(declaringContext, types)
         }
   }
   private fun parseSumType(
      declaringContext: ParserRuleContext, types: List<Type>
   ): Either<List<CompilationError>, Type> {
      // Align type structures:
      // A[] | B[] becomes Array<A|B>
      // Stream<A> | Stream<B> becomes Stream<A|B>
      // Map<A,B> | Map<C,D> -> not yet supported, but I guess could become Map<A|C,B|D>
      return when {
         types.all { it is ArrayType } -> {
            val memberTypes = types.map { (it as ArrayType).memberType }
            parseSumType(declaringContext, memberTypes).map { internalSumType ->
               ArrayType(internalSumType, declaringContext.toCompilationUnit())
            }
         }
         types.all { it is StreamType } -> {
            val memberTypes = types.map { (it as StreamType).type }
            parseSumType(declaringContext, memberTypes).map { internalSumType ->
               StreamType(internalSumType, declaringContext.toCompilationUnit())
            }
         }
         types.all { it is ObjectType } -> {
            createSumType(declaringContext, types)
         }
         else -> {
            declaringContext.createCompilationError("Cannot create sum type of mismatched container types: Types in a sum must be all arrays, streams, or object types")
         }
      }
   }

   /**
    * Creates the actual sum type.
    * Call this once all structural types (eg: Array, Stream)  have been resolve to their member types.
    */
   private fun createSumType(declaringContext: ParserRuleContext, types: List<Type>):Either<List<CompilationError>, Type> {
      // MP: 8-Mar-24: Don't register Sum Types into the type system.
      // Instead, treat them more like Array types, which are created on-demand.
      // This is because otherwise, the following code becomes invalid:
      // query JoinedStreamsA {
      //    stream { Tweet | TweetAnalytics }
      // }
      //
      // query JoinedStreamsB {
      //    stream { Tweet | TweetAnalytics }
      // }
      // as we've registered the union type twice.
      //
      // I also considered registering the type twice, but with context-specific names,
      // eg: JoinedStreamsA$Union_TweetTweetAnalytics and
      //     JoinedStreamsB$Union_TweetTweetAnalytics
      //
      // I ditched that idea, as not registered seems to be simpler for now. It's similar to how we
      // handle arrays.
      //
      // Note for future self:
      // Languages like Typescript handle this differently.
      // They DO register the type, but use structure, rather than naming, as the unique
      // attribute. Two types with the same structure but different names, are registered as
      // a single structure (as a first-class concept), and two names pointing to the structure.
      // This is how things like structural equality are implemented.
      // For us, given the focus on semantics, that wouldn't quite work - ie., two types that
      // are struturally similar but with different names indicate two different semantic concepts.
      // However, It is something to consider when dealing with side-effects of not registering
      // the union type.
      return when (declaringContext) {
         is UnionTypeContext ->  UnionType(
            types,
            emptyList(),
            declaringContext.toCompilationUnit()
         ).right()
         is IntersectionTypeContext -> IntersectionType(
            types,
            emptyList(),
            declaringContext.toCompilationUnit()
         ).right()
         else -> declaringContext.createCompilationError(
            "An internal error occurred parsing a sum type - expected to find either a UnionType or IntersectionType, but neither were present"
         )
      }
   }

}

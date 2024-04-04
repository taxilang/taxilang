package lang.taxi.compiler

import lang.taxi.TaxiParser.K_Stream
import lang.taxi.TaxiParser.QueryBodyContext
import lang.taxi.TaxiParser.ScalarAccessorExpressionContext
import lang.taxi.TaxiParser.TypeExpressionContext
import lang.taxi.expressions.TypeExpression
import lang.taxi.searchUpForRule
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.toCompilationUnit
import lang.taxi.toCompilationUnits
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * Allows hooking in custom logic to override the type being build when creating a TypeExpression.
 * Almost always the default instance is fine.
 */
interface TypedExpressionBuilder {
   fun typedExpression(type: Type, constraints: List<Constraint>, parserContext: ParserRuleContext): TypeExpression {
      return TypeExpression(type, constraints, parserContext.toCompilationUnits())
   }
}

object DefaultTypedExpressionBuilder : TypedExpressionBuilder

/**
 * Special wrapper which handles stream queries:
 *
 * stream { Foo }
 *
 * adapting the Foo to Stream<Foo>
 *
 *
 */
object StreamDecoratingTypedExpressionBuilder : TypedExpressionBuilder {
   override fun typedExpression(
      type: Type,
      constraints: List<Constraint>,
      parserContext: ParserRuleContext
   ): TypeExpression {
      val queryBodyContext = parserContext.searchUpForRule<QueryBodyContext>()
         ?: return super.typedExpression(type, constraints, parserContext)

      if (queryBodyContext.queryOrMutation().queryDirective().K_Stream() == null) {
         return super.typedExpression(type, constraints, parserContext)
      }

      // we're inside a stream query.
      // The goal is to rewrite  stream { foo } to Stream<foo>.
      // However, we have to be careful not to rewrite complex expressions like
      // stream { Foo.filter( Bar == Baz ) }
      // In that example foo should become Stream<Foo>, but Bar and Baz should not be changed

      // Ensure we're not inside the (Bar == Baz) part of the above
      if (parserContext.searchUpForRule<ScalarAccessorExpressionContext>() != null) {
         return super.typedExpression(type, constraints, parserContext)
      }
      // Note: We may need other AST checks here...


      // Looks like we should rewrite the type:
      val streamType = StreamType.of(type, parserContext.toCompilationUnit())
      return super.typedExpression(streamType, constraints, parserContext)
   }
}

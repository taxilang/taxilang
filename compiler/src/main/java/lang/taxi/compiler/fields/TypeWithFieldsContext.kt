package lang.taxi.compiler.fields

import arrow.core.Either
import arrow.core.rightIfNotNull
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.compiler.TokenProcessor
import lang.taxi.types.ObjectType
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext

/**
 * Wrapper interface for "Things we need to compile fields from".
 * We compile either for a type body, or an annotation body.
 */
interface TypeWithFieldsContext {
   fun findNamespace(): String
   fun memberDeclaration(
      fieldName: String,
      compilingTypeName: String,
      requestingToken: ParserRuleContext
   ): Either<List<CompilationError>, TaxiParser.TypeMemberDeclarationContext> {
      val memberDeclaration = this.memberDeclarations
         .firstOrNull { TokenProcessor.unescape(it.fieldDeclaration().identifier().text) == fieldName }

      return memberDeclaration.rightIfNotNull {
         listOf(CompilationError(requestingToken.start, "Field $fieldName does not exist on type $compilingTypeName"))
      }
   }

   val conditionalTypeDeclarations: List<TaxiParser.ConditionalTypeStructureDeclarationContext>
   val memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>
   val parent: RuleContext?
   val hasSpreadOperator: Boolean
   val objectType: ObjectType?
}

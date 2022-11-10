package lang.taxi.compiler.fields

import lang.taxi.TaxiParser
import lang.taxi.types.ObjectType
import org.antlr.v4.runtime.RuleContext

class TypeBodyContext(
   private val typeBody: TaxiParser.TypeBodyContext?,
   val namespace: String,
   override val objectType: ObjectType? = null
) :
   TypeWithFieldsContext {
   // Cheating - I don't think this method is ever called when the typeBody is null.
   override fun findNamespace(): String = namespace
   override val conditionalTypeDeclarations: List<TaxiParser.ConditionalTypeStructureDeclarationContext>
      get() = typeBody?.conditionalTypeStructureDeclaration() ?: emptyList()
   override val memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>
      get() = typeBody?.typeMemberDeclaration() ?: emptyList()
   override val parent: RuleContext?
      get() = typeBody?.parent
   override val hasSpreadOperator: Boolean
      get() = typeBody?.SPREAD_OPERATOR() != null

}

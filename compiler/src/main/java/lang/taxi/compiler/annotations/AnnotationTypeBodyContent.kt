package lang.taxi.compiler.annotations

import lang.taxi.TaxiParser
import lang.taxi.compiler.fields.TypeWithFieldsContext
import org.antlr.v4.runtime.RuleContext

class AnnotationTypeBodyContent(private val typeBody: TaxiParser.AnnotationTypeBodyContext?, val namespace: String) :
   TypeWithFieldsContext {
   // Cheating - I don't think this method is ever called when the typeBody is null.
   override fun findNamespace(): String = namespace
   override val conditionalTypeDeclarations: List<TaxiParser.ConditionalTypeStructureDeclarationContext> = emptyList()
   override val memberDeclarations: List<TaxiParser.TypeMemberDeclarationContext>
      get() = typeBody?.typeMemberDeclaration() ?: emptyList()
   override val parent: RuleContext?
      get() = typeBody?.parent
}

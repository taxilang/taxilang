package lang.taxi

import lang.taxi.TaxiParser.ServiceDeclarationContext
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.lang.Exception

internal typealias Namespace = String
data class Tokens(val unparsedTypes: Map<String, Pair<Namespace, ParserRuleContext>>,
                  val unparsedExtensions: List<Pair<Namespace, ParserRuleContext>>,
                  val unparsedServices: Map<String, Pair<Namespace, ServiceDeclarationContext>>) {
    fun plus(others: Tokens): Tokens {
        return Tokens(this.unparsedTypes + others.unparsedTypes,
                this.unparsedExtensions + others.unparsedExtensions,
                this.unparsedServices + others.unparsedServices
        )
    }
}

class TokenCollator : TaxiBaseListener() {
    val exceptions = mutableMapOf<ParserRuleContext, Exception>()
    private var namespace: String = Namespaces.DEFAULT_NAMESPACE


    private val unparsedTypes = mutableMapOf<String, Pair<Namespace, ParserRuleContext>>()
    private val unparsedExtensions = mutableListOf<Pair<Namespace, ParserRuleContext>>()
    private val unparsedServices = mutableMapOf<String, Pair<Namespace, ServiceDeclarationContext>>()
//    private val unparsedTypes = mutableMapOf<String, ParserRuleContext>()
//    private val unparsedExtensions = mutableListOf<ParserRuleContext>()
//    private val unparsedServices = mutableMapOf<String, ServiceDeclarationContext>()

    fun tokens(): Tokens {
        return Tokens(unparsedTypes, unparsedExtensions, unparsedServices)
    }

    override fun exitFieldDeclaration(ctx: TaxiParser.FieldDeclarationContext) {
        collateExceptions(ctx)
        // Check to see if an inline type alias is declared
        // If so, mark it for processing later
        val typeType = ctx.typeType()
        if (typeType.aliasedType() != null) {
            val classOrInterfaceType = typeType.classOrInterfaceType()
            unparsedTypes.put(qualify(classOrInterfaceType.Identifier().text()), namespace to typeType)
        }
        super.exitFieldDeclaration(ctx)
    }

    override fun exitEnumDeclaration(ctx: TaxiParser.EnumDeclarationContext) {
        collateExceptions(ctx)
        val name = qualify(ctx.classOrInterfaceType().Identifier().text())
        unparsedTypes.put(name, namespace to ctx)
        super.exitEnumDeclaration(ctx)
    }

    override fun exitNamespaceDeclaration(ctx: TaxiParser.NamespaceDeclarationContext) {
        collateExceptions(ctx)
        this.namespace = ctx.qualifiedName().Identifier().text()
        super.exitNamespaceDeclaration(ctx)
    }

    override fun enterNamespaceBody(ctx: TaxiParser.NamespaceBodyContext) {
        val parent = ctx.parent as ParserRuleContext
        val namespaceNode = parent.getChild(TaxiParser.QualifiedNameContext::class.java,0)
        this.namespace = namespaceNode.Identifier().text()
        super.enterNamespaceBody(ctx)
    }



    override fun exitServiceDeclaration(ctx: ServiceDeclarationContext) {
        collateExceptions(ctx)
        val qualifiedName = qualify(ctx.Identifier().text)
        unparsedServices[qualifiedName] = namespace to ctx
        super.exitServiceDeclaration(ctx)
    }

    override fun exitTypeDeclaration(ctx: TaxiParser.TypeDeclarationContext) {
        collateExceptions(ctx)
        val typeName = qualify(ctx.Identifier().text)
        unparsedTypes.put(typeName, namespace to ctx)
        super.exitTypeDeclaration(ctx)
    }

    override fun exitTypeAliasDeclaration(ctx: TaxiParser.TypeAliasDeclarationContext) {
        collateExceptions(ctx)
        val typeName = qualify(ctx.Identifier().text)
        unparsedTypes.put(typeName, namespace to ctx)
        super.exitTypeAliasDeclaration(ctx)
    }

    override fun exitTypeExtensionDeclaration(ctx: TaxiParser.TypeExtensionDeclarationContext) {
        collateExceptions(ctx)
        val typeName = qualify(ctx.Identifier().text)
        unparsedExtensions.add(namespace to ctx)
        super.exitTypeExtensionDeclaration(ctx)
    }

    private fun collateExceptions(ctx: ParserRuleContext) {
        if (ctx.exception != null) {
            exceptions.put(ctx, ctx.exception)
        }
    }

    private fun qualify(name: String): String {
        if (name.contains("."))
        // This is already qualified
            return name
        if (namespace.isEmpty()) return name
        return "$namespace.$name"
    }

    fun List<TerminalNode>.text(): String {
        return this.joinToString(".")
    }


}

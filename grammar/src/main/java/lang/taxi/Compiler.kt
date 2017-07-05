package lang.taxi

import lang.taxi.types.*
import lang.taxi.types.Annotation
import org.antlr.v4.runtime.*
import java.io.File
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.nio.file.Path

data class CompilationError(val offendingToken: Token, val detailMessage: String?)
class CompilationException(val errors: List<CompilationError>) : RuntimeException(errors.map { it.detailMessage }.filterNotNull().joinToString()) {
    constructor(offendingToken: Token, detailMessage: String?) : this(listOf(CompilationError(offendingToken, detailMessage)))
}

class Compiler(val input: CharStream) {
    constructor(path: Path) : this(CharStreams.fromPath(path))
    constructor(source: String) : this(CharStreams.fromString(source))
    constructor(file: File) : this(CharStreams.fromPath(file.toPath()))

    companion object {
        fun main(args: Array<String>) {
        }
    }

    fun compile(): TaxiDocument {
        // TODO : Can probably be smarter about this, and
        // stream the source, rather than returning a string from the
        // source provider
        val lexer = TaxiLexer(input)
        val parser = TaxiParser(CommonTokenStream(lexer))

        val listener = DocumentListener()
        parser.addParseListener(listener)
        val doc = parser.document()
        doc.exception?.let {
            throw CompilationException(it.offendingToken, it.message)
        }
        val errors = listener.exceptions.map { (context, exception) ->
            CompilationError(context.start, exception.message)
        }
        if (errors.isNotEmpty())
            throw CompilationException(errors)

        return listener.buildTaxiDocument()
    }
}

private class DocumentListener : TaxiBaseListener() {
    val exceptions = mutableMapOf<ParserRuleContext, Exception>()
    private var namespace: String? = null
    private val typeSystem = TypeSystem()
    private val unparsedTypes = mutableMapOf<String, ParserRuleContext>()
    private val unparsedExtensions = mutableListOf<ParserRuleContext>()
    fun buildTaxiDocument(): TaxiDocument {
        compile()
        return TaxiDocument(namespace, typeSystem.typeList())
    }

    private fun qualify(name: String): String {
        if (namespace == null) return name
        return "$namespace.$name"
    }

    override fun exitNamespaceDeclaration(ctx: TaxiParser.NamespaceDeclarationContext) {
        collateExceptions(ctx)
        this.namespace = (ctx.payload as ParserRuleContext).children[1].text
        super.exitNamespaceDeclaration(ctx)
    }

    private fun compile() {
        createEmptyTypes()
        compileTokens()
        compileTypeExtensions()
    }

    private fun createEmptyTypes() {
        unparsedTypes.forEach { tokenName, token ->
            when (token) {
                is TaxiParser.EnumDeclarationContext -> typeSystem.register(EnumType.undefined(tokenName))
                is TaxiParser.TypeDeclarationContext -> typeSystem.register(ObjectType.undefined(tokenName))
                is TaxiParser.TypeAliasDeclarationContext -> typeSystem.register(TypeAlias.undefined(tokenName))
            }
        }
    }

    private fun compileTokens() {
        unparsedTypes.forEach { tokenName, _ ->
            compileToken(tokenName)
        }
    }

    private fun compileToken(tokenName: String) {
        val tokenRule = unparsedTypes[tokenName]
        if (typeSystem.isDefined(tokenName) && typeSystem.getType(tokenName) is TypeAlias) {
            // As type aliases can be defined inline, it's perfectly acceptable for
            // this to already exist
            return
        }
        when (tokenRule) {
            is TaxiParser.TypeDeclarationContext -> compileType(tokenName, tokenRule)
            is TaxiParser.EnumDeclarationContext -> compileEnum(tokenName, tokenRule)
            is TaxiParser.TypeAliasDeclarationContext -> compileTypeAlias(tokenName, tokenRule)
        // TODO : This is a bit broad - assuming that all typeType's that hit this
        // line will be a TypeAlias inline.  It could be a normal field declaration.
            is TaxiParser.TypeTypeContext -> compileInlineTypeAlias(tokenRule)
            else -> TODO("Not handled: $tokenRule")
        }
    }

    private fun compileTypeExtensions() {
        unparsedExtensions.forEach { typeRule ->
            when (typeRule) {
                is TaxiParser.TypeExtensionDeclarationContext -> compileTypeExtension(typeRule)
                else -> TODO("Not handled: $typeRule")
            }
        }
    }

    private fun compileTypeExtension(typeRule: TaxiParser.TypeExtensionDeclarationContext) {
        val typeName = typeRule.Identifier().text
        val type = typeSystem.getType(typeName) as ObjectType
        val annotations = collateAnnotations(typeRule.annotation())
        val fieldExtensions = typeRule.typeExtensionBody().typeExtensionMemberDeclaration().map { member ->
            val fieldName = member.typeExtensionFieldDeclaration().Identifier().text
            val fieldAnnotations = collateAnnotations(member.annotation())
            FieldExtension(fieldName, fieldAnnotations)
        }
        type.extensions.add(ObjectTypeExtension(annotations, fieldExtensions))

    }

    private fun compileTypeAlias(tokenName: String, tokenRule: TaxiParser.TypeAliasDeclarationContext) {
        val qualifiedName = tokenRule.aliasedType().typeType().text
        val typePointedTo = typeSystem.getType(qualifiedName)
        val annotations = collateAnnotations(tokenRule.annotation())

        val definition = TypeAliasDefinition(typePointedTo, annotations)
        this.typeSystem.register(TypeAlias(tokenName, definition))
    }


    override fun exitTypeExtensionDeclaration(ctx: TaxiParser.TypeExtensionDeclarationContext) {
        collateExceptions(ctx)
        val typeName = qualify(ctx.Identifier().text)
        unparsedExtensions.add(ctx)
        super.exitTypeExtensionDeclaration(ctx)
    }

    override fun exitFieldDeclaration(ctx: TaxiParser.FieldDeclarationContext) {
        collateExceptions(ctx)
        // Check to see if an inline type alias is declared
        // If so, mark it for processing later
        val typeType = ctx.typeType()
        if (typeType.aliasedType() != null) {
            val classOrInterfaceType = typeType.classOrInterfaceType()
            unparsedTypes.put(qualify(classOrInterfaceType.Identifier().text), typeType)
        }
        super.exitFieldDeclaration(ctx)
    }

    override fun exitTypeDeclaration(ctx: TaxiParser.TypeDeclarationContext) {
        collateExceptions(ctx)
        val typeName = qualify(ctx.Identifier().text)
        unparsedTypes.put(typeName, ctx)
        super.exitTypeDeclaration(ctx)
    }

    override fun exitTypeAliasDeclaration(ctx: TaxiParser.TypeAliasDeclarationContext) {
        collateExceptions(ctx)
        val typeName = qualify(ctx.Identifier().text)
        unparsedTypes.put(typeName, ctx)
        super.exitTypeAliasDeclaration(ctx)
    }

    private fun compileType(typeName: String, ctx: TaxiParser.TypeDeclarationContext) {
        val fields = ctx.typeBody().typeMemberDeclaration().map { member ->
            val fieldAnnotations = collateAnnotations(member.annotation())
            Field(name = member.fieldDeclaration().Identifier().text,
                    type = parseType(member.fieldDeclaration().typeType()),
                    nullable = member.fieldDeclaration().typeType().optionalType() != null,
                    annotations = fieldAnnotations
            )
        }
        val annotations = collateAnnotations(ctx.annotation())
        this.typeSystem.register(ObjectType(typeName, ObjectTypeDefinition(fields, annotations)))
    }

    private fun collateAnnotations(annotations: List<TaxiParser.AnnotationContext>): List<Annotation> {
        return annotations.map { annotation ->
            val name = annotation.qualifiedName().text
            val parameters: Map<String, Any?> = annotation.elementValuePairs()?.elementValuePair()?.map {
                it.Identifier().text to it.elementValue().literal()?.value()
            }?.toMap() ?: emptyMap()
            Annotation(name, parameters)
        }
    }

    private fun parseType(typeType: TaxiParser.TypeTypeContext): Type {
        val type = when {
//            typeType.aliasedType() != null -> compileInlineTypeAlias(typeType)
            typeType.classOrInterfaceType() != null -> resolveUserType(typeType.classOrInterfaceType())
            typeType.primitiveType() != null -> PrimitiveType.fromDeclaration(typeType.getChild(0).text)
            else -> throw IllegalArgumentException()
        }
        if (typeType.listType() != null) {
            return ArrayType(type)
        } else {
            return type
        }
    }

    /**
     * Handles type aliases that are declared inline (firstName : PersonFirstName as String)
     * rather than those declared explicitly (type alias PersonFirstName as String)
     */
    private fun compileInlineTypeAlias(aliasTypeDefinition: TaxiParser.TypeTypeContext): Type {
        val aliasedType = parseType(aliasTypeDefinition.aliasedType().typeType())
        val typeAliasName = qualify(aliasTypeDefinition.classOrInterfaceType().Identifier().text)
        // Annotations not supported on Inline type aliases
        val annotations = emptyList<Annotation>()
        val typeAlias = TypeAlias(typeAliasName, TypeAliasDefinition(aliasedType, annotations))
        typeSystem.register(typeAlias)
        return typeAlias
    }

    private fun resolveUserType(classType: TaxiParser.ClassOrInterfaceTypeContext): Type {
        val typeName = qualify(classType.Identifier().text)
        if (typeSystem.contains(typeName)) {
            return typeSystem.getType(typeName)
        }

        if (unparsedTypes.contains(typeName)) {
            compileToken(typeName)
            return typeSystem.getType(typeName)
        }
        throw CompilationException(classType.start, "Unresolved type : $typeName")
    }

    override fun exitEnumDeclaration(ctx: TaxiParser.EnumDeclarationContext) {
        collateExceptions(ctx)
        val name = qualify(ctx.Identifier().text)
        unparsedTypes.put(name, ctx)
        super.exitEnumDeclaration(ctx)
    }

    private fun collateExceptions(ctx: ParserRuleContext) {
        if (ctx.exception != null) {
            exceptions.put(ctx, ctx.exception)
        }
    }

    private fun compileEnum(typeName: String, ctx: TaxiParser.EnumDeclarationContext) {
        val enumValues = ctx.enumConstants().enumConstant().map { enumConstant ->
            val annotations = collateAnnotations(enumConstant.annotation())
            val value = enumConstant.Identifier().text
            EnumValue(value, annotations)
        }
        val annotations = collateAnnotations(ctx.annotation())
        val enumType = EnumType(typeName, EnumDefinition(enumValues, annotations))
        typeSystem.register(enumType)
    }

}

private fun TaxiParser.LiteralContext.value(): Any {
    return when {
        this.StringLiteral() != null -> this.StringLiteral().text.trim('"')
        this.IntegerLiteral() != null -> this.IntegerLiteral().text.toInt()
        this.BooleanLiteral() != null -> this.BooleanLiteral().text.toBoolean()
        else -> TODO()
//      this.IntegerLiteral() != null -> this.IntegerLiteral()
    }
}

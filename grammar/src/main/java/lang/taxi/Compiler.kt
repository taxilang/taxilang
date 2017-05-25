package lang.taxi

import lang.taxi.types.*
import org.antlr.v4.runtime.*
import java.io.File
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.nio.file.Path

data class CompilationError(val offendingToken: Token, val detailMessage: String?)
class CompilationException(val errors:List<CompilationError>) : RuntimeException(errors.map { it.detailMessage }.filterNotNull().joinToString()) {
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

      return listener.buildTaxiDocument()
   }
}

private class DocumentListener : TaxiBaseListener() {
   private var namespace: String? = null
   private val typeSystem = TypeSystem()
   fun buildTaxiDocument(): TaxiDocument {
      typeSystem.assertAllTypesResolved()
      return TaxiDocument(namespace, typeSystem.typeList())
   }

   override fun enterNamespaceDeclaration(ctx: TaxiParser.NamespaceDeclarationContext?) {
      super.enterNamespaceDeclaration(ctx)
   }

   override fun exitNamespaceDeclaration(ctx: TaxiParser.NamespaceDeclarationContext) {
      this.namespace = (ctx.payload as ParserRuleContext).children[1].text
      super.exitNamespaceDeclaration(ctx)
   }

   override fun exitTypeDeclaration(ctx: TaxiParser.TypeDeclarationContext) {
      val typeName = ctx.Identifier().text
      val fields = ctx.typeBody().typeMemberDeclaration().map { member ->
         Field(name = member.fieldDeclaration().Identifier().text,
            type = parseType(member.fieldDeclaration().typeType()),
            nullable = member.fieldDeclaration().typeType().optionalType() != null
         )
      }
      this.typeSystem.register(ObjectType(typeName, ObjectTypeDefinition(fields)))
      super.exitTypeDeclaration(ctx)
   }

   private fun parseType(typeType: TaxiParser.TypeTypeContext): Type {
      val type = when {
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

   private fun resolveUserType(classType: TaxiParser.ClassOrInterfaceTypeContext): Type {
      val typeName = classType.Identifier().text
      return typeSystem.getOrCreate(typeName, classType.start)
   }

}

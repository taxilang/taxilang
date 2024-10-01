package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Namespace
import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.FunctionDeclarationContext
import lang.taxi.expressions.Expression
import lang.taxi.functions.Function
import lang.taxi.functions.FunctionDefinition
import lang.taxi.functions.FunctionModifier
import lang.taxi.toCompilationUnit
import java.util.*

class FunctionCompiler(
   private val tokenProcessor: TokenProcessor,
) {
   val typeSystem = tokenProcessor.typeSystem
   fun compileFunction(
      namespaceAndParserContext: Pair<Namespace, TaxiParser.FunctionDeclarationContext>,
      qualifiedName: String
   ): Either<List<CompilationError>, Function> {
      val (namespace, functionToken) = namespaceAndParserContext
      val typeArguments = (functionToken.typeArguments()?.typeReference() ?: emptyList()).map { typeType ->
         tokenProcessor.resolveGenericTypeArgument(qualifiedName, typeType)
      }

      return requireBodyIfNotDeclareFunction(functionToken)
         .flatMap { requireNoBodyPresentIfDeclarationFunction(functionToken) }
         .flatMap {
            tokenProcessor.parseType(
               namespace,
               functionToken.nullableTypeReference().typeReference(),
               typeArguments
            ).flatMap { returnType ->
               val parameters =
                  functionToken.operationParameterList()?.operationParameter()
                     ?.mapIndexed { index, parameterDefinition ->
                        val anonymousParameterTypeName = "$qualifiedName\$Param$index"
                        tokenProcessor.parseParameter(
                           namespace,
                           parameterDefinition,
                           typeArguments,
                           anonymousParameterTypeName,
                           paramIndex = index
                        )
                     }?.reportAndRemoveErrorList(tokenProcessor.errors) ?: emptyList()

               val modifiers = functionToken.functionModifiers().map { modifier ->
                  FunctionModifier.forToken(modifier.text)
               }.let {
                  if (it.isEmpty()) {
                     EnumSet.noneOf(FunctionModifier::class.java)
                  } else {
                     EnumSet.copyOf(it)
                  }
               }

               if (modifiers.contains(FunctionModifier.Extension) && parameters.isEmpty()) {
                  return listOf(
                     CompilationError(
                        functionToken.toCompilationUnit(),
                        "Extension functions must have at least one parameter, as this defines the type the function can operate against"
                     )
                  )
                     .left()
               }


               val nullable = functionToken.nullableTypeReference().Nullable() != null
               val typeDoc = tokenProcessor.parseTypeDoc(functionToken.typeDoc())

               // Compile body, if present
               val functionBody = functionToken.functionExpression()?.let { functionExpression ->

                  tokenProcessor.expressionCompiler(scopedArguments = parameters)
                     .compile(functionExpression.expressionGroup())
                     .flatMap { expression ->
                        tokenProcessor.typeChecker.ifAssignableOrErrorList(expression.returnType, returnType, functionToken) { expression }
                     }
               }
                  ?: (null as Expression?).right()

               functionBody.map { body ->
                  val function = Function(
                     qualifiedName,
                     FunctionDefinition(
                        parameters,
                        returnType,
                        nullable,
                        modifiers,
                        typeArguments,
                        body,
                        typeDoc,
                        functionToken.toCompilationUnit()
                     )
                  )
                  this.typeSystem.register(function)
               }
            }
         }

   }

   private fun requireNoBodyPresentIfDeclarationFunction(functionToken: FunctionDeclarationContext): Either<List<CompilationError>, FunctionDeclarationContext> {
      return if (functionToken.K_Declare() != null && functionToken.functionExpression() != null) {
         listOf(CompilationError(functionToken.toCompilationUnit(), "A declare function must not provide an implementation. Either update this definition to remove the 'declare' modifier, or remove the implementation"))
            .left()
      } else {
         functionToken.right()
      }
   }

   private fun requireBodyIfNotDeclareFunction(functionToken: TaxiParser.FunctionDeclarationContext): Either<List<CompilationError>, FunctionDeclarationContext> {
      return if (functionToken.K_Declare() == null && functionToken.functionExpression() == null) {
         listOf(CompilationError(functionToken.toCompilationUnit(), "A function that is not a 'declare' function must provide an implementation. Either update this definition to 'declare function', or provide an implementation"))
            .left()
      } else {
         functionToken.right()
      }
   }
}

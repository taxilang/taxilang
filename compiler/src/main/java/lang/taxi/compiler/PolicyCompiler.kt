package lang.taxi.compiler

import arrow.core.Either
import arrow.core.flatMap
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.compiler.fields.FieldTypeSpec
import lang.taxi.policies.Policy
import lang.taxi.policies.PolicyOperationScope
import lang.taxi.policies.PolicyRule
import lang.taxi.services.OperationScope
import lang.taxi.toCompilationUnits
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList

class PolicyCompiler(private val tokenProcessor: TokenProcessor) {
   fun compilerPolicy(
      name: String,
      token: TaxiParser.PolicyDeclarationContext,
      targetType: Type
   ): Either<List<CompilationError>, Policy> {

      return compilePolicyInputs(token.expressionInputs(), targetType).flatMap { inputs ->
         // When compiling a policy, "this" refers to the type the policy is enforced against
         val thisToken = ProjectionFunctionScope.implicitThis(targetType)
         val resolutionContext = ResolutionContext(activeScopes = inputs + thisToken)
         val expressionCompiler = tokenProcessor.expressionCompiler(
            scopedArguments = resolutionContext.argumentsInScope
         )
         val annotations = tokenProcessor.collateAnnotations(token.annotation())
         token.policyRuleSet().map { compilePolicyRule(it, expressionCompiler, targetType) }
            .invertEitherList()
            .flattenErrors()
            .map { rules ->
               Policy(
                  name,
                  targetType,
                  (inputs + thisToken).distinct(),
                  rules,
                  annotations,
                  token.toCompilationUnits()
               )
            }
      }
   }


//                val annotations = emptyList<Annotation>() // TODO
//            val ruleSets = compilePolicyRulesets(namespace, token)
//            Policy(
//               name,
//               targetType,
//               ruleSets,
//               annotations,
//               compilationUnits = listOf(token.toCompilationUnit())
//            )

   private fun compilePolicyInputs(
      expressionInputs: TaxiParser.ExpressionInputsContext?,
      targetType: Type
   ): Either<List<CompilationError>, List<ProjectionFunctionScope>> {
      return tokenProcessor.parseProjectionScope(expressionInputs, FieldTypeSpec.forType(targetType), emptyList())
   }

   private fun compilePolicyRule(
      policyRule: TaxiParser.PolicyRuleSetContext,
      expressionCompiler: ExpressionCompiler,
      targetType: Type
   ): Either<List<CompilationError>, PolicyRule> {
      val policyScope = PolicyOperationScope.parse(policyRule.policyScope()?.text)
      val operationScope = OperationScope.forToken(policyRule.operationScope()?.text)
      return expressionCompiler.compile(policyRule.expressionGroup(), targetType = targetType).map { expression ->
         PolicyRule(operationScope, policyScope, expression)
      }
   }
   /*

   private fun compilePolicyRulesets(namespace: String, token: PolicyDeclarationContext): List<RuleSet> {
      return token.policyRuleSet().map {
         compilePolicyRuleset(namespace, it)
      }
   }

   private fun compilePolicyRuleset(namespace: String, token: PolicyRuleSetContext): RuleSet {
      val operationType = token.policyOperationType().identifier()?.text
      val operationScope = PolicyOperationScope.parse(token.policyScope()?.text)
      val scope = PolicyScope.from(operationType, operationScope)
      val statements = if (token.policyBody() != null) {
         token.policyBody().policyStatement().map { compilePolicyStatement(namespace, it) }
      } else {
         listOf(
            PolicyStatement(
               ElseCondition(),
               Instructions.parse(token.policyInstruction()),
               token.toCompilationUnit()
            )
         )
      }
      return RuleSet(scope, statements)
   }

   private fun compilePolicyStatement(namespace: String, token: PolicyStatementContext): PolicyStatement {
      val (condition, instruction) = compileCondition(namespace, token)
      return PolicyStatement(condition, instruction, token.toCompilationUnit())
   }

   private fun compileCondition(
      namespace: String,
      token: PolicyStatementContext
   ): Pair<Condition, Instruction> {
      return when {
         token.policyCase() != null -> compileCaseCondition(namespace, token.policyCase())
         token.policyElse() != null -> ElseCondition() to Instructions.parse(token.policyElse().policyInstruction())
         else -> error("Invalid condition is neither a case nor an else")
      }
   }

   private fun compileCaseCondition(
      namespace: String,
      case: PolicyCaseContext
   ): Pair<Condition, Instruction> {
      val typeResolver = typeResolver(namespace)
      val condition = CaseCondition(
         Subjects.parse(case.policyExpression(0), typeResolver),
         Operator.parse(case.policyOperator().text),
         Subjects.parse(case.policyExpression(1), typeResolver)
      )
      val instruction = Instructions.parse(case.policyInstruction())
      return condition to instruction
   }
    */
}

/*

object Subjects {
   fun parse(expression: TaxiParser.PolicyExpressionContext, typeResolver: NamespaceQualifiedTypeResolver): Subject {
      return when {
         expression.callerIdentifer() != null -> RelativeSubject(RelativeSubject.RelativeSubjectSource.CALLER, typeResolver.resolve(expression.callerIdentifer().typeReference()).orThrowCompilationException())
         expression.thisIdentifier() != null -> RelativeSubject(RelativeSubject.RelativeSubjectSource.THIS, typeResolver.resolve(expression.thisIdentifier().typeReference()).orThrowCompilationException())
         expression.literalArray() != null -> LiteralArraySubject(expression.literalArray().literal().map { it.value() })
         expression.literal() != null -> LiteralSubject(expression.literal().valueOrNull())
         else -> error("Unhandled subject : ${expression.text}")
      }
   }
}

 */


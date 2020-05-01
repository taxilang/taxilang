package lang.taxi.policies

import lang.taxi.*

object Instructions {
   fun parse(instruction: TaxiParser.PolicyInstructionContext): Instruction {
      return when {
         instruction.policyInstructionEnum() != null -> {
            require(instruction.policyInstructionEnum().text == Instruction.InstructionType.PERMIT.symbol) { "Only permit or filter currently supported" }
            PermitInstruction
         }
         instruction.policyFilterDeclaration() != null -> {
            val fieldIdentifiers = instruction.policyFilterDeclaration().filterAttributeNameList()?.Identifier()
               ?: emptyList()
            val fieldNames = fieldIdentifiers.map { it.text }
            FilterInstruction(fieldNames)
         }
         else -> error("Unhandled instruction type")
      }
   }
}

object Subjects {
   fun parse(expression: TaxiParser.PolicyExpressionContext, typeResolver: TypeResolver): Subject {
      return when {
         expression.callerIdentifer() != null -> RelativeSubject(RelativeSubject.RelativeSubjectSource.CALLER, typeResolver.invoke(expression.callerIdentifer().typeType()).orThrowCompilationException())
         expression.thisIdentifier() != null -> RelativeSubject(RelativeSubject.RelativeSubjectSource.THIS, typeResolver.invoke(expression.thisIdentifier().typeType()).orThrowCompilationException())
         expression.literalArray() != null -> LiteralArraySubject(expression.literalArray().literal().map { it.value() })
         expression.literal() != null -> LiteralSubject(expression.literal().valueOrNull())
         else -> error("Unhandled subject : ${expression.text}")
      }
   }
}

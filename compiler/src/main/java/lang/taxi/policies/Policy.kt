package lang.taxi.policies

import lang.taxi.*
import lang.taxi.types.Annotation

data class Policy(
        override val qualifiedName: String,
        val targetType: Type,
        val statements: List<PolicyStatement>,
        override val annotations: List<Annotation>,
        override val compilationUnits: List<CompilationUnit>
) : Annotatable, Named, Compiled {

}

data class PolicyStatement(
        val condition: Condition,
        val instruction: Instruction,
        val source: CompilationUnit
) {
}

interface Condition {
    // TODO
//    fun matches(any: Any): Boolean
}

class ElseCondition : Condition {
//    override fun matches(any: Any) = true
}

sealed class Subject {
    companion object {
        fun parse(expression: TaxiParser.PolicyExpressionContext, typeResolver: TypeResolver): Subject {
            return when {
                expression.callerIdentifer() != null -> RelativeSubject(RelativeSubject.RelativeSubjectSource.CALLER, typeResolver.invoke(expression.callerIdentifer().typeType()))
                expression.thisIdentifier() != null -> RelativeSubject(RelativeSubject.RelativeSubjectSource.THIS, typeResolver.invoke(expression.thisIdentifier().typeType()))
                expression.anyOfOperator() != null -> AnyOfValuesSubject(expression.anyOfOperator().literal().map { it.value() })
                expression.literal() != null -> LiteralSubject(expression.literal().value())
                else -> error("Unhandled subject : ${expression.text}")
            }
        }
    }
}

class CaseCondition(val lhSubject: Subject, val operator: Operator, val rhSubject: Subject) : Condition {

}

data class RelativeSubject(val source: RelativeSubjectSource, val targetType: Type) : Subject() {
    enum class RelativeSubjectSource {
        CALLER, THIS;
    }
}

data class AnyOfValuesSubject(val values: List<Any>) : Subject()
data class LiteralSubject(val value: Any) : Subject()

enum class Operator(val symbol: String) {
    EQUAL("="),
    NOT_EQUAL("!=");

    companion object {
        private val symbols = Operator.values().associateBy { it.symbol }
        fun parse(value: String): Operator {
            return symbols[value] ?: error("No operator matches symbol $value")
        }
    }


}

data class Instruction(
        val type: InstructionType,
        val processorName: String? = null
) {
    init {
        if (this.type.requiresProcessor && this.processorName == null) {
            error("A processor must be specified if using instruction of type ${type.symbol}")
        }
    }

    companion object {
        fun parse(instruction: TaxiParser.PolicyInstructionContext): Instruction {
            val type = instruction.getChild(0).text
            return Instruction(InstructionType.parse(type), instruction.Identifier()?.text)
        }
    }

    enum class InstructionType(val symbol: String, val requiresProcessor: Boolean = false) {
        PERMIT("permit"),
        PROCESS("process", true),
        DEFER("defer"),
        DENY("deny");

        companion object {
            private val bySymbol = InstructionType.values().associateBy { it.symbol }

            fun parse(symbol: String) = bySymbol[symbol] ?: error("Invalid instruction with symbol $symbol")
        }
    }
}
package lang.taxi.policies

import lang.taxi.Operator
import lang.taxi.types.*
import lang.taxi.types.Annotation

data class Policy(
        override val qualifiedName: String,
        val targetType: Type,
        val ruleSets: List<RuleSet>,
        override val annotations: List<Annotation>,
        override val compilationUnits: List<CompilationUnit>
) : Annotatable, Named, Compiled

data class RuleSet(
        val scope: PolicyScope,
        val statements: List<PolicyStatement>
)

/**
 * operationType is a user-defined value, that descrbbes the behaviour of the operation.
 * Common values would be read,write - but it;s up too the user to ddefine.
 * These then need to be applied to operations, for the policy to apply.
 *
 * Alternatively, a wildcard operationType of '*' may be defined, which matches all
 * operation
 *
 */
data class PolicyScope(val operationType: String = WILDCARD_OPERATION_TYPE, val policyOperationScope: PolicyOperationScope = DEFAULT_OPERATION_SCOPE) {
    fun appliesTo(operationType: String?, policyOperationScope: PolicyOperationScope): Boolean {
        val operationTypeMatches = operationTypeMatches(this.operationType, operationType)
        val scopeMatches = operationScopeMatches(this.policyOperationScope, policyOperationScope)
        return operationTypeMatches && scopeMatches
    }

    override fun toString(): String {
        return "Policy scope $operationType ${policyOperationScope.symbol}"
    }

    private fun operationScopeMatches(policyScope: PolicyOperationScope, policyOperationScope: PolicyOperationScope): Boolean {
        // TODO : Haven't given this much thought, so this needs revisiting.
        // In theory, if one of the scopes covers both internal & external, then it's a match,
        // But this is a quick impl, and that thought might be wrong.
        return when {
            policyScope == policyOperationScope -> true
            policyScope == PolicyOperationScope.INTERNAL_AND_EXTERNAL -> true
            policyOperationScope == PolicyOperationScope.INTERNAL_AND_EXTERNAL -> true
            else -> false
        }

    }

    private fun operationTypeMatches(policyOperationType: String, operationType: String?): Boolean {
//        The idea here is that if a policy is defined as wildcard, it always matches.
        // If an operation hasn't defined a scope, then no policies with defined scopes (other than wildcard) match
        return when {
            policyOperationType == WILDCARD_OPERATION_TYPE -> true
            operationType == null -> false
            policyOperationType == operationType -> true
            else -> false
        }
    }

    companion object {
        const val WILDCARD_OPERATION_TYPE = "*"
        val DEFAULT_OPERATION_SCOPE = PolicyOperationScope.INTERNAL_AND_EXTERNAL

        val DEFAULT_POLICY_SCOPE = PolicyScope()

        fun from(operationType: String?, policyOperationScope: PolicyOperationScope?): PolicyScope {
            return PolicyScope(operationType ?: WILDCARD_OPERATION_TYPE, policyOperationScope ?: DEFAULT_OPERATION_SCOPE)
        }
    }
}

/**
 * Differentiates between data returned to a caller (External),
 * and data collected by Vyne as part of a query plan (Internal).
 * The idea being you can write more relaxed rules for Vyne to collect data,
 * and then strict rules about what is actually returned back out to the caller.
 */

enum class PolicyOperationScope(val symbol: String) {
    INTERNAL_AND_EXTERNAL("internal"),

    EXTERNAL("external");

    companion object {
        private val bySymbol = PolicyOperationScope.values().associateBy { it.symbol }
        fun parse(input: String?): PolicyOperationScope? {
            return if (input == null) null else
                bySymbol[input] ?: error("Unknown scope - $input")
        }

    }
}

data class PolicyStatement(
        val condition: Condition,
        val instruction: Instruction,
        val source: CompilationUnit
) {
}

interface Condition {
}

class ElseCondition : Condition {
//    override fun matches(any: Any) = true
}

sealed class Subject {
}

class CaseCondition(val lhSubject: Subject, val operator: Operator, val rhSubject: Subject) : Condition {

}

data class RelativeSubject(val source: RelativeSubjectSource, val targetType: Type) : Subject() {
    enum class RelativeSubjectSource {
        CALLER, THIS;
    }
}

data class LiteralArraySubject(val values: List<Any>) : Subject()
data class LiteralSubject(val value: Any?) : Subject()

data class InstructionProcessor(
        val name: String,
        val args: List<Any> = emptyList()
)

interface Instruction {
    val type: Instruction.InstructionType
    val description: String

    // Processors commend out for now.
    // https://gitlab.com/vyne/vyne/issues/52
    enum class InstructionType(val symbol: String /*, val requiresProcessor: Boolean = false */) {
        PERMIT("permit"),
        //        PROCESS("process", true),
//        DEFER("defer"),
        FILTER("filter");

        companion object {
            private val bySymbol = InstructionType.values().associateBy { it.symbol }

            fun parse(symbol: String) = bySymbol[symbol] ?: error("Invalid instruction with symbol $symbol")
        }
    }

}

object PermitInstruction : Instruction {
    override val type = Instruction.InstructionType.PERMIT
    override val description: String = type.symbol

    override fun toString(): String {
        return "Instruction permit"
    }

}

data class FilterInstruction(val fieldNames: List<String> = emptyList()) : Instruction {
    override val type = Instruction.InstructionType.FILTER
    override val description: String
        get() = this.toString()
    val isFilterAll = fieldNames.isEmpty()

    override fun toString(): String {
        val fieldNameList = if (isFilterAll) "all" else fieldNames.joinToString(",")
        return "Instruction filter $fieldNameList"
    }
}
// TODO : Simplifying instructions at the moment to either Permit, or Filter xxx.
// Re-introduce processors later.
// https://gitlab.com/vyne/vyne/issues/52
//
//data class Instruction(
//        val type: InstructionType,
//        val filteredFields: List<String>?,
//        val processor: InstructionProcessor?
//) {
//    init {
//        if (this.type.requiresProcessor && this.processor == null) {
//            error("A processor must be specified if using instruction of type ${type.symbol}")
//        }
//    }
//
//    override fun toString(): String {
//        val processorString = if (processor != null) " with processor ${processor.name}" else ""
//        return "Instruction ${type.symbol}$processorString"
//    }
//
//    companion object {
//        fun parse(instruction: TaxiParser.PolicyInstructionContext): Instruction {
//            return when {
//                instruction.policyInstructionEnum() != null -> Instruction(InstructionType.parse(instruction.policyInstructionEnum().text), null)
//                instruction.policyFilterDeclaration() != null -> {
//                    val filterDeclaration = instruction.policyFilterDeclaration()
//
//                }
//
//                instruction.policyProcessorDeclaration() != null -> {
//                    val processor = instruction.policyProcessorDeclaration()
//                    val processorName = processor.qualifiedName().text
//                    val params = processor.policyProcessorParameterList().policyParameter().map { parameter ->
//                        when {
//                            parameter.literal() != null -> parameter.literal().value()
//                            parameter.literalArray() != null -> parameter.literalArray().value()
//                            else -> error("Unhandled param type")
//                        }
//                    }
//                    Instruction(InstructionType.PROCESS, InstructionProcessor(processorName, params))
//                }
//                else -> error("Unhandled policy type")
//            }
//        }
//    }
//}

package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.policies.CaseCondition
import lang.taxi.policies.ElseCondition
import lang.taxi.policies.FilterInstruction
import lang.taxi.policies.Instruction
import lang.taxi.policies.LiteralArraySubject
import lang.taxi.policies.PolicyOperationScope
import lang.taxi.policies.RelativeSubject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccessPolicyTest {

    lateinit var doc: TaxiDocument
    @BeforeEach
    fun setup() {
        val taxiDef = """
namespace test {
    type TradingDesk {
        deskId : DeskId as String
    }
    type Trader {
        traderId : TraderId as String
        desk : DeskId
    }
    type Trade {
        desk : DeskId
        counterPartyName : String
        rate : Decimal
    }
    type alias Group as String
    type UserAuthorization {
        groups : Groups as Group[]
    }

// Processors disabled: https://gitlab.com/vyne/vyne/issues/52
//    policy TradeCounterpartyPolicy against Trade {
//        read {
//            process using vyne.StringAttributeMasker(['counterParty'])
//        }
//    }

    policy TradeDeskPolicy against Trade {
        read external {
            case caller.DeskId == this.test.DeskId -> permit
            case caller.Groups in ["ADMIN","COMPLIANCE"] -> permit
            case caller.DeskId != this.DeskId -> filter (counterPartyName , rate)
            else -> filter
        }
        read internal {
            permit
        }
        write {
            case caller.DeskId == this.DeskId -> permit
            case caller.Groups in ["ADMIN","COMPLIANCE"] -> permit
            case caller.DeskId != this.DeskId -> filter
            else -> filter
        }
    }
}
        """.trimIndent()
        doc = Compiler(taxiDef).compile()
    }

    @Test
    fun canCompileBasicAccessPolicy() {
        expect(doc.policies).to.have.size(1)
        expect(doc.containsPolicy("test.TradeDeskPolicy")).to.be.`true`
        val policy = doc.policy("test.TradeDeskPolicy")
        expect(policy).to.be.not.`null`

        expect(policy.targetType.qualifiedName).to.equal("test.Trade")
        expect(policy.ruleSets).to.have.size(3)
        val ruleSet = policy.ruleSets[0]
        expect(ruleSet.statements).to.have.size(4)
        expect(ruleSet.scope.operationType).to.equal("read")
        expect(ruleSet.scope.policyOperationScope).to.equal(PolicyOperationScope.EXTERNAL)
        expect(policy.ruleSets[0].statements).to.have.size(4)

        val statement1 = ruleSet.statements[0]
        val condition1 = statement1.condition as CaseCondition
        val lhSubject = condition1.lhSubject as RelativeSubject
        expect(lhSubject.source).to.equal(RelativeSubject.RelativeSubjectSource.CALLER)
        expect(lhSubject.targetType.qualifiedName).to.equal("test.DeskId")
        expect(condition1.operator).to.equal(Operator.EQUAL)

        expect(statement1.instruction.type).to.equal(Instruction.InstructionType.PERMIT)

        val statement2 = ruleSet.statements[1]
        val condition2 = statement2.condition as CaseCondition
        val anyOf = condition2.rhSubject as LiteralArraySubject
        expect(anyOf.values).to.equal(listOf("ADMIN", "COMPLIANCE"))

        // TODO assert the actual policies have been parsed correctly
    }

    @Test
    fun when_noCaseStatement_then_elseStatementIsDefined() {
        val policy = doc.policy("test.TradeDeskPolicy")
        val ruleSet = policy.ruleSets.first { it.scope.operationType == "read" && it.scope.policyOperationScope == PolicyOperationScope.INTERNAL_AND_EXTERNAL }
        expect(ruleSet.statements).to.have.size(1)
        expect(ruleSet.statements.first().condition).to.be.instanceof(ElseCondition::class.java)

    }

    @Test
    fun whenScopeIsNotDefined_then_itDefaultsToInternalExternal() {
        val policy = doc.policy("test.TradeDeskPolicy")
        val ruleSet = policy.ruleSets.first { it.scope.operationType == "write" }
        expect(ruleSet.scope.policyOperationScope).to.equal(PolicyOperationScope.INTERNAL_AND_EXTERNAL)
    }

    @Test
    fun parsesFilterInstructionsCorrectly() {
        val policy = doc.policy("test.TradeDeskPolicy")
        val ruleSet = policy.ruleSets.first { it.scope.operationType == "read" && it.scope.policyOperationScope == PolicyOperationScope.EXTERNAL }
        val filterAllStatement = ruleSet.statements[3]
        val filterAllInstruction = filterAllStatement.instruction as FilterInstruction
        expect(filterAllInstruction.isFilterAll).to.be.`true`
        expect(filterAllInstruction.fieldNames).to.be.empty

        val filterAttributesStatement = ruleSet.statements[2]
        val filterAttributesInstruction = filterAttributesStatement.instruction as FilterInstruction
        expect(filterAttributesInstruction.isFilterAll).to.be.`false`
        expect(filterAttributesInstruction.fieldNames).to.contain.elements("rate","counterPartyName")
    }

    // Processors commented out for now:
    // https://gitlab.com/vyne/vyne/issues/52
//    @Test
//    fun parsesProcessorDetailsCorrectly() {
//        val policy = doc.policy("test.TradeCounterpartyPolicy")
//        val ruleSet = policy.ruleSets.first { it.scope.operationType == "read" }
//        val instruction = ruleSet.statements.first().instruction
//        expect(instruction.type).to.equal(Instruction.InstructionType.PROCESS)
//        expect(instruction.processor!!.name).to.equal("vyne.StringAttributeMasker")
//        expect(instruction.processor!!.args).to.have.size(1)
//        val arg = instruction.processor!!.args.first()
//        expect(arg).to.equal(listOf("counterParty"))
//    }
}

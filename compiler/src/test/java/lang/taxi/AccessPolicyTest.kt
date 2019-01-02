package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.policies.*
import org.junit.Before
import org.junit.Test

class AccessPolicyTest {

    lateinit var doc: TaxiDocument
    @Before
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
    }
    type alias Group as String
    type UserAuthorization {
        groups : Groups as Group[]
    }


    policy TradeDeskPolicy against Trade {
        read external {
            case caller.DeskId = this.DeskId -> permit
            case caller.Groups in ["ADMIN","COMPLIANCE"] -> permit
            case caller.DeskId != this.DeskId -> process using MaskTradeDetailsProcessor
            else -> deny
        }
        read internal {
            permit
        }
        write {
            case caller.DeskId = this.DeskId -> permit
            case caller.Groups in ["ADMIN","COMPLIANCE"] -> permit
            case caller.DeskId != this.DeskId -> process using MaskTradeDetailsProcessor
            else -> deny
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
        expect(ruleSet.scope.operationScope).to.equal(OperationScope.EXTERNAL)
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
        expect(anyOf.values).to.equal(listOf("ADMIN","COMPLIANCE"))

        // TODO assert the actual policies have been parsed correctly
    }

    @Test
    fun when_noCaseStatement_then_elseStatementIsDefined() {
        val policy = doc.policy("test.TradeDeskPolicy")
        val ruleSet = policy.ruleSets.first { it.scope.operationType == "read" && it.scope.operationScope == OperationScope.INTERNAL_AND_EXTERNAL}
        expect(ruleSet.statements).to.have.size(1)
        expect(ruleSet.statements.first().condition).to.be.instanceof(ElseCondition::class.java)

    }
    @Test
    fun whenScopeIsNotDefined_then_itDefaultsToInternalExternal() {
        val policy = doc.policy("test.TradeDeskPolicy")
        val ruleSet = policy.ruleSets.first { it.scope.operationType == "write" }
        expect(ruleSet.scope.operationScope).to.equal(OperationScope.INTERNAL_AND_EXTERNAL)
    }
}
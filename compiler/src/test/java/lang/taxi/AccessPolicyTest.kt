package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.policies.Instruction
import org.junit.Test

class AccessPolicyTest {
    @Test
    fun canCompileBasicAccessPolicy() {
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
        case caller.DeskId = this.DeskId -> permit
        case caller.Groups = anyOf("ADMIN","COMPLIANCE") -> permit
        case caller.DeskId != this.DeskId -> process using MaskTradeDetailsProcessor
        else -> deny
    }
}
        """.trimIndent()
        val doc = Compiler(taxiDef).compile()
        expect(doc.policies).to.have.size(1)
        expect(doc.containsPolicy("test.TradeDeskPolicy")).to.be.`true`
        val policy = doc.policy("test.TradeDeskPolicy")
        expect(policy).to.be.not.`null`

        expect(policy.targetType.qualifiedName).to.equal("test.Trade")
        expect(policy.statements).to.have.size(4)

        // TODO assert the actual policies have been parsed correctly
    }
}
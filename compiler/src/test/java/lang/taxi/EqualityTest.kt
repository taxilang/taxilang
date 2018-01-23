package lang.taxi

import com.winterbe.expekt.expect
import org.junit.Test

class EqualityTest {

    data class TestMember(val firstName:String?, val lastName:String?, val age:Int)  {
        val equality = Equality(this,TestMember::firstName, TestMember::lastName)
        override fun equals(other: Any?): Boolean = equality.isEqualTo(other)
        override fun hashCode(): Int = equality.hash()
    }
    @Test
    fun given_nullsInFieldsTested_then_equalsReturnsCorrectly() {
        expectEqual(TestMember("A","B",10),TestMember("A","B",10))
        expectEqual(TestMember("A","B",10),TestMember("A","B",20))
        expectNotEqual(TestMember("A","B",10),TestMember(null,"B",20))
        expectNotEqual(TestMember("A","B",10),TestMember("A",null,20))
        expectNotEqual(TestMember("A","B",10),null)
        expectNotEqual(TestMember(null,"B",10),TestMember("A","B",20))
        expectNotEqual(TestMember("A",null,10),TestMember("A","B",20))
    }


    private fun expectEqual(first:Any,second:Any) {
        expect(first).to.equal(second)
        expect(first.hashCode()).to.equal(second.hashCode())
    }
    private fun expectNotEqual(first:Any,second:Any?) {
        expect(first).not.to.equal(second)
        if (second != null) {
            expect(first.hashCode()).not.to.equal(second.hashCode())
        }

    }
}
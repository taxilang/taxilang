package lang.taxi

import com.winterbe.expekt.expect
import org.junit.Assert.*
import org.junit.Test

class QualifiedNameTest {

    @Test
    fun generatesCorrectly() {
        expect(QualifiedName.from("Foo").toString()).to.equal("Foo")
        expect(QualifiedName.from("bar.Foo").toString()).to.equal("bar.Foo")
    }
}
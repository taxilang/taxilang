package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.types.QualifiedName
import org.junit.jupiter.api.Test

class QualifiedNameTest {

    @Test
    fun generatesCorrectly() {
        expect(QualifiedName.from("Foo").toString()).to.equal("Foo")
        expect(QualifiedName.from("bar.Foo").toString()).to.equal("bar.Foo")
    }
}

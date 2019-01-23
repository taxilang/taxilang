package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.annotations.DataType
import org.junit.Test

class TypeNamesTest {
    @Test
    fun given_typeIsCollection_then_arrayTypeNameIsGenerated() {
        val typeName = TypeNames.deriveTypeName<List<Book>>()
        expect(typeName).to.equal("foo.Book[]")
    }

    @Test
    fun given_typeIsNotCollection_then_canUseInlineApproach() {
        val typeName = TypeNames.deriveTypeName<Book>()
        expect(typeName).to.equal("foo.Book")
    }
}

@DataType("foo.Book")
data class Book(val title: String)
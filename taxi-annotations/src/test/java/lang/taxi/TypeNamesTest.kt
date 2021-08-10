package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.annotations.DataType
import org.junit.jupiter.api.Test

class TypeNamesTest {
    @Test
    fun given_typeIsCollection_then_arrayTypeNameIsGenerated() {
        val typeName = TypeNames.deriveTypeName<List<Book>>()
        // Note: this test used to assert the value was foo.Book[].
        // I've swapped to the parametrized name, as this seems more correct.
        // However, swap back and note why if there are issues.
        expect(typeName).to.equal("lang.taxi.Array<foo.Book>")
    }

    @Test
    fun given_typeIsNotCollection_then_canUseInlineApproach() {
        val typeName = TypeNames.deriveTypeName<Book>()
        expect(typeName).to.equal("foo.Book")
    }
}

@DataType("foo.Book")
data class Book(val title: String)

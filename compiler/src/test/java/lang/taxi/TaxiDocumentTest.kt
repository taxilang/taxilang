package lang.taxi

import com.winterbe.expekt.expect
import org.junit.Test

class TaxiDocumentTest {

    @Test
    fun when_mergingDocuments_then_typesAndServicesAreCombined() {
        val src1 = """
            type Person {
               email : Email as String
            }""".compile()
        val src2 = """
            type Author {
               name : Name as String
            }""".compile()
        val doc = src1.merge(src2)
        expect(doc.containsType("Person")).to.be.`true`
        expect(doc.containsType("Author")).to.be.`true`
    }

    @Test
    fun given_documentsContainingDuplicateDefinitions_when_mergingDocuments_then_typesAndServicesAreCombined() {
        val src1 = """
            type Person {
               email : Email as String
            }
            type Animal {
               species : Species as String
            }
            """.compile()
        val src2 = """
            type Person {
               email : Email as String
            }
            type Author {
               name : Name as String
           }
            """.compile()
        val doc = src1.merge(src2)
        expect(doc.containsType("Person")).to.be.`true`
        expect(doc.containsType("Animal")).to.be.`true`
        expect(doc.containsType("Author")).to.be.`true`
    }

    @Test(expected = DocumentMalformedException::class)
    fun given_documentsContainingConflictingDefinitions_when_mergingDocuments_then_exceptionIsThrown() {
        val src1 = """
            type Person {
               email : Email as String
            }
            """.compile()
        val src2 = """
            type Person {
               name : Name as String
            }
            """.compile()
        src1.merge(src2)
    }

    fun String.compile(): TaxiDocument {
        return Compiler(this).compile()
    }

}
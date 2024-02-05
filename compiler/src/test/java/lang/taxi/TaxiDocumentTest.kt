package lang.taxi

import com.winterbe.expekt.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TaxiDocumentTest {

    @Test
    fun when_mergingDocuments_then_typesAndServicesAreCombined() {
        val src1 = """
            type Person {
               email : Email inherits String
            }""".compile()
        val src2 = """
            type Author {
               name : Name inherits String
            }""".compile()
        val doc = src1.merge(src2)
        expect(doc.containsType("Person")).to.be.`true`
        expect(doc.containsType("Author")).to.be.`true`
    }

    @Test
    fun given_documentsContainingDuplicateDefinitions_when_mergingDocuments_then_typesAndServicesAreCombined() {
        val src1 = """
            type Person {
               email : Email inherits String
            }
            type Animal {
               species : Species inherits String
            }
            """.compile()
        val src2 = """
            type Person {
               email : Email inherits String
            }
            type Author {
               name : Name inherits String
           }
            """.compile()
        val doc = src1.merge(src2)
        expect(doc.containsType("Person")).to.be.`true`
        expect(doc.containsType("Animal")).to.be.`true`
        expect(doc.containsType("Author")).to.be.`true`
    }

    @Test
    fun given_documentsContainingConflictingDefinitions_when_mergingDocuments_then_exceptionIsThrown() {
        val src1 = """
            type Person {
               email : Email inherits String
            }
            """.compile()
        val src2 = """
            type Person {
               name : Name inherits String
            }
            """.compile()
        assertThrows<DocumentMalformedException> {
           src1.merge(src2)
        }
    }

    fun String.compile(): TaxiDocument {
        return Compiler(this).compile()
    }

}

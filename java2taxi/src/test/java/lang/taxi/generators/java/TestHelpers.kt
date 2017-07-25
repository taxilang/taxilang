package lang.taxi.generators.java

import com.winterbe.expekt.expect
import lang.taxi.Compiler

object TestHelpers {
    fun expectToCompileTheSame(generated: List<String>, expected: String) {
        return expectToCompileTheSame(generated, listOf(expected))
    }
    fun expectToCompileTheSame(generated: List<String>, expected: List<String>) {
        val generatedDoc = Compiler.fromStrings(generated).compile()
        val expectedDoc = Compiler.fromStrings(expected).compile()
        expect(generatedDoc).to.equal(expectedDoc)
    }
}

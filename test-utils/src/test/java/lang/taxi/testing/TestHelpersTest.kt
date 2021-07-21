package lang.taxi.testing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

class TestHelpersTest {

   @Test
   fun `expectToCompileTheSame fails if the generated input is empty`() {

      val assertionError = catchThrowable {
         TestHelpers.expectToCompileTheSame(
            generated = listOf(""),
            expected = listOf("""
               namespace foo
               type bar
               """.trimIndent()
            ),
         )
      }
      assertThat(assertionError).hasMessage("""
         Generated docs did not match expected.  Errors:
         Type foo.bar not present
      """.trimIndent())
   }
   @Test
   fun `expectToCompileTheSame fails if the expected input is empty`() {

      val assertionError = catchThrowable {
         TestHelpers.expectToCompileTheSame(
            generated = listOf("""
               namespace foo
               type bar
               """.trimIndent()
            ),
            expected = listOf("")
         )
      }
      assertThat(assertionError).hasMessage("""
         Generated docs did not match expected.  Errors:
         Type foo.bar not present
      """.trimIndent())
   }

   @Test
   fun `expectToCompileTheSame fails if the expected input has no type`() {

      val assertionError = catchThrowable {
         TestHelpers.expectToCompileTheSame(
            generated = listOf("""
               namespace foo
               type bar
               """.trimIndent()
            ),
            expected = listOf("""
               namespace foo
            """.trimIndent())
         )
      }
      assertThat(assertionError).hasMessage("""
         Generated docs did not match expected.  Errors:
         Type foo.bar not present
      """.trimIndent())
   }

   @Test
   fun `expectToCompileTheSame fails if the expected input has too many types`() {

      val assertionError = catchThrowable {
         TestHelpers.expectToCompileTheSame(
            generated = listOf("""
               namespace foo
               type One
               """.trimIndent()
            ),
            expected = listOf("""
               namespace foo
               type One
               type Two
            """.trimIndent())
         )
      }
      assertThat(assertionError).hasMessage("""
         Generated docs did not match expected.  Errors:
         Type foo.Two not present
      """.trimIndent())
   }
}

package lang.taxi.testing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TestHelpersTest {

   @Test
   fun `expectToCompileTheSame fails if the generated input is empty`() {

      val assertionError = assertThrows<AssertionError> {
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

         Generated:

      """.trimIndent())
   }
   @Test
   fun `expectToCompileTheSame fails if the expected input is empty`() {

      val assertionError = assertThrows<AssertionError> {
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
         Type foo.bar was present but not expected

         Generated:
         namespace foo
         type bar
      """.trimIndent())
   }

   @Test
   fun `expectToCompileTheSame fails if the expected input has no type`() {

      val assertionError = assertThrows<AssertionError> {
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
         Type foo.bar was present but not expected

         Generated:
         namespace foo
         type bar
      """.trimIndent())
   }

   @Test
   fun `expectToCompileTheSame fails if the expected input has too many types`() {

      val assertionError = assertThrows<AssertionError> {
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

         Generated:
         namespace foo
         type One
      """.trimIndent())
   }

   @Test
   fun `expectToCompileTheSame fails if the services have different annotations`() {

      val assertionError = assertThrows<AssertionError> {
         TestHelpers.expectToCompileTheSame(
            generated = listOf("""
               namespace foo
               @SomeAnnotation(1)
               service bar {}
               """.trimIndent()
            ),
            expected = listOf("""
               namespace foo
               @SomeAnnotation(2)
               service bar {}
            """.trimIndent())
         )
      }
      assertThat(assertionError).hasMessage("""
         Generated docs did not match expected.  Errors:
         Parameter value in annotation SomeAnnotation on element foo.bar does not have the expected value:  Expected: 1 Actual: 2

         Generated:
         namespace foo
         @SomeAnnotation(1)
         service bar {}
         """.trimIndent())
   }
}

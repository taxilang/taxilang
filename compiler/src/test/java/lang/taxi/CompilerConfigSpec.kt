package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.linter.LinterRuleConfiguration
import lang.taxi.linter.TaxiConfLinterRuleConfig
import lang.taxi.messages.Severity
import lang.taxi.packages.CompilerOptions
import lang.taxi.packages.TaxiPackageProject

class CompilerConfigSpec : DescribeSpec({

   describe("TaxiPackageProject.buildCompilerConfig extension") {

      it("should build CompilerConfig with default compiler options") {
         val project = TaxiPackageProject(
            name = "foo/test-project",
            version = "1.0.0"
         )

         val config = project.buildCompilerConfig()

         config.compilerOptions shouldBe CompilerOptions.DEFAULT
         config.compilerOptions.duplicateDefinitionSeverity shouldBe Severity.ERROR
      }

      it("should build CompilerConfig with custom compiler options") {
         val project = TaxiPackageProject(
            name = "foo/test-project",
            version = "1.0.0",
            compilerOptions = CompilerOptions(
               duplicateDefinitionSeverity = Severity.WARNING
            )
         )

         val config = project.buildCompilerConfig()

         config.compilerOptions.duplicateDefinitionSeverity shouldBe Severity.WARNING
      }

      it("should include linter configuration from project") {
         val project = TaxiPackageProject(
            name = "foo/test-project",
            version = "1.0.0",
            linter = mapOf(
               "some-rule" to TaxiConfLinterRuleConfig(
                  enabled = true,
                  severity = Severity.ERROR
               )
            )
         )

         val config = project.buildCompilerConfig()

         // The linter rules should be converted and included
         config.linterRuleConfiguration.isNotEmpty() shouldBe true
      }

      it("should combine both compiler options and linter configuration") {
         val project = TaxiPackageProject(
            name = "foo/test-project",
            version = "1.0.0",
            compilerOptions = CompilerOptions(
               duplicateDefinitionSeverity = Severity.INFO
            ),
            linter = mapOf(
               "some-rule" to TaxiConfLinterRuleConfig(
                  enabled = true,
                  severity = Severity.WARNING
               )
            )
         )

         val config = project.buildCompilerConfig()

         config.compilerOptions.duplicateDefinitionSeverity shouldBe Severity.INFO
         config.linterRuleConfiguration.isNotEmpty() shouldBe true
      }

      it("should allow configuring unknown annotation severity") {
         val source = """
            @UnknownAnnotation
            type Person { name: String }
         """.trimIndent()

         // Default behavior (ERROR)
         Compiler(source).compile().errors.size shouldBe 1

         // Configured as WARNING
         val warningConfig = CompilerConfig(
            compilerOptions = CompilerOptions(unknownAnnotationSeverity = Severity.WARNING)
         )
         Compiler(source, warningConfig).compile().let { result ->
            result.errors.size shouldBe 1
            result.errors.first().severity shouldBe Severity.WARNING
         }

         // Configured as INFO
         val infoConfig = CompilerConfig(
            compilerOptions = CompilerOptions(unknownAnnotationSeverity = Severity.INFO)
         )
         Compiler(source, infoConfig).compile().let { result ->
            result.errors.size shouldBe 1
            result.errors.first().severity shouldBe Severity.INFO
         }
      }
   }
})

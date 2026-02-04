package lang.taxi.lsp.taxiconf

import lang.taxi.lsp.hocon.HoconValidator
import lang.taxi.packages.TaxiPackageProject
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

/**
 * Validator for taxi.conf files.
 *
 * Validates TaxiPackageProject configurations beyond what config4k can check.
 */
class TaxiConfValidator : HoconValidator<TaxiPackageProject> {

   override fun validate(value: TaxiPackageProject, diagnostics: MutableList<Diagnostic>) {
      // Validate required fields
      if (value.name.isBlank()) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Field 'name' is required",
            DiagnosticSeverity.Error,
            "taxi.conf"
         ))
      }

      if (value.version.isBlank()) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Field 'version' is required",
            DiagnosticSeverity.Error,
            "taxi.conf"
         ))
      }

      // Validate version format (basic check)
      if (!isValidVersion(value.version)) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Invalid version format: '${value.version}'. Expected format: X.Y.Z or X.Y.Z-SNAPSHOT",
            DiagnosticSeverity.Warning,
            "taxi.conf"
         ))
      }

      // Validate project name format
      if (!isValidProjectName(value.name)) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Invalid project name format: '${value.name}'. Expected format: namespace/project-name",
            DiagnosticSeverity.Warning,
            "taxi.conf"
         ))
      }
   }

   private fun isValidVersion(version: String): Boolean {
      // Basic version validation: X.Y.Z or X.Y.Z-SNAPSHOT
      val versionPattern = Regex("""^\d+\.\d+\.\d+(-SNAPSHOT|-[a-zA-Z0-9]+)?$""")
      return versionPattern.matches(version)
   }

   private fun isValidProjectName(name: String): Boolean {
      // Project name should be in format: namespace/project-name
      // Allow dots in namespace (e.g., org.taxilang/project)
      val namePattern = Regex("""^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$""")
      return namePattern.matches(name)
   }
}

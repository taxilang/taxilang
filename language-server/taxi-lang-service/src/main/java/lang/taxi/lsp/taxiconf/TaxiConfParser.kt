package lang.taxi.lsp.taxiconf

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import lang.taxi.packages.TaxiPackageProject
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.io.StringReader

/**
 * Parses and validates taxi.conf files, providing diagnostics for errors.
 */
class TaxiConfParser {

   data class ParseResult(
      val project: TaxiPackageProject?,
      val diagnostics: List<Diagnostic>,
      val config: Config? = null
   )

   /**
    * Parse a taxi.conf file content and return diagnostics
    */
   fun parse(content: String): ParseResult {
      val diagnostics = mutableListOf<Diagnostic>()

      try {
         // First, try to parse the HOCON configuration
         val config = ConfigFactory.parseString(content)

         // Try to extract into TaxiPackageProject
         try {
            val project = config.extract<TaxiPackageProject>()

            // Validate required fields
            validateRequiredFields(project, diagnostics)

            return ParseResult(project, diagnostics, config)
         } catch (e: Exception) {
            // Extract failed - try to provide helpful diagnostics
            val diagnostic = createDiagnosticFromException(e, content)
            diagnostics.add(diagnostic)
            return ParseResult(null, diagnostics, config)
         }
      } catch (e: ConfigException.Parse) {
         // Parse error - create diagnostic with position information
         val diagnostic = Diagnostic(
            extractRangeFromParseException(e),
            e.message ?: "Configuration parse error",
            DiagnosticSeverity.Error,
            "taxi.conf"
         )
         diagnostics.add(diagnostic)
         return ParseResult(null, diagnostics)
      } catch (e: Exception) {
         // Other configuration errors
         val diagnostic = Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            e.message ?: "Configuration error",
            DiagnosticSeverity.Error,
            "taxi.conf"
         )
         diagnostics.add(diagnostic)
         return ParseResult(null, diagnostics)
      }
   }

   private fun validateRequiredFields(project: TaxiPackageProject, diagnostics: MutableList<Diagnostic>) {
      // name and version are required
      if (project.name.isBlank()) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Field 'name' is required",
            DiagnosticSeverity.Error,
            "taxi.conf"
         ))
      }

      if (project.version.isBlank()) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Field 'version' is required",
            DiagnosticSeverity.Error,
            "taxi.conf"
         ))
      }

      // Validate version format (basic check)
      if (!isValidVersion(project.version)) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Invalid version format: '${project.version}'. Expected format: X.Y.Z or X.Y.Z-SNAPSHOT",
            DiagnosticSeverity.Warning,
            "taxi.conf"
         ))
      }

      // Validate project name format
      if (!isValidProjectName(project.name)) {
         diagnostics.add(Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Invalid project name format: '${project.name}'. Expected format: namespace/project-name",
            DiagnosticSeverity.Warning,
            "taxi.conf"
         ))
      }
   }

   private fun isValidVersion(version: String): Boolean {
      // Basic version validation: X.Y.Z or X.Y.Z-SNAPSHOT
      val versionPattern = Regex("""^\d+\.\d+\.\d+(-SNAPSHOT|-[a-zA-Z0-9]+)?${'$'}""")
      return versionPattern.matches(version)
   }

   private fun isValidProjectName(name: String): Boolean {
      // Project name should be in format: namespace/project-name
      // Allow dots in namespace (e.g., org.taxilang/project)
      val namePattern = Regex("""^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+${'$'}""")
      return namePattern.matches(name)
   }

   private fun createDiagnosticFromException(e: Exception, content: String): Diagnostic {
      val message = e.message ?: "Configuration extraction error"

      // Try to extract field name from error message
      val fieldPattern = Regex("""No configuration setting found for key '([^']+)'""")
      val match = fieldPattern.find(message)

      if (match != null) {
         val fieldName = match.groupValues[1]
         return Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Missing required field: '$fieldName'",
            DiagnosticSeverity.Error,
            "taxi.conf"
         )
      }

      return Diagnostic(
         Range(Position(0, 0), Position(0, 0)),
         message,
         DiagnosticSeverity.Error,
         "taxi.conf"
      )
   }

   private fun extractRangeFromParseException(e: ConfigException.Parse): Range {
      // ConfigException.Parse contains line number information
      val origin = e.origin()
      val lineNumber = (origin?.lineNumber() ?: 1) - 1 // LSP is 0-based

      return Range(
         Position(lineNumber.coerceAtLeast(0), 0),
         Position(lineNumber.coerceAtLeast(0), 100) // Approximate end position
      )
   }
}

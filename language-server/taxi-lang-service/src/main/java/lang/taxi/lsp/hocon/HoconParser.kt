package lang.taxi.lsp.hocon

import com.google.common.base.Throwables
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import kotlin.reflect.KClass

/**
 * Generic HOCON parser and validator.
 *
 * Parses HOCON configuration files and validates them against a Kotlin data class type.
 * Uses actual parsing with TypeSafe Config and config4k to ensure diagnostics match
 * real parsing behavior across all tools (VSCode, CLI, etc.).
 *
 * @param T The Kotlin type representing the expected HOCON structure
 */
abstract class HoconParser<T : Any>(
   private val validator: HoconValidator<T>? = null
) {
   abstract fun extract(config: Config): T

   data class ParseResult<T>(
      val value: T?,
      val diagnostics: List<Diagnostic>,
      val config: Config? = null
   )

   /**
    * Parse HOCON content and return the result with diagnostics
    */
   fun parse(content: String, sourceName: String = "hocon"): ParseResult<T> {
      val diagnostics = mutableListOf<Diagnostic>()

      try {
         // First, parse the HOCON configuration
         val config = ConfigFactory.parseString(content)

         // Try to extract into the target type
         try {
            val value = extract(config)

            // Run custom validation if provided
            validator?.validate(value, diagnostics)

            return ParseResult(value, diagnostics, config)
         } catch (e: Exception) {
            // Extraction failed - create diagnostic from exception
            val diagnostic = createDiagnosticFromException(e, content, sourceName)
            diagnostics.add(diagnostic)
            return ParseResult(null, diagnostics, config)
         }
      } catch (e: ConfigException.Parse) {
         // Parse error - create diagnostic with position information
         val diagnostic = Diagnostic(
            extractRangeFromParseException(e),
            e.message ?: "Configuration parse error",
            DiagnosticSeverity.Error,
            sourceName
         )
         diagnostics.add(diagnostic)
         return ParseResult(null, diagnostics)
      } catch (e: ConfigException) {
         // Other configuration errors
         val diagnostic = Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            e.message ?: "Configuration error: ${e.javaClass.simpleName}",
            DiagnosticSeverity.Error,
            sourceName
         )
         diagnostics.add(diagnostic)
         return ParseResult(null, diagnostics)
      } catch (e: Exception) {
         // Unexpected errors
         val diagnostic = Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            e.message ?: "Unexpected error: ${e.javaClass.simpleName}",
            DiagnosticSeverity.Error,
            sourceName
         )
         diagnostics.add(diagnostic)
         return ParseResult(null, diagnostics)
      }
   }

   private fun createDiagnosticFromException(e: Exception, content: String, sourceName: String): Diagnostic {
      val rootCause = Throwables.getRootCause(e)
      val message = rootCause.message ?: "Could not parse $sourceName"

      // Try to extract field name from error message
      val fieldPattern = Regex("""No configuration setting found for key '([^']+)'""")
      val match = fieldPattern.find(message)

      if (match != null) {
         val fieldName = match.groupValues[1]
         return Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Missing required field: '$fieldName'",
            DiagnosticSeverity.Error,
            sourceName
         )
      }

      // Try to extract type mismatch information
      val typeMismatchPattern = Regex("""Cannot convert.*to.*""")
      if (typeMismatchPattern.containsMatchIn(message)) {
         return Diagnostic(
            Range(Position(0, 0), Position(0, 0)),
            "Type mismatch: $message",
            DiagnosticSeverity.Error,
            sourceName
         )
      }

      return Diagnostic(
         Range(Position(0, 0), Position(0, 0)),
         message,
         DiagnosticSeverity.Error,
         sourceName
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

/**
 * Interface for custom validation logic beyond what config4k provides.
 *
 * Implement this to add domain-specific validation rules.
 */
interface HoconValidator<T : Any> {
   /**
    * Validate the parsed configuration and add diagnostics for any issues.
    *
    * @param value The successfully parsed configuration object
    * @param diagnostics Mutable list to add diagnostics to
    */
   fun validate(value: T, diagnostics: MutableList<Diagnostic>)
}

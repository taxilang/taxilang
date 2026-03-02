package lang.taxi.packages

import lang.taxi.messages.Severity

/**
 * Configuration options for the Taxi compiler.
 *
 * These options control various aspects of compilation behavior,
 * including error severity levels and validation rules.
 */
@kotlinx.serialization.Serializable
data class CompilerOptions(
   /**
    * Specifies the severity level for duplicate type and service definitions.
    *
    * When multiple declarations of the same type or service (by fully qualified name)
    * are found, this setting determines how the compiler should respond:
    * - ERROR (default): Compilation fails with an error
    * - WARNING: Compilation continues with a warning message
    * - INFO: Compilation continues with an informational message
    *
    * Note: Service extensions are always allowed regardless of this setting.
    * This only affects duplicate declarations, not extensions.
    */
   val duplicateDefinitionSeverity: Severity = Severity.ERROR
) {
   companion object {
      /**
       * Default compiler options with standard settings.
       */
      val DEFAULT = CompilerOptions()

      fun merge(a: CompilerOptions, b: CompilerOptions): CompilerOptions {
         return CompilerOptions(
            duplicateDefinitionSeverity = minOf(a.duplicateDefinitionSeverity, b.duplicateDefinitionSeverity)
         )
      }
   }
}

package lang.taxi.packages

import lang.taxi.messages.Severity

/**
 * Configuration options for the Taxi compiler.
 *
 * These options control various aspects of compilation behavior,
 * including error severity levels and validation rules.
 */
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
   val duplicateDefinitionSeverity: Severity = Severity.ERROR,

   /**
    * Specifies the severity level for unknown annotation types.
    *
    * When an annotation is encountered that cannot be resolved to a known annotation type,
    * this setting determines how the compiler should respond:
    * - ERROR (default): Compilation fails with an error
    * - WARNING: Compilation continues with a warning message
    * - INFO: Compilation continues with an informational message
    *
    * This is a breaking change from previous behavior where unknown annotations
    * were silently treated as dynamic annotations.
    */
   val unknownAnnotationSeverity: Severity = Severity.ERROR
) {
   companion object {
      /**
       * Default compiler options with standard settings.
       */
      val DEFAULT = CompilerOptions()
   }
}

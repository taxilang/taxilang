package lang.taxi.toggles

enum class FeatureToggle {
   ENABLED,
   DISABLED,

   /**
    * Turn a feature on, but not to generate any breaking behaviour.
    * For example, generate compiler warnings, rather than errors.
    */
   SOFT_ENABLED
}

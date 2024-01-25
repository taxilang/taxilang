package org.taxilang.packagemanager.layout

enum class TaxiArtifactType(val extension: String) {

   /**
    * Indicates a request for the project model.
    * Maven has this baked in as pom, which is difficult for us to change.
    * For Taxi - this is translated to taxi.conf file
    */
   TAXI_CONF_FILE("pom"),

   /**
    * Indicates a request for the actual Taxi project, including all it's
    * sources.
    */
   TAXI_PROJECT_BUNDLE("bundle")
}

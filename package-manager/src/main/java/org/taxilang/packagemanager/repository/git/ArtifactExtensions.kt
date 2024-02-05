package org.taxilang.packagemanager.repository.git

import org.eclipse.aether.artifact.Artifact


object ArtifactExtensions {

   /**
    * We store a reference to the requested version that was originally in the taxi.conf file.
    * This tells us where to load the content from, but gets lost after we've actually resolved
    * to a real version (in the GitVersionResolver)
    */
   const val REQUESTED_VERSION = "requestedVersion"
}

val Artifact.requestedVersion: String
   get() {
      return this.properties[ArtifactExtensions.REQUESTED_VERSION]
         ?: error("Artifact was not initialized properly - requestedVersion is missing")
   }

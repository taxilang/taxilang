package org.taxilang.packagemanager.layout

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer
import org.eclipse.aether.internal.impl.LocalPathComposer
import org.taxilang.packagemanager.transports.changeExtension
import java.nio.file.Paths

/**
 * Responsible for providing a path when resolving an artifact against the local cache.
 *
 * We defer all work to the "normal" LocalPathComposer (DefaultLocalPathComposer),
 * but swap out file names to the taxi equivalent
 */
class TaxiLocalPathComposer(
   val fallback: LocalPathComposer = DefaultLocalPathComposer()
) : LocalPathComposer by fallback {

   override fun getPathForArtifact(artifact: Artifact, local: Boolean): String {
      val path = fallback.getPathForArtifact(artifact, local)
      return when (artifact.extension) {
         TaxiArtifactType.TAXI_CONF_FILE.extension -> {
            Paths.get(path)
               .resolveSibling("taxi.conf")
               .toString()
         }

         TaxiArtifactType.TAXI_PROJECT_BUNDLE.extension -> {
            Paths.get(path).changeExtension("zip").toString()
         }

         else -> path
      }
   }
}

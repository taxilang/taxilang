package org.taxilang.packagemanager.transports

import lang.taxi.packages.PackageIdentifier
import java.nio.file.Path
import java.nio.file.Paths

object TaxiFileSystemUtils {
   fun projectPath(baseRepo: Path, identifier: PackageIdentifier):Path {
      val orgPath = identifier.name.organisation
         .split(".").joinToString("/").let {
            baseRepo.resolve(Paths.get(it))
         }

      val path = orgPath.resolve(Paths.get(identifier.name.name, identifier.version.toString()))
      path.toFile().mkdirs()
      return path
   }
}

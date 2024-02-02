package lang.taxi.packages

import lang.taxi.packages.utils.log
import lang.taxi.sources.SourceCode
import java.nio.file.Files
import java.nio.file.Path

@Deprecated("Use PackageManager instead")
class PackageImporter(
   private val importerConfig: ImporterConfig,
   private val downloaderFactory: PackageDownloaderFactory = PackageDownloaderFactory(importerConfig)
) {

   fun fetchDependencies(projectConfig: TaxiPackageProject): Set<PackageSource> {
      return projectConfig.dependencyPackages.flatMap { fetchDependency(it, projectConfig) }.toSet()
   }

   private fun fetchDependency(identifier: PackageIdentifier, projectConfig: TaxiPackageProject): Set<PackageSource> {
      val fromLocal = fetchFromLocalRepo(identifier)
      return if (fromLocal != null) {
         fromLocal
      } else {
         val downloaded = downloaderFactory.create(projectConfig).download(identifier)
         if (downloaded) {
            fetchFromLocalRepo(identifier) ?: error("Content was not found despite successfully downloading")
         } else {
            error("Could not download ${identifier.id} from any repositories")
         }
      }
   }

   private fun fetchFromLocalRepo(identifier: PackageIdentifier): Set<PackageSource>? {
      val localPath = identifier.localFolder(this.importerConfig.localCache)
      return if (Files.exists(localPath)) {
         localPath.toFile()
            .walk()
            .filter { it.extension == "taxi" }
            .map { sourceFile ->
               PackageSource(identifier, sourceFile.nameWithoutExtension, sourceFile.toPath(), sourceFile.readText())
            }
            .toSet()
      } else {
         log().info("${identifier.id} not found locally at $localPath")
         null
      }
   }
}

fun PackageIdentifier.localFolder(localCache: Path): Path {
   return localCache.resolve(this.name.organisation)
      .resolve(this.name.name)
      .resolve(this.version.toString())
}

class PackageSource(val project: PackageIdentifier, val fileName: String, val path:Path, val contents: String) {
   val source: SourceCode = SourceCode(path.toFile().canonicalPath, contents, path)
}

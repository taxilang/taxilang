package lang.taxi.packages

import lang.taxi.packages.utils.log
import java.nio.file.Files
import java.nio.file.Path

class PackageImporter(private val importerConfig: ImporterConfig, private val downloaderFactory: PackageDownloaderFactory = PackageDownloaderFactory(importerConfig)) {

   fun fetchDependencies(projectConfig: ProjectConfig): Set<PackageSource> {
      return projectConfig.dependencyPackages.flatMap { fetchDependency(it,projectConfig) }.toSet()
   }

   private fun fetchDependency(identifier: PackageIdentifier, projectConfig: ProjectConfig): Set<PackageSource> {
      val fromLocal = fetchFromLocalRepo(identifier)
      if (fromLocal != null) {
         return fromLocal
      } else {
         val downloaded = downloaderFactory.create(projectConfig).download(identifier)
         if (downloaded) {
            return fetchFromLocalRepo(identifier) ?: error("Content was not found despite successfully downloading")
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
               PackageSource(identifier, sourceFile.nameWithoutExtension, sourceFile.readText())
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

class PackageSource(val project: PackageIdentifier, val fileName: String, val contents: String)

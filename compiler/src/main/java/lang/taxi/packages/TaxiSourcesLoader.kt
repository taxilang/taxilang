package lang.taxi.packages

import lang.taxi.sources.SourceCode
import lang.taxi.sources.SourceCodeLanguages
import lang.taxi.utils.log
import org.taxilang.packagemanager.PackageManager
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

class TaxiSourcesLoader(private val sourceRoot: Path) {
   companion object {

      fun loadPackageAndDependencies(
         packageRootPath: Path,
         project: TaxiPackageProject,
         packageManager: PackageManager = PackageManager.withDefaultRepositorySystem(ImporterConfig.forProject(project)),
         builtInSourcesToInclude: List<SourceCode> = emptyList()
      ): TaxiPackageSources {
         val dependencySources = packageManager.fetchDependencies(project)
            .flatMap { packageSource -> TaxiSourcesLoader(packageSource.packageRootPath!!).load() }

         val allDependencies = dependencySources + builtInSourcesToInclude
         return loadPackage(packageRootPath, project, allDependencies)
      }

      fun loadPackageAndDependencies(packageRootPath: Path, importer: PackageManager): TaxiPackageSources {
         val taxiConfFile = packageRootPath.resolve("taxi.conf")
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
         return loadPackageAndDependencies(packageRootPath, taxiPackage, importer)
      }

      fun loadPackageAndDependencies(
         packageRootPath: Path,
      ): TaxiPackageSources {
         val taxiConfFile = packageRootPath.resolve("taxi.conf")
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
         return loadPackageAndDependencies(
            packageRootPath,
            taxiPackage,
            PackageManager.withDefaultRepositorySystem(ImporterConfig.forProject(taxiPackage))
         )
      }

      fun loadPackage(
         packageRootPath: Path,
         project: TaxiPackageProject,
         dependencySources: List<SourceCode> = emptyList()
      ): TaxiPackageSources {
         val sourceRoot = packageRootPath.resolve(project.sourceRoot)
         if (project.taxiConfFile == null) {
            error("No taxi.conf file - how did this happen?")
         }
         val sources = TaxiSourcesLoader(sourceRoot).load()
         val readme = findReadme(packageRootPath)

         return TaxiPackageSources(project, sources + dependencySources, readme)
      }

      fun findReadme(packageRootPath: Path): SourceCode? {
         if (!Files.isDirectory(packageRootPath)) {
            return null
         }
         val optional =  Files.list(packageRootPath)
            .filter { it.fileName.toString().equals("readme.md", ignoreCase = true) }
            .findFirst()
            .map { path ->
               SourceCode(
                  path.fileName.toString(), path.readText(), path, SourceCodeLanguages.MARKDOWN
               )
            }
         return if (optional.isEmpty) {
            null
         } else {
            optional.get()
         }
      }

      fun loadPackage(taxiConfFileOrDirectory: Path): TaxiPackageSources {
         val (taxiConfFile, packageRootPath) = if (taxiConfFileOrDirectory.isDirectory()) {
            taxiConfFileOrDirectory.resolve("taxi.conf") to taxiConfFileOrDirectory
         } else {
            taxiConfFileOrDirectory to taxiConfFileOrDirectory.parent
         }

         if (!Files.exists(taxiConfFile)) {
            throw FileNotFoundException("No taxi config file exists at $taxiConfFileOrDirectory")
         }
         val taxiPackage = TaxiPackageLoader.forDirectoryOrFilePath(taxiConfFile).load()
         return loadPackage(packageRootPath, taxiPackage)
      }
   }

   fun load(): List<SourceCode> {
      val sources = sourceRoot.toFile().walkBottomUp()
         .filter { it.isFile && it.extension == "taxi" }
         .map { file ->
            SourceCode.from(file)
         }
         .toList()

      if (sources.isEmpty()) {
         log().warn("No sources were found at $sourceRoot.")
      }

      return sources
   }
}

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

typealias PackageRootDirectory = Path
typealias TaxiConfFilePath = Path

class TaxiSourcesLoader(private val sourceRoot: Path) {
   companion object {

      private fun packageRootAndTaxiConfAtPath(path: Path): Pair<PackageRootDirectory, TaxiConfFilePath> {
         return if (path.isDirectory()) {
            path to path.resolve("taxi.conf")
         } else {
            path.parent to path
         }
      }

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

      fun loadPackageAndDependencies(path: Path, importer: PackageManager): TaxiPackageSources {
         val (packageRoot, taxiConfFile) = packageRootAndTaxiConfAtPath(path)
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
         return loadPackageAndDependencies(packageRoot, taxiPackage, importer)
      }

      fun loadPackageAndDependencies(
         path: Path,
      ): TaxiPackageSources {
         val (packageRoot, taxiConfFile) = packageRootAndTaxiConfAtPath(path)
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
         return loadPackageAndDependencies(
            packageRoot,
            taxiPackage,
            PackageManager.withDefaultRepositorySystem(ImporterConfig.forProject(taxiPackage))
         )
      }

      private fun loadPackage(
         path: Path,
         project: TaxiPackageProject,
         dependencySources: List<SourceCode> = emptyList()
      ): TaxiPackageSources {
         val sourceRoot = path.resolve(project.sourceRoot)
         if (project.taxiConfFile == null) {
            error("No taxi.conf file - how did this happen?")
         }
         val sources = TaxiSourcesLoader(sourceRoot).load()
         val readme = findReadme(path)

         return TaxiPackageSources(project, sources + dependencySources, readme)
      }

      fun findReadme(path: Path): SourceCode? {
         val (packageRoot, taxiConfFile) = packageRootAndTaxiConfAtPath(path)
         val optional = Files.list(packageRoot)
            .filter { it.fileName.toString().equals("readme.md", ignoreCase = true) }
            .findFirst()
            .map { filePath ->
               SourceCode(
                  filePath.fileName.toString(), filePath.readText(), filePath, SourceCodeLanguages.MARKDOWN
               )
            }
         return if (optional.isEmpty) {
            null
         } else {
            optional.get()
         }
      }

      fun loadPackage(path: Path): TaxiPackageSources {
         val (packageRoot, taxiConfFile) = packageRootAndTaxiConfAtPath(path)
         if (!Files.exists(taxiConfFile)) {
            throw FileNotFoundException("No taxi config file exists at $path")
         }
         val taxiPackage = TaxiPackageLoader.forDirectoryOrFilePath(taxiConfFile).load()
         return loadPackage(packageRoot, taxiPackage)
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

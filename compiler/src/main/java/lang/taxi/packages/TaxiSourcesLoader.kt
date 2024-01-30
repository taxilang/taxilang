package lang.taxi.packages

import lang.taxi.sources.SourceCode
import lang.taxi.utils.log
import org.taxilang.packagemanager.PackageManager
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

class TaxiSourcesLoader(private val sourceRoot: Path) {
   companion object {

      fun loadPackageAndDependencies(
         packageRootPath: Path,
         project: TaxiPackageProject,
         packageManager: PackageManager = PackageManager.withDefaultRepositorySystem(ImporterConfig.forProject(project))
      ): TaxiPackageSources {
         val dependencySources = packageManager.fetchDependencies(project)
            .flatMap { packageSource ->  TaxiSourcesLoader(packageSource.packageRootPath!!).load() }
         return loadPackage(packageRootPath, project, dependencySources)
      }

      fun loadPackageAndDependencies(packageRootPath: Path, importer: PackageManager): TaxiPackageSources {
         val taxiConfFile = packageRootPath.resolve("taxi.conf")
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
         return loadPackageAndDependencies(packageRootPath, taxiPackage, importer)
      }

      fun loadPackageAndDependencies(
         packageRootPath: Path,
         userFacingLogger: MessageLogger = LogWritingMessageLogger
      ): TaxiPackageSources {
         val taxiConfFile = packageRootPath.resolve("taxi.conf")
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
         return loadPackageAndDependencies(
            packageRootPath,
            taxiPackage,
            PackageManager.withDefaultRepositorySystem(ImporterConfig.forProject(taxiPackage, userFacingLogger))
         )
      }

      fun loadPackage(
         packageRootPath: Path,
         project: TaxiPackageProject,
         dependencySources: List<SourceCode> = emptyList()
      ): TaxiPackageSources {
         val sourceRoot = packageRootPath.resolve(project.sourceRoot)
         val sources = TaxiSourcesLoader(sourceRoot).load()
         val projectWithRoot = project.copy(packageRootPath = packageRootPath)

         return TaxiPackageSources(projectWithRoot, sources + dependencySources)
      }

      fun loadPackage(packageRootPath: Path): TaxiPackageSources {
         val taxiConfFile = packageRootPath.resolve("taxi.conf")
         if (!Files.exists(taxiConfFile)) {
            throw FileNotFoundException("No taxi config file exists at $taxiConfFile")
         }
         val taxiPackage = TaxiPackageLoader(taxiConfFile).load()
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

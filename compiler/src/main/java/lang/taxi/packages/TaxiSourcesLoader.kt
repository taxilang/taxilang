package lang.taxi.packages

import lang.taxi.sources.SourceCode
import lang.taxi.utils.log
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

class TaxiSourcesLoader(private val sourceRoot: Path) {
   companion object {

      fun loadPackageAndDependencies(
         packageRootPath: Path,
         project: TaxiPackageProject,
         importer: PackageImporter = PackageImporter(ImporterConfig.forProject(project))
      ): TaxiPackageSources {
         val dependencySources = importer.fetchDependencies(project)
            .map { it.source }
         return loadPackage(packageRootPath, project, dependencySources)
      }

      fun loadPackageAndDependencies(packageRootPath: Path, importer: PackageImporter): TaxiPackageSources {
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
            PackageImporter(ImporterConfig.forProject(taxiPackage, userFacingLogger))
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

//      private fun makeAdditionalSourcesAbsolute(
//         packageRootPath: Path,
//         project: TaxiPackageProject
//      ): TaxiPackageProject {
//         val absoluteSources = project.additionalSources.map { (key, path) ->
//            require(!path.isAbsolute) { "Path $path must be relative"}
//
//            val absolutePath =  path.resolve(packageRootPath).normalize()
//            require(absolutePath.startsWith(packageRootPath)) { "Path $path must be relative to $packageRootPath" }
//            key to absolutePath
//         }.toMap()
//         return project.copy(additionalSources = absoluteSources)
//      }

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
            // TODO : WHy is this relative to the root, and not absolute?
//            val pathRelativeToSourceRoot = sourceRoot.relativize(file.toPath()).toString()
//            SourceCode(pathRelativeToSourceRoot, file.readText(), file.toPath())
            SourceCode.from(file)
         }
         .toList()

      if (sources.isEmpty()) {
         log().warn("No sources were found at $sourceRoot.")
      }

      return sources
   }
}

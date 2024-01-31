package org.taxilang.packagemanager

import lang.taxi.packages.ImporterConfig
import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.TaxiPackageProject
import net.lingala.zip4j.ZipFile
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.collection.DependencyCollectionException
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.installation.InstallRequest
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResolutionException
import org.taxilang.packagemanager.layout.TaxiArtifactType
import org.taxilang.packagemanager.repository.git.ArtifactExtensions
import org.taxilang.packagemanager.repository.git.GitRepositorySupport
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class PackageManager(
   private val importerConfig: ImporterConfig,
   private val repositorySystem: RepositorySystem,
   private val repositorySystemSession: RepositorySystemSession,
   private val defaultRepositories: List<RemoteRepository> = listOf(GitRepositorySupport.GIT_REMOTE_REPOSITORY)
) {
   private val userFacingLogger = importerConfig.userFacingLogger

   init {
      configureLocalRepository()
   }

   private fun configureLocalRepository() {
      val localRepository = LocalRepository(importerConfig.localCache.toFile())
      repositorySystem.newLocalRepositoryManager(repositorySystemSession, localRepository).apply {
         (repositorySystemSession as DefaultRepositorySystemSession).setLocalRepositoryManager(this)
      }
   }

   companion object {
      fun withDefaultRepositorySystem(config: ImporterConfig): PackageManager {
         val (system, session) = RepositorySystemProvider.build()
         return PackageManager(config, system, session)
      }
   }

   /**
    * Installs a project into the local repository.
    */
   fun bundleAndInstall(projectLocation: Path, projectConfig: TaxiPackageProject): Path {
      val bundle = TaxiFileBasedPackageBundler.createBundle(
         projectLocation,
         projectConfig.identifier
      )
      val dependency = projectConfig.identifier.asDependency(
         file = bundle.zip,
         extension = "zip"
      )
      val installRequest = InstallRequest()
      installRequest.addArtifact(dependency.artifact)
      val result = repositorySystem.install(repositorySystemSession, installRequest)
      val artifactPath = repositorySystemSession.localRepository.basedir.resolve(
         repositorySystemSession.localRepositoryManager.getPathForLocalArtifact(dependency.artifact)
      )
      processArchive(artifactPath)

      val fetchedDependencies = fetchDependencies(projectConfig)
      return artifactPath.toPath().parent
   }

   fun fetchDependencies(projectConfig: TaxiPackageProject): List<TaxiPackageProject> {
      val request = buildDependencyRequest(projectConfig)
      val result = try {
         repositorySystem.collectDependencies(repositorySystemSession, request)
      } catch (e: DependencyCollectionException) {
         userFacingLogger.error("Failed to collect dependencies: ${e.message}")
         return emptyList()
      }

      val artifactRequests = collectArtifactRequests(result.root.children)

      val resolved = try {
         repositorySystem.resolveArtifacts(
            repositorySystemSession,
            artifactRequests
         )
      } catch (e: ArtifactResolutionException) {
         userFacingLogger.error("Failed to resolve artifacts: ${e.message}")
         return emptyList()
      }

      return resolved.map { processArchive(it.artifact.file) }
         .mapNotNull { path ->
            val taxiConf = path.resolve("taxi.conf")
            if (!taxiConf.exists()) {
               userFacingLogger.error("No taxi.conf found at $path")
               null
            } else {
               try {
                  TaxiProjectLoader(taxiConf)
                     .load()
               } catch (e: Exception) {
                  userFacingLogger.error("Failed to read the taxi.conf at $path: ${e::class.java.simpleName} - ${e.message}")
                  null
               }
            }

         }
   }

   private fun processArchive(file: File): Path {
      // If it's a file, it's a zip.
      return if (file.isFile) {
         return unzipIfRequired(file)
      } else {
         file.toPath()
      }
   }

   private fun unzipIfRequired(file: File): Path {
      // TODO: It seems silly to always unzip,
      // however avoiding premature optimization for now.
      // In future: Consider comparing the hash of the zip now with a previous
      // hash, and only unzip if things have changed.
      val unzipDir = file.toPath().resolveSibling("bundle/")
      return if (unzipDir.exists() && unzipDir.isDirectory()) {
         unzipDir
      } else {
         unzipDir.toFile().mkdirs()
         ZipFile(file).extractAll(unzipDir.absolutePathString())
         unzipDir
      }
   }

   private fun collectArtifactRequests(nodes: List<DependencyNode>): List<ArtifactRequest> {
      return nodes.flatMap { dependency ->
         val artifactRequest = ArtifactRequest(
            DefaultArtifact(
               dependency.artifact.groupId,
               dependency.artifact.artifactId,
               dependency.artifact.classifier,
               TaxiArtifactType.TAXI_PROJECT_BUNDLE.extension,
               dependency.artifact.version,
               dependency.artifact.properties,
               dependency.artifact.file
            ),
            dependency.repositories,
            dependency.requestContext
         )
         listOf(artifactRequest) + collectArtifactRequests(dependency.children)
      }
   }


   private fun buildDependencyRequest(projectConfig: TaxiPackageProject): CollectRequest {
      val request = CollectRequest()
      request.root = projectConfig.identifier.asDependency()
      projectConfig.dependencyPackages.forEach { dependencyIdentifier ->
         request.addDependency(dependencyIdentifier.asDependency())
      }
      request.repositories = defaultRepositories
      return request
   }
}

/**
 * Returns a project, along with all its dependencies - including transitive dependencies.
 */
data class TaxiPackageGraph(
   val rootProject: TaxiPackageProject,
   val dependencies: List<TaxiPackageProject>
)

fun PackageIdentifier.asDependency(file: Path? = null, extension: String? = null): Dependency {
   return Dependency(
      DefaultArtifact(
         this.name.organisation,
         this.name.name,
         null,
         extension,
         this.version,
         mutableMapOf(
            ArtifactExtensions.REQUESTED_VERSION to this.version
         ),
         file?.toFile()

      ),
      "compile"
   )
}
package lang.taxi.lsp.sourceService

import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.ProjectName
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.packages.TaxiProjectLoader
import lang.taxi.utils.log
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.repository.WorkspaceRepository
import java.io.File
import java.nio.file.Path

/**
 * Responsible for resolving taxi.conf dependencies within the workspace.
 */
class TaxiWorkspaceReader(private val taxiConfFiles: List<Path>, private val workspaceName: String = "workspace") : WorkspaceReader {
   private val repository = WorkspaceRepository(workspaceName)

   private val projects: List<TaxiPackageProject> = taxiConfFiles.mapNotNull { path ->
      try {
         TaxiProjectLoader(path).load()
      } catch (e:Exception) {
         log().warn("Failed to read taxi.conf file at ${path.toAbsolutePath()} - ${e.message}")
         null
      }
   }
   override fun getRepository(): WorkspaceRepository = repository

   override fun findArtifact(artifact: Artifact): File? {
      val requestedPackage = artifact.asPackageIdentifier()
      val foundProject = projects.firstOrNull { it.identifier == requestedPackage }
      return foundProject?.taxiConfFile?.toFile()
   }

   override fun findVersions(artifact: Artifact): MutableList<String> {
      val requestedPackage = artifact.asPackageIdentifier()
      return projects.filter { it.identifier.name == requestedPackage.name }
         .map { it.version }
         .toMutableList()
   }
}


fun Artifact.asPackageIdentifier():PackageIdentifier {
 return PackageIdentifier(ProjectName( this.groupId, this.artifactId), this.version)
}

package org.taxilang.packagemanager.repository.git

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.impl.VersionResolver
import org.eclipse.aether.resolution.VersionRequest
import org.eclipse.aether.resolution.VersionResult
import org.taxilang.packagemanager.TaxiProjectLoader
import java.net.URI

class GitVersionResolver : VersionResolver {
   override fun resolveVersion(session: RepositorySystemSession, request: VersionRequest): VersionResult {
      if (!GitRepoTransport.isGitUrl(request.artifact.version)) {
         return VersionResult(request)
            .setVersion(request.artifact.version)
      }

      val path = GitRepoTransport(session)
         .cloneRepo(URI(request.artifact.version))
      val taxiConf = path.resolve("taxi.conf")
      val project = TaxiProjectLoader().withConfigFileAt(taxiConf).load()
      val result = VersionResult(request)
         .setVersion(project.version)
         .setRepository(GitRepositorySupport.GIT_REMOTE_REPOSITORY)
      return result
   }
}

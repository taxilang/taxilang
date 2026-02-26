package org.taxilang.packagemanager.repository.git

import lang.taxi.packages.TaxiProjectLoader
import lang.taxi.utils.log
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.RepositoryPolicy
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.PeekTask
import org.eclipse.aether.spi.connector.transport.PutTask
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.NoTransporterException
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.RefNotFoundException
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.taxilang.packagemanager.TaxiPackageBundler
import org.taxilang.packagemanager.layout.TaxiArtifactType
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.inputStream

object GitRepositorySupport {
   val GIT_REMOTE_REPOSITORY = RemoteRepository.Builder("git", GitRepoTransportFactory.REPO_TYPE, "git-repos")
      .setPolicy(
         RepositoryPolicy(
            true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_IGNORE
         )
      ).build()
}

class GitRepoTransportFactory : TransporterFactory {
   companion object {
      const val REPO_TYPE = "git"
   }

   override fun newInstance(session: RepositorySystemSession, remote: RemoteRepository): Transporter {
      if (remote.contentType == REPO_TYPE) {
         return GitRepoTransport(session)
      } else {
         throw NoTransporterException(remote)
      }

   }

   override fun getPriority(): Float = 0F
}

enum class GitProviderShorthand(private val prefix: String, private val fullDomain: String) {
   Github("github:", "https://github.com"),
   Gitlab("gitlab:", "https://gitlab.com");

   fun matches(url: String) = url.startsWith(prefix)

   /**
    * Converts:
    * github:foo/bar to https://github.com/foo/bar.git
    * github:foo/bar#0.34.0 to https://github.com/foo/bar.git#0.34.0
    */
   fun resolveUrl(url: String): String {
      val parts = url.split("#")
      val repoUrl = parts[0].replace(prefix, fullDomain + "/") + ".git"
      return if (parts.size > 1) {
         "$repoUrl#${parts[1]}"
      } else {
         repoUrl
      }
   }

   companion object {
      fun isKnownPrefix(url: String): Boolean {
         return values().any { url.startsWith(it.prefix) }
      }

      fun forUrl(url: String): GitProviderShorthand? {
         return values().find { url.startsWith(it.prefix) }
      }
   }
}

class GitRepoTransport(private val session: RepositorySystemSession) :
   Transporter {
   companion object {
      const val EXTENSION_QUERY_PARAM = "extension"
      const val ARTIFACT_ID_PARAM = "artifactId"

      fun isGitUrl(uri: URI): Boolean {
         return if (GitProviderShorthand.isKnownPrefix(uri.toASCIIString())) {
            true
         } else {
            isGitUrl(uri.withoutQueryString().toASCIIString())
         }

      }

      fun isGitUrl(url: String): Boolean {
         return when {
            url.endsWith(".git") || url.endsWith(".git/") -> true
            // Branch / commit sha reference
            url.contains(".git#") -> true
            GitProviderShorthand.values().any { it.matches(url) } -> true
            else -> false
         }
      }

      /**
       * Resolves shorthands (eg: github: gitlab:) to full urls
       */
      fun resolveGitShorthandIfPresent(url: String): String {
         val shortHand = GitProviderShorthand.forUrl(url)
         return shortHand?.resolveUrl(url) ?: url
      }

      fun resolveGitShorthandIfPresent(uri: URI): URI {
         val resolved = resolveGitShorthandIfPresent(uri.toASCIIString())
         return URI.create(resolved)
      }

      /**
       * Returns the directory to clone the git URI to, relative to the provided
       * git workspace directory.
       *
       * We use a hierarchical structure - so https://github.com/taxi-lang/test-project-a.git#0.34.0
       * becomes github.com/taxi-lang/test-project-a/0.34.0
       *
       * The fragment (tag/branch) is included in the path to allow different versions
       * to coexist without conflicts.
       */
      fun uriToGitWorkspaceDirectory(gitWorkspace: Path, uri: String) =
         uriToGitWorkspaceDirectory(gitWorkspace, URI.create(uri))

      /**
       * Returns the directory to clone the git URI to, relative to the provided
       * git workspace directory.
       *
       */
      fun uriToGitWorkspaceDirectory(gitWorkspace: Path, uri: URI): Path {
         val repoPath = uri.path
            .removeSuffix("/")
            .removeSuffix(".git")
            .removePrefix("/")
         val pathToRepo = listOfNotNull(uri.host, repoPath, uri.fragment ?: "@default")
            .joinToString("/")
         return gitWorkspace.resolve(pathToRepo)
      }
   }

   override fun close() {
   }

   override fun classify(error: Throwable): Int {
      return when (error) {
         is NotGitRepositoryException -> Transporter.ERROR_NOT_FOUND
         else -> Transporter.ERROR_OTHER
      }
   }

   override fun peek(task: PeekTask?) {
      error("Peek not supported by Git transport")
   }

   override fun get(task: GetTask) {
      if (!isGitUrl(task.location)) {
         throw NotGitRepositoryException()
      }
      // We expect to have received a location from the
      // GitProjectLayout, which encodes additional information in the queryString.
      // Strip that out now.
      val resolvedGitUri = GitRepoTransport.resolveGitShorthandIfPresent(task.location)
      val gitRepoUri = resolvedGitUri.withoutQueryString()
      val queryParams = resolvedGitUri.queryParams()
      val extension = queryParams[EXTENSION_QUERY_PARAM]

      when (extension) {
         TaxiArtifactType.TAXI_CONF_FILE.extension -> {
            val repoPath = cloneRepo(gitRepoUri)
            val filePath = repoPath.resolve("taxi.conf")
            task.copyPathToDestination(filePath)
         }

         TaxiArtifactType.TAXI_PROJECT_BUNDLE.extension -> {
            val repoPath = cloneRepo(gitRepoUri)
            val zipFile = createBundleZipAt(repoPath)
            task.copyPathToDestination(zipFile)
         }

         else -> error("Unhandled type of get request for git repo: ${task.location.toASCIIString()}")
      }
   }

   /**
    * Converts the downloaded repo to a zip file.
    *
    * Maven Artifact Repo is built around artifacts being a single file.
    * So, even through we've just downloaded the repo, we need to zip it up, to transfer it
    * to another location.
    */
   private fun createBundleZipAt(repoPath: Path): Path {
      val taxiConfPath = repoPath.resolve("taxi.conf")
      val taxiConf = TaxiProjectLoader(taxiConfPath).load()
      val bundle = TaxiPackageBundler.createBundle(repoPath, taxiConf.identifier)
      return bundle.zip
   }

   fun cloneRepo(uri: URI): Path {

      val gitWorkspace = session.localRepository.basedir
         .resolve(".gitWorkspace")
      // Create a consistent directory name for checking out the git repo to.
      val checkoutDir = uriToGitWorkspaceDirectory(gitWorkspace.toPath(), uri)
         .toFile()
      val branchName = uri.fragment
      if (checkoutDir.exists()) {
         // Conscious choice here.
         // We're a package manager, so it's expected we're working against
         // immutable version tags or branches.
         // If not, and the package has been updated or tag moved, the user would need
         // to do a "taxi update" (which doesn't currently exist) - so would need to delete
         // and reclone the directory.
         log().debug("Git repo exists for $uri at $checkoutDir - not updating")
      } else {
         checkoutDir.mkdirs()
         val branchPart = if (branchName == null) {
            "on the default branch"
         } else {
            "on branch $branchName"
         }
         val gitUri = uri.withoutFragment()
         log().info("Cloning git repo $gitUri $branchPart to directory $checkoutDir")

         try {
            Git.cloneRepository()
               .setURI(gitUri.toASCIIString())
               .setDirectory(checkoutDir)
               .setDepth(1)
               .setCloneAllBranches(false)
               .apply {
                  if (branchName != null && branchName.isNotEmpty()) {
                     setBranch(branchName)
                  }
               }
               .call()
         } catch (e:RefNotFoundException) {
            log().warn("Branch or tag '$branchName' not found in $gitUri")
            throw IllegalArgumentException("Branch or tag '$branchName' not found in $gitUri", e)
         }

      }

      return checkoutDir.toPath()
   }

   override fun put(task: PutTask?) {
      error("Put not supported by Git transport")
   }

}

class NotGitRepositoryException : RuntimeException()

fun URI.queryParams(): Map<String, String> {
   if (this.query.isNullOrEmpty()) {
      return emptyMap()
   }
   return query.split("&").mapNotNull { queryParam ->
      val parts = queryParam.split("=").map { it.trim() }
      if (parts.size == 2) {
         parts[0] to parts[1]
      } else {
         null
      }
   }.toMap()
}

fun URI.withoutQueryString(): URI {
   return URI(this.scheme, this.userInfo, this.host, this.port, this.path, null, this.fragment)
}

fun URI.withoutFragment(): URI {
   return URI(this.scheme, this.userInfo, this.host, this.port, this.path, this.query, null)
}


fun GetTask.copyPathToDestination(path: Path) {
   this.newOutputStream().use { outputStream ->
      path.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
   }
}

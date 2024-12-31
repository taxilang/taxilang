package org.taxilang.packagemanager.repository.git

import lang.taxi.packages.TaxiProjectLoader
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.RepositoryPolicy
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.PeekTask
import org.eclipse.aether.spi.connector.transport.PutTask
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.NoTransporterException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.RepositoryBuilder
import org.taxilang.packagemanager.TaxiPackageBundler
import org.taxilang.packagemanager.layout.TaxiArtifactType
import lang.taxi.utils.log
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
      TODO("Not yet implemented")
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
      val localRepoName = listOfNotNull(uri.host, uri.path)
         .joinToString(".")
         .replace("/", ".")
         .removeSuffix(".")
         .removePrefix(".")

      val checkoutDir = gitWorkspace.resolve(localRepoName)
      val branchName = uri.fragment
      val gitUri = uri.withoutFragment()
      if (checkoutDir.exists()) {
         log().debug("Using existing git repository at $checkoutDir")
         val git = Git(RepositoryBuilder().setGitDir(checkoutDir.resolve(".git")).build())
         switchToRequiredBranch(branchName, git)
      } else {
         checkoutDir.mkdirs()
         val branchPart = if (branchName == null) {
            "on the default branch"
         } else {
            "on branch $branchName"
         }
         log().info("Cloning git repo $gitUri $branchPart to directory $checkoutDir")

         Git.cloneRepository()
            .setURI(gitUri.toASCIIString())
            .setDirectory(checkoutDir)
            .setDepth(1)
            .setBranch(branchName)
            .setCloneAllBranches(false)
            .call()
      }

      return checkoutDir.toPath()
   }

   /**
    * Returns the branch to check out when a repository is cloned locally.
    * If a branch name was provided, we use that. Otherwise, we find the "default" branch
    */
   private fun switchToRequiredBranch(branchName: String?, git: Git): String? {
      val branchNameToCheckout = branchName ?: findDefaultBranch(git)
      return switchToBranchOrTag(branchNameToCheckout, git)
   }

   /**
    * Resolves the default branch (eg., master / main)
    */
   private fun findDefaultBranch(git: Git): String? {
      val headRef = git.repository.exactRef("refs/remotes/origin/HEAD")
         ?: git.repository.exactRef("HEAD")
         ?: return null

      val defaultBranchName = when {
         headRef.target.name.startsWith("refs/remotes/origin/") -> headRef.target.name.removePrefix("refs/remotes/origin/")
         headRef.target.name.startsWith("refs/heads/") -> headRef.target.name.removePrefix("refs/heads/")
         else -> headRef.target.name
      }
      /// TODO: This still appears to be null sometimes in unit tests
      // We could fetch the remote to find the HEAD, but will see if this is an issue
      // with real repos.
      return defaultBranchName
   }

   private fun switchToBranchOrTag(refName: String?, git: Git): String? {
      val repository = git.repository
      val ref = refName?.let {
         repository.findRef(refName)
            ?: repository.findRef("refs/tags/$refName")
            ?: repository.findRef("refs/heads/$refName")
            ?: throw IllegalArgumentException("Could not find branch or tag '$refName' in repository '$repository'")
      }

      if (ref != null) {
         log().info("Switching ${repository.identifier} to branch $refName")
         git.checkout()
            .setName(ref.name)
            .call()
      } else {
         git.checkout().call()
      }


      val isTag = ref?.name?.startsWith("refs/tags/") ?: false
      if (!isTag) {
         log().debug("Pulling branch $refName")
         git.pull().call()
      }
      return ref?.name
   }

   override fun put(task: PutTask?) {
      TODO("Not yet implemented")
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

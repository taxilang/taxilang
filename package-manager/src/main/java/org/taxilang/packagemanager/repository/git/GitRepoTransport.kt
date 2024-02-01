package org.taxilang.packagemanager.repository.git

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.RepositoryPolicy
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.PeekTask
import org.eclipse.aether.spi.connector.transport.PutTask
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.RepositoryBuilder
import org.taxilang.packagemanager.TaxiFileBasedPackageBundler
import org.taxilang.packagemanager.TaxiProjectLoader
import org.taxilang.packagemanager.layout.TaxiArtifactType
import org.taxilang.packagemanager.utils.log
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.inputStream

object GitRepositorySupport {
   val GIT_REMOTE_REPOSITORY = RemoteRepository.Builder("git", "git", "read-from-projects")
      .setPolicy(
         RepositoryPolicy(
            true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_IGNORE
         )
      ).build()
}

class GitRepoTransportFactory : TransporterFactory {
   override fun newInstance(session: RepositorySystemSession, remote: RemoteRepository): Transporter {
      return GitRepoTransport(session)
   }

   override fun getPriority(): Float = 0F
}

class GitRepoTransport(private val session: RepositorySystemSession) :
   Transporter {
   companion object {
      const val EXTENSION_QUERY_PARAM = "extension"
      const val ARTIFACT_ID_PARAM = "artifactId"

      fun isGitUrl(uri: URI): Boolean {
         return isGitUrl(uri.withoutQueryString().toASCIIString())
      }

      fun isGitUrl(url: String): Boolean {
         return when {
            url.endsWith(".git") || url.endsWith(".git/") -> true
            // Branch / commit sha reference
            url.contains(".git#") -> true
            else -> false
         }
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
      val gitRepoUri = task.location.withoutQueryString()
      val queryParams = task.location.queryParams()
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
      val bundle = TaxiFileBasedPackageBundler.createBundle(repoPath, taxiConf.identifier)
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
         log().info("Using existing git repository at $checkoutDir")
         val git = Git(RepositoryBuilder().setGitDir(checkoutDir.resolve(".git")).build())
         val gitRepo = git.repository
         val requiredBranch = getRequiredBranch(branchName, git)
         if (requiredBranch != null && gitRepo.branch != requiredBranch) {
            log().info("Switching git repo at $checkoutDir to branch $requiredBranch")
            git.checkout().setName(requiredBranch).call()
         }
         log().info("Pulling repo at $checkoutDir")
         git.pull().call()
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
   private fun getRequiredBranch(branchName: String?, git: Git): String? {
      if (branchName != null) {
         return branchName
      }
      val headRef = git.repository.exactRef("refs/remotes/origin/HEAD")
      val defaultBranchName = headRef?.target?.name?.substring("refs/remotes/origin/".length)
      /// TODO: This still appears to be null sometimes in unit tests
      // We could fetch the remote to find the HEAD, but will see if this is an issue
      // with real repos.
      return defaultBranchName
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
   val uriWithoutQuery = this.toASCIIString().split("?")[0]
   return URI(uriWithoutQuery)
}

fun URI.withoutFragment(): URI {
   return URI(this.scheme, this.userInfo, this.host, this.port, this.path, this.query, null)
}


fun GetTask.copyPathToDestination(path: Path) {
   this.newOutputStream().use { outputStream ->
      path.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
   }
}

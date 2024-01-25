package org.taxilang.packagemanager.repository.git

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.metadata.Metadata
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory
import org.eclipse.aether.spi.connector.layout.RepositoryLayout
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory
import org.eclipse.aether.transfer.NoRepositoryLayoutException
import java.net.URI

class GitProjectLayoutFactory : RepositoryLayoutFactory {
   companion object {
      const val LAYOUT_TYPE = "git"
   }
   override fun newInstance(session: RepositorySystemSession, repository: RemoteRepository): RepositoryLayout {
      if (repository.contentType == LAYOUT_TYPE) {
         return GitProjectLayout()
      } else {
         throw NoRepositoryLayoutException(repository)
      }
   }

   override fun getPriority(): Float = 0F

}
class GitProjectLayout : RepositoryLayout {
   companion object {

      // We can't serve this artifact through Git,
      // but the RepositoryLayout API doesn't let us signal this until someone tries to resolve the file.
      val INVALID_REQUEST =  URI("taxi+git", null,  "/invalid-request" , null )
   }

   override fun getChecksumAlgorithmFactories(): MutableList<ChecksumAlgorithmFactory> {
      // TODO : Checksums
      return mutableListOf()
   }

   override fun hasChecksums(artifact: Artifact?): Boolean {
      // TODO : Checksums
      return false
   }


   override fun getLocation(artifact: Artifact, upload: Boolean): URI {
      val result = if (GitRepoTransport.isGitUrl(artifact.requestedVersion)) {
         URI(artifact.requestedVersion)
            .resolve("?${GitRepoTransport.EXTENSION_QUERY_PARAM}=${artifact.extension}&${GitRepoTransport.ARTIFACT_ID_PARAM}=${artifact.groupId}:${artifact.artifactId}")

      } else {
        INVALID_REQUEST
      }
      return result

   }

   override fun getLocation(metadata: Metadata?, upload: Boolean): URI {
      TODO("Not yet implemented")
   }

   override fun getChecksumLocations(
      artifact: Artifact?,
      upload: Boolean,
      location: URI?
   ): MutableList<RepositoryLayout.ChecksumLocation> {
      // TODO : Checksums
      return mutableListOf()
   }

   override fun getChecksumLocations(
      metadata: Metadata?,
      upload: Boolean,
      location: URI?
   ): MutableList<RepositoryLayout.ChecksumLocation> {
      // TODO : Checksums
      return mutableListOf()

   }
}

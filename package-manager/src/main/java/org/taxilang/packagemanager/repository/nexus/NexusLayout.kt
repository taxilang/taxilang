package org.taxilang.packagemanager.repository.nexus

import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.ProjectName
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.metadata.Metadata
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory
import org.eclipse.aether.spi.connector.layout.RepositoryLayout
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory
import org.eclipse.aether.transfer.NoRepositoryLayoutException
import org.taxilang.packagemanager.layout.TaxiArtifactType
import java.net.URI

class NexusLayoutFactory : RepositoryLayoutFactory {

   override fun newInstance(session: RepositorySystemSession, repository: RemoteRepository): RepositoryLayout {
      if (repository.contentType == NexusTransportFactory.REPO_TYPE) {
         return NexusLayout(session, repository)
      } else {
         throw NoRepositoryLayoutException(repository)
      }
   }

   override fun getPriority(): Float = 0F
}


class NexusLayout(private val session: RepositorySystemSession, private val repository: RemoteRepository) :
   RepositoryLayout {
   override fun getChecksumAlgorithmFactories(): MutableList<ChecksumAlgorithmFactory> {
      return mutableListOf()
   }

   override fun hasChecksums(artifact: Artifact): Boolean {
      return false
   }

   override fun getLocation(artifact: Artifact, upload: Boolean): URI {

      val identifier = PackageIdentifier(
         ProjectName(artifact.groupId, artifact.artifactId),
         artifact.version
      )

      val artifactType = TaxiArtifactType.fromExtension(artifact.extension)

      val uri = NexusPackageService.url(
         repository.url,
         repository.id,
         identifier,
         artifactType
      )
      return URI.create(uri)
   }

   override fun getLocation(metadata: Metadata, upload: Boolean): URI {
      TODO("Not yet implemented")
   }

   override fun getChecksumLocations(
      artifact: Artifact?,
      upload: Boolean,
      location: URI?
   ): MutableList<RepositoryLayout.ChecksumLocation> {
      return mutableListOf()
   }

   override fun getChecksumLocations(
      metadata: Metadata?,
      upload: Boolean,
      location: URI?
   ): MutableList<RepositoryLayout.ChecksumLocation> {
      return mutableListOf()
   }

}

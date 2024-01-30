package org.taxilang.packagemanager.layout

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.impl.ArtifactDescriptorReader
import org.eclipse.aether.impl.ArtifactResolver
import org.eclipse.aether.impl.VersionResolver
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy
import org.eclipse.aether.resolution.ArtifactDescriptorPolicyRequest
import org.eclipse.aether.resolution.ArtifactDescriptorRequest
import org.eclipse.aether.resolution.ArtifactDescriptorResult
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResolutionException
import org.eclipse.aether.spi.locator.Service
import org.eclipse.aether.spi.locator.ServiceLocator
import org.taxilang.packagemanager.TaxiProjectLoader
import org.taxilang.packagemanager.asDependency
import javax.tools.Tool

// This class is modelled heavily from maven's DefaultArtifactDescriptorReader
class TaxiDescriptorReader : ArtifactDescriptorReader, Service {
   override fun readArtifactDescriptor(
      session: RepositorySystemSession,
      request: ArtifactDescriptorRequest
   ): ArtifactDescriptorResult {
      val result = ArtifactDescriptorResult(request)
      return loadTaxiConf(
         session, request, result
      )
   }


   private lateinit var artifactResolver: ArtifactResolver
   private lateinit var versionResolver: VersionResolver
   override fun initService(locator: ServiceLocator) {
      artifactResolver = locator.getService(ArtifactResolver::class.java)
      versionResolver = locator.getService(VersionResolver::class.java)
   }

   private fun loadTaxiConf(
      session: RepositorySystemSession,
      request: ArtifactDescriptorRequest,
      result: ArtifactDescriptorResult
   ): ArtifactDescriptorResult {
      val artifact = DefaultArtifact(
         request.artifact.groupId,
         request.artifact.artifactId,
         request.artifact.classifier,
         "pom", // swapping this to taxi.conf happens in the TaxiFileSystemTransport
         request.artifact.version,
         request.artifact.properties,
         request.artifact.file
      )
      val resolveRequest = ArtifactRequest(
         artifact,
         request.repositories,
         request.requestContext
      )
      return try {
         val resolveResult = artifactResolver.resolveArtifact(session, resolveRequest)
         val project = TaxiProjectLoader(resolveResult.artifact.file.toPath())
            .load()

         project.dependencyPackages.forEach { packageIdentifier ->
            result.addDependency(packageIdentifier.asDependency())
         }
         result
      } catch (e: ArtifactResolutionException) {
         if ((getPolicy(session, artifact, request) and ArtifactDescriptorPolicy.IGNORE_MISSING) != 0) {
            result
         } else {
            throw (e)
         }
      }
   }

   private fun getPolicy(session: RepositorySystemSession, a: Artifact, request: ArtifactDescriptorRequest): Int {
      val policy = session.artifactDescriptorPolicy ?: return ArtifactDescriptorPolicy.STRICT
      return policy.getPolicy(session, ArtifactDescriptorPolicyRequest(a, request.requestContext))
   }
}

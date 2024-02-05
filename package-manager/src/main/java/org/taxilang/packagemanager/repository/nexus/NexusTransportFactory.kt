package org.taxilang.packagemanager.repository.nexus

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.PeekTask
import org.eclipse.aether.spi.connector.transport.PutTask
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transfer.NoTransporterException
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.taxilang.packagemanager.utils.log
import java.io.FileOutputStream


class NexusTransport(val session: RepositorySystemSession, val repository: RemoteRepository) : Transporter {
   override fun close() {
   }

   override fun classify(error: Throwable): Int {
      return when (error) {
         is HttpRequestFailedException -> {
            if (error.response.status.code == 404) {
               Transporter.ERROR_NOT_FOUND
            } else {
               Transporter.ERROR_OTHER
            }
         }

         else -> Transporter.ERROR_OTHER
      }
   }

   override fun peek(task: PeekTask) {
      TODO("Not yet implemented")
   }

   override fun get(task: GetTask) {
      val client = ApacheClient()
      val uri = task.location.toASCIIString()
      val request = Request(Method.GET, uri)
      log().info("Attempting to download from $uri")
      val response = client(request)

      log().info("Download from $uri returned ${response.status}")
      if (response.status.successful) {

         FileOutputStream(task.dataFile).use { outputStream ->
            response.body.stream.copyTo(outputStream)
         }
      } else {
         throw HttpRequestFailedException(response)
      }
   }

   override fun put(task: PutTask) {
      TODO("Not yet implemented")
   }

}

class NexusTransportFactory : TransporterFactory {
   companion object {
      const val REPO_TYPE = "taxi-nexus"
   }

   override fun newInstance(session: RepositorySystemSession, repository: RemoteRepository): Transporter {
      return if (repository.contentType == REPO_TYPE) {
         NexusTransport(session, repository)
      } else {
         throw NoTransporterException(repository)
      }
   }

   override fun getPriority(): Float = 0F
}

class HttpRequestFailedException(val response: Response) :
   RuntimeException("Http request failed with ${response.status.code}")

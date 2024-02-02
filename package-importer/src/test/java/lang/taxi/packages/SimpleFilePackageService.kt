package lang.taxi.packages

import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import lang.taxi.packages.repository.PackageService
import lang.taxi.packages.repository.PackageServiceFactory
import lang.taxi.packages.utils.log
import org.apache.commons.io.IOUtils
import org.http4k.core.MemoryResponse
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.File
import java.io.InputStream
import java.nio.file.Path

class SimpleFilePackageService(private val repositoryRoot: Path) : PackageService {
   companion object {
      fun fileServiceFactory(repositoryRoot: Path): Pair<SimpleFilePackageService, PackageServiceFactory> {
         val service = SimpleFilePackageService(repositoryRoot)
         val serviceFactory = mock<PackageServiceFactory> {
            on { get(any(), any()) } doReturn service
         }
         return service to serviceFactory
      }
   }

   override fun upload(zip: File, project: TaxiPackageProject): Response {
      val destination = repositoryRoot.resolve(project.identifier.fileSafeIdentifier + ".zip").toFile()
      destination.createNewFile()
      IOUtils.copy(zip.inputStream(), destination.outputStream())

      log().info("Copied zip to ${destination.canonicalPath}")

      return MemoryResponse(Status.OK)
   }

   override fun attemptDownload(identifier: PackageIdentifier): InputStream? {
      val file = repositoryRoot.resolve(identifier.fileSafeIdentifier + ".zip").toFile()
      return if (file.exists()) {
         file.inputStream()
      } else {
         null
      }

   }

   override val repositoryType: String = "file"
}

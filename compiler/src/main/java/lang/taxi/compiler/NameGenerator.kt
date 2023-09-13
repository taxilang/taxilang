package lang.taxi.compiler

import java.security.SecureRandom
import java.util.*

object NameGenerator {
   private val random: SecureRandom = SecureRandom()
   private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()


   fun generate(prefix: String = "AnonymousType"): String {
      return "$prefix${randomString()}"
   }
   // This is both shorter than a UUID (e.g. Xl3S2itovd5CDS7cKSNvml4_ODA)  and also more secure having 160 bits of entropy.
   fun randomString(length:Int  = 20): String {
      val buffer = ByteArray(length)
      random.nextBytes(buffer)
      return encoder.encodeToString(buffer)
   }
}

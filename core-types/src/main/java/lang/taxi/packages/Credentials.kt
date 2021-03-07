package lang.taxi.packages

import java.util.*

data class Credentials(val repositoryName: String, val username: String, val password: String) {
   fun asBasicAuthHeader(): String {
      val auth = "$username:$password"
      val encodedAuth = Base64.getEncoder().encode(auth.toByteArray(Charsets.ISO_8859_1))
      return "Basic ${String(encodedAuth)}"
   }
}

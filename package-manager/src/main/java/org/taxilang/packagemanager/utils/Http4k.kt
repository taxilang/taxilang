package org.taxilang.packagemanager.utils

import lang.taxi.utils.log
import org.http4k.core.Credentials
import org.http4k.core.Request
import java.util.*

fun Request.basicAuth(username: String, password: String): Request {
   return this.header("Authorization", basicAuthHeader(username, password))
}

fun Request.basicAuth(credentials: lang.taxi.packages.Credentials?): Request {
   return if (credentials == null) {
      log().info("Will attempt to publish to ${this.uri} without any credentials")
      this
   } else {
      log().info("Will attempt to publish to ${this.uri} using basic auth credentials supplied")
      this.basicAuth(credentials.username, credentials.password)
   }

}

fun basicAuthHeader(username: String, password: String): String {
   val auth = "$username:$password"
   val encodedAuth = Base64.getEncoder().encode(auth.toByteArray(Charsets.ISO_8859_1))
   return "Basic ${String(encodedAuth)}"
}

fun Credentials.asBasicAuthHeader(): String = basicAuthHeader(this.user, this.password)

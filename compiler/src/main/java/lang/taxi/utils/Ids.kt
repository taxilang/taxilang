package lang.taxi.utils

import com.aventrix.jnanoid.jnanoid.NanoIdUtils

internal object Ids {
   /**
    * Must only contain letters that would produce a valid taxi identifier. ie - exclude "-", since that's
    * invalid as the first character in an indentifier.
    */
   private val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
   fun id(prefix: String, size: Int = 6): String =
      prefix + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, ALPHABET, size)
}

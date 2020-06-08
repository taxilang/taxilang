package lang.taxi.packages

import java.io.File

data class PluginSettings(val repositories: List<String> = emptyList(), val localCache: String = "~/.taxi/plugins") {
   val localCachePath by lazy {
      val path = this.localCache.replace("~", System.getProperty("user.home"))
      File(path)
   }
}

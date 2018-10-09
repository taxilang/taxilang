package lang.taxi.cli.plugins

import java.util.*

class PluginVersionProvider {
    companion object {
        private val props: Properties by lazy {
            val props = Properties()
            props.load(this.javaClass.getResourceAsStream("/application.properties"))
            props
        }

        @JvmStatic
        fun getVersion(): String {
            return props.getProperty("version")
        }
    }
}

package lang.taxi.cli

import com.beust.jcommander.Parameter
import org.apache.commons.lang3.SystemUtils
import org.springframework.util.StringUtils
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

open class CliOptions {

    @Parameter(names = arrayOf("--debug", "-d"), description = "debug mode")
    var debug: Boolean = false

    @Parameter(names = arrayOf("--help"), description = "help", help = true)
    var help: Boolean = false

    @Parameter(names = arrayOf("-p", "--project"), description = "Project home")
    var projectHome: String = System.getProperty("user.dir")

    @Parameter(names = arrayOf("-f", "--file"), description = "Specify taxi project file")
    var taxiFile = "taxi.conf"

    @Parameter(names = arrayOf("--quiet", "-q"), description = "Quiet (non-interactive) mode")
    var quietMode: Boolean = false

    fun getProjectHome(): Path {
        return if (StringUtils.isEmpty(projectHome)) {
            SystemUtils.getUserDir().toPath()
        } else {
            Paths.get(projectHome)
        }
    }

    fun getTaxiFile(): File {
        return getProjectHome().resolve(taxiFile).toFile()
    }

}

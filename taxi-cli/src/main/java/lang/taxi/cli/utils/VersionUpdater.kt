package lang.taxi.cli.utils

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import org.apache.commons.io.FileUtils
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.file.Path

@Component
class VersionUpdater {

   fun write(source: Path, version: String) {
      val sourceConfig = ConfigFactory.parseFileAnySyntax(source.toFile())
      val updated = sourceConfig.withValue("version", ConfigValueFactory.fromAnyRef(version))
      val configString = updated.root().render(ConfigRenderOptions.defaults()
         .setOriginComments(false)
         .setJson(false)
      )

      FileUtils.write(source.toFile(), configString, Charset.defaultCharset())
   }
}

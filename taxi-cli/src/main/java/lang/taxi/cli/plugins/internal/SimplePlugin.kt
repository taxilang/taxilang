package lang.taxi.cli.plugins.internal

import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.plugins.Artifact
import org.springframework.stereotype.Component

@Component
class SimplePlugin : InternalPlugin {
    override val artifact: Artifact = Artifact.parse("Simple")

}
package lang.taxi.plugins.jpa.kotlin;

import lang.taxi.generators.kotlin.AnnotationFactories;
import lang.taxi.plugins.ArtifactId;
import lang.taxi.plugins.ComponentProviderPlugin;
import org.jetbrains.annotations.NotNull;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;

import java.util.List;

@Extension
public class KotlinJpaPlugin implements ComponentProviderPlugin, ExtensionPoint {
    @NotNull
    @Override
    public ArtifactId getId() {
        return new ArtifactId("taxi","jpak");
    }

    @NotNull
    @Override
    public List<Object> getComoponents() {
        return KotlinJpaProcessors.INSTANCE.getProcessors();
    }
}

package lang.taxi.cli.commands

import io.kotest.matchers.file.shouldExist
import lang.taxi.cli.plugins.internal.projectAndEnvironmentAt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PackageProjectCommandTest {
    @TempDir
    lateinit var folder: File

    @Test
    fun `can build a project referencing stdlib types`() {
       folder.deployProject("samples/references-std-lib")
       val (project,environment) = projectAndEnvironmentAt(folder.toPath())
       PackageProjectCommand().execute(environment)

       folder.resolve("demo-0.1.0.taxi.zip").shouldExist()
    }
 }

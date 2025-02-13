package lang.taxi.codegen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class InlineFilesTest : DescribeSpec({
    describe("Extracting filenames from comments") {
       it("gets the filename when present") {
          val src = """//@file:someTsFile.ts line23
             |hello
          """.trimMargin()
          InlineFiles.getFilenameFromComment(src)
             .shouldBe("someTsFile.ts line23")
       }
       it("returns default on empty string") {
          val src = ""
          InlineFiles.getFilenameFromComment(src, "default")
             .shouldBe("default")
       }
       it("returns default on when not present") {
          val src = "Hello"
          InlineFiles.getFilenameFromComment(src, "default")
             .shouldBe("default")
       }
    }
 })

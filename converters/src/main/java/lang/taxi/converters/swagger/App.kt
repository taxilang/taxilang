package lang.taxi.converters.swagger

import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.Charset

fun main(args: Array<String>) {
   val input = args[0]
   val output = args[1]
   val taxiDef = SwaggerConverter().toTaxiTypes(File(input).inputStream())

   FileUtils.writeStringToFile(File(output), taxiDef, Charset.defaultCharset())
}

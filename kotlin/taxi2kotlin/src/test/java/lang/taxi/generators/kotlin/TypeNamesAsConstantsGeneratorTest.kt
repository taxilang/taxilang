package lang.taxi.generators.kotlin

import com.winterbe.expekt.should
import lang.taxi.types.QualifiedName
import org.junit.jupiter.api.Test

class TypeNamesAsConstantsGeneratorTest {

   @Test
   fun generatesConstantsObjectsCorrectly() {
      val generator = TypeNamesAsConstantsGenerator()
      generator.asConstant(QualifiedName.from("com.foo.Bar"))
      generator.asConstant(QualifiedName.from("com.foo.Baz"))
      generator.asConstant(QualifiedName.from("io.foo.Baz"))

      val expected = """package taxi.generated

import kotlin.String

object TypeNames {
  object com {
    object foo {
      const val Bar: String = "com.foo.Bar"

      const val Baz: String = "com.foo.Baz"
    }
  }

  object io {
    object foo {
      const val Baz: String = "io.foo.Baz"
    }
  }
}
"""
      val source = generator.generate()
      source.content.should.equal(expected)
   }
}

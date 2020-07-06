package lang.taxi.generators.kotlin

import com.squareup.kotlinpoet.*
import lang.taxi.generators.WritableSource
import lang.taxi.types.QualifiedName
import lang.taxi.utils.takeHead

private class StaticNameWrapper(val name: String) {
   private val children = mutableMapOf<String, StaticNameWrapper>()
   private val members = mutableListOf<QualifiedName>()

   fun resolve(namespace: String): StaticNameWrapper {
      val (nextNamespace, remaining) = namespace.split(".").takeHead()
      val child = children.getOrPut(nextNamespace) {
         StaticNameWrapper(nextNamespace)
      }
      return if (remaining.isEmpty()) {
         child
      } else {
         child.resolve(remaining.joinToString("."))
      }

   }

   fun addMember(name: QualifiedName) {
      this.members.add(name)
   }

   fun buildTypeSpec(): TypeSpec {
      val builder = TypeSpec.objectBuilder(name)
      this.children.values.forEach { builder.addType(it.buildTypeSpec()) }
      this.members.forEach { name ->
         builder.addProperty(PropertySpec.builder(name.typeName, String::class, KModifier.CONST)
            .initializer("%S", name.fullyQualifiedName)
            .build()
         )
      }
      return builder.build()
   }
}


/**
 * This class generates objects containing either more nested objects,
 * or static constants of the names of types.
 * This allows type-safe compilation of static name references, and avoids
 * magic strings in annotations.
 * ie
 *
 * instead of this:
 * ```
 * @DataType("com.foo.bar.Baz")
 * ```
 * you'll get:
 * ```
 * @DataType(TypeNames.com.foo.bar.Baz)
 * ```
 */
class TypeNamesAsConstantsGenerator(val topLevelPackage: String = "") {
   private val topLevelWrapper = StaticNameWrapper("TypeNames")

   fun asConstant(qualifiedName: QualifiedName): MemberName {
      topLevelWrapper.resolve(qualifiedName.namespace).addMember(qualifiedName)
      return MemberName(ClassName(topLevelPackage, "TypeNames", qualifiedName.namespace), qualifiedName.typeName)
   }

   fun generate(): WritableSource {
      // Note: Cheating a little bit here, as we're using the Taxi qualified name
      // around teh topLevelWrapper - which isn't a type at all.
      val wrapperQualifiedName = QualifiedName(topLevelPackage, topLevelWrapper.name)
      val fileSpec = FileSpec.get(topLevelPackage, topLevelWrapper.buildTypeSpec())
      return FileSpecWritableSource(wrapperQualifiedName, fileSpec)
   }

}

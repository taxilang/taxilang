package lang.taxi.generators.kotlin

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import lang.taxi.TaxiDocument
import lang.taxi.generators.*
import lang.taxi.jvm.common.PrimitiveTypes
import lang.taxi.types.*
import lang.taxi.utils.log
import java.nio.file.Path
import java.nio.file.Paths


class KotlinGenerator : ModelGenerator {
   // TODO : This really shouldn't be a field.
   private lateinit var processorHelper: ProcessorHelper

   companion object {
      val kotlinPrimtivies = listOf(String::class, Int::class, Boolean::class)
         .map { it.javaObjectType.name to it }
         .toMap()

   }

   override fun generate(taxi: TaxiDocument, processors: List<Processor>, environment: TaxiEnvironment): List<WritableSource> {
      // TODO : Shouldn't be assinging a field here - should be passing it through
      this.processorHelper = ProcessorHelper(processors)
      return taxi.types.mapNotNull { generateType(it) }
   }

   private fun generateType(type: Type): WritableSource? {
      return when (type) {
         is ObjectType -> generateType(type)
         is TypeAlias -> generateType(type)
         is EnumType -> generateType(type)
         else -> TODO("Type ${type.javaClass.name} not yet supported")
      }
   }

   private fun generateType(type: EnumType): WritableSource? {
      if (type.values.isEmpty()) {
         log().info("Not generating enum ${type.qualifiedName} as it has no enum properties")
         return null
      }
      val builder = TypeSpec.enumBuilder(type.className())
      type.values.forEach { builder.addEnumConstant(it.name) }
      return FileSpecWritableSource.from(type.qualifiedName, builder.build())
   }

   private fun generateType(type: ObjectType): WritableSource {
      val qualifiedName = type.toQualifiedName()
      val properties = mutableListOf<PropertySpec>()
      val specBuilder = TypeSpec.classBuilder(type.className())
         .addModifiers(KModifier.DATA)
         .primaryConstructor(
            FunSpec.constructorBuilder().apply {
               type.fields.forEach { field ->
                  // Note - because we're building a data class, we need
                  // to create "val xxxx:Foo", which is BOTH a constructor param
                  // and a property.  We add the constructor param here, and collect
                  // the property for later
                  val javaType = getJavaType(field.type)
                  properties.add(processorHelper
                     .processField(PropertySpec.builder(field.name, javaType).initializer(field.name), field)
                     .build())
                  this.addParameter(field.name, javaType)
               }
            }.build()
         )
         .addProperties(properties)
      val spec = processorHelper.processType(specBuilder, type).build()
      return FileSpecWritableSource.from(qualifiedName.namespace, spec)
   }

   private fun generateType(typeAlias: TypeAlias): WritableSource {
      val typeAliasQualifiedName = typeAlias.toQualifiedName()
      val typeAliasSpec = TypeAliasSpec
         .builder(typeAliasQualifiedName.typeName, getJavaType(typeAlias.aliasType!!))
         .build()

      val fileSpec = FileSpec.builder(typeAliasQualifiedName.namespace, typeAliasQualifiedName.typeName)
         .addTypeAlias(typeAliasSpec)
         .build()
      return FileSpecWritableSource(typeAliasQualifiedName, fileSpec)
   }

   private fun getJavaType(type: Type): TypeName {
      return when (type) {
         is PrimitiveType -> typeNameOf(PrimitiveTypes.getJavaType(type))
         is ArrayType -> {
            val innerType = getJavaType(type.type)
            List::class.asClassName().plusParameter(innerType)
         }
         else -> ClassName.bestGuess(type.qualifiedName)
//            else -> TODO("getJavaType for type ${type.javaClass.name} not yet supported")
      }
   }

   private fun typeNameOf(javaType: Class<*>): TypeName {
      return preferKotlinType(javaType.asTypeName())
   }

   private fun preferKotlinType(typeName: TypeName): TypeName {
      return if (kotlinPrimtivies.containsKey(typeName.toString())) {
         kotlinPrimtivies[typeName.toString()]!!.asTypeName()
      } else {
         typeName
      }
   }


}

fun Named.className(): ClassName {
   return this.toQualifiedName().asClassName()
}

fun QualifiedName.asClassName(): ClassName {
   return ClassName.bestGuess(this.toString())
}

data class StringWritableSource(val name: QualifiedName, override val content: String) : WritableSource {
   override val path: Path = name.toPath()
}

data class FileSpecWritableSource(val qualifiedName: QualifiedName, val spec: FileSpec) : WritableSource {
   override val path: Path = qualifiedName.toPath()
   override val content: String = spec.toString()

   companion object {
      fun from(packageName: String, spec: TypeSpec): FileSpecWritableSource {
         val qualifiedName = QualifiedName(packageName, spec.name!!)
         val fileSpec = FileSpec.get(packageName, spec)
         return FileSpecWritableSource(qualifiedName, fileSpec)
      }
   }
}
private fun QualifiedName.toPath(): Path {
   val rawPath = this.toString().split(".").fold(Paths.get("")) { path, part -> path.resolve(part) }
   val pathWithSuffix = rawPath.resolveSibling(rawPath.fileName.toString() + ".kt")
   return pathWithSuffix
}

internal class ProcessorHelper(private val processors: List<Processor>) {

   inline fun <reified KBuilderType> processType(builder: KBuilderType, type: Type): KBuilderType {
      return this.processors
         .asSequence()
         .filterIsInstance<TypeProcessor<KBuilderType>>()
         .fold(builder) { acc: KBuilderType, fieldProcessor: TypeProcessor<KBuilderType> -> fieldProcessor.process(acc, type) }
   }

   inline fun <reified KBuilderType> processField(builder: KBuilderType, field: Field): KBuilderType {
      return this.processors
         .asSequence()
         .filterIsInstance<FieldProcessor<KBuilderType>>()
         .fold(builder) { acc: KBuilderType, fieldProcessor: FieldProcessor<KBuilderType> -> fieldProcessor.process(acc, field) }
   }
}

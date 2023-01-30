package lang.taxi.generators.kotlin

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import lang.taxi.TaxiDocument
import lang.taxi.annotations.DataType
import lang.taxi.generators.FieldProcessor
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.Processor
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.generators.TypeProcessor
import lang.taxi.generators.WritableSource
import lang.taxi.jvm.common.PrimitiveTypes
import lang.taxi.types.*
import java.nio.file.Path
import java.nio.file.Paths


class KotlinGenerator(private val typeNamesTopLevelPackageName: String = "taxi.generated") : ModelGenerator {
   // TODO : This really shouldn't be a field.
   private lateinit var processorHelper: ProcessorHelper

   companion object {
      val kotlinPrimitives = listOf(String::class, Int::class, Boolean::class)
         .map { it.javaObjectType.name to it }
         .toMap()

   }

   override fun generate(
      taxi: TaxiDocument,
      processors: List<Processor>,
      environment: TaxiProjectEnvironment
   ): List<WritableSource> {
      // TODO : Shouldn't be assigning a field here - should be passing it through
      this.processorHelper = ProcessorHelper(processors)
      val typeNameConstantsGenerator = TypeNamesAsConstantsGenerator(typeNamesTopLevelPackageName)
      return taxi.types
         // Hack - exclude taxi.stdlib, to avoid the noise of writing annotation classes right now.
         // We should implement this propertly
         .filter { it.toQualifiedName().namespace != "taxi.stdlib" }
         .mapNotNull { generateType(it, typeNameConstantsGenerator) } +
         typeNameConstantsGenerator.generate()
   }

   private fun generateType(type: Type, typeNamesAsConstantsGenerator: TypeNamesAsConstantsGenerator): WritableSource? {
      return when (type) {
         is ObjectType -> generateType(type, typeNamesAsConstantsGenerator)
         is TypeAlias -> generateType(type, typeNamesAsConstantsGenerator)
         is EnumType -> generateType(type, typeNamesAsConstantsGenerator)
         is AnnotationType -> generateType(type, typeNamesAsConstantsGenerator)
         else -> TODO("Type ${type.javaClass.name} not yet supported")
      }
   }

   private fun generateType(
      type: AnnotationType,
      typeNamesAsConstantsGenerator: TypeNamesAsConstantsGenerator
   ): WritableSource? {
      val builder = TypeSpec.annotationBuilder(type.className())
      type.fields.forEach { field ->
         builder.addProperty(PropertySpec.builder(field.name, getJavaType(field.type)).build())
      }
      return FileSpecWritableSource.from(type.toQualifiedName(), builder.build())
   }

   private fun generateType(
      type: EnumType,
      typeNamesAsConstantsGenerator: TypeNamesAsConstantsGenerator
   ): WritableSource? {
      // Handle inherited enums as type aliases
      if (type.baseEnum != type) {
         return generateInheritedEnum(type, typeNamesAsConstantsGenerator)
      }

      val builder = TypeSpec.enumBuilder(type.className())
         .addAnnotation(
            AnnotationSpec.builder(DataType::class)
               .addMember("value = %M", typeNamesAsConstantsGenerator.asConstant(type.toQualifiedName()))
               .addMember("imported = %L", true)
               .build()
         )

      val hasValues = type.values.any { it.value != it.name }
      if (hasValues) {
         val valueType = type.values.first { it.value != it.name }
            .value::class
         builder.primaryConstructor(
            FunSpec.constructorBuilder()
               .addParameter("value", valueType)
               .build()
         )
            .addProperty(
               PropertySpec.builder("value", valueType)
                  .initializer("value")
                  .build()
            )
      }

      type.values.forEach { enumValue ->
         if (hasValues) {
            val typeSpec = TypeSpec.anonymousClassBuilder()

            when (enumValue.value) {
               is String -> typeSpec.addSuperclassConstructorParameter("%S", enumValue.value)
               is Number -> typeSpec.addSuperclassConstructorParameter("%L", enumValue.value)
               else -> TODO("Code generation for enum value of type ${enumValue.value::class.java} not yet supported")
            }
            builder.addEnumConstant(enumValue.name, typeSpec.build())
         } else {
            builder.addEnumConstant(enumValue.name)
         }

      }

      return FileSpecWritableSource.from(type.toQualifiedName(), builder.build())
   }

   private fun generateType(
      type: ObjectType,
      typeNamesAsConstantsGenerator: TypeNamesAsConstantsGenerator
   ): WritableSource {
      val qualifiedName = type.toQualifiedName()


      if (type.allFields.isEmpty()) {
         return generateScalarType(type, typeNamesAsConstantsGenerator)
      }

      val properties = mutableListOf<PropertySpec>()
      val superclassProperties = mutableListOf<Field>()
      val specBuilder = TypeSpec.classBuilder(type.className())
         .addModifiers(KModifier.OPEN)
         .addAnnotation(
            AnnotationSpec.builder(DataType::class)
               .addMember("value = %M", typeNamesAsConstantsGenerator.asConstant(type.toQualifiedName()))
               .addMember("imported = %L", true)
               .build()
         )
         .primaryConstructor(
            FunSpec.constructorBuilder().apply {
               type.allFields.forEach { field ->
                  // Note - because we're building a data class, we need
                  // to create "val xxxx:Foo", which is BOTH a constructor param
                  // and a property.  We add the constructor param here, and collect
                  // the property for later
                  val javaType = getJavaType(field.type).let { typeName ->
                     if (field.nullable) {
                        typeName.copy(nullable = true)
                     } else {
                        typeName
                     }
                  }

                  this.addParameter(field.name, javaType)
                  if (type.fields.contains(field)) {
                     properties.add(
                        processorHelper
                           .processField(PropertySpec.builder(field.name, javaType).initializer(field.name), field)
                           .build()
                     )
                  } else {
                     // This field is inherited
                     superclassProperties.add(field)
                  }


               }
            }.build()
         )
         .addProperties(properties)
      if (type.inheritsFrom.size == 1) {
         val superType = type.inheritsFrom.first()
         if (willGenerateAsInterface(superType)) {
            specBuilder.addSuperinterface(getJavaType(superType))
         } else {
            specBuilder.superclass(getJavaType(superType))
            superclassProperties.forEach { superclassProperty ->
               specBuilder.addSuperclassConstructorParameter(
                  "%N = %N",
                  superclassProperty.name,
                  superclassProperty.name
               )
            }

         }
      } else if (type.inheritsFrom.size > 1) {
         error("Types with multiple inheritence is not supported")
      }
      val spec = processorHelper.processType(specBuilder, type).build()
      return FileSpecWritableSource.from(qualifiedName, spec)
   }

   private fun willGenerateAsInterface(type: Type): Boolean {
      // This will likely evolve.  For now, we treat empty types
      // as interfaces.
      return type is ObjectType && type.allFields.isEmpty()
   }

   private fun generateScalarType(
      type: ObjectType,
      typeNamesAsConstantsGenerator: TypeNamesAsConstantsGenerator
   ): WritableSource {
      return if (type.inheritsFromPrimitive) {
         require(type.inheritsFrom.size <= 1) { "Don't know how to generate a scalar type that inherits from multiple types" }
         generateScalarAsPrimitiveTypeAlias(type, typeNamesAsConstantsGenerator)
      } else {
         generateScalarAsInterface(type, typeNamesAsConstantsGenerator)
      }
   }

   private fun generateScalarAsInterface(type: ObjectType, typeNames: TypeNamesAsConstantsGenerator): WritableSource {
      val name = type.toQualifiedName()
      val spec = TypeSpec
         .interfaceBuilder(name.asClassName())
         .addAnnotation(
            AnnotationSpec.builder(DataType::class)
               .addMember("value = %M", typeNames.asConstant(type.toQualifiedName()))
               .addMember("imported = %L", true)
               .build()
         )
         .build()
      return FileSpecWritableSource.from(name, spec)
   }

   private fun generateScalarAsPrimitiveTypeAlias(
      type: ObjectType,
      typeNames: TypeNamesAsConstantsGenerator
   ): FileSpecWritableSource {
      val inheritsFrom = type.inheritsFrom.first()

      val typeAliasQualifiedName = type.toQualifiedName()
      val javaType = try {
         getJavaType(inheritsFrom)
      } catch (e: Exception) {
         throw CodeGenerationFailedException("Failed to generate Kotlin code for type ${type.qualifiedName}: ${e.message ?: "A ${e::class.simpleName} error was thrown"}")
      }
      val typeAliasSpec = TypeAliasSpec
         .builder(typeAliasQualifiedName.typeName, javaType)
         .addAnnotation(
            AnnotationSpec.builder(DataType::class)
               .addMember("value = %M", typeNames.asConstant(type.toQualifiedName()))
               .addMember("imported = %L", true)
               .build()
         )
         .build()

      val fileSpec = FileSpec.builder(typeAliasQualifiedName.namespace, typeAliasQualifiedName.typeName)
         .addTypeAlias(typeAliasSpec)
         .build()
      return FileSpecWritableSource(typeAliasQualifiedName, fileSpec)
   }

   private fun generateInheritedEnum(type: EnumType, typeNames: TypeNamesAsConstantsGenerator): WritableSource {
      val typeAliasQualifiedName = type.toQualifiedName()
      val baseEnumQualifiedName = type.baseEnum!!
      val typeAliasSpec = TypeAliasSpec
         .builder(typeAliasQualifiedName.typeName, getJavaType(baseEnumQualifiedName))
         .addAnnotation(
            AnnotationSpec.builder(DataType::class)
               .addMember("value = %M", typeNames.asConstant(typeAliasQualifiedName))
               .addMember("imported = %L", true)
               .build()
         )
         .build()

      val fileSpec = FileSpec.builder(typeAliasQualifiedName.namespace, typeAliasQualifiedName.typeName)
         .addTypeAlias(typeAliasSpec)
         .build()
      return FileSpecWritableSource(typeAliasQualifiedName, fileSpec)
   }

   private fun generateType(typeAlias: TypeAlias, typeNames: TypeNamesAsConstantsGenerator): WritableSource {
      val typeAliasQualifiedName = typeAlias.toQualifiedName()
      val typeAliasSpec = TypeAliasSpec
         .builder(typeAliasQualifiedName.typeName, getJavaType(typeAlias.aliasType!!))
         .addAnnotation(
            AnnotationSpec.builder(DataType::class)
               .addMember("value = %M", typeNames.asConstant(typeAlias.toQualifiedName()))
               .addMember("imported = %L", true)
               .build()
         )
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
      return if (kotlinPrimitives.containsKey(typeName.toString())) {
         kotlinPrimitives[typeName.toString()]!!.asTypeName()
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
      fun from(qualifiedName: QualifiedName, spec: TypeSpec): FileSpecWritableSource {
         val fileSpec = FileSpec.get(qualifiedName.namespace, spec)
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
         .fold(builder) { acc: KBuilderType, fieldProcessor: TypeProcessor<KBuilderType> ->
            fieldProcessor.process(
               acc,
               type
            )
         }
   }

   inline fun <reified KBuilderType> processField(builder: KBuilderType, field: Field): KBuilderType {
      return this.processors
         .asSequence()
         .filterIsInstance<FieldProcessor<KBuilderType>>()
         .fold(builder) { acc: KBuilderType, fieldProcessor: FieldProcessor<KBuilderType> ->
            fieldProcessor.process(
               acc,
               field
            )
         }
   }
}

class CodeGenerationFailedException(message: String) : RuntimeException(message)

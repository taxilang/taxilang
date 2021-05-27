package lang.taxi.generators.kotlin

import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmAnnotationArgument
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import lang.taxi.annotations.DataType
import org.reflections8.Reflections
import org.reflections8.scanners.SubTypesScanner
import org.reflections8.scanners.TypeAnnotationsScanner
import org.reflections8.util.ClasspathHelper
import org.reflections8.util.ConfigurationBuilder
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

class TypeAliasRegister private constructor(classes: Collection<Class<*>>) {
   fun findDataType(parameter: KParameter?): DataType? {
      if (parameter == null) {
         return null
      }
      return TypeAliasNameFinder.findTypeAliasName(parameter)?.let { typeAliasName ->
         findDataType(typeAliasName)
      }
   }

   fun findDataType(type: KType): DataType? {
      return TypeAliasNameFinder.findTypeAliasName(type)?.let { typeAliasName -> findDataType(typeAliasName) }
   }

   fun findDataType(kotlinProperty: KCallable<*>?): DataType? {
      if (kotlinProperty == null) return null
      val typeAliasName = when (kotlinProperty) {
         is KProperty<*> -> TypeAliasNameFinder.findTypeAliasName(kotlinProperty)
         else -> null
      } ?: return null
      return findDataType(typeAliasName)
   }

   private fun findDataType(typeAliasName: String): DataType? {
      val annotatedType = typesWithMeta[typeAliasName]
      return annotatedType?.annotation
   }

   private val typesWithMeta = classes
      .map { clazz -> clazz to KotlinClassMetadata.read(clazz.readMetadata()) }
      .filter { (_, value) -> value is KotlinClassMetadata.FileFacade }
      .flatMap { (clazz, metadata) ->
         val typeAliasesWithDataType = (metadata as KotlinClassMetadata.FileFacade).toKmPackage()
            .typeAliases
            .mapNotNull { typeAlias ->
               val annotation = typeAlias.annotations.dataTypeAnnotation()
               if (annotation != null) {
                  Triple(clazz, typeAlias, annotation)
               } else {
                  null
               }
            }
            .map { (clazz, typeAlias, dataType) ->
               // Compiler is being weird, and clazz.packageName is throwing an error
               val packageName = clazz.`package`.name
               AnnotatedTypeAlias(packageName + "." + typeAlias.name, dataType)
            }
         typeAliasesWithDataType
      }
      .associateBy { it.qualifiedName }

   companion object {
      private val registeredPackageNames = mutableListOf<String>()

      /**
       * Main way to register a package for scanning in production apps.
       * Call with registerPackage("foo.bar.baz").
       */
      fun registerPackage(packageName: String) = registeredPackageNames.add(packageName)

      fun forRegisteredPackages():TypeAliasRegister = forPackageNames(registeredPackageNames)
      fun empty(): TypeAliasRegister {
         return TypeAliasRegister(emptyList())
      }

      fun forPackageNames(packageNames: List<String>): TypeAliasRegister {
         val urls = packageNames.flatMap { ClasspathHelper.forPackage(it) }
            .toTypedArray()
         val reflections = Reflections(
            ConfigurationBuilder()
               .setUrls(*urls)
               .setScanners(TypeAnnotationsScanner(), SubTypesScanner())
         )
         val types = reflections.getTypesAnnotatedWith(Metadata::class.java)
         return TypeAliasRegister(types)
      }
   }
}


data class AnnotatedTypeAlias(val qualifiedName: String, val annotation: DataType)

private val dataTypeClassName = DataType::class.qualifiedName?.replace(".", "/")!!
private val dataClassConstructor = DataType::class.primaryConstructor!!
private val dataClassConstructorParams = dataClassConstructor.parameters.associateBy { it.name!! }

private fun List<KmAnnotation>.dataTypeAnnotation(): DataType? {
   val annotations = this.filter { annotation -> annotation.className == dataTypeClassName }
   if (annotations.isEmpty()) {
      return null
   }
   val annotation = annotations.first()
   val args = annotation.arguments.map { (name, value) ->
      val param = dataClassConstructorParams[name]!!
      param to when (value) {
         is KmAnnotationArgument.StringValue -> value.value
         is KmAnnotationArgument.BooleanValue -> value.value
         else -> error("Annotation type of ${value::class.simpleName} not supported")
      } as Any
   }.toMap()
   val dataType: DataType = dataClassConstructor.callBy(args)
   return dataType
}

private fun Class<*>.readMetadata(): KotlinClassHeader {
   return getAnnotation(Metadata::class.java).run {
      KotlinClassHeader(kind, metadataVersion, data1, data2, extraString, packageName, extraInt)
   }
}

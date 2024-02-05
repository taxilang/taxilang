package lang.taxi.generators.kotlin

import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmAnnotationArgument
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import lang.taxi.annotations.DataType
import lang.taxi.utils.log
import org.reflections8.Reflections
import org.reflections8.scanners.SubTypesScanner
import org.reflections8.scanners.TypeAnnotationsScanner
import org.reflections8.util.ClasspathHelper
import org.reflections8.util.ConfigurationBuilder
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

class TypeAliasRegister private constructor(classes: List<Class<*>> = emptyList()) {

   private val typesWithMeta: MutableMap<String, AnnotatedTypeAlias> = mutableMapOf()
   private val registeredPackages: MutableSet<String> = mutableSetOf()

   init {
      registerClasses(classes)
   }


   private fun registerPackage(packageName: String) {
      val urls = ClasspathHelper.forPackage(packageName)
         .toTypedArray()
      val reflections = Reflections(
         ConfigurationBuilder()
            .setUrls(*urls)
            .setScanners(TypeAnnotationsScanner(), SubTypesScanner())
      )
      val types = reflections.getTypesAnnotatedWith(Metadata::class.java)
      registerClasses(types)
      registeredPackages.add(packageName)
   }

   private fun registerClasses(classes: Collection<Class<*>>) {
      val classesAndAliases = classes
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
      this.typesWithMeta.putAll(classesAndAliases)
   }

   fun findDataType(parameter: KParameter?): AnnotatedTypeAlias? {
      if (parameter == null) {
         return null
      }
      return TypeAliasNameFinder.findTypeAliasName(parameter)?.let { typeAliasName ->
         findDataType(typeAliasName)
      }
   }

   fun findDataType(type: KType): AnnotatedTypeAlias? {

      return TypeAliasNameFinder.findTypeAliasName(type)?.let { typeAliasName ->
         val nameParts = typeAliasName.split(".")

         // Don't try to register types in the root package.
         if (nameParts.size > 1) {
            val packageName = nameParts.take(nameParts.size - 1).joinToString(".")
            if (!registeredPackages.contains(packageName)) {
               // Register the package now
               registerPackage(packageName)
            }

         }

         findDataType(typeAliasName)
      }
   }

   fun findDataType(kotlinProperty: KCallable<*>?): AnnotatedTypeAlias? {
      if (kotlinProperty == null) return null
      val typeAliasName = when (kotlinProperty) {
         is KProperty<*> -> TypeAliasNameFinder.findTypeAliasName(kotlinProperty)
         else -> null
      } ?: return null
      return findDataType(typeAliasName)
   }

   private fun findDataType(typeAliasName: String): AnnotatedTypeAlias? {
      val annotatedType = typesWithMeta[typeAliasName]
      return annotatedType
   }


   companion object {
      private val registeredPackageNames = mutableListOf<String>()
      private object MapKey {}
      private val currentRegister = ConcurrentHashMap<MapKey,TypeAliasRegister>()

      /**
       * Main way to register a package for scanning in production apps.
       * Call with registerPackage("foo.bar.baz").
       */
      @JvmStatic
      fun registerPackage(packageName: String): Boolean {
         log().info("Registering package $packageName for type alias detection")
         val result = registeredPackageNames.add(packageName)
         // Invalidate the cache, so we rebuild next time we're asked.
         currentRegister.remove(MapKey)
         return result
      }

      @JvmStatic
      fun registerPackage(vararg packageClasses: KClass<*>) {
         packageClasses.forEach {
            registerPackage(it.java.packageName)
         }
      }

      @JvmStatic
      fun forRegisteredPackages(): TypeAliasRegister {
         return currentRegister.getOrPut(MapKey) {
            forPackageNames(registeredPackageNames)
         }
      }

      @JvmStatic
      fun empty(): TypeAliasRegister {
         return TypeAliasRegister(emptyList())
      }

      @JvmStatic
      fun forPackageNames(packageNames: List<String>): TypeAliasRegister {
         val urls = packageNames.flatMap { ClasspathHelper.forPackage(it) }
            .toTypedArray()
         val reflections = Reflections(
            ConfigurationBuilder()
               .setUrls(*urls)
               .setScanners(TypeAnnotationsScanner(), SubTypesScanner())
         )
         val types = reflections.getTypesAnnotatedWith(Metadata::class.java)
         return TypeAliasRegister(types.toList())
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

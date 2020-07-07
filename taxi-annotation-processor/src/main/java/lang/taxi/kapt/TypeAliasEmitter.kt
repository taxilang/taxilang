package lang.taxi.kapt

import com.google.auto.service.AutoService
import lang.taxi.annotations.DataType
import me.eugeniomarletti.kotlin.metadata.KotlinFileMetadata
import me.eugeniomarletti.kotlin.metadata.KotlinMetadataUtils
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.metadata.shadow.serialization.deserialization.getName
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import java.io.File
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

fun String.quoted(): String = "\"$this\""
data class ParsedAnnotation(
   val name: String,
   val args: Map<String, String> = emptyMap()
) {
   fun annotationArgsJavaSource(packageName: String, simpleName: String): String {
      val argsMapName = this.argsMapName(packageName, simpleName)
      val source = """
            Map<String,String> $argsMapName = new HashMap<String,String>();
            ${args.map { (key, value) -> "$argsMapName.put(${key.quoted()} , ${value.quoted()});" }.joinToString("\n")}
        """.trimIndent()
      return source
   }

   private fun String.packageEscpaed() = this.replace(".", "_")
   private fun argsMapName(packageName: String, simpleName: String) = "${packageName.packageEscpaed()}_${simpleName}_${this.name.packageEscpaed()}_annotationArgs"


   fun toJavaSource(packageName: String, simpleName: String): String {
      val argsMapName = this.argsMapName(packageName, simpleName)
      return """
            new ParsedAnnotation(
                ${name.quoted()},
                $argsMapName
            )
        """.trimIndent()
   }

   fun arg(name: String): String? = this.args[name]
}

data class KotlinTypeAlias(
   val packageName: String,
   val simpleName: String,
   val resolvedType: String,
   val annotations: Set<out ParsedAnnotation>
) {
   val qualifiedName = "$packageName.$simpleName"

   fun registrationStatement(): String {
      val annotationArgsMaps = this.annotations.map { it.annotationArgsJavaSource(packageName, simpleName) }.joinToString("\n")
      val annotationSource = if (annotations.isEmpty()) {
         "new HashSet<ParsedAnnotation>()"
      } else {
         "Sets.newHashSet(${annotations.map { it.toJavaSource(packageName, simpleName) }.joinToString(", \n")})"
      }
      return """
            $annotationArgsMaps
            TypeAliasRegistry.register(
                new KotlinTypeAlias(
                    ${packageName.quoted()},
                    ${simpleName.quoted()},
                    ${resolvedType.quoted()},
                    $annotationSource
                )
            );
        """.trimIndent()
   }

   fun getAnnotation(qualifiedName: String): ParsedAnnotation? {
      return this.annotations.firstOrNull { it.name == qualifiedName }
   }
}

@AutoService(Processor::class)
class TypeAliasEmitter : KotlinMetadataUtils, KotlinAbstractProcessor() {
   init {
      println("TypeAliaseEmitter initiated")
   }

   override fun process(annotations: Set<TypeElement>, roundEnvironment: RoundEnvironment): Boolean {
      messager.printMessage(Diagnostic.Kind.OTHER, "Looking for TypeAliases")
      val typeAliases = roundEnvironment.rootElements
         .filter { element -> element.kotlinMetadata != null }
         .map { element ->
            val meta = element.kotlinMetadata!!
            val typeAliases = when (meta) {
               is KotlinFileMetadata -> parseAliasesFromMeta(meta, element)
               else -> {
                  messager.printMessage(Diagnostic.Kind.NOTE, "Skipping meta kind ${meta.multiFileClassKind?.javaClass?.name} from elmeent ${element.simpleName}")
                  emptyList()
               }
            }
            element to typeAliases
         }.toMap()

      writeTypeAliases(typeAliases)

      roundEnvironment.getElementsAnnotatedWith(DataType::class.java).forEach {
         messager.printMessage(Diagnostic.Kind.NOTE, "found annotation on type ${it.simpleName}")
      }
      return true
   }

   private fun writeTypeAliases(typeAliases: Map<Element, List<KotlinTypeAlias>>) {
      val typeAliasesByPackageName = typeAliases
         .filterValues { it.isNotEmpty() }
         .map { (_, aliases) ->
            aliases.first().packageName to aliases
         }
         .groupBy { it.first }
         .mapValues { (_, listOfPairs: List<Pair<String, List<KotlinTypeAlias>>>) -> listOfPairs.flatMap { it.second } }

      typeAliasesByPackageName.forEach { (_, aliases) ->
         val (packageName, source) = generateSource(aliases)
         val dirPath = packageName.replace('.', File.separatorChar)
         val filePath = "TypeAliases.java"
         val dir = File(generatedDir, dirPath).also { it.mkdirs() }
         val file = File(dir, filePath)
         file.writeText(source)
      }

   }

   private fun generateSource(aliases: List<KotlinTypeAlias>): Pair<String, String> {
      val packageName = aliases.first().packageName
      val registrationStatements = aliases.map {
         it.registrationStatement()
      }.joinToString("\n\n")

      val source = """
            |package $packageName;
            |
            |import lang.taxi.TypeAliasRegistrar;
            |import lang.taxi.TypeAliasRegistry;
            |import lang.taxi.kapt.ParsedAnnotation;
            |import lang.taxi.kapt.KotlinTypeAlias;
            |import javax.annotation.Generated;
            |import java.util.HashMap;
            |import java.util.HashSet;
            |import java.util.Map;
            |import com.google.common.collect.Sets;
            |
            |@Generated("lang.taxi.TypeAliasEmitter")
            |public class TypeAliases implements TypeAliasRegistrar {
            |   public void register() {
            |       $registrationStatements
            |   }
            |}
        """.trimMargin()
      return packageName to source
   }

   private fun parseAliasesFromMeta(meta: KotlinFileMetadata, element: Element): List<KotlinTypeAlias> {
      messager.printMessage(Diagnostic.Kind.NOTE, "Examining KotlinFileMetadata from element ${element.simpleName}")
      val aliases = meta.data.packageProto.typeAliasOrBuilderList.map { typeAliasMeta ->
         val nameResolver = meta.data.nameResolver

         val packageName = elementUtils.getPackageOf(element).toString()
         val typeAliasName = nameResolver.getName(typeAliasMeta.name).toString()
         val resolvedType = nameResolver.getQualifiedClassName(typeAliasMeta.expandedType.className).replace("/", ".")

         val parsedAnnotations = typeAliasMeta.annotationList.map { annotation ->
            val annotationName = nameResolver.getName(annotation.id).asString().replace("/", ".")
            val arguments = annotation.argumentOrBuilderList.map { argument ->
               val argumentName = nameResolver.getString(argument.nameId)
               val argumentValue = nameResolver.getString(argument.value.stringValue)
               argumentName to argumentValue
            }.toMap()
            ParsedAnnotation(annotationName, arguments)
         }.toSet()
         KotlinTypeAlias(packageName, typeAliasName, resolvedType, parsedAnnotations)
      }
      if (aliases.isNotEmpty()) {
         messager.printMessage(Diagnostic.Kind.NOTE, "Found ${aliases.size} aliases - ${aliases.joinToString(", ") { it.simpleName }}")
      } else {
         messager.printMessage(Diagnostic.Kind.NOTE, "No aliases present")
      }
      return aliases
   }


   override fun getSupportedSourceVersion(): SourceVersion {
      return SourceVersion.latest()
   }

   override fun getSupportedAnnotationTypes(): MutableSet<String> {
      return mutableSetOf(DataType::class.java.name)
   }

}

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

    private fun argsMapName(packageName: String, simpleName: String) = "${packageName.replace(".", "_")}_${simpleName}_annotationArgs"


    fun toJavaSource(packageName: String, simpleName: String): String {
        val argsMapName = this.argsMapName(packageName, simpleName)
        return """
            new ParsedAnnotation(
                ${name.quoted()},
                $argsMapName
            )
        """.trimIndent()
    }
}

data class TypeAlias(
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
                new TypeAlias(
                    ${packageName.quoted()},
                    ${simpleName.quoted()},
                    ${resolvedType.quoted()},
                    $annotationSource
                )
            );
        """.trimIndent()
    }
}

@AutoService(Processor::class)
class TypeAliasEmitter : KotlinMetadataUtils, KotlinAbstractProcessor() {

    override fun process(annotations: Set<TypeElement>, roundEnvironment: RoundEnvironment): Boolean {
        val typeAliases = roundEnvironment.rootElements
                .filter { element -> element.kotlinMetadata != null }
                .map { element ->
                    val meta = element.kotlinMetadata!!
                    val typeAliases = when (meta) {
                        is KotlinFileMetadata -> parseAliasesFromMeta(meta, element)
                        else -> emptyList()
                    }
                    element to typeAliases
                }.toMap()

        writeTypeAliases(typeAliases)
        return true
    }

    private fun writeTypeAliases(typeAliases: Map<Element, List<TypeAlias>>) {
        typeAliases.forEach { (_, aliases) ->
            if (aliases.isNotEmpty()) {
                val (packageName, source) = generateSource(aliases)
                val dirPath = packageName.replace('.', File.separatorChar)
                val filePath = "TypeAliases.java"
                val dir = File(generatedDir, dirPath).also { it.mkdirs() }
                val file = File(dir, filePath)
                file.writeText(source)
            }
        }

    }

    private fun generateSource(aliases: List<TypeAlias>): Pair<String, String> {
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
            |import lang.taxi.kapt.TypeAlias;
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

    private fun parseAliasesFromMeta(meta: KotlinFileMetadata, element: Element): List<TypeAlias> {
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
            TypeAlias(packageName, typeAliasName, resolvedType, parsedAnnotations)
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

package lang.taxi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import lang.taxi.services.Operation
import lang.taxi.services.OperationScope
import lang.taxi.services.Parameter
import lang.taxi.services.QueryOperation
import lang.taxi.services.QueryOperationCapability
import lang.taxi.services.Service
import lang.taxi.services.ServiceMember
import lang.taxi.services.SimpleQueryCapability
import lang.taxi.types.Annotation
import lang.taxi.types.AnnotationType
import lang.taxi.types.AnnotationTypeDefinition
import lang.taxi.types.ArrayType
import lang.taxi.types.CompilationUnit
import lang.taxi.types.EnumDefinition
import lang.taxi.types.EnumType
import lang.taxi.types.EnumValue
import lang.taxi.types.Field
import lang.taxi.types.MapType
import lang.taxi.types.Modifier
import lang.taxi.types.ObjectType
import lang.taxi.types.ObjectTypeDefinition
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias
import lang.taxi.types.TypeAliasDefinition
import lang.taxi.types.TypeKind

// ============================================================
// DTO hierarchy — all annotated with @Serializable.
// Types are represented by their qualified name (a String) wherever
// a reference is stored, breaking cycles.
// ============================================================

@Serializable
sealed class TypeDto {
    abstract val qualifiedName: String
}

@Serializable
@SerialName("ObjectType")
data class ObjectTypeDto(
    override val qualifiedName: String,
    val definition: ObjectTypeDefinitionDto? = null,
) : TypeDto()

@Serializable
data class ObjectTypeDefinitionDto(
    val fields: List<FieldDto> = emptyList(),
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val modifiers: List<String> = emptyList(),
    val inheritsFrom: List<String> = emptyList(),
    val typeDoc: String? = null,
    val typeKind: String = "Type",
    val isAnonymous: Boolean = false,
)

@Serializable
data class FieldDto(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val modifiers: List<String> = emptyList(),
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val typeDoc: String? = null,
)

@Serializable
@SerialName("EnumType")
data class EnumTypeDto(
    override val qualifiedName: String,
    val definition: EnumDefinitionDto? = null,
) : TypeDto()

@Serializable
data class EnumDefinitionDto(
    val values: List<EnumValueDto>,
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val inheritsFrom: List<String> = emptyList(),
    val isLenient: Boolean = false,
    val valueType: String,
    val typeDoc: String? = null,
)

@Serializable
data class EnumValueDto(
    val name: String,
    val value: JsonElement = JsonNull,
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val typeDoc: String? = null,
    val isDefault: Boolean = false,
)

@Serializable
@SerialName("TypeAlias")
data class TypeAliasDto(
    override val qualifiedName: String,
    val definition: TypeAliasDefinitionDto? = null,
) : TypeDto()

@Serializable
data class TypeAliasDefinitionDto(
    val aliasTo: String,
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val typeDoc: String? = null,
)

@Serializable
@SerialName("AnnotationType")
data class AnnotationTypeDto(
    override val qualifiedName: String,
    val definition: AnnotationTypeDefinitionDto? = null,
) : TypeDto()

@Serializable
data class AnnotationTypeDefinitionDto(
    val fields: List<FieldDto> = emptyList(),
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val typeDoc: String? = null,
)

@Serializable
@SerialName("PrimitiveType")
data class PrimitiveTypeDto(
    override val qualifiedName: String,
    val declaration: String,
) : TypeDto()

@Serializable
@SerialName("ArrayType")
data class ArrayTypeDto(
    override val qualifiedName: String,
    val memberType: String,
) : TypeDto()

@Serializable
@SerialName("MapType")
data class MapTypeDto(
    override val qualifiedName: String,
    val keyType: String,
    val valueType: String,
) : TypeDto()

@Serializable
data class AnnotationInstanceDto(
    val name: String,
    val parameters: Map<String, JsonElement> = emptyMap(),
)

// ---- Service DTOs ----

@Serializable
sealed class ServiceMemberDto {
    abstract val name: String
}

@Serializable
@SerialName("Operation")
data class OperationDto(
    override val name: String,
    val scope: String = "read",
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val parameters: List<ParameterDto> = emptyList(),
    val returnType: String,
    val typeDoc: String? = null,
) : ServiceMemberDto()

@Serializable
@SerialName("QueryOperation")
data class QueryOperationDto(
    override val name: String,
    val grammar: String,
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val parameters: List<ParameterDto> = emptyList(),
    val returnType: String,
    val capabilities: List<String> = emptyList(),
    val typeDoc: String? = null,
) : ServiceMemberDto()

@Serializable
data class ParameterDto(
    val name: String,
    val type: String,
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val typeDoc: String? = null,
)

@Serializable
data class ServiceDto(
    val qualifiedName: String,
    val members: List<ServiceMemberDto> = emptyList(),
    val annotations: List<AnnotationInstanceDto> = emptyList(),
    val typeDoc: String? = null,
)

@Serializable
data class TaxiDocumentDto(
    val schemaVersion: String = "1.0",
    val types: List<TypeDto> = emptyList(),
    val services: List<ServiceDto> = emptyList(),
)

// ============================================================
// Serializer / Deserializer
// ============================================================

object TaxiDocumentSerializer {

    val json = Json {
        prettyPrint = true
        encodeDefaults = false
        classDiscriminator = "kind"
    }

    fun serialize(document: TaxiDocument): String =
        json.encodeToString(toDto(document))

    fun deserialize(jsonString: String): TaxiDocument {
        val dto = json.decodeFromString<TaxiDocumentDto>(jsonString)
        return fromDto(dto)
    }

    // ---- TaxiDocument → DTO ----

    private fun toDto(document: TaxiDocument): TaxiDocumentDto = TaxiDocumentDto(
        types = document.types.map { toTypeDto(it) },
        services = document.services.map { toServiceDto(it) },
    )

    private fun toTypeDto(type: Type): TypeDto = when (type) {
        is ObjectType -> ObjectTypeDto(
            qualifiedName = type.qualifiedName,
            definition = type.definition?.let { def ->
                ObjectTypeDefinitionDto(
                    fields = def.fields.map { toFieldDto(it) },
                    annotations = def.annotations.map { toAnnotationDto(it) },
                    modifiers = def.modifiers.map { it.token },
                    inheritsFrom = def.inheritsFrom.map { it.toTypeRefString() },
                    typeDoc = def.typeDoc,
                    typeKind = def.typeKind.name,
                    isAnonymous = def.isAnonymous,
                )
            },
        )

        is EnumType -> EnumTypeDto(
            qualifiedName = type.qualifiedName,
            definition = type.definition?.let { def ->
                EnumDefinitionDto(
                    values = def.values.map { toEnumValueDto(it) },
                    annotations = def.annotations.map { toAnnotationDto(it) },
                    inheritsFrom = def.inheritsFrom.map { it.toTypeRefString() },
                    isLenient = def.isLenient,
                    valueType = def.valueType.toTypeRefString(),
                    typeDoc = def.typeDoc,
                )
            },
        )

        is TypeAlias -> TypeAliasDto(
            qualifiedName = type.qualifiedName,
            definition = type.definition?.let { def ->
                TypeAliasDefinitionDto(
                    aliasTo = def.aliasType.toTypeRefString(),
                    annotations = def.annotations.map { toAnnotationDto(it) },
                    typeDoc = def.typeDoc,
                )
            },
        )

        is AnnotationType -> AnnotationTypeDto(
            qualifiedName = type.qualifiedName,
            definition = type.definition?.let { def ->
                AnnotationTypeDefinitionDto(
                    fields = def.fields.map { toFieldDto(it) },
                    annotations = def.annotations.map { toAnnotationDto(it) },
                    typeDoc = def.typeDoc,
                )
            },
        )

        is PrimitiveType -> PrimitiveTypeDto(
            qualifiedName = type.qualifiedName,
            declaration = type.declaration,
        )

        is ArrayType -> ArrayTypeDto(
            qualifiedName = type.qualifiedName,
            memberType = type.type.toTypeRefString(),
        )

        is MapType -> MapTypeDto(
            qualifiedName = type.qualifiedName,
            keyType = type.keyType.toTypeRefString(),
            valueType = type.valueType.toTypeRefString(),
        )

        else -> throw IllegalArgumentException(
            "Unsupported type kind for serialization: ${type::class.simpleName} (${type.qualifiedName})"
        )
    }

    private fun toFieldDto(field: Field) = FieldDto(
        name = field.name,
        type = field.type.toTypeRefString(),
        nullable = field.nullable,
        modifiers = field.modifiers.map { it.token },
        annotations = field.annotations.map { toAnnotationDto(it) },
        typeDoc = field.typeDoc,
    )

    private fun toEnumValueDto(value: EnumValue) = EnumValueDto(
        name = value.name,
        value = value.value.toJsonElement(),
        annotations = value.annotations.map { toAnnotationDto(it) },
        synonyms = value.synonyms,
        typeDoc = value.typeDoc,
        isDefault = value.isDefault,
    )

    private fun toAnnotationDto(annotation: Annotation) = AnnotationInstanceDto(
        name = annotation.qualifiedName,
        parameters = annotation.parameters.mapValues { (_, v) -> v.toJsonElement() },
    )

    private fun toServiceDto(service: Service) = ServiceDto(
        qualifiedName = service.qualifiedName,
        members = service.members.map { toServiceMemberDto(it) },
        annotations = service.annotations.map { toAnnotationDto(it) },
        typeDoc = service.typeDoc,
    )

    private fun toServiceMemberDto(member: ServiceMember): ServiceMemberDto = when (member) {
        is Operation -> OperationDto(
            name = member.name,
            scope = member.scope.token,
            annotations = member.annotations.map { toAnnotationDto(it) },
            parameters = member.parameters.map { toParameterDto(it) },
            returnType = member.returnType.toTypeRefString(),
            typeDoc = member.typeDoc,
        )

        is QueryOperation -> QueryOperationDto(
            name = member.name,
            grammar = member.grammar,
            annotations = member.annotations.map { toAnnotationDto(it) },
            parameters = member.parameters.map { toParameterDto(it) },
            returnType = member.returnType.toTypeRefString(),
            capabilities = member.capabilities.map { it.asTaxi() },
            typeDoc = member.typeDoc,
        )

        else -> throw IllegalArgumentException("Unsupported service member type: ${member::class.simpleName}")
    }

    private fun toParameterDto(parameter: Parameter) = ParameterDto(
        name = parameter.name ?: "",
        type = parameter.type.toTypeRefString(),
        annotations = parameter.annotations.map { toAnnotationDto(it) },
        typeDoc = parameter.typeDoc,
    )

    // ---- DTO → TaxiDocument ----

    private fun fromDto(dto: TaxiDocumentDto): TaxiDocument {
        val registry = buildTypeRegistry(dto.types)
        val types = resolveTypeDefs(dto.types, registry)
        val services = dto.services.map { fromServiceDto(it, registry) }.toSet()
        return TaxiDocument(types = types, services = services)
    }

    /**
     * Phase 1 — seed the registry:
     * - all PrimitiveTypes (keyed by both qualified name and short declaration)
     * - empty shells for every user-defined type so forward/circular refs resolve
     */
    private fun buildTypeRegistry(typeDtos: List<TypeDto>): MutableMap<String, Type> {
        val registry = mutableMapOf<String, Type>()
        PrimitiveType.values().forEach { p ->
            registry[p.qualifiedName] = p
            registry[p.declaration] = p
        }
        typeDtos.forEach { dto ->
            when (dto) {
                is ObjectTypeDto -> registry[dto.qualifiedName] = ObjectType.undefined(dto.qualifiedName)
                is EnumTypeDto -> registry[dto.qualifiedName] = EnumType.undefined(dto.qualifiedName)
                is TypeAliasDto -> registry[dto.qualifiedName] = TypeAlias.undefined(dto.qualifiedName)
                is AnnotationTypeDto -> registry[dto.qualifiedName] = AnnotationType.undefined(dto.qualifiedName)
                is PrimitiveTypeDto -> registry[dto.qualifiedName] =
                    PrimitiveType.fromDeclaration(dto.declaration)
                is ArrayTypeDto, is MapTypeDto -> { /* resolved on-demand */ }
            }
        }
        return registry
    }

    /**
     * Phase 2 — fill in definitions for each user type.
     * Returns the set of non-primitive types to include in TaxiDocument.
     */
    private fun resolveTypeDefs(typeDtos: List<TypeDto>, registry: MutableMap<String, Type>): Set<Type> {
        typeDtos.forEach { dto ->
            when (dto) {
                is ObjectTypeDto -> {
                    val shell = registry[dto.qualifiedName] as ObjectType
                    dto.definition?.let { def ->
                        shell.definition = ObjectTypeDefinition(
                            fields = def.fields.map { fromFieldDto(it, registry) }.toSet(),
                            annotations = def.annotations.map { fromAnnotationDto(it) }.toSet(),
                            modifiers = def.modifiers.map { Modifier.fromToken(it) },
                            inheritsFrom = def.inheritsFrom.map { resolveTypeRef(it, registry) },
                            typeDoc = def.typeDoc,
                            typeKind = TypeKind.valueOf(def.typeKind),
                            isAnonymous = def.isAnonymous,
                            compilationUnit = CompilationUnit.unspecified(),
                        )
                    }
                }

                is EnumTypeDto -> {
                    val shell = registry[dto.qualifiedName] as EnumType
                    dto.definition?.let { def ->
                        shell.definition = EnumDefinition(
                            values = def.values.map { v ->
                                EnumValue(
                                    name = v.name,
                                    value = v.value.toAny() ?: v.name,
                                    enumValueQualifiedName = EnumValue.enumValueQualifiedName(
                                        shell.toQualifiedName(), v.name
                                    ),
                                    annotations = v.annotations.map { fromAnnotationDto(it) },
                                    synonyms = v.synonyms,
                                    typeDoc = v.typeDoc,
                                    isDefault = v.isDefault,
                                )
                            },
                            annotations = def.annotations.map { fromAnnotationDto(it) },
                            compilationUnit = CompilationUnit.unspecified(),
                            inheritsFrom = def.inheritsFrom.map { resolveTypeRef(it, registry) },
                            isLenient = def.isLenient,
                            valueType = resolveTypeRef(def.valueType, registry),
                            typeDoc = def.typeDoc,
                        )
                    }
                }

                is TypeAliasDto -> {
                    val shell = registry[dto.qualifiedName] as TypeAlias
                    dto.definition?.let { def ->
                        shell.definition = TypeAliasDefinition(
                            aliasType = resolveTypeRef(def.aliasTo, registry),
                            annotations = def.annotations.map { fromAnnotationDto(it) },
                            compilationUnit = CompilationUnit.unspecified(),
                            typeDoc = def.typeDoc,
                        )
                    }
                }

                is AnnotationTypeDto -> {
                    val shell = registry[dto.qualifiedName] as AnnotationType
                    dto.definition?.let { def ->
                        shell.definition = AnnotationTypeDefinition(
                            fields = def.fields.map { fromFieldDto(it, registry) },
                            annotations = def.annotations.map { fromAnnotationDto(it) },
                            typeDoc = def.typeDoc,
                            compilationUnit = CompilationUnit.unspecified(),
                        )
                    }
                }

                is PrimitiveTypeDto, is ArrayTypeDto, is MapTypeDto -> { /* nothing to fill in */ }
            }
        }

        return typeDtos
            .filterNot { it is PrimitiveTypeDto || it is ArrayTypeDto || it is MapTypeDto }
            .mapNotNull { registry[it.qualifiedName] }
            .toSet()
    }

    /**
     * Resolves a type-reference string such as:
     *   "demo.Person"
     *   "lang.taxi.Array<demo.Person>"
     *   "lang.taxi.Map<lang.taxi.String,demo.OrderId>"
     */
    private fun resolveTypeRef(typeRefName: String, registry: MutableMap<String, Type>): Type {
        val qn = QualifiedName.from(typeRefName)
        return when {
            ArrayType.isArrayTypeName(qn.fullyQualifiedName) -> {
                val memberTypeName = qn.parameters.singleOrNull()?.parameterizedName
                    ?: error("Array type reference '$typeRefName' must have exactly one type parameter")
                ArrayType(resolveTypeRef(memberTypeName, registry), CompilationUnit.unspecified())
            }

            MapType.isMapTypeName(qn) -> {
                require(qn.parameters.size == 2) {
                    "Map type reference '$typeRefName' must have exactly two type parameters"
                }
                MapType(
                    keyType = resolveTypeRef(qn.parameters[0].parameterizedName, registry),
                    valueType = resolveTypeRef(qn.parameters[1].parameterizedName, registry),
                    source = CompilationUnit.unspecified(),
                )
            }

            StreamType.isStreamTypeName(qn) -> {
                val memberTypeName = qn.parameters.singleOrNull()?.parameterizedName
                    ?: error("Stream type reference '$typeRefName' must have exactly one type parameter")
                StreamType(resolveTypeRef(memberTypeName, registry), CompilationUnit.unspecified())
            }

            else -> registry[typeRefName]
                ?: registry[qn.typeName]
                ?: error("Unknown type reference: '$typeRefName'")
        }
    }

    private fun fromFieldDto(dto: FieldDto, registry: MutableMap<String, Type>) = Field(
        name = dto.name,
        type = resolveTypeRef(dto.type, registry),
        nullable = dto.nullable,
        annotations = dto.annotations.map { fromAnnotationDto(it) },
        typeDoc = dto.typeDoc,
        compilationUnit = CompilationUnit.unspecified(),
    )

    private fun fromAnnotationDto(dto: AnnotationInstanceDto) = Annotation(
        name = dto.name,
        parameters = dto.parameters.mapValues { (_, v) -> v.toAny() },
    )

    private fun fromServiceDto(dto: ServiceDto, registry: MutableMap<String, Type>) = Service(
        qualifiedName = dto.qualifiedName,
        members = dto.members.map { fromServiceMemberDto(it, registry) },
        annotations = dto.annotations.map { fromAnnotationDto(it) },
        compilationUnits = listOf(CompilationUnit.unspecified()),
        typeDoc = dto.typeDoc,
    )

    private fun fromServiceMemberDto(dto: ServiceMemberDto, registry: MutableMap<String, Type>): ServiceMember =
        when (dto) {
            is OperationDto -> Operation(
                name = dto.name,
                scope = OperationScope.forToken(dto.scope),
                annotations = dto.annotations.map { fromAnnotationDto(it) },
                parameters = dto.parameters.map { fromParameterDto(it, registry) },
                returnType = resolveTypeRef(dto.returnType, registry),
                compilationUnits = listOf(CompilationUnit.unspecified()),
                typeDoc = dto.typeDoc,
            )

            is QueryOperationDto -> QueryOperation(
                name = dto.name,
                grammar = dto.grammar,
                annotations = dto.annotations.map { fromAnnotationDto(it) },
                parameters = dto.parameters.map { fromParameterDto(it, registry) },
                returnType = resolveTypeRef(dto.returnType, registry),
                compilationUnits = listOf(CompilationUnit.unspecified()),
                capabilities = parseCapabilities(dto.capabilities),
                typeDoc = dto.typeDoc,
            )
        }

    private fun fromParameterDto(dto: ParameterDto, registry: MutableMap<String, Type>) = Parameter(
        name = dto.name,
        type = resolveTypeRef(dto.type, registry),
        annotations = dto.annotations.map { fromAnnotationDto(it) },
        constraints = emptyList(),
        typeDoc = dto.typeDoc,
    )

    private fun parseCapabilities(capabilities: List<String>): List<QueryOperationCapability> =
        capabilities.map { symbol ->
            SimpleQueryCapability.values().firstOrNull { it.symbol == symbol }
                ?: error("Unknown query capability: $symbol")
        }
}

// ============================================================
// Extension helpers
// ============================================================

/**
 * Serialization-friendly type-reference string.
 * Generic types include their parameters, e.g. "lang.taxi.Array<demo.Person>".
 */
fun Type.toTypeRefString(): String = when (this) {
    is ArrayType -> "lang.taxi.Array<${this.type.toTypeRefString()}>"
    is MapType -> "lang.taxi.Map<${this.keyType.toTypeRefString()},${this.valueType.toTypeRefString()}>"
    is StreamType -> "lang.taxi.Stream<${this.type.toTypeRefString()}>"
    else -> this.qualifiedName
}

/** Convert a Kotlin [Any?] to a [JsonElement]. */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    else -> JsonPrimitive(this.toString())
}

/** Convert a [JsonElement] back to a plain Kotlin value. */
fun JsonElement.toAny(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> boolean
        intOrNull != null -> int
        longOrNull != null -> long
        doubleOrNull != null -> double
        else -> content
    }

    else -> content
}

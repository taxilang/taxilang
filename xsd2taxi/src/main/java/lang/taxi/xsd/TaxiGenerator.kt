package lang.taxi.xsd

import com.sun.xml.xsom.XSComplexType
import com.sun.xml.xsom.XSComponent
import com.sun.xml.xsom.XSDeclaration
import com.sun.xml.xsom.XSElementDecl
import com.sun.xml.xsom.XSModelGroup
import com.sun.xml.xsom.XSModelGroupDecl
import com.sun.xml.xsom.XSParticle
import com.sun.xml.xsom.XSSchemaSet
import com.sun.xml.xsom.XSSimpleType
import com.sun.xml.xsom.XSType
import com.sun.xml.xsom.XSUnionSimpleType
import com.sun.xml.xsom.XSWildcard
import com.sun.xml.xsom.impl.Ref
import com.sun.xml.xsom.impl.SchemaSetImpl.AnyType
import com.sun.xml.xsom.parser.XSOMParser
import lang.taxi.TaxiDocument
import lang.taxi.generators.FieldName
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.NamespacedType
import lang.taxi.generators.SchemaTypeDeclaration
import lang.taxi.generators.SchemaWriter
import lang.taxi.generators.TypeDefinitionHelper
import lang.taxi.types.ArrayType
import lang.taxi.types.CompilationUnit
import lang.taxi.types.EnumDefinition
import lang.taxi.types.EnumType
import lang.taxi.types.EnumValue
import lang.taxi.types.Field
import lang.taxi.types.FormatsAndZoneOffset
import lang.taxi.types.Modifier
import lang.taxi.types.ObjectType
import lang.taxi.types.ObjectTypeDefinition
import lang.taxi.types.PrimitiveType
import lang.taxi.types.PrimitiveType.Companion.INHERITS_FROM_ANY
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypeDefinition
import lang.taxi.types.UnresolvedImportedType
import lang.taxi.types.UserType
import lang.taxi.utils.log
import lang.taxi.xsd.XsdPrimitives.primtiviesTaxiDoc
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import javax.xml.parsers.SAXParserFactory
import kotlin.io.path.inputStream

data class XsdReaderConfig(
   /**
    * Allows for overriding referenced Xsd's.
    * Sometimes XSD files will reference another XSD, but the
    * path to load it is often invalid.
    *
    * For example. Given an import declaration of:
    *
    * ```
    * 	<xs:import namespace="http://www.fsa.gov.uk/XMLSchema/FSAFeedCommon-v1-2"
    * 		schemaLocation="https://gabriel.fca.org.uk/specifications/MER/DRG/PSD-CommonTypes/v1.2/FSAFeedCommon-v1-2.xsd"/>
    * ```
    *
    * This could be overridden to a local file by specifying:
    * mapOf(
    *   "http://www.fsa.gov.uk/XMLSchema/FSAFeedCommon-v1-2" to Paths.get("./fsa-feed-common.xsd")
    * )
    */
   val xsdImportOverrides: Map<String, Path> = emptyMap(),
   val defaultModelModifiers: List<Modifier> = listOf(Modifier.CLOSED)
) {
//   private val xsdInputSourceCache = mutableMapOf<String, InputSource>()
   fun makeFilePathsRelativeTo(
      /**
       * The directory that config files should be resolved against
       */
      configFilePath: Path
   ): XsdReaderConfig {
      return this.copy(
         xsdImportOverrides = xsdImportOverrides.mapValues { (_, path) ->
            configFilePath.resolve(path)
         }
      )
   }

   companion object {
      val EMPTY = XsdReaderConfig(emptyMap())
   }


   val entityResolver: EntityResolver = EntityResolver { publicId, systemId ->
      if (publicId == null && systemId == null) return@EntityResolver null
      val key = (publicId ?: systemId)!!
      val inputSource = xsdImportOverrides.get(publicId)
         ?.let { path ->
            InputSource(path.inputStream()).apply {
               this.publicId = key
               this.systemId = key
            }

         }
      inputSource
   }
}

class TaxiGenerator(
   private val schemaWriter: SchemaWriter = SchemaWriter(),
   private val config: XsdReaderConfig = XsdReaderConfig.EMPTY,
) {

   private val parsedModelGroups: MutableMap<QualifiedName, ParsedList> = mutableMapOf()
   private val parsedTypes: MutableMap<QualifiedName, Type> = mutableMapOf()
   private val logger = Logger()


   fun generateTaxiDocument(xsSchemaSet: XSSchemaSet): TaxiDocument {
      xsSchemaSet.schemas
         // Don't parse the root xsd namespace
         .filterNot { it.targetNamespace == XsdPrimitives.XML_NAMESPACE }
         .map { schema ->
            schema.types.map { (name, typeDeclaration: XSType) ->
               val qualifiedName = getQualifiedName(typeDeclaration)
               getOrParseType(
                  typeDeclaration,
                  typeDefinitionHelper = TypeDefinitionHelper.forHint(NamespacedType(qualifiedName))
               )
            }
            schema.elementDecls.map { (name, declaration) ->
               val qualifiedName = getQualifiedName(declaration.type, anonymousTypeNamePrefix = name)
               val typeDefinitionHelper = TypeDefinitionHelper.forHint(NamespacedType(qualifiedName))
               getOrParseType(declaration.type, typeDefinitionHelper = typeDefinitionHelper)
            }
         }

      val typesToOutput = parsedTypes
         .filterNot { (name, _) -> name.namespace == SchemaNames.XML_PACKAGE_NAME }
         .filterNot { (name, _) -> name.namespace == PrimitiveType.NAMESPACE }
         .values

      val taxiDoc = listOf(
         primtiviesTaxiDoc,
         XsdAnnotations.annotationsTaxiDoc,
         TaxiDocument(typesToOutput.toSet(), emptySet())
      ).reduce { acc, taxiDocument -> acc.merge(taxiDocument) }

      return taxiDoc
   }

   fun generateTaxiDocument(inputStream: InputStream): TaxiDocument {
      val parser = XSOMParser(SAXParserFactory.newDefaultInstance())
      parser.setAnnotationParser(XsdDocumentationParserFactory())
      parser.entityResolver = config.entityResolver
      parser.errorHandler = SaxErrorHandler()
      parser.parse(inputStream)
      val parsed = parser.result
      return generateTaxiDocument(parsed)
   }

   fun generateAsStrings(inputStream: InputStream): GeneratedTaxiCode {
      val taxiDoc = generateTaxiDocument(inputStream)

      val taxi = schemaWriter.generateSchemas(
         listOf(taxiDoc)
      )
      return GeneratedTaxiCode(
         taxi, logger.messages
      )
   }

   fun generateAsStrings(source: File /* generatorOptions: GeneratorOptions*/): GeneratedTaxiCode {
      return generateAsStrings(source.inputStream())
   }

   private fun shouldCreateSemanticSubtype(type: XSType, typeDefinitionHelper: TypeDefinitionHelper): Boolean {
      return typeDefinitionHelper.considerCreatingSemanticSubtype && getQualifiedNameOrNull(type) != null
   }

   private fun parseComplexType(
      complexType: XSComplexType,
      typeDefinitionHelper: TypeDefinitionHelper
   ): Pair<Type, TypeDefinitionBuilder?> {
      val parsedName = typeDefinitionHelper.suggestName().parameterizedName
      val emptyType = ObjectType(typeDefinitionHelper.suggestName().parameterizedName, null)

      // If we're creating a field, which is a complex type, create a field-specific semantic subtype
      // of the complex type, rather than an entire new type
      if (shouldCreateSemanticSubtype(complexType, typeDefinitionHelper)) {
         val baseTypeQualifiedName = getQualifiedName(complexType)
         val parsedBaseType = getOrParseType(
            complexType,
            typeDefinitionHelper = TypeDefinitionHelper.forHint(NamespacedType(baseTypeQualifiedName))
         )
         val semanticSubType = ObjectType(
            typeDefinitionHelper.suggestName().parameterizedName,
            ObjectTypeDefinition(
               inheritsFrom = listOf(parsedBaseType),
               compilationUnit = CompilationUnit.unspecified()
            )
         )
         return semanticSubType to null
      }

      val definitionBuilder: TypeDefinitionBuilder = {
         var isWildcard = isWildcardType(complexType, typeDefinitionHelper)
         val attributes = parseAttributesToFields(complexType, typeDefinitionHelper)
         val fields = parseTypeBodyToFields(complexType, typeDefinitionHelper, complexType.targetNamespace)
         val allFields = fields + attributes
         val docs = getDocumentation(complexType)


         val baseType = complexType.baseType?.let { baseType ->
            val baseTypeName = getQualifiedName(baseType)
            if (baseTypeName == XsdPrimitives.ANY_TYPE) {
               INHERITS_FROM_ANY
            } else {
               listOf(
                  getOrParseType(
                     baseType,
                     typeDefinitionHelper = TypeDefinitionHelper.forHint(NamespacedType(baseTypeName))
                  )
               )
            }
         } ?: INHERITS_FROM_ANY


         if (isWildcard && allFields.isNotEmpty()) {
            // Will need to revisit this in a future iteration
            log().warn("Type $parsedName has a wildCard content type, along with defied fields.  The content is being dropped in favour of the content.")
            isWildcard = false
         }

         when {
            // Xsd permits inheriting enum classes, adding attributes
            // We need to treat these as a special usecase and build out a composite class
            baseType.isNotEmpty() && baseType.any { it is EnumType } -> {
               buildObjectDefinitionForTypeInheritingEnumClass(
                  typeDefinitionHelper.suggestName(),
                  allFields,
                  baseType,
                  docs
               )
            }

            isWildcard -> {
               ObjectTypeDefinition(
                  allFields.toSet(),
                  compilationUnit = CompilationUnit.unspecified(),
                  inheritsFrom = INHERITS_FROM_ANY,
                  typeDoc = docs,
                  modifiers = config.defaultModelModifiers,
               )
            }

            else -> {
               ObjectTypeDefinition(
                  allFields.toSet(),
                  compilationUnit = CompilationUnit.unspecified(),
                  inheritsFrom = baseType,
                  typeDoc = docs,
                  modifiers = config.defaultModelModifiers,
                  annotations = setOfNotNull(
                     XsdAnnotations.xmlRoot,
                     XsdAnnotations.xmlNamespaceOrNull(complexType.targetNamespace)
                  )
               )
            }
         }
      }
      return emptyType to definitionBuilder
   }

   private fun buildObjectDefinitionForTypeInheritingEnumClass(
      typeName: QualifiedName,
      allFields: List<Field>,
      baseType: List<Type>,
      docs: String?
   ): TypeDefinition {
      require(baseType.size == 1) { "Cannot handle a type inheriting an enum when there are multiple base types" }
      val baseEnum = baseType.first() as EnumType
      val enumFieldName = baseEnum.toQualifiedName().typeName.decapitalize()
      // TODO : Add an Xml body annotation here
      val enumBodyField = Field(
         enumFieldName,
         baseEnum,
         nullable = false,
         annotations = listOf(XsdAnnotations.xmlBody),
         compilationUnit = CompilationUnit.unspecified()

      )
      val compositeFields = allFields + enumBodyField
      return ObjectTypeDefinition(
         compositeFields.toSet(),
         typeDoc = docs,
         compilationUnit = CompilationUnit.unspecified()
      )

   }

   /**
    * Looks for a wildcard (ie., any) type.
    * eg:
    *     <xs:complexType name="xxx">
    *          <xs:sequence>
    *             <xs:any namespace="##any" processContents="lax"/>
    *           </xs:sequence>
    *      </xs:complexType>
    */
   private fun isWildcardType(complexType: XSComplexType, typeDefinitionHelper: TypeDefinitionHelper): Boolean {
      val particle = parseParticle(complexType, typeDefinitionHelper) ?: return false
      return (particle is ParsedList && particle.list.size == 1 && particle.list.first() is ParsedWildcard)
   }

   private fun parseParticle(complexType: XSComplexType, typeDefinitionHelper: TypeDefinitionHelper): ParsedContent? {
      val particle = complexType.contentType.asParticle() ?: return null
      return parseParticle(particle, typeDefinitionHelper, complexType.targetNamespace)
   }

   private fun parseTypeBodyToFields(
      complexType: XSComplexType,
      helper: TypeDefinitionHelper,
      declaringSchemaNamespace: String?
   ): List<Field> {
      val parsedParticle = parseParticle(complexType, helper) ?: return emptyList()
      require(parsedParticle is ParsedList) { "Expected to receive a parsedList here" }
      val fields = parsedParticle.list
         .filterIsInstance<ParsedElement>()
         .map {
            val isArray = it.maxOccurs == -1 || it.maxOccurs > 1
            val typeOrArrayType = if (isArray) {
               ArrayType.of(it.type)
            } else it.type
            val nullable = it.minOccurs == 0 || parsedParticle.compositor == XSModelGroup.Compositor.CHOICE
            Field(
               it.name,
               typeOrArrayType,
               nullable = nullable,
               typeDoc = it.docs,
               annotations = listOfNotNull(XsdAnnotations.xmlNamespaceOrNull(it.elementNamespace)),
               compilationUnit = CompilationUnit.unspecified()
            )
         }
      return fields
   }

   private fun parseAttributesToFields(complexType: XSComplexType, helper: TypeDefinitionHelper): List<Field> {
      val attributes = complexType.declaredAttributeUses?.map { attribute ->
         val typeDoc: String? = getDocumentation(attribute.decl)
         Field(
            name = attribute.decl.name,
            type = getOrParseType(
               attribute.decl.type,
               typeDefinitionHelper = helper.append(FieldName(attribute.decl.name))
            ),
            nullable = !attribute.isRequired,
            annotations = listOf(XsdAnnotations.xmlAttribute),
            typeDoc = typeDoc,
//            defaultValue = attribute.defaultValue?.value ?: attribute.fixedValue?.value,
            compilationUnit = CompilationUnit.unspecified()
         )
      }
         ?: emptyList()
      return attributes
   }

   private fun getDocumentation(component: XSComponent?): String? {
      return component?.annotation?.annotation?.let { annotation ->
         if (annotation is XsdDocumentationElement) {
            annotation.content
         } else {
            null
         }
      }
   }

   private fun parseParticle(
      particle: XSParticle, typeDefinitionHelper: TypeDefinitionHelper,
      declaringSchemaNamespace: String?
   ): ParsedContent {
      return when (val term = particle.term) {
         is XSModelGroup -> parseModelGroup(term, typeDefinitionHelper, declaringSchemaNamespace)
         is XSElementDecl -> parseElement(particle, term, typeDefinitionHelper, declaringSchemaNamespace)
         is XSWildcard -> ParsedWildcard
         is XSModelGroupDecl -> parseModelGroupDeclaration(term, typeDefinitionHelper, declaringSchemaNamespace)
         else -> TODO("parseParticle not implemented for term with class ${term::class.simpleName}")
      }
   }

   private fun parseModelGroupDeclaration(
      term: XSModelGroupDecl,
      typeDefinitionHelper: TypeDefinitionHelper,
      declaringSchemaNamespace: String?
   ): ParsedContent {
      return parsedModelGroups.getOrPut(getQualifiedName(term)) {
         parseModelGroup(term.modelGroup, typeDefinitionHelper, declaringSchemaNamespace) as ParsedList
      }
   }

   private fun parseElement(
      particle: XSParticle,
      term: XSElementDecl,
      typeDefinitionHelper: TypeDefinitionHelper,
      declaringSchemaNamespace: String?,
   ): ParsedElement {
      val schemaMetadata = XsdTaxiTypeDeclarations.getTaxiTypeReference(term.foreignAttributes)
      val typeHelper = when {
         schemaMetadata != null -> TypeDefinitionHelper.forHint(schemaMetadata)
         term.isGlobal -> {
            // For global elements, don't create a semantic subtype wrapper
            // Instead, use the element's type directly
            TypeDefinitionHelper.forHint(NamespacedType(getQualifiedName(term)))
         }

         else -> typeDefinitionHelper.append(FieldName(term.name))
      }
      val type = getOrParseType(
         term.type,
         typeDefinitionHelper = typeHelper
      )
      val docs = getDocumentation(term)

      // Determine the element's namespace
      val elementNamespace = when {
         // If it's a local element declaration, use the declaring schema's namespace
         !term.isGlobal -> declaringSchemaNamespace
         // If it's a global element itself, use its own namespace
         else -> term.targetNamespace
      }

      return ParsedElement(
         term.name,
         type,
         particle.minOccurs.toInt(),
         particle.maxOccurs.toInt(),
         docs,
         elementNamespace
      )
   }


   private fun getOrParseType(
      type: XSType,
      typeDefinitionHelper: TypeDefinitionHelper,
      declarationLocation: SchemaTypeDeclaration.DeclarationLocation = SchemaTypeDeclaration.DeclarationLocation.Model
   ): Type {
      val suggestedName = typeDefinitionHelper.suggestName()
      var definitionBuilder: TypeDefinitionBuilder? = null
      val parsedType = parsedTypes.getOrPut(suggestedName) {
         if (!typeDefinitionHelper.declaresNewType(declarationLocation)) {
            return@getOrPut UnresolvedImportedType(suggestedName.parameterizedName)
         }
         val (typeStub, typeBuilder) = parseType(type, typeDefinitionHelper)
         definitionBuilder = typeBuilder
         typeStub
      }
      definitionBuilder?.let { callback ->
         val typeDef = callback()
         require(parsedType is UserType<*, *>) {
            "Found a builder, but type $suggestedName is of type ${parsedType::class.simpleName}"
         }
         (parsedType as UserType<TypeDefinition, TypeDefinition>).definition = typeDef
      }
      return parsedType
   }

   /**
    * Returns the declared type name in the provided Xsd type if present.
    * If not, then walks up the inheritance chain to return the nearest declared type name, or null
    * if none is found
    */
   private fun getQualifiedNameOrBaseTypeQualifiedNameIfPresent(type: XSType): QualifiedName? {
      val qualifiedName = getQualifiedNameOrNull(type)
      if (qualifiedName != null) return qualifiedName
      return if (type.baseType != null) {
         getQualifiedNameOrBaseTypeQualifiedNameIfPresent(type.baseType)
      } else null
   }

   private fun getQualifiedNameOrNull(type: XSDeclaration, anonymousTypeNamePrefix: String? = null): QualifiedName? {
      val packageName = SchemaNames.schemaNamespaceToPackageName(type.targetNamespace)
      return if (type.name == null && anonymousTypeNamePrefix == null) {
         null
      } else if (type.name == null) {
         QualifiedName(packageName, anonymousTypeNamePrefix!!)
//         QualifiedName(packageName, "$anonymousTypeNamePrefix#AnonymousType")
      } else {
         QualifiedName(packageName, type.name)
      }
   }

   private fun getQualifiedName(type: XSDeclaration, anonymousTypeNamePrefix: String? = null): QualifiedName {
      return getQualifiedNameOrNull(type, anonymousTypeNamePrefix)
         ?: error("Type is anonymous in xsd, and no anonymous typeName prefix was provided")
   }

   private fun parseType(
      type: XSType,
      typeDefinitionHelper: TypeDefinitionHelper,
   ): Pair<Type, TypeDefinitionBuilder?> {
      return when (type) {
         is Ref.ComplexType -> parseComplexType(type.asComplexType(), typeDefinitionHelper)
         is Ref.SimpleType -> parseSimpleType(type.asSimpleType(), typeDefinitionHelper)
         is AnyType -> PrimitiveType.ANY to null
         else -> TODO("xsd parseType not implemented for type with name ${type.name}")
      }

   }

   private fun parseSimpleType(
      simpleType: XSSimpleType,
      typeDefinitionHelper: TypeDefinitionHelper,
   ): Pair<Type, TypeDefinitionBuilder?> {

      if (isEnum(simpleType)) {
         return parseEnumType(typeDefinitionHelper, simpleType) to null
      }
      if (isEnumUnionExtension(simpleType, typeDefinitionHelper = typeDefinitionHelper!!)) {
         return parseEnumUnionExtension(
            typeDefinitionHelper.suggestName(),
            simpleType as XSUnionSimpleType,
            typeDefinitionHelper
         ) to null
      }


      val declaredBaseTypeName = getQualifiedNameOrBaseTypeQualifiedNameIfPresent(simpleType.baseType)
      val declaredTypeQualifiedName = getQualifiedNameOrBaseTypeQualifiedNameIfPresent(simpleType)
      val baseType = when {

         // When we've been provided a type reference, we need to use the underlying simple type
         // as the inherited type.
         XsdPrimitives.isPrimitive(declaredTypeQualifiedName) -> XsdPrimitives.getType(declaredTypeQualifiedName!!)
         typeDefinitionHelper.hasExplicitName && declaredTypeQualifiedName != null -> {
            parseSimpleType(
               simpleType,
               TypeDefinitionHelper.forHint(NamespacedType(declaredTypeQualifiedName))
            ).first
         }


         shouldCreateSemanticSubtype(simpleType, typeDefinitionHelper) -> {
            getOrParseType(simpleType, TypeDefinitionHelper.forHint(NamespacedType(getQualifiedName(simpleType))))
         }

         XsdPrimitives.isPrimitive(declaredBaseTypeName) -> XsdPrimitives.getType(declaredBaseTypeName!!)

         else -> {
            val baseTypeDeclaration = simpleType.baseType
            val parsedBaseType = getOrParseType(
               baseTypeDeclaration,
               typeDefinitionHelper = TypeDefinitionHelper.forHint(NamespacedType(getQualifiedName(baseTypeDeclaration)))
            )
            parsedBaseType
         }
      }
      val restictions = getRestrictions(simpleType)

      val type = ObjectType(
         typeDefinitionHelper.suggestName().parameterizedName,
         null
      )
      val builder = {
         ObjectTypeDefinition(
            inheritsFrom = listOf(baseType),
            formatAndOffset = FormatsAndZoneOffset.forNullable(
               if (restictions.isNotEmpty()) restictions else null,
               null
            ),
            // Not emitting the formattedInstanceOfType, b/c of the way the SchemaWriter is filtering
            // on classes to output.
            // The output is still generated correctly.
//            formattedInstanceOfType = if (restictions.isNotEmpty()) baseType else null,
            compilationUnit = CompilationUnit.unspecified(),
            annotations = emptySet()  // Don't add namespace to semantic subtypes
         )
      }
      return type to builder
   }

   /**
    * Builds an enum class, that follows Xsd's union approach.
    * The generated type will have the full set of enum values, and additionally
    * declares enum synonyms between this type and the enum types it's composing.
    */
   private fun parseEnumUnionExtension(
      qualifiedName: QualifiedName,
      simpleType: XSUnionSimpleType,
      typeDefinitionHelper: TypeDefinitionHelper
   ): Type {
      val members = (0 until simpleType.memberSize).map { idx -> simpleType.getMember(idx) }
      val valuesFromExtendedEnums = members.filter { it.isGlobal }
         .map {
            val enumValueQualifiedName =
               getQualifiedNameOrNull(it) ?: error("Failed to parse qualified name of extended enum value $it")
            getOrParseType(
               it,
               typeDefinitionHelper = TypeDefinitionHelper.forHint(NamespacedType(enumValueQualifiedName)),
               declarationLocation = SchemaTypeDeclaration.DeclarationLocation.Field
            )
         }
         .filterIsInstance<EnumType>()
         .flatMap { enumType ->
            enumType.values.map { enumValue ->
               EnumValue(
                  enumValue.name,
                  enumValue.value,
                  EnumValue.enumValueQualifiedName(qualifiedName, enumValue.name),
                  enumValue.annotations,
                  synonyms = listOf(enumValue.enumValueQualifiedName),
                  typeDoc = enumValue.typeDoc
               )
            }
         }
      val localEnumValues = members.filter { it.isLocal }
         .map { parseEnumType(typeDefinitionHelper, it) }
         .flatMap { enumType -> enumType.values }

      return EnumType(
         qualifiedName.fullyQualifiedName,
         EnumDefinition(
            valuesFromExtendedEnums + localEnumValues,
            listOfNotNull(XsdAnnotations.xmlNamespaceOrNull(simpleType.targetNamespace)),
            CompilationUnit.unspecified(),
            valueType = PrimitiveType.STRING,
            typeDoc = getDocumentation(simpleType)

         )
      )
   }

   private fun isEnumUnionExtension(simpleType: XSSimpleType, typeDefinitionHelper: TypeDefinitionHelper): Boolean {
      if (simpleType is XSUnionSimpleType) {
         // A Union type is one way of modelling an extension to an enum,
         //where a new type is declared having members as a union of the members of other enum classes,
         // and some locally defined enum values.
         // We've witnessed this used heavily in the FpML spec.
         // As the xsd spec is very flexible, it's possible to declare other union types / configurations, though
         // at the time of writing, haven't seen any other examples.
         val members = (0 until simpleType.memberSize).map { idx -> simpleType.getMember(idx) }
         val hasLocalEnumType = members.filter { it.isLocal }
            .all { isEnum(it) }
         val nonLocalMemberTypes = members.filter { it.isGlobal }
            .map { getOrParseType(it, typeDefinitionHelper = typeDefinitionHelper) }
         val nonLocalMembersAreAllEnums = nonLocalMemberTypes.all { it is EnumType }
         return hasLocalEnumType && nonLocalMembersAreAllEnums
      } else {
         return false
      }
   }

   private fun getRestrictions(simpleType: XSSimpleType): List<String> {
      val patterns = simpleType.getFacets("pattern")
         .map { it.value.value }
         // Escape any back-slashes, since they're special characters
         .map { it.replace("""\""", """\\""") }
      if (patterns.isNotEmpty()) {
         return patterns
      }
      return emptyList()
   }

   private fun parseEnumType(typeDefinitionHelper: TypeDefinitionHelper, simpleType: XSSimpleType): EnumType {
      val qualifiedName = typeDefinitionHelper.suggestName()
      if (shouldCreateSemanticSubtype(simpleType, typeDefinitionHelper)) {
         val baseTypeQualifiedName = getQualifiedName(simpleType)
         val parsedBaseType = getOrParseType(
            simpleType,
            typeDefinitionHelper = TypeDefinitionHelper.forHint(NamespacedType(baseTypeQualifiedName))
         ) as EnumType
         val semanticSubType = EnumType(
            qualifiedName.parameterizedName,
            EnumDefinition(
               emptyList(),
               annotations = emptyList(),  // Don't copy namespace to semantic subtypes
               inheritsFrom = listOf(parsedBaseType),
               valueType = parsedBaseType.definition!!.valueType,
               compilationUnit = CompilationUnit.unspecified()
            )
         )
         return semanticSubType
      }


      val enumValues = simpleType.getFacets("enumeration")
         .map { xsFacet ->
            val doc = getDocumentation(xsFacet)
            val enumValue = xsFacet.value.value
            val enumName = EnumNaming.toValidEnumName(enumValue)
            EnumValue(enumName, enumValue, EnumValue.enumValueQualifiedName(qualifiedName, enumValue), typeDoc = doc)
         }

      return EnumType(
         qualifiedName.fullyQualifiedName,
         EnumDefinition(
            enumValues,
            compilationUnit = CompilationUnit.unspecified(),
            valueType = PrimitiveType.STRING, // Possibly more flexible to pass this in from the xsd type
            typeDoc = getDocumentation(simpleType),
            annotations = listOfNotNull(XsdAnnotations.xmlNamespaceOrNull(simpleType.targetNamespace)),
         )
      )
   }

   private fun isEnum(type: XSSimpleType): Boolean {
      return type.getFacets("enumeration")?.isNotEmpty() ?: false
   }

   private fun parseModelGroup(
      term: XSModelGroup,
      typeDefinitionHelper: TypeDefinitionHelper,
      declaringSchemaNamespace: String?
   ): ParsedContent {
      val children = term.children.map { parseParticle(it, typeDefinitionHelper, declaringSchemaNamespace) }
      return ParsedList(children, term.compositor)
   }
}

data class ParsedElement(
   val name: String,
   val type: Type,
   val minOccurs: Int,
   val maxOccurs: Int,
   val docs: String?,
   val elementNamespace: String?
) :
   ParsedContent

data class ParsedList(val list: List<ParsedContent>, val compositor: XSModelGroup.Compositor) : ParsedContent
interface ParsedContent
object ParsedWildcard : ParsedContent

typealias TypeDefinitionBuilder = () -> TypeDefinition

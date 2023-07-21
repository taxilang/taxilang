package lang.taxi.xsd

import com.sun.xml.xsom.*
import com.sun.xml.xsom.impl.Ref
import com.sun.xml.xsom.parser.XSOMParser
import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import lang.taxi.types.*
import lang.taxi.utils.log
import lang.taxi.xsd.XsdPrimitives.primtiviesTaxiDoc
import java.io.File
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

//data class GeneratorOptions(val defaultNamespace: String)
class TaxiGenerator(
   private val schemaWriter: SchemaWriter = SchemaWriter()
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
               getOrParseType(typeDeclaration)
            }
            schema.elementDecls.map { (name, declaration) ->
               getOrParseType(declaration.type, anonymousTypeNamePrefix = name)
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

   private fun parseComplexType(
      complexType: XSComplexType,
      parsedName: QualifiedName
   ): Pair<Type, TypeDefinitionBuilder?> {
      val typeName = parsedName
      val emptyType = ObjectType(typeName.fullyQualifiedName, null)
      val definitionBuilder: TypeDefinitionBuilder = {
         var isWildcard = isWildcardType(complexType)
         val attributes = parseAttributesToFields(complexType)
         val fields = parseTypeBodyToFields(complexType)
         val allFields = fields + attributes
         val docs = getDocumentation(complexType)
         val baseType = complexType.baseType?.let { baseType ->
            val baseTypeName = getQualifiedName(baseType)
            if (baseTypeName == XsdPrimitives.ANY_TYPE) {
               emptySet()
            } else {
               setOf(getOrParseType(baseType))
            }
         } ?: emptySet()


         if (isWildcard && allFields.isNotEmpty()) {
            // Will need to revisit this in a future iteration
            log().warn("Type $parsedName has a wildCard content type, along with defied fields.  The content is being dropped in favour of the content.")
            isWildcard = false
         }

         when {
            // Xsd permits inheriting enum classes, adding attributes
            // We need to treat these as a special usecase and build out a composite class
            baseType.isNotEmpty() && baseType.any { it is EnumType } -> {
               buildObjectDefinitionForTypeInheritingEnumClass(typeName, allFields, baseType, docs)
            }

            isWildcard -> {
               ObjectTypeDefinition(
                  allFields.toSet(),
                  compilationUnit = CompilationUnit.unspecified(),
                  inheritsFrom = setOf(PrimitiveType.ANY),
                  typeDoc = docs,
                  modifiers = listOf(Modifier.CLOSED)
               )
            }

            else -> {
               ObjectTypeDefinition(
                  allFields.toSet(),
                  compilationUnit = CompilationUnit.unspecified(),
                  inheritsFrom = baseType,
                  typeDoc = docs,
                  modifiers = listOf(Modifier.CLOSED)
               )
            }
         }
      }
      return emptyType to definitionBuilder
   }

   private fun buildObjectDefinitionForTypeInheritingEnumClass(
      typeName: QualifiedName,
      allFields: List<Field>,
      baseType: Set<Type>,
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
         annotations = listOf(XsdAnnotations.xmlBody()),
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
   private fun isWildcardType(complexType: XSComplexType): Boolean {
      val particle = parseParticle(complexType) ?: return false
      return (particle is ParsedList && particle.list.size == 1 && particle.list.first() is ParsedWildcard)
   }

   private fun parseParticle(complexType: XSComplexType): ParsedContent? {
      val particle = complexType.contentType.asParticle() ?: return null
      return parseParticle(particle)
   }

   private fun parseTypeBodyToFields(complexType: XSComplexType): List<Field> {
      val parsedParticle = parseParticle(complexType) ?: return emptyList()
      require(parsedParticle is ParsedList) { "Expected to receive a parsedList here" }
      val fields = parsedParticle.list
         .filterIsInstance<ParsedElement>()
         .map {
            val nullable = it.minOccurs == 0 || parsedParticle.compositor == XSModelGroup.Compositor.CHOICE
            Field(
               it.name,
               it.type,
               nullable = nullable,
               typeDoc = it.docs,
               compilationUnit = CompilationUnit.unspecified()
            )
         }
      return fields
   }

   private fun parseAttributesToFields(complexType: XSComplexType): List<Field> {
      val attributes = complexType.declaredAttributeUses?.map { attribute ->
         val typeDoc: String? = getDocumentation(attribute.decl)
         Field(
            name = attribute.decl.name,
            type = getOrParseType(attribute.decl.type),
            nullable = !attribute.isRequired,
            annotations = listOf(XsdAnnotations.xmlAttribute()),
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

   private fun parseParticle(particle: XSParticle): ParsedContent {
      return when (val term = particle.term) {
         is XSModelGroup -> parseModelGroup(term)
         is XSElementDecl -> parseElement(particle, term)
         is XSWildcard -> ParsedWildcard
         is XSModelGroupDecl -> parseModelGroupDeclaration(term)
         else -> TODO()
      }
   }

   private fun parseModelGroupDeclaration(term: XSModelGroupDecl): ParsedContent {
      return parsedModelGroups.getOrPut(getQualifiedName(term)) {
         parseModelGroup(term.modelGroup) as ParsedList
      }
   }

   private fun parseElement(particle: XSParticle, term: XSElementDecl): ParsedElement {
      val taxiTypeDeclaration = XsdTaxiTypeDeclarations.getTaxiTypeReference(term.foreignAttributes)
      val type = getOrParseType(term.type, anonymousTypeNamePrefix = term.name, taxiTypeDeclaration)
      val docs = getDocumentation(term)
      return ParsedElement(term.name, type, particle.minOccurs.toInt(), particle.maxOccurs.toInt(), docs)
   }


   private fun getOrParseType(
      type: XSType,
      anonymousTypeNamePrefix: String? = null,
      taxiTypeReference: TaxiTypeReference? = null
   ): Type {
      val qualifiedName = taxiTypeReference?.typeName ?: getQualifiedName(type, anonymousTypeNamePrefix)
      var definitionBuilder: TypeDefinitionBuilder? = null
      val parsedType = parsedTypes.getOrPut(qualifiedName) {
         val (typeStub, typeBuilder) = parseType(type, qualifiedName, taxiTypeReference)
         definitionBuilder = typeBuilder
         typeStub
      }
      definitionBuilder?.let { callback ->
         val typeDef = callback()
         require(parsedType is UserType<*, *>) {
            "Found a builder, but type $qualifiedName is of type ${parsedType::class.simpleName}"
         }
         (parsedType as UserType<TypeDefinition, TypeDefinition>).definition = typeDef
      }
      return parsedType
   }

   private fun getQualifiedName(type: XSDeclaration, anonymousTypeNamePrefix: String? = null): QualifiedName {
      val packageName = SchemaNames.schemaNamespaceToPackageName(type.targetNamespace)
      return if (type.name == null && anonymousTypeNamePrefix == null) {
         error("Type is anonymous in xsd, and no anonymous typeName prefix was provided")
      } else if (type.name == null) {
         QualifiedName(packageName, anonymousTypeNamePrefix!!)
//         QualifiedName(packageName, "$anonymousTypeNamePrefix#AnonymousType")
      } else {
         QualifiedName(packageName, type.name)
      }
   }

   private fun parseType(
      type: XSType,
      parsedName: QualifiedName,
      taxiTypeReference: TaxiTypeReference? = null
   ): Pair<Type, TypeDefinitionBuilder?> {
      System.out.println("Parsing $parsedName")
      return when (type) {
         is Ref.ComplexType -> parseComplexType(type.asComplexType(), parsedName)
         is Ref.SimpleType -> parseSimpleType(type.asSimpleType(), parsedName, taxiTypeReference)
         else -> TODO(type.name)
      }

   }

   private fun parseSimpleType(
      simpleType: XSSimpleType,
      parsedName: QualifiedName,
      taxiTypeReference: TaxiTypeReference? = null
   ): Pair<Type, TypeDefinitionBuilder?> {
      val qualifiedName = parsedName
      if (XsdPrimitives.isPrimitive(qualifiedName)) {
         return XsdPrimitives.getType(qualifiedName) to null
      }
      if (isEnum(simpleType)) {
         return parseEnumType(qualifiedName, simpleType) to null
      }
      if (isEnumUnionExtension(simpleType)) {
         return parseEnumUnionExtension(qualifiedName, simpleType as XSUnionSimpleType) to null
      }

      // When we've been provided a type reference, we need to use the underlying simple type
      // as the inherited type.
      val baseType = if (taxiTypeReference != null) {
         parseSimpleType(simpleType, getQualifiedName(simpleType), null).first
      } else {
         getOrParseType(simpleType.baseType)
      }
      val restictions = getRestrictions(simpleType)

      val type = ObjectType(
         qualifiedName.fullyQualifiedName,
         null
      )
      val builder = {
         ObjectTypeDefinition(
            inheritsFrom = setOf(baseType),
            formatAndOffset = FormatsAndZoneOffset.forNullable(
               if (restictions.isNotEmpty()) restictions else null,
               null
            ),
            // Not emitting the formattedInstanceOfType, b/c of the way the SchemaWriter is filtering
            // on classes to output.
            // The output is still generated correctly.
//            formattedInstanceOfType = if (restictions.isNotEmpty()) baseType else null,
            compilationUnit = CompilationUnit.unspecified()
         )
      }
      return type to builder
   }

   /**
    * Builds an enum class, that follows Xsd's union approach.
    * The generated type will have the full set of enum values, and additionally
    * declares enum synonyms between this type and the enum types it's composing.
    */
   private fun parseEnumUnionExtension(qualifiedName: QualifiedName, simpleType: XSUnionSimpleType): Type {
      val members = (0 until simpleType.memberSize).map { idx -> simpleType.getMember(idx) }
      val valuesFromExtendedEnums = members.filter { it.isGlobal }
         .map { getOrParseType(it) }
         .filterIsInstance<EnumType>()
         .flatMap { enumType ->
            enumType.values.map { enumValue ->
               EnumValue(
                  enumValue.name,
                  enumValue.value,
                  EnumValue.enumValueQualifiedName(qualifiedName, enumValue.name),
                  enumValue.annotations,
                  synonyms = listOf(enumValue.qualifiedName),
                  typeDoc = enumValue.typeDoc
               )
            }
         }
      val localEnumValues = members.filter { it.isLocal }
         .map { parseEnumType(qualifiedName, it) }
         .flatMap { enumType -> enumType.values }

      return EnumType(
         qualifiedName.fullyQualifiedName,
         EnumDefinition(
            valuesFromExtendedEnums + localEnumValues,
            emptyList(),
            CompilationUnit.unspecified(),
            basePrimitive = PrimitiveType.STRING,
            typeDoc = getDocumentation(simpleType)

         )
      )
   }

   private fun isEnumUnionExtension(simpleType: XSSimpleType): Boolean {
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
            .map { getOrParseType(it) }
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

   private fun parseEnumType(qualifiedName: QualifiedName, simpleType: XSSimpleType): EnumType {
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
            basePrimitive = PrimitiveType.STRING, // Possibly more flexible to pass this in from the xsd type
            typeDoc = getDocumentation(simpleType)
         )
      )
   }

   private fun isEnum(type: XSSimpleType): Boolean {
      return type.getFacets("enumeration")?.isNotEmpty() ?: false
   }

   private fun parseModelGroup(term: XSModelGroup): ParsedContent {
      val children = term.children.map { parseParticle(it) }
      return ParsedList(children, term.compositor)
   }
}

data class ParsedElement(val name: String, val type: Type, val minOccurs: Int, val maxOccurs: Int, val docs: String?) :
   ParsedContent

data class ParsedList(val list: List<ParsedContent>, val compositor: XSModelGroup.Compositor) : ParsedContent
interface ParsedContent
object ParsedWildcard : ParsedContent

typealias TypeDefinitionBuilder = () -> TypeDefinition

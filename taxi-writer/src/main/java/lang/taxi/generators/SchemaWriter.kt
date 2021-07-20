package lang.taxi.generators

import lang.taxi.TaxiDocument
import lang.taxi.services.Operation
import lang.taxi.services.OperationContract
import lang.taxi.services.QueryOperation
import lang.taxi.services.Service
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.types.Accessor
import lang.taxi.types.Annotatable
import lang.taxi.types.ArrayType
import lang.taxi.types.EnumType
import lang.taxi.types.Field
import lang.taxi.types.ObjectType
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias
import lang.taxi.types.UnresolvedImportedType
import lang.taxi.types.VoidType
import lang.taxi.utils.quotedIfNecessary
import lang.taxi.utils.trimEmptyLines


open class SchemaWriter {
   private val formatter = SourceFormatter()

   enum class ImportLocation {
      /**
       * Imports are written at the top of each individual generated taxi string.
       * As a result, each taxi string is self-describing.
       * However, these strings cannot be concatenated together into a single
       * taxi string, as the location of the imports makes them invalid
       */
      WriteImportsInline,

      /**
       * Imports are returned as a seperate taxi string - the first item in the returned list.
       * As a result, the strings can be concatenated together into a single taxi string
       * with multiple namespaces.
       */
      CollectImports
   }

   fun generateSchemas(docs: List<TaxiDocument>, importLocation: ImportLocation = ImportLocation.CollectImports): List<String> {
      return docs.flatMap { generateSchema(it, importLocation) }
   }

   private fun generateSchema(doc: TaxiDocument, importLocation: ImportLocation): List<String> {
      data class SourceAndImports(val source: String, val imports: List<String>)

      val sources = doc.toNamespacedDocs().mapNotNull { namespacedDoc ->
         val (importedTypes, types) = getImportsAndTypes(namespacedDoc)
         if (types.isEmpty() && namespacedDoc.services.isEmpty()) {
            return@mapNotNull null
         }

         val typeDeclarations = types
            // Exclude formatted types -- these are declared inline at the field reference
            // Note : This is breaking top-level fomratted types, eg:
            // type CurrenySymbol inherits String( @format = "[A-Z]{3,3}" )
            // For now, I've worked around it in the xml code gen (the only place that supports this currently)
            // by not setting the formattedInstanceOfType.  It produces the correct output, but it's a bit hacky
            .filterNot { it is ObjectType && it.formattedInstanceOfType != null }
            // Exclude calculated types - these are declared inline at the field reference
            .filterNot { it is ObjectType && it.calculatedInstanceOfType != null }
            .map { generateTypeDeclaration(it, namespacedDoc.namespace) }
         val typesTaxiString = typeDeclarations.joinToString("\n\n").trim()

         val requiredImportsInServices = findImportedTypesOnServices(namespacedDoc.services)
         val requiredImports = (importedTypes + requiredImportsInServices).distinct()
         val imports = requiredImports.map { "import ${it.qualifiedName}" }


         val servicesTaxiString = namespacedDoc.services.joinToString("\n") { generateServiceDeclaration(it, namespacedDoc.namespace) }.trim()
         //return:
         // Wrap in namespace declaration, if it exists
         val rawTaxi = """${typesTaxiString.prependIndent()}

${servicesTaxiString.prependIndent()}"""
            .let { taxiBlock ->
               if (namespacedDoc.namespace.isNotEmpty()) {
                  """namespace ${namespacedDoc.namespace} {
$taxiBlock
}
""".trim()
               } else {
                  taxiBlock
               }
            }

         val taxiWithImports = if (importLocation == ImportLocation.WriteImportsInline) {
            """${imports.joinToString("\n")}
               |
               |$rawTaxi
            """.trimMargin().trim()
         } else {
            rawTaxi
         }
         SourceAndImports(formatter.format(taxiWithImports), imports)
      }


      return if (importLocation == ImportLocation.CollectImports) {
         val importStatements = sources.flatMap { it.imports }
         // The .joinToString() below generates an emptyString ("") if importStatements is empty,
         // which results in an empty string being prepended to the output set.
         // Avoid this by checking if the importStatemnts was empty
         if (importStatements.isNotEmpty()) {
            val imports = importStatements.joinToString("\n")
            listOf(imports) + sources.map { it.source }
         } else {
            sources.map { it.source }
         }

      } else {
         sources.map { it.source }
      }
   }

   private fun findImportedTypesOnServices(services: Set<Service>): List<UnresolvedImportedType> {
      return services.flatMap {
         it.operations.flatMap { operation ->
            val allOperationTypeReferences = operation.parameters.map { parameter -> parameter.type } + operation.returnType
            allOperationTypeReferences.filterIsInstance<UnresolvedImportedType>()
         }
      }.distinct()
   }

   private fun getImportsAndTypes(doc: TaxiDocument): Pair<List<UnresolvedImportedType>, List<Type>> {
      val imports = doc.types.filterIsInstance<UnresolvedImportedType>()
      val types = doc.types.filter { it !is UnresolvedImportedType }
      return imports to types

   }

   private fun generateServiceDeclaration(service: Service, namespace: String): String {
      val operations = service.members.joinToString("\n") {
         when (it) {
            is QueryOperation -> it.asTaxi()
            is Operation -> generateOperationDeclaration(it, namespace)
            else -> error("Unhandled service member type ${it::class.simpleName}")
         }
      }.prependIndent()
//        service PersonService {
//            @Get("/foo/bar")
//            operation getPerson(@AnotherAnnotation PersonId):Person
//        }

      return """${service.typeDoc.asTypeDocBlock()}${generateAnnotations(service)}
service ${service.toQualifiedName().qualifiedRelativeTo(namespace)} {
$operations
}""".trim()
   }

   private fun generateAnnotations(annotatedElement: Annotatable): String {
      return annotatedElement.annotations.map { annotation ->
         if (annotation.parameters.isEmpty()) {
            "@${annotation.qualifiedName}"
         } else {
            val annotationParams = annotation.parameters.map { "${it.key} = ${it.value!!.inQuotesIfNeeded()}" }.joinToString(" , ")
            "@${annotation.qualifiedName}($annotationParams)"
         }
      }.joinToString("\n")
   }

   private fun generateOperationDeclaration(operation: Operation, namespace: String): String {
      val params = operation.parameters.map { param ->
         val constraintString = constraintString(param.constraints)
         val paramAnnotations = generateAnnotations(param) + " "

         val paramName = if (!param.name.isNullOrEmpty()) param.name?.reservedWordEscaped() + " : " else ""
         val paramDeclaration = typeAsTaxi(param.type, namespace)
         paramAnnotations + paramName + paramDeclaration + constraintString
      }.joinToString(", ")
      val returnDeclaration = if (operation.returnType != VoidType.VOID) {
         val returnType = typeAsTaxi(operation.returnType, namespace)
         val returnContract = if (operation.contract != null) generateReturnContract(operation.contract!!) else ""
         " : $returnType$returnContract"
      } else {
         ""
      }

      val operationName = operation.name
      val typeDoc = operation.typeDoc.asTypeDocBlock()
      val annotations = generateAnnotations(operation)
      val scope = if (operation.scope != null) operation.scope + " " else ""
      return """$typeDoc
$annotations
${scope}operation $operationName( $params )$returnDeclaration""".trimIndent()
         .trim()
         .trimEmptyLines()
   }

   private fun generateReturnContract(contract: OperationContract): String {
      return constraintString(contract.returnTypeConstraints)
   }

   private fun constraintString(constraints: List<Constraint>): String {
      if (constraints.isEmpty()) {
         return ""
      }
      val constraintString = constraints
         .map { it.asTaxi() }
         .joinToString(", ")

      return "( $constraintString )"
   }

   private fun generateTypeDeclaration(type: Type, currentNamespace: String): String {
      return when (type) {
         is ObjectType -> generateObjectTypeDeclaration(type, currentNamespace)
         is TypeAlias -> generateTypeAliasDeclaration(type, currentNamespace)
         is EnumType -> generateEnumDeclaration(type, currentNamespace)
         is ArrayType -> "" // We don't generate top-level array types
         is TaxiStatementGenerator -> type.asTaxi()
         else -> TODO("No schema writer defined for type $type")
      }
   }

   private fun generateEnumDeclaration(type: EnumType, currentNamespace: String): String {
      val enumDocs = type.typeDoc.asTypeDocBlock()
      val enumValueDeclarations = type.values.map { enumValue ->
         val enumValueTypedoc = enumValue.typeDoc.asTypeDocBlock()
         val enumValueDeclaration = if (enumValue.name != enumValue.value) {
            "(${enumValue.value.inQuotesIfNeeded()})"
         } else ""
         val synonymDeclaration = when {
            enumValue.synonyms.isEmpty() -> ""
            enumValue.synonyms.size == 1 -> " synonym of ${enumValue.synonyms.first()}"
            else -> " synonym of [${enumValue.synonyms.joinToString(",")}]"
         }

         """$enumValueTypedoc
${generateAnnotations(enumValue)} ${enumValue.name}$enumValueDeclaration${synonymDeclaration}""".trim().trimEmptyLines()
      }.joinToString(",\n").prependIndent()
      return """$enumDocs
${generateAnnotations(type)} enum ${type.toQualifiedName().typeName} {
$enumValueDeclarations
}
        """.trimEmptyLines()
   }

   private fun generateTypeAliasDeclaration(type: TypeAlias, currentNamespace: String): String {
      val aliasType = type.aliasType!!
      val aliasTypeString = typeAsTaxi(aliasType, currentNamespace)

      return "type alias ${type.toQualifiedName().typeName.reservedWordEscaped()} as $aliasTypeString"
   }

   private fun generateObjectTypeDeclaration(type: ObjectType, currentNamespace: String): String {

      val body = if (type.fields.isNotEmpty()) {
         val fieldDelcarations = type.fields.map { generateFieldDeclaration(it, currentNamespace) }.joinToString("\n").prependIndent()
         """{
            |$fieldDelcarations
            |}
         """.trimMargin()
      } else ""

      val modifiers = type.modifiers.joinToString(" ") { it.token }
      val inheritanceString = getInheritenceString(type)

      // When writing formats, we only care about the ones declared on this type, not inherited elsewhere
      val inheritedFormats = type.inheritsFrom.flatMap { it.format ?: emptyList() }
      val declaredFormats = (type.format ?: emptyList()).filter { !inheritedFormats.contains(it) }
      val typeFormat = when {
         declaredFormats.isEmpty() -> ""
         declaredFormats.size == 1 -> "(@format = ${declaredFormats.first().quotedIfNecessary()})"
         else -> "(@format = [${declaredFormats.joinToString(",") { it.quotedIfNecessary() }}])"
      }
      val typeDoc = type.typeDoc.asTypeDocBlock()

      val typeKind = if (type.fields.isEmpty()) "type" else "model"
      return """$typeDoc
         |$modifiers $typeKind ${type.toQualifiedName().typeName.reservedWordEscaped()}$inheritanceString$typeFormat $body"""
         .trimMargin()
         .trimEmptyLines()
   }

   private fun getInheritenceString(type: ObjectType): String {
      return if (type.inheritsFromNames.isEmpty()) {
         ""
      } else {
         " inherits ${type.inheritsFromNames.joinToString(",")}"
      }
   }

   private fun generateFieldDeclaration(field: Field, currentNamespace: String): String {
      val fieldType = field.type
      val fieldTypeString = typeAsTaxi(fieldType, currentNamespace, field.nullable)
      val constraints = constraintString(field.constraints)
      val accessor = field.accessor?.let { accessorAsString(field.accessor!!) } ?: ""
      val annotations = generateAnnotations(field)
      val typeDoc = field.typeDoc.asTypeDocBlock()

      return "$typeDoc $annotations ${field.name.reservedWordEscaped()} : $fieldTypeString $constraints $accessor".trim()
   }

   private fun accessorAsString(accessor: Accessor): String {
      return when (accessor) {
         is TaxiStatementGenerator -> accessor.asTaxi()
         else -> "/* accessor of type ${accessor::class.simpleName} does not support taxi generation */"
      }
   }

   private fun typeAsTaxi(type: Type, currentNamespace: String, nullability: Boolean? = null): String {
      val nullableString = nullability?.let { nullability -> if (nullability) "?" else "" } ?: ""
      return when {
         type is ArrayType -> typeAsTaxi(type.type, currentNamespace) + "[]" + nullableString
         type is UnresolvedImportedType -> type.toQualifiedName().qualifiedRelativeTo(currentNamespace) + nullableString
         type.formattedInstanceOfType != null -> typeAsTaxi(type.formattedInstanceOfType!!, currentNamespace) + nullableString + """( ${writeFormat(type.format, type.offset)} )"""
         type is ObjectType && type.calculatedInstanceOfType != null -> typeAsTaxi(type.calculatedInstanceOfType!!, currentNamespace) + nullableString + " " + type.calculation!!.asTaxi()
         else -> type.toQualifiedName().qualifiedRelativeTo(currentNamespace) + nullableString
      }
   }

   private fun writeFormat(formats: List<String>?, offset: Int?): String {
      return when {
         formats != null && offset != null -> """@format = [${formats.joinToString(", ") { """"$it"""" }}] @offset = $offset"""
         formats != null -> formats.joinToString(", ") { """@format = "$it"""" }
         offset != null -> """@offset = $offset"""
         else -> ""
      }
   }
}

private fun String?.asTypeDocBlock(): String {
   return if (this.isNullOrEmpty()) {
      ""
   } else {
      "[[ $this ]]\n"
   }
}

private fun Any.inQuotesIfNeeded(): String {
   return when (this) {
      is Boolean -> this.toString()
      is Number -> this.toString()
      else -> "\"${this}\""
   }
}

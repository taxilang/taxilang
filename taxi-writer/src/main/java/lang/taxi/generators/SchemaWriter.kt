package lang.taxi.generators

import lang.taxi.TaxiDocument
import lang.taxi.services.Operation
import lang.taxi.services.OperationContract
import lang.taxi.services.Service
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.types.*


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

         val typeDeclarations = types.map { generateTypeDeclaration(it, namespacedDoc.namespace) }
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
      val operations = service.operations
         .map { generateOperationDeclaration(it, namespace) }
         .joinToString("\n").prependIndent()
//        service PersonService {
//            @Get("/foo/bar")
//            operation getPerson(@AnotherAnnotation PersonId):Person
//        }

      return """
${generateAnnotations(service)}
service ${service.toQualifiedName().qualifiedRelativeTo(namespace)} {
$operations
}""".trim()
   }

   private fun generateAnnotations(annotatedElement: Annotatable): String {
      return annotatedElement.annotations.map { annotation ->
         if (annotation.parameters.isEmpty()) {
            "@${annotation.name}"
         } else {
            val annotationParams = annotation.parameters.map { "${it.key} = ${it.value!!.inQuotesIfNeeded()}" }.joinToString(" , ")
            "@${annotation.name}($annotationParams)"
         }
      }.joinToString("\n")
   }

   private fun generateOperationDeclaration(operation: Operation, namespace: String): String {
      val params = operation.parameters.map { param ->
         val constraintString = constraintString(param.constraints)
         val paramAnnotations = generateAnnotations(param) + " "

         val paramName = if (!param.name.isNullOrEmpty()) param.name + " : " else ""
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

      val annotations = generateAnnotations(operation)
      val scope = if (operation.scope != null) operation.scope + " " else ""
      return """$annotations
${scope}operation $operationName( $params )$returnDeclaration""".trimIndent().trim()
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
         else -> TODO("No schema writer defined for type $type")
      }
   }

   private fun generateEnumDeclaration(type: EnumType, currentNamespace: String): String {
      val enumValueDeclarations = type.values.map { enumValue ->
         "${generateAnnotations(enumValue)} ${enumValue.name}".trim()
      }.joinToString(",\n").prependIndent()
      return """
${generateAnnotations(type)} enum ${type.toQualifiedName().typeName} {
$enumValueDeclarations
}
        """
   }

   private fun generateTypeAliasDeclaration(type: TypeAlias, currentNamespace: String): String {
      val aliasType = type.aliasType!!
      val aliasTypeString = typeAsTaxi(aliasType, currentNamespace)

      return "type alias ${type.toQualifiedName().typeName.reservedWordEscaped()} as $aliasTypeString"
   }

   private fun generateObjectTypeDeclaration(type: ObjectType, currentNamespace: String): String {

      val fieldDelcarations = type.fields.map { generateFieldDeclaration(it, currentNamespace) }.joinToString("\n").prependIndent()
      val modifiers = type.modifiers.map { it.token }.joinToString(" ")
      val inheritanceString = getInheritenceString(type)

      return """$modifiers type ${type.toQualifiedName().typeName.reservedWordEscaped()}$inheritanceString {
$fieldDelcarations
}"""
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
      val fieldTypeString = typeAsTaxi(fieldType, currentNamespace)

      val constraints = constraintString(field.constraints)

      val annotations = generateAnnotations(field)
      return "$annotations ${field.name.reservedWordEscaped()} : $fieldTypeString $constraints".trim()
   }

   private fun typeAsTaxi(type: Type, currentNamespace: String): String {
      return when (type) {
         is ArrayType -> typeAsTaxi(type.type, currentNamespace) + "[]"
         else -> type.toQualifiedName().qualifiedRelativeTo(currentNamespace)
      }
   }
}

private fun Any.inQuotesIfNeeded(): String {
   return when (this) {
      is Boolean -> this.toString()
      is Number -> this.toString()
      else -> "\"${this}\""
   }
}

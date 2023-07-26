package lang.taxi.generators

import lang.taxi.TaxiDocument
import lang.taxi.accessors.Accessor
import lang.taxi.expressions.Expression
import lang.taxi.services.*
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.types.*
import lang.taxi.types.Annotation
import lang.taxi.utils.trimEmptyLines


open class SchemaWriter(
   /**
    * Allows the caller to optionally filter annotations that will / won't be written out.
    * A first-pass implementation, and will likely be refactored into something more robust that
    * allows filtering of more things than just annotations
    */
   private val annotationFilter: (Annotatable, Annotation) -> Boolean = { _, _ -> true },

   /**
    * Allows the caller to optionally filter types that will / won't be written out.
    * If a type is not written out, it's expected to be provided from another schema, or the
    * resulting code won't compile.
    */
   private val typeFilter: (Type) -> Boolean = { true }
) {
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

   fun generateSchemas(
      docs: List<TaxiDocument>,
      importLocation: ImportLocation = ImportLocation.CollectImports
   ): List<String> {
      return docs.flatMap { generateSchema(it, importLocation) }
   }

   fun generateTaxi(type: Type): String {
      return generateTaxiForTypes(listOf(type), type.toQualifiedName().namespace)
         .single()
   }

   private fun generateSchema(doc: TaxiDocument, importLocation: ImportLocation): List<String> {
      data class SourceAndImports(val source: String, val imports: List<String>)

      val sources = doc.toNamespacedDocs()
         .mapNotNull { namespacedDoc ->
            val (importedTypes, types) = getImportsAndTypes(namespacedDoc)


            val typeDeclarations = generateTaxiForTypes(types, namespacedDoc.namespace)
            // typeDeclarations excludes any types in the schema that were present,
            // but we don't need to output. (eg., builtin types).
            // To prevent emitting empty schemas, check now and bail
            if (typeDeclarations.isEmpty() && namespacedDoc.services.isEmpty()) {
               return@mapNotNull null
            }

            val typesTaxiString = typeDeclarations.joinToString("\n\n").trim()

            val requiredImportsInServices = findImportedTypesOnServices(namespacedDoc.services)
            val requiredImports = (importedTypes + requiredImportsInServices).distinct()
            val imports = requiredImports.map { "import ${it.qualifiedName}" }


            val servicesTaxiString =
               namespacedDoc.services.joinToString("\n") { generateServiceDeclaration(it, namespacedDoc.namespace) }
                  .trim()
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

   private fun generateTaxiForTypes(
      types: List<Type>,
      currentNamespace: String
   ): List<String> {
      val typeDeclarations = types
         // Exclude formatted types -- these are declared inline at the field reference
         // Note : This is breaking top-level fomratted types, eg:
         // type CurrenySymbol inherits String( @format = "[A-Z]{3,3}" )
         // For now, I've worked around it in the xml code gen (the only place that supports this currently)
         // by not setting the formattedInstanceOfType.  It produces the correct output, but it's a bit hacky
         //
//         .filterNot { it is ObjectType && it.declaresFormat }
         .filterNot { it is PrimitiveType }
         .filter { typeFilter(it) }
         .map { generateTypeDeclaration(it, currentNamespace) }
      return typeDeclarations
   }

   private fun findImportedTypesOnServices(services: Set<Service>): List<UnresolvedImportedType> {
      return services.flatMap {
         it.operations.flatMap { operation ->
            val allOperationTypeReferences =
               operation.parameters.map { parameter -> parameter.type } + operation.returnType
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
            is Table -> it.asTaxi()
            is Stream -> it.asTaxi()
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

   private fun generateAnnotations(annotatedElement: Annotatable, declaredFormats: List<String> = emptyList()): String {
      val annotations = annotatedElement.annotations
         .filter { annotation -> annotationFilter(annotatedElement, annotation) }
         .map { annotation ->
            if (annotation.parameters.isEmpty()) {
               "@${annotation.qualifiedName}"
            } else {
               val annotationParams =
                  annotation.parameters.map { "${it.key} = ${it.value!!.inQuotesIfNeeded()}" }.joinToString(" , ")
               "@${annotation.qualifiedName}($annotationParams)"
            }
         }.joinToString("\n")
      val formats = declaredFormats.joinToString("\n") { "@Format(\"$it\") " }
      return (annotations + "\n" + formats).trim()
   }

   private fun generateOperationDeclaration(operation: Operation, namespace: String): String {
      var paramsHaveDocs = false
      val paramsList = operation.parameters.map { param ->
         val constraintString = constraintString(param.constraints)
         val paramAnnotations = generateAnnotations(param) + " "
         val paramDocs = param.typeDoc.asTypeDocBlock()
         if (param.typeDoc != null) {
            paramsHaveDocs = true
         }
         val paramName = if (!param.name.isNullOrEmpty()) param.name?.reservedWordEscaped() + " : " else ""
         val paramDeclaration = typeAsTaxi(param.type, namespace)
         paramDocs + paramAnnotations + paramName + paramDeclaration + constraintString
      }

      val params = if (paramsHaveDocs || paramsList.size > 2) {
         // Put each param on a seperate line
         paramsList.joinToString(prefix = "\n    ", separator = ",\n   ")
      } else {
         // Bunch params together.
         paramsList.joinToString(", ")
      }

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
      val scope = if (operation.scope != null) operation.scope.token + " " else ""
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
         val fieldDelcarations =
            type.fields.map { generateFieldDeclaration(it, currentNamespace) }.joinToString("\n").prependIndent()
         """{
            |$fieldDelcarations
            |}
         """.trimMargin()
      } else ""

      val modifiers = type.modifiers.joinToString(" ") { it.token }
      val inheritanceString = getInheritanceString(type, currentNamespace)

      // When writing formats, we only care about the ones declared on this type, not inherited elsewhere
      val inheritedFormats = type.inheritsFrom.flatMap { it.format ?: emptyList() }
      val declaredFormats = (type.format ?: emptyList()).filter { !inheritedFormats.contains(it) }

      val typeDoc = type.typeDoc.asTypeDocBlock()
      val annotations = generateAnnotations(type, declaredFormats)

      val typeKind = if (type.fields.isEmpty()) "type" else "model"
      return """$typeDoc
         |$annotations
         |$modifiers $typeKind ${type.toQualifiedName().typeName.reservedWordEscaped()}$inheritanceString $body"""
         .trimMargin()
         .trimEmptyLines()
   }

   private fun getInheritanceString(type: ObjectType, currentNamespace: String): String {
      return if (type.inheritsFrom.isEmpty()) {
         ""
      } else {
         " inherits " + type.inheritsFrom.joinToString(",") { typeAsTaxi(it, currentNamespace) }
      }
   }

   private fun generateFieldDeclaration(field: Field, currentNamespace: String): String {
      val fieldType = field.type
      val fieldTypeString = typeAsTaxi(fieldType, currentNamespace, field.nullable)
      val constraints = constraintString(field.constraints)
      val accessor = field.accessor?.let {
         if (it is Expression) {
            "by ${accessorAsString(it)}"
         } else {
            accessorAsString(it)
         }
      } ?: ""
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

   private fun typeAsTaxi(type: Type, currentNamespace: String, nullability: Boolean = false): String {
      val nullableString = if (nullability) "?" else ""
      fun nestedArray(type: ArrayType) = "Array<" + typeAsTaxi(type.type, currentNamespace) + ">"
      fun simpleArray(type: ArrayType) = typeAsTaxi(type.type, currentNamespace) + "[]"
      val typeHasFormat = if (type.format != null) {
         val inheritedFormats = type.inheritsFrom.flatMap { it.format ?: emptyList() }.filterNotNull()
         val formatsNotInherited = (type.format ?: emptyList()).filter { !inheritedFormats.contains(it) }
         formatsNotInherited.isNotEmpty()
      } else false
      return when {
         type is ArrayType -> (if (type.type is ArrayType) nestedArray(type) else simpleArray(type)) + nullableString
         type is UnresolvedImportedType -> type.toQualifiedName().qualifiedRelativeTo(currentNamespace) + nullableString
//         typeHasFormat -> {
//            typeAsTaxi(
//               type,
//               currentNamespace
//            ) + nullableString /*+ """( ${writeFormat(type.format, type.offset)} )"""*/
//         }
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
      "[[ ${this.trim()} ]]\n"
   }
}

private fun Any.inQuotesIfNeeded(): String {
   return when (this) {
      is Boolean -> this.toString()
      is Number -> this.toString()
      else -> "\"${this}\""
   }
}

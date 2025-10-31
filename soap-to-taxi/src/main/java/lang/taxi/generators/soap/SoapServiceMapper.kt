package lang.taxi.generators.soap

import lang.taxi.TaxiDocument
import lang.taxi.generators.Logger
import lang.taxi.generators.NamingUtils.getNamespace
import lang.taxi.services.Operation
import lang.taxi.services.OperationScope
import lang.taxi.services.Parameter
import lang.taxi.services.Service
import lang.taxi.sources.SourceCode
import lang.taxi.types.*
import lang.taxi.utils.log
import org.apache.cxf.service.model.MessagePartInfo
import org.apache.cxf.service.model.OperationInfo
import org.apache.cxf.service.model.ServiceInfo
import java.net.URI
import java.net.URL
import javax.xml.namespace.QName
import kotlin.math.log

class SoapServiceMapper(
   private val wsdlURL: URL,
   private val serviceInfo: ServiceInfo,
   private val logger: Logger,
   private val types: TaxiDocument,
   private val wsdlSource: SourceCode
) {

   private val serviceName = qNameToQualifiedName(serviceInfo.name)

   private val operations = mutableListOf<Operation>()

   /**
    * When generating operations, we'll update types to declare them as parameter types.
    */
   private val _modifiedTypes = mutableMapOf<QualifiedName, Type>()
   val modifiedTypes: Map<QualifiedName, Type>
      get() = _modifiedTypes

   fun generateOperation(operationInfo: OperationInfo): Operation {
      val operationName = qNameToQualifiedName(operationInfo.name)
      val inputs = messagePartsToTypes(operationInfo.input.messageParts, operationInfo.name, "input")
      markInputsAsParameterTypes(inputs)
      val parameters =
         inputs.mapIndexed { index, type ->  Parameter(annotations = emptyList(), type = type, name = "p$index", constraints = emptyList()) }
      val responseTypes = messagePartsToTypes(operationInfo.output.messageParts, operationInfo.name, "output")
      require(responseTypes.size == 1) { "Expected a single response type for operation ${operationName}, but found ${responseTypes.size}" }
      val isXsdScalarType = isXsdScalarType(operationInfo)
      val responseType = unwrapEnvelopeType(responseTypes.single(), isXsdScalarType)
      val operation = Operation(
         name = operationName.typeName,
         scope = OperationScope.READ_ONLY, // TODO : How do we detect mutating services?
         annotations = emptyList(),
         parameters = parameters,
         returnType = responseType,
         compilationUnits = listOf(CompilationUnit.Companion.generatedFor(operationInfo.name.toString()))
      )
      return operation
   }

   /**
    * Indicates if the return type of the operation will be a scalar type.
    * Does this by looking at the XSD directly, rather than the parsed Taxi type.
    *
    * This is because if the Taxi type declared in the XSD schema is imported,
    * then we don't have type information
    */
   private fun isXsdScalarType(operationInfo: OperationInfo): Boolean {
      // CXF creates an unwrapped version for document/literal wrapped style
      val unwrapped = operationInfo.unwrappedOperation ?: operationInfo
      val outputMessage = unwrapped.output ?: return false
      val parts = outputMessage.messageParts.toList()

      if (parts.size != 1) {
         return false
      }

      val part = parts.first()

      // Check if it's a simple XSD type
      part.typeQName?.let { qname ->
         if (qname.namespaceURI == "http://www.w3.org/2001/XMLSchema") {
            return true
         }
      }

      // Check the XML schema for the element
      val xmlSchema = part.xmlSchema
      if (xmlSchema is org.apache.ws.commons.schema.XmlSchemaElement) {
         val schemaType = xmlSchema.schemaType
         return schemaType is org.apache.ws.commons.schema.XmlSchemaSimpleType
      }

      return false
   }

   private fun markInputsAsParameterTypes(inputs: List<Type>) {
      inputs.forEach { inputType ->
         val existingDefinition = (inputType as ObjectType).definition!!
         if (!existingDefinition.modifiers.contains(Modifier.PARAMETER_TYPE)) {
            val modifiedType = (inputType as ObjectType).copy(
               definition = inputType.definition!!.copy(
                  modifiers = listOf(Modifier.PARAMETER_TYPE)
               )
            )
            storeAsModified(modifiedType)
         }
      }
   }

   private fun storeAsModified(modifiedType: ObjectType) {
      if (this._modifiedTypes.containsKey(modifiedType.toQualifiedName())) {
         error("Type ${modifiedType.qualifiedName} has already been mutated")
      }
      this._modifiedTypes[modifiedType.toQualifiedName()] = modifiedType
   }

   /**
    * If the provided type has a single field, then we return it.
    * Useful for SOAP noise, where you have something like:
    *
    * <FooResponse>
    *    <FooResult>
    *       <...>
    *    </FooResult>
    * </FooResponse>
    *
    * Will unwrap FooResult from FooResponse
    *
    * Note - the SOAP Client actually performs this unwrapping (ie., the returned value from the soapClient
    * is FooResult, not the outer envelope type - FooResponse), so services must not declare their return type as
    * the FooResponse envelope type
    */
   private fun unwrapEnvelopeType(type: Type, isXsdScalarType: Boolean): Type {
      val isTaxiEnvelopeType = type is ObjectType && type.fields.size == 1 && type.fields.single().type is ObjectType
      return if (isTaxiEnvelopeType || isXsdScalarType) {
         if (type is ObjectType) {
            type.fields.single().type
         } else {
            log().error("An unexpected error when trying to unwrap envelope type ${type.qualifiedName} - expected this to be an ObjectType, but was ${type::class.simpleName}")
            type
         }
      } else {
         type
      }
   }

   private fun messagePartsToTypes(
      messageParts: List<MessagePartInfo>,
      // used for logging only
      operationName: QName,
      // used for logging only
      direction: String
   ): List<Type> {
      return messageParts.map { messagePartInfo ->
         val qualifiedName = qNameToQualifiedName(messagePartInfo.elementQName)
         if (!types.containsType(qualifiedName.parameterizedName)) {
            error("Operation $operationName expects $direction type ${qualifiedName.parameterizedName} (from ${messagePartInfo.elementQName}) but no such type is present in the schema")
         }
         types.type(qualifiedName.parameterizedName)
      }
   }


   private fun qNameToQualifiedName(typeName: QName): QualifiedName {
      val namespace = getNamespace(URI.create(typeName.namespaceURI), namespaceElementsToOmit = listOf("www"))
      return QualifiedName(namespace, typeName.localPart)
   }

   fun generateService(importedXsds: List<SourceCode>): Service {
      serviceInfo.`interface`.operations.forEach { operationInfo ->
         operations.add(generateOperation(operationInfo))
      }
      val importedXsdCompilationUnits = importedXsds.map { CompilationUnit(it) }

      val service = Service(
         qualifiedName = serviceName.fullyQualifiedName,
         members = operations,
         annotations = listOf(SoapAnnotations.soapService(wsdlURL.toExternalForm())),
         compilationUnits = listOf(
            CompilationUnit.generatedFor(serviceInfo.name.toString()),
            CompilationUnit(wsdlSource)
         ) + importedXsdCompilationUnits
      )

      return service
   }
}

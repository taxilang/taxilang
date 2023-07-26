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
import org.apache.cxf.service.model.MessagePartInfo
import org.apache.cxf.service.model.OperationInfo
import org.apache.cxf.service.model.ServiceInfo
import java.net.URI
import java.net.URL
import javax.xml.namespace.QName

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
         inputs.map { Parameter(annotations = emptyList(), type = it, name = null, constraints = emptyList()) }
      val responseTypes = messagePartsToTypes(operationInfo.output.messageParts, operationInfo.name, "output")
      require(responseTypes.size == 1) { "Expected a single response type for operation ${operationName}, but found ${responseTypes.size}" }
      val responseType = unwrapEnvelopeType(responseTypes.single())
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
    */
   private fun unwrapEnvelopeType(type: Type): Type {
      if (type is ObjectType && type.fields.size == 1 && type.fields.single().type is ObjectType) {
         return type.fields.single().type
      } else {
         return type
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

   fun generateService(): Service {
      serviceInfo.`interface`.operations.forEach { operationInfo ->
         operations.add(generateOperation(operationInfo))
      }

      val service = Service(
         qualifiedName = serviceName.fullyQualifiedName,
         members = operations,
         annotations = listOf(SoapAnnotations.soapService(wsdlURL.toExternalForm())),
         compilationUnits = listOf(
            CompilationUnit.generatedFor(serviceInfo.name.toString()),
            CompilationUnit(wsdlSource)
         )
      )

      return service
   }
}

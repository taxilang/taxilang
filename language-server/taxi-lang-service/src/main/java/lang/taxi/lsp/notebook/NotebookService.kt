package lang.taxi.lsp.notebook

import lang.taxi.services.Operation
import lang.taxi.services.ServiceMember
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

/**
 * Service interface for TaxiQL notebook operations.
 * This defines the custom JSONRPC endpoints that extend the language server
 * to support VSCode notebook functionality.
 *
 * Implementations should be provided by the standalone server module.
 */
interface NotebookService {

   @JsonRequest("taxiql/generateQueryPlan")
   fun generateQueryPlan(params: StubQueryRequest): CompletableFuture<QueryPlanResponse>

   /**
    * Execute a TaxiQL query using stubbed service responses.
    * Endpoint: taxiql/executeWithStubs
    */
   @JsonRequest("taxiql/executeWithStubs")
   fun executeWithStubs(params: StubQueryRequest): CompletableFuture<StubQueryResponse>

   /**
    * List all available operations from the Taxi schema.
    * Endpoint: taxiql/listOperations
    */
   @JsonRequest("taxiql/listOperations")
   fun listOperations(params: ListOperationsRequest): CompletableFuture<ListOperationsResponse>

   /**
    * Generate a placeholder stub response for an operation.
    * Endpoint: taxiql/generatePlaceholderStub
    */
   @JsonRequest("taxiql/generatePlaceholderStub")
   fun generatePlaceholderStub(params: GeneratePlaceholderRequest): CompletableFuture<GeneratePlaceholderResponse>
}

// Request/Response data classes


data class QueryPlanRequest(
   val query: String
)
data class QueryPlanResponse(
   val diagramData: Any // Is actually QueryPlanDiagramData, but that's not maven linked here.
)

/**
 * Request to execute a query with stubs.
 * Maps to the StubQueryMessage contract from taxi-playground-core.
 */
data class StubQueryRequest(
   val query: String,
   val projectRoot: String,
   val notebookPath: String,
   val stubs: List<OperationStub> = emptyList(),
   val parameters: Map<String, Any> = emptyMap()
)

/**
 * Stub configuration for a single operation.
 * Matches the OperationStub from taxi-playground-core.
 */
data class OperationStub(
   val operationName: String,
   val response: String,
   val echoInput: Boolean = false,
   val conditionalResponses: List<ResponseCondition> = emptyList()
)

data class ResponseCondition(
   val inputs: List<ParameterValue>,
   val response: StubbedResponse
)

data class StubbedResponse(
   val body: String
)

data class ParameterValue(
   val name: String,
   val value: Any
)

/**
 * Response from executing a query with stubs.
 */
data class StubQueryResponse(
   val data: Any,
   val contentType: String,
   val metadata: ExecutionMetadata
)

data class ExecutionMetadata(
   val rowCount: Int? = null,
   val executionTime: String? = null,
   val status: String,
   val warnings: List<String>? = null,
   val profilerData: Any? // is actually an Orbital QueryProfileData, but that's not linked here.
)

data class ListOperationsRequest(
   val projectRoot: String
)

data class ListOperationsResponse(
   val operations: List<ServiceMemberDto>
)

/**
 * Simple DTO for service members (operations, queries, etc.)
 * to avoid GSON serialization issues with complex Taxi/Orbital types.
 */
data class ServiceMemberDto(
   val qualifiedName: String,
   val serviceName: String,
   val name: String,
   val parameters: List<ParameterDto>,
   val returnType: TypeReferenceDto,
   val displayName: String
)

data class ParameterDto(
   val name: String,
   val type: TypeReferenceDto
)

data class TypeReferenceDto(
   val qualifiedName: String,
   val typeName: String
)

data class GeneratePlaceholderRequest(
   val operationQualifiedName: String,
   val projectRoot: String
)

data class GeneratePlaceholderResponse(
   val jsonStub: String
)


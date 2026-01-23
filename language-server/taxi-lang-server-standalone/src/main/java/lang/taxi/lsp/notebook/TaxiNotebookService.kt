package lang.taxi.lsp.notebook

import com.orbitalhq.playground.StubQueryMessage
import com.orbitalhq.playground.StubQueryService
import lang.taxi.lsp.TaxiCompilerService
import lang.taxi.utils.log
import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the NotebookService for TaxiQL notebooks.
 * This handles the actual query execution using StubQueryService.
 */
class TaxiNotebookService(
   private val compilerService: TaxiCompilerService
) : NotebookService {

   private val stubQueryService = StubQueryService()

   override fun executeWithStubs(params: StubQueryRequest): CompletableFuture<StubQueryResponse> {
      log().info("Received executeWithStubs request for query: ${params.query.take(50)}...")
      log().info("Number of stubs: ${params.stubs.size}")

      return CompletableFuture.supplyAsync {
         try {
            val startTime = System.currentTimeMillis()

            // Get the compiled schema from the last successful compilation
            // The LSP already has the schema loaded, so we pass an empty string as per requirements
            val schemaSource = ""

            log().info("Executing query for project root: ${params.projectRoot}")

            // Convert LSP OperationStub to playground OperationStub
            val playgroundStubs = params.stubs.map { stub ->
               com.orbitalhq.playground.OperationStub(
                  operationName = stub.operationName,
                  response = stub.response,
                  echoInput = stub.echoInput,
                  conditionalResponses = stub.conditionalResponses.map { condition ->
                     com.orbitalhq.playground.ResponseCondition(
                        inputs = condition.inputs.map { param ->
                           com.orbitalhq.playground.ParameterValue(param.name, param.value)
                        },
                        response = com.orbitalhq.playground.StubbedResponse(condition.response.body)
                     )
                  }
               )
            }

            // Create StubQueryMessage
            val stubQueryMessage = StubQueryMessage(
               schema = schemaSource,
               query = params.query,
               parameters = params.parameters,
               stubs = playgroundStubs,
               expectedJson = null,
               stackId = null,
               project = null
            )

            // Execute the query
            val (publisher, contentType) = stubQueryService.submitQuery(stubQueryMessage)

            // Collect results
            val results = if (publisher is Flux<*>) {
               publisher.collectList().block() ?: emptyList()
            } else {
               listOf(publisher)
            }

            val executionTime = System.currentTimeMillis() - startTime

            log().info("Query executed successfully in ${executionTime}ms")

            StubQueryResponse(
               data = if (results.size == 1) results[0] ?: emptyList<Any>() else results,
               contentType = contentType,
               metadata = ExecutionMetadata(
                  rowCount = if (results.isNotEmpty() && results[0] is List<*>) (results[0] as List<*>).size else results.size,
                  executionTime = "${executionTime}ms",
                  status = "success",
                  warnings = if (params.stubs.isEmpty()) listOf("No stubs provided - query may fail") else null,
                  trace = listOf(
                     "Received request at TaxiNotebookService",
                     "Project root: ${params.projectRoot}",
                     "Stubs configured: ${params.stubs.size}",
                     "Query executed via StubQueryService"
                  )
               )
            )
         } catch (e: Exception) {
            log().error("Error executing query with stubs", e)
            StubQueryResponse(
               data = emptyList<Any>(),
               contentType = "application/json",
               metadata = ExecutionMetadata(
                  status = "error",
                  warnings = listOf("Execution failed: ${e.message}"),
                  trace = listOf(
                     "Error: ${e.javaClass.simpleName}",
                     "Message: ${e.message}"
                  )
               )
            )
         }
      }
   }

   override fun listOperations(params: ListOperationsRequest): CompletableFuture<ListOperationsResponse> {
      log().info("Received listOperations request for project: ${params.projectRoot}")

      return CompletableFuture.supplyAsync {
         try {
            // TODO: Implement actual operation listing from schema
            // For now, return test operations to validate the plumbing

            log().info("Listing operations from project root: ${params.projectRoot}")

            ListOperationsResponse(
               operations = listOf(
                  Operation(
                     service = "CustomerService",
                     operation = "getCustomer",
                     returnType = "Customer",
                     metadata = mapOf(
                        "httpMethod" to "GET",
                        "path" to "/customers/{id}"
                     )
                  ),
                  Operation(
                     service = "OrderService",
                     operation = "listOrders",
                     returnType = "Order[]",
                     metadata = mapOf(
                        "httpMethod" to "GET",
                        "path" to "/orders"
                     )
                  ),
                  Operation(
                     service = "ProductService",
                     operation = "findProduct",
                     returnType = "Product",
                     metadata = mapOf(
                        "httpMethod" to "GET",
                        "path" to "/products/{id}"
                     )
                  )
               )
            )
         } catch (e: Exception) {
            log().error("Error listing operations", e)
            throw e
         }
      }
   }
}

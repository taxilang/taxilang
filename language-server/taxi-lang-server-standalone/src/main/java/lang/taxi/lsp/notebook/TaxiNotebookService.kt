package lang.taxi.lsp.notebook

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.orbitalhq.cockpit.core.query.QueryInsightUtils
import com.orbitalhq.playground.StubQueryMessage
import com.orbitalhq.playground.StubQueryService
import com.orbitalhq.schemas.taxi.TaxiSchema
import com.orbitalhq.utils.Ids
import lang.taxi.lsp.TaxiCompilerService
import lang.taxi.utils.log
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the NotebookService for TaxiQL notebooks.
 * This handles the actual query execution using StubQueryService.
 */
class TaxiNotebookService(
   private val compilerService: TaxiCompilerService
) : NotebookService {

   private val stubQueryService = StubQueryService()
   private val insightUtils = QueryInsightUtils()

   override fun generateQueryPlan(params: StubQueryRequest): CompletableFuture<QueryPlanResponse> {
      return CompletableFuture.supplyAsync {
         val taxiDocument = compilerService.lastSuccessfulCompilation()
            ?.documentOrEmpty!!
         val schema = TaxiSchema(taxiDocument, emptyList())
         val parseResult = insightUtils.parseQuery(
            query = params.query,
            schema = schema,
            generateNewQueryPlan = true,
            arguments = params.parameters
         ).block()

         QueryPlanResponse(
            parseResult!!.queryPlan
         )
      }
   }

   override fun executeWithStubs(params: StubQueryRequest): CompletableFuture<StubQueryResponse> {
      log().info("Received executeWithStubs request for query: ${params.query.take(50)}...")
      log().info("Number of stubs: ${params.stubs.size}")

      return CompletableFuture.supplyAsync {
         try {
            val startTime = System.currentTimeMillis()

            // Get the compiled schema from the last successful compilation
            // The LSP already has the schema loaded, so we pass an empty string as per requirements
            val taxiDocument = compilerService.lastSuccessfulCompilation()
               ?.documentOrEmpty!!

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
            val queryId: String = Ids.id("notebook-query")

            // Create StubQueryMessage
            val stubQueryMessage = StubQueryMessage(
               schema = "",
               query = params.query,
               parameters = params.parameters,
               stubs = playgroundStubs,
               expectedJson = null,
               stackId = null,
               project = null
            )

            // Execute the query
            val (publisher, contentType) = stubQueryService.submitQuery(
               stubQueryMessage,
               queryId,
               taxiDocument = taxiDocument
            )

            // Collect results
            val results = when (publisher) {
               is Flux<*> -> publisher.collectList().block() ?: emptyList<Any>()
               is Mono<*> -> publisher.block()
               else -> error("Expected a Flux or Mono, but got ${publisher::class}")
            }

            val rowCount = if (results is Collection<*>) results.size else 1
            val queryProfileData = stubQueryService.getAndPurgeProfileData(queryId)

            val executionTime = System.currentTimeMillis() - startTime

            log().info("Query executed successfully in ${executionTime}ms")

            StubQueryResponse(
               data = results,
               contentType = contentType,
               metadata = ExecutionMetadata(
                  rowCount = rowCount,
                  executionTime = "${executionTime}ms",
                  status = "success",
                  warnings = emptyList(),
                  profilerData = queryProfileData
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
                  profilerData = null
               )
            )
         }
      }
   }

   override fun listOperations(params: ListOperationsRequest): CompletableFuture<ListOperationsResponse> {
      log().info("Received listOperations request for project: ${params.projectRoot}")

      return CompletableFuture.supplyAsync {
         try {
            val taxiDocument = compilerService.lastSuccessfulCompilation()
               ?.documentOrEmpty!!

            ListOperationsResponse(
               operations = taxiDocument.services.flatMap { it.members }
            )
         } catch (e: Exception) {
            log().error("Error listing operations", e)
            throw e
         }
      }
   }
}

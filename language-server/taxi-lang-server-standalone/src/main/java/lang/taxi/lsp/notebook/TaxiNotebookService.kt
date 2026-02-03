package lang.taxi.lsp.notebook

import com.orbitalhq.cockpit.core.query.QueryInsightUtils
import com.orbitalhq.cockpit.core.query.QueryPlanDiagramBuilder
import com.orbitalhq.models.json.Jackson
import com.orbitalhq.playground.StubQueryMessage
import com.orbitalhq.playground.StubQueryService
import com.orbitalhq.schemas.OperationNames
import com.orbitalhq.schemas.Service
import com.orbitalhq.schemas.Type
import com.orbitalhq.schemas.fqn
import com.orbitalhq.schemas.taxi.TaxiSchema
import com.orbitalhq.stubbing.MockTypedInstanceBuilder
import com.orbitalhq.utils.Ids
import lang.taxi.TaxiDocument
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

         val sanitizedQueryPlan = parseResult!!.queryPlan.copy(
            // The QuerySankeyChartRow is causing GSON to have a stack overflow.
            // Don't know why, but it isn't used, so just remove it.
            steps = emptyList(),
         )
         QueryPlanResponse(
            sanitizedQueryPlan
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
                  conditionalResponses = (stub.conditionalResponses.orEmpty()).map { condition ->
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

            val schema = TaxiSchema(taxiDocument, emptyList())
            val members = schema.remoteOperations

            ListOperationsResponse(
               operations = taxiDocument.services.flatMap { service ->
                  service.members.map { member ->
                     toServiceMemberDto(member, service.qualifiedName)
                  }
               }
            )
         } catch (e: Exception) {
            log().error("Error listing operations", e)
            throw e
         }
      }
   }

   /**
    * Map a Taxi ServiceMember to a simple DTO for JSONRPC serialization.
    * Handles all subtypes: Operation, Query, etc.
    */
   private fun toServiceMemberDto(member: lang.taxi.services.ServiceMember, serviceQualifiedName: String): ServiceMemberDto {
      val qualifiedName = member.qualifiedName
      // Extract the name from the qualified name (after the ::)
      val name = qualifiedName.split("::").last()

      // Extract parameters if available (Operations and Queries have them)
      val parameters = when (member) {
         is lang.taxi.services.Operation -> member.parameters.map { param ->
            ParameterDto(
               name = param.name,
               type = TypeReferenceDto(
                  qualifiedName = param.type.qualifiedName,
                  typeName = param.type.toQualifiedName().typeName
               )
            )
         }
         else -> emptyList()
      }

      // Get return type
      val returnType = TypeReferenceDto(
         qualifiedName = member.returnType.qualifiedName,
         typeName = member.returnType.toQualifiedName().typeName
      )

      return ServiceMemberDto(
         qualifiedName = OperationNames.qualifiedName(serviceQualifiedName,member.name).fullyQualifiedName,
         serviceName = serviceQualifiedName,
         name = name,
         parameters = parameters,
         returnType = returnType,
         displayName = OperationNames.displayName(serviceQualifiedName, member.qualifiedName)
      )
   }

   override fun generatePlaceholderStub(params: GeneratePlaceholderRequest): CompletableFuture<GeneratePlaceholderResponse> {
      log().info("Received generatePlaceholderStub request for operation: ${params.operationQualifiedName}")

      return CompletableFuture.supplyAsync {
         try {
            val schema = compilerService.lastSuccessfulCompilation()?.let { TaxiSchema(it.documentOrEmpty, emptyList()) }
               ?: TaxiSchema.empty()
            val operationQualifiedName = params.operationQualifiedName.fqn()
            val (service,operation) = schema.remoteOperation(operationQualifiedName)
            val mockInstance = MockTypedInstanceBuilder.build(operation.returnType, schema)
            val rawValue = mockInstance.toRawObject()
            val json = Jackson.defaultObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rawValue)
            GeneratePlaceholderResponse(jsonStub = json)
         } catch (e: Exception) {
            log().error("Error generating placeholder stub", e)
            throw e
         }
      }
   }

   override fun getDiagramData(params: DiagramDataRequest): CompletableFuture<QueryPlanResponse> {
      log().info("Received getDiagramData request for names: ${params.names}")

      return CompletableFuture.supplyAsync {
         try {
            // TODO: Implement actual diagram generation
            // For now, return a stub/empty response
            log().info("Returning stub diagram data for: ${params.names}")

            val taxi = compilerService.lastSuccessfulCompilation()?.documentOrEmpty ?: TaxiDocument.empty()
            val schema = TaxiSchema(taxi, emptyList())
            val builder = QueryPlanDiagramBuilder(schema)
            params.names.forEach { name ->
               try {
                  when (val member = schema.getMember(name.fqn())) {
                     is Type -> builder.addType(member)
                     is Service -> builder.addService(member)
                  }
               } catch (e: Exception) {
                  // TODO : How do we handle bad types?
               }
            }
            val diagram = builder.build("")
            // Return empty diagram data - the actual implementation will be provided later
            QueryPlanResponse(
               diagramData = diagram
            )
         } catch (e: Exception) {
            log().error("Error generating diagram data", e)
            throw e
         }
      }
   }
}

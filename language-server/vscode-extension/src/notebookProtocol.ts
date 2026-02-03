/**
 * TaxiQL Notebook Protocol Definitions
 *
 * This file defines the RPC protocol between the VSCode extension
 * and the TaxiQL language server for notebook operations.
 */

/**
 * Request to execute a query with stubs.
 * Maps to StubQueryRequest from the JVM.
 */
export interface StubQueryRequest {
   /** The TaxiQL query to execute */
   query: string;

   /** Project root directory */
   projectRoot: string;

   /** Path to the notebook file */
   notebookPath: string;

   /** List of operation stubs */
   stubs: OperationStub[];

   /** Query parameters */
   parameters?: Record<string, any>;
}

/**
 * Stub configuration for a single operation.
 * Matches OperationStub from taxi-playground-core.
 */
export interface OperationStub {
   /** Fully qualified operation name (e.g., "com.example.CustomerService::getCustomer") */
   operationName: string;

   /** JSON response to return */
   response: string;

   /** If true, echo the input parameters as the response */
   echoInput?: boolean;

   /** Conditional responses based on input parameters */
   conditionalResponses?: ResponseCondition[];
}

export interface ResponseCondition {
   /** Input parameter values that trigger this response */
   inputs: ParameterValue[];

   /** The response to return */
   response: StubbedResponse;
}

export interface StubbedResponse {
   /** Response body */
   body: string;
}

export interface ParameterValue {
   /** Parameter name */
   name: string;

   /** Parameter value */
   value: any;
}

/**
 * Response from executing a query with stubs.
 */
export interface StubQueryResponse {
   /** The result data */
   data: any;

   /** Content type of the response */
   contentType: string;

   /** Metadata about the execution */
   metadata: {
      /** Number of rows returned (if applicable) */
      rowCount?: number;

      /** Execution time */
      executionTime?: string;

      /** Status of execution */
      status: string;

      /** Any warnings or errors */
      warnings?: string[];

      /** Debug/trace information */
      trace?: string[];

      [key: string]: any;
   };
}

/**
 * Request parameters for taxiql/listOperations
 * This endpoint lists available operations from the Taxi schema
 */
export interface ListOperationsRequest {
   /** Project root directory (for resolving Taxi schema) */
   projectRoot: string;
}

/**
 * Response from taxiql/listOperations
 * Contains the list of available operations
 */
export interface ListOperationsResponse {
   /** List of operations available in the schema */
   operations: ServiceMemberDto[];
}

/**
 * DTO for service members (operations, queries, etc.)
 * Matches ServiceMemberDto from the JVM
 */
export interface ServiceMemberDto {
   /** Fully qualified name (for requests) */
   qualifiedName: string;

   /** Service qualified name */
   serviceName: string;

   /** Member name (operation name, query name, etc.) */
   name: string;

   /** Operation parameters */
   parameters: ParameterDto[];

   /** Return type */
   returnType: TypeReferenceDto;

   /** Display name for UI */
   displayName: string;
}

export interface ParameterDto {
   /** Parameter name */
   name: string;

   /** Parameter type */
   type: TypeReferenceDto;
}

export interface TypeReferenceDto {
   /** Fully qualified type name */
   qualifiedName: string;

   /** Simple type name */
   typeName: string;
}

/**
 * Response from taxiql/generateQueryPlan and taxiql/getDiagramData
 * Contains the query plan/diagram visualization data
 */
export interface QueryPlanResponse {
   /** The diagram/query plan data (structure defined in docs2.0 query-plan types) */
   diagramData: any;

   /** Any compilation messages or errors */
   messages?: string[];
}

/**
 * Request to generate a placeholder stub for an operation
 */
export interface GeneratePlaceholderRequest {
   /** Fully qualified operation name */
   operationQualifiedName: string;

   /** Project root directory */
   projectRoot: string;
}

/**
 * Response containing generated placeholder JSON
 */
export interface GeneratePlaceholderResponse {
   /** Generated JSON stub */
   jsonStub: string;
}

/**
 * Request to get diagram data for a Taxi diagram
 */
export interface DiagramDataRequest {
   /** List of type/service names to include in diagram */
   names: string[];

   /** Project root directory */
   projectRoot: string;
}

/**
 * LSP Custom Request Methods
 */
export const TAXIQL_EXECUTE_WITH_STUBS = "taxiql/executeWithStubs";
export const TAXIQL_LIST_OPERATIONS = "taxiql/listOperations";
export const TAXIQL_GENERATE_QUERY_PLAN = "taxiql/generateQueryPlan";
export const TAXIQL_GENERATE_PLACEHOLDER_STUB = "taxiql/generatePlaceholderStub";
export const TAXIQL_GET_DIAGRAM_DATA = "taxiql/getDiagramData";

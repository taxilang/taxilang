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
   operations: Operation[];
}

export interface Operation {
   /** Service name */
   service: string;

   /** Operation name */
   operation: string;

   /** Return type of the operation */
   returnType: string;

   /** Optional metadata about the operation */
   metadata?: {
      /** HTTP method (if applicable) */
      httpMethod?: string;

      /** URL path (if applicable) */
      path?: string;

      /** Parameters */
      parameters?: Array<{
         name: string;
         type: string;
      }>;

      [key: string]: any;
   };
}

/**
 * LSP Custom Request Methods
 */
export const TAXIQL_EXECUTE_WITH_STUBS = "taxiql/executeWithStubs";
export const TAXIQL_LIST_OPERATIONS = "taxiql/listOperations";

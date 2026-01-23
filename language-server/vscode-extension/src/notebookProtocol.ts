/**
 * TaxiQL Notebook Protocol Definitions
 *
 * This file defines the RPC protocol between the VSCode extension
 * and the TaxiQL language server for notebook operations.
 */

/**
 * Request parameters for taxiql/plan
 * This endpoint generates a query execution plan
 */
export interface PlanRequest {
   /** The TaxiQL query to plan */
   query: string;

   /** Project root directory (for resolving imports, etc.) */
   projectRoot: string;

   /** Path to the notebook file (for context) */
   notebookPath: string;
}

/**
 * Response from taxiql/plan
 * Contains the query execution plan as a graph
 */
export interface PlanResponse {
   /** Nodes in the execution plan */
   nodes: PlanNode[];

   /** Edges between nodes in the execution plan */
   edges: PlanEdge[];

   /** Additional metadata about the plan */
   metadata?: {
      /** Original query (truncated if too long) */
      query?: string;

      /** Status of plan generation */
      status?: string;

      /** Any warnings or notes */
      warnings?: string[];

      [key: string]: any;
   };
}

export interface PlanNode {
   /** Unique identifier for this node */
   id: string;

   /** Display label for the node */
   label: string;

   /** Type of operation (e.g., "root", "operation", "service", "transformation") */
   type: string;

   /** Additional metadata about this node */
   metadata?: {
      /** Source location in Taxi files */
      sourceLocation?: string;

      /** Service name (if applicable) */
      serviceName?: string;

      /** Operation name (if applicable) */
      operationName?: string;

      [key: string]: any;
   };
}

export interface PlanEdge {
   /** Source node ID */
   from: string;

   /** Target node ID */
   to: string;

   /** Optional label for the edge */
   label?: string;
}

/**
 * Request parameters for taxiql/executeWithStubs
 * This endpoint executes a query using stubbed service responses
 */
export interface ExecuteWithStubsRequest {
   /** The TaxiQL query to execute */
   query: string;

   /** ID of the stubs to use for execution */
   stubsId: string;

   /** Project root directory */
   projectRoot: string;

   /** Path to the notebook file */
   notebookPath: string;
}

/**
 * Response from taxiql/executeWithStubs
 * Contains the execution results and metadata
 */
export interface ExecuteWithStubsResponse {
   /** The result data (array of objects, single object, or primitive) */
   data: any;

   /** Metadata about the execution */
   metadata: {
      /** Original query (truncated if too long) */
      query?: string;

      /** Stubs ID used */
      stubsId: string;

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
export const TAXIQL_PLAN = "taxiql/plan";
export const TAXIQL_EXECUTE_WITH_STUBS = "taxiql/executeWithStubs";
export const TAXIQL_LIST_OPERATIONS = "taxiql/listOperations";

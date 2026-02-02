/**
 * Types for Stub Editor Webview
 * These match the protocol types but are local to the webview
 */

export interface OperationStub {
   operationName: string;
   response: string;
   conditionalResponses?: ResponseCondition[];
   echoInput?: boolean;
}

export interface ResponseCondition {
   inputs: ParameterValue[];
   response: StubbedResponse;
}

export interface StubbedResponse {
   body: string;
}

export interface ParameterValue {
   name: string;
   value: any;
}

export interface ServiceMember {
   qualifiedName: string;
   serviceName: string;
   name: string;
   parameters: Parameter[];
   returnType: TypeReference;
   displayName: string;
}

export interface TypeReference {
   qualifiedName: string;
   typeName: string;
}

export interface Parameter {
   name: string;
   type: TypeReference;
}

/**
 * Message types for webview communication
 */
export type WebviewMessage =
   | { type: 'getOperations' }
   | { type: 'save'; stubs: OperationStub[] }
   | { type: 'cancel' }
   | { type: 'generatePlaceholder'; operationQualifiedName: string };

export type ExtensionMessage =
   | { type: 'init'; stubs: OperationStub[]; operations: ServiceMember[] }
   | { type: 'operationsLoaded'; operations: ServiceMember[] }
   | { type: 'placeholderGenerated'; jsonStub: string };

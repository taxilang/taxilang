/**
 * Type definitions for query plan visualization
 * Adapted from the Orbital UI codebase
 */

export interface QualifiedName {
  fullyQualifiedName: string;
  typeName: string;
  namespace: string;
}

export interface CompilationMessage {
  severity: string;
  message: string;
  line?: number;
  column?: number;
}

export interface Type {
  name: QualifiedName;
  // Add other properties as needed
}

export interface QueryParameter {
  name: string;
  value: any;
}

export type DiagramNodeKind =
  | 'SERVICE'
  | 'SCALAR_TYPE'
  | 'MODEL'
  | 'QUERY'
  | 'OPERATION'
  | 'CONSTANT'
  | 'EXPRESSION'
  | 'REQUEST_MODEL'
  | 'RESPONSE_MODEL'
  | 'REQUEST_RESPONSE_MODEL';

export interface DiagramNodeMember {
  handleId: string;
  name: string;
  typeName: string;
}

export interface DiagramNode {
  id: string;
  kind: DiagramNodeKind;
  title: string;
  icon: string | null;
  badgeLabel: string | null;
  qualifiedName: string | null;
  members: DiagramNodeMember[];
}

export interface DiagramLink {
  sourceId: string;
  sourceHandleId: string;
  targetId: string;
  targetHandleId: string;
}

export interface QueryPlanDiagramData {
  queryId: string;
  nodes: DiagramNode[];
  links: DiagramLink[];
}

export interface QueryPlan {
  steps: any[]; // QuerySankeyChartRow[] - simplified for now
  queryExecutionMessages: any; // Message - simplified for now
  diagramData: QueryPlanDiagramData;
}

export interface QueryParseMetadata {
  taxi: string;
  queryKind: string;
  name: QualifiedName;
  compilationMessages: CompilationMessage[];
  queryPlan: QueryPlan;
  returnType: Type;
  parameters: QueryParameter[];
  facts: QueryParameter[];
  hasCompilationErrors: boolean;
  hasQueryErrors: boolean;
}

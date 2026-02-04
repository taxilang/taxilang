import React from 'react';
import { Node } from '@xyflow/react';
import { DiagramNode } from '../types';
import { BaseQueryPlanNode } from './BaseQueryPlanNode';

export interface QueryPlanOperationNodeData {
  node: DiagramNode;
}

export default function QueryPlanOperationNode({ data }: Node<QueryPlanOperationNodeData>) {
  return <BaseQueryPlanNode node={data.node} />;
}

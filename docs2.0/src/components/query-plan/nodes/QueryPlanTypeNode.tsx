import React from 'react';
import { Node } from '@xyflow/react';
import { DiagramNode } from '../types';
import { BaseQueryPlanNode } from './BaseQueryPlanNode';

export interface QueryPlanTypeNodeData extends Record<string, unknown> {
  node: DiagramNode;
}

export default function QueryPlanTypeNode({ data }: Node<QueryPlanTypeNodeData>) {
  return <BaseQueryPlanNode node={data.node} />;
}

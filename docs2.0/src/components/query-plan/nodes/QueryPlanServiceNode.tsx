import React from 'react';
import { Node } from '@xyflow/react';
import { DiagramNode } from '../types';
import { BaseQueryPlanNode } from './BaseQueryPlanNode';

export interface QueryPlanServiceNodeData {
  node: DiagramNode;
}

export default function QueryPlanServiceNode({ data }: Node<QueryPlanServiceNodeData>) {
  return <BaseQueryPlanNode node={data.node} />;
}

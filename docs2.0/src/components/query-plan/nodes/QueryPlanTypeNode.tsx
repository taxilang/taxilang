import React from 'react';
import { Handle, Node, Position } from '@xyflow/react';
import { DiagramNode } from '../types';
import { NodeBadge } from '../NodeBadge';
import { getNodeTheme } from '../theme';

export interface QueryPlanTypeNodeData {
  node: DiagramNode;
}

export default function QueryPlanTypeNode({ data }: Node<QueryPlanTypeNodeData>) {
  const { node } = data;
  const theme = getNodeTheme(node.kind);
  const badgeLabel = node.badgeLabel || node.kind;

  return (
    <div className="query-plan-type-node">
      <Handle type="target" position={Position.Left} id={`${node.id}-left`} />
      <div className="query-plan-type-node-header">
        <span className="query-plan-type-node-title">{node.title}</span>
        <NodeBadge label={badgeLabel} kind={node.kind} iconId={node.icon} />
      </div>
      {node.members && node.members.length > 0 && (
        <div className="query-plan-type-node-body">
          {node.members.map((member) => (
            <div key={member.handleId} className="query-plan-type-node-member">
              <Handle
                type="target"
                position={Position.Left}
                id={`${member.handleId}-left`}
                style={{ top: 'auto' }}
              />
              <span className="member-name">{member.name}</span>
              <span className="member-type">{member.typeName}</span>
              <Handle
                type="source"
                position={Position.Right}
                id={`${member.handleId}-right`}
                style={{ top: 'auto' }}
              />
            </div>
          ))}
        </div>
      )}
      <Handle type="source" position={Position.Right} id={`${node.id}-right`} />

      <style jsx>{`
        .query-plan-type-node {
          background: white;
          border: 2px solid ${theme.borderColor};
          border-radius: 6px;
          min-width: 200px;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        }

        .query-plan-type-node-header {
          padding: 8px 12px;
          background: ${theme.backgroundColor};
          border-bottom: 1px solid ${theme.borderColor}30;
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 8px;
        }

        .query-plan-type-node-title {
          font-weight: 600;
          font-size: 14px;
          color: ${theme.textColor};
        }

        .query-plan-type-node-body {
          padding: 4px 0;
        }

        .query-plan-type-node-member {
          padding: 4px 12px;
          display: flex;
          justify-content: space-between;
          align-items: center;
          font-size: 13px;
          position: relative;
        }

        .query-plan-type-node-member:not(:last-child) {
          border-bottom: 1px dashed #e5e7eb;
        }

        .member-name {
          color: #374151;
        }

        .member-type {
          color: #9ca3af;
          font-size: 12px;
        }
      `}</style>
    </div>
  );
}

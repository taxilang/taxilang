import React from 'react';
import { Handle, Position } from '@xyflow/react';
import { DiagramNode } from '../types';
import { NodeBadge } from '../NodeBadge';
import { getNodeTheme } from '../theme';

interface BaseQueryPlanNodeProps {
  node: DiagramNode;
  headerHandles?: boolean; // Whether to show handles in the header
}

export function BaseQueryPlanNode({ node }: BaseQueryPlanNodeProps) {
  const theme = getNodeTheme(node.kind);
  const badgeLabel = node.badgeLabel || node.kind;

  return (
    <div className="query-plan-node">
      <div className="query-plan-node-header">
        <Handle type="target" position={Position.Left} id={`${node.id}-left`} />
        <span className="query-plan-node-title">{node.title}</span>
        <NodeBadge label={badgeLabel} kind={node.kind} iconId={node.icon} />
        <Handle type="source" position={Position.Right} id={`${node.id}-right`} />
      </div>
      {node.members && node.members.length > 0 && (
        <div className="query-plan-node-body">
          {node.members.map((member) => (
            <div key={member.handleId} className="query-plan-node-member">
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

      <style jsx>{`
        .query-plan-node {
          background: white;
          border: 2px solid ${theme.borderColor};
          border-radius: 6px;
          min-width: 200px;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        }

        .query-plan-node-header {
          position: relative;
          padding: 8px 12px;
          background: ${theme.backgroundColor};
          border-bottom: 1px solid ${theme.borderColor}30;
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 8px;
        }

        .query-plan-node-title {
          font-weight: 600;
          font-size: 14px;
          color: ${theme.textColor};
        }

        .query-plan-node-body {
          padding: 4px 0;
        }

        .query-plan-node-member {
          padding: 4px 12px;
          display: flex;
          justify-content: space-between;
          align-items: center;
          font-size: 13px;
          position: relative;
        }

        .query-plan-node-member:not(:last-child) {
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

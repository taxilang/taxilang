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

  const nodeStyle: React.CSSProperties = {
    background: 'white',
    border: `2px solid ${theme.borderColor}`,
    borderRadius: '6px',
    minWidth: '200px',
    boxShadow: '0 2px 4px rgba(0, 0, 0, 0.1)',
  };

  const headerStyle: React.CSSProperties = {
    position: 'relative',
    padding: '8px 12px',
    background: theme.backgroundColor,
    borderBottom: `1px solid ${theme.borderColor}30`,
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: '8px',
  };

  const titleStyle: React.CSSProperties = {
    fontWeight: 600,
    fontSize: '14px',
    color: theme.textColor,
  };

  const bodyStyle: React.CSSProperties = {
    padding: '4px 0',
  };

  const memberStyle: React.CSSProperties = {
    padding: '4px 12px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    fontSize: '13px',
    position: 'relative',
  };

  const memberNameStyle: React.CSSProperties = {
    color: '#374151',
  };

  const memberTypeStyle: React.CSSProperties = {
    color: '#9ca3af',
    fontSize: '12px',
  };

  return (
    <div style={nodeStyle}>
      <div style={headerStyle}>
        <Handle type="target" position={Position.Left} id={`${node.id}-left`} />
        <span style={titleStyle}>{node.title}</span>
        <NodeBadge label={badgeLabel} kind={node.kind} iconId={node.icon} />
        <Handle type="source" position={Position.Right} id={`${node.id}-right`} />
      </div>
      {node.members && node.members.length > 0 && (
        <div style={bodyStyle}>
          {node.members.map((member, index) => (
            <div
              key={member.handleId}
              style={{
                ...memberStyle,
                borderBottom: index < node.members.length - 1 ? '1px dashed #e5e7eb' : 'none',
              }}
            >
              <Handle
                type="target"
                position={Position.Left}
                id={`${member.handleId}-left`}
                style={{ top: 'auto' }}
              />
              <span style={memberNameStyle}>{member.name}</span>
              <span style={memberTypeStyle}>{member.typeName}</span>
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
    </div>
  );
}

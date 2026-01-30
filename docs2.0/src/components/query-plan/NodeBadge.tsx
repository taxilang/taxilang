import React from 'react';
import * as TablerIcons from '@tabler/icons-react';
import { DiagramNodeKind, getNodeTheme, getBadgeClass } from './theme';

interface NodeBadgeProps {
  label: string;
  kind: DiagramNodeKind;
  iconId?: string | null;
}

// Icon mapping from server icon IDs to Tabler icon components
const ICON_COMPONENTS: Record<string, React.ComponentType<any>> = {
  'code-variable': TablerIcons.IconVariable,
  'arrow-right-to-arc': TablerIcons.IconArrowRightToArc,
  'arrow-left-from-arc': TablerIcons.IconArrowLeftFromArc,
  'arrows-exchange': TablerIcons.IconArrowsExchange,
  'blocks': TablerIcons.IconBlocks,
  'help-hexagon': TablerIcons.IconHelpHexagon,
  'switch-horizontal': TablerIcons.IconSwitchHorizontal,
  'square-letter-c': TablerIcons.IconSquareLetterC,
  'file-lambda': TablerIcons.IconLambda,
};

function getIconComponent(iconId: string | null | undefined): React.ComponentType<any> | null {
  if (!iconId) return null;
  return ICON_COMPONENTS[iconId] || null;
}

export function NodeBadge({ label, kind, iconId }: NodeBadgeProps): React.JSX.Element {
  const IconComponent = getIconComponent(iconId);
  const theme = getNodeTheme(kind);
  const cssClass = getBadgeClass(kind);

  return (
    <span className={`node-badge ${cssClass}`}>
      {IconComponent && (
        <IconComponent className="badge-icon" size={14} stroke={1.5} />
      )}
      <span className="badge-label">{label}</span>

      <style jsx>{`
        .node-badge {
          font-family: var(--font-sans, system-ui, sans-serif);
          font-weight: normal;
          padding: 2px 6px;
          font-size: 10px;
          margin-left: 8px;
          align-self: center;
          text-transform: capitalize;
          border-radius: 4px;
          display: flex;
          align-items: center;
          gap: 4px;
          background-color: ${theme.badgeBackground};
          color: ${theme.badgeTextColor};
        }

        .node-badge :global(.badge-icon) {
          flex-shrink: 0;
        }

        .badge-label {
          line-height: 1;
          white-space: nowrap;
        }
      `}</style>
    </span>
  );
}

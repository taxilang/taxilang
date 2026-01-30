/**
 * Centralized theme and color definitions for query plan nodes
 */

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

export interface NodeTheme {
  borderColor: string;
  backgroundColor: string;
  textColor: string;
  badgeBackground: string;
  badgeTextColor: string;
}

export const NODE_THEMES: Record<DiagramNodeKind, NodeTheme> = {
  MODEL: {
    borderColor: '#84cc16', // lime-500
    backgroundColor: '#f7fee7', // lime-50
    textColor: '#3f6212', // lime-800
    badgeBackground: 'rgba(77, 124, 15, 0.1)',
    badgeTextColor: 'rgba(77, 124, 15, 0.8)',
  },
  SCALAR_TYPE: {
    borderColor: '#a855f7', // purple-500
    backgroundColor: '#faf5ff', // purple-50
    textColor: '#6b21a8', // purple-800
    badgeBackground: 'rgba(168, 85, 247, 0.1)',
    badgeTextColor: 'rgba(168, 85, 247, 0.8)',
  },
  REQUEST_MODEL: {
    borderColor: '#3b82f6', // blue-500
    backgroundColor: '#eff6ff', // blue-50
    textColor: '#1e40af', // blue-800
    badgeBackground: 'rgba(59, 130, 246, 0.1)',
    badgeTextColor: 'rgba(59, 130, 246, 1)',
  },
  RESPONSE_MODEL: {
    borderColor: '#22c55e', // green-500
    backgroundColor: '#f0fdf4', // green-50
    textColor: '#166534', // green-800
    badgeBackground: 'rgba(34, 197, 94, 0.1)',
    badgeTextColor: 'rgba(34, 197, 94, 1)',
  },
  REQUEST_RESPONSE_MODEL: {
    borderColor: '#14b8a6', // teal-500
    backgroundColor: '#f0fdfa', // teal-50
    textColor: '#115e59', // teal-800
    badgeBackground: 'rgba(20, 184, 166, 0.1)',
    badgeTextColor: 'rgba(20, 184, 166, 1)',
  },
  SERVICE: {
    borderColor: '#0284c7', // sky-600
    backgroundColor: '#f0f9ff', // sky-50
    textColor: '#075985', // sky-900
    badgeBackground: 'rgba(2, 132, 199, 0.1)',
    badgeTextColor: 'rgba(2, 132, 199, 1)',
  },
  OPERATION: {
    borderColor: '#0284c7', // sky-600
    backgroundColor: '#f0f9ff', // sky-50
    textColor: '#075985', // sky-900
    badgeBackground: 'rgba(2, 132, 199, 0.1)',
    badgeTextColor: 'rgba(2, 132, 199, 1)',
  },
  QUERY: {
    borderColor: '#8b5cf6', // violet-500
    backgroundColor: '#f5f3ff', // violet-50
    textColor: '#5b21b6', // violet-800
    badgeBackground: 'rgba(139, 92, 246, 0.1)',
    badgeTextColor: 'rgba(139, 92, 246, 1)',
  },
  EXPRESSION: {
    borderColor: '#f59e0b', // amber-500
    backgroundColor: '#fffbeb', // amber-50
    textColor: '#78350f', // amber-900
    badgeBackground: 'rgba(245, 158, 11, 0.1)',
    badgeTextColor: 'rgba(245, 158, 11, 1)',
  },
  CONSTANT: {
    borderColor: '#10b981', // emerald-500
    backgroundColor: '#ecfdf5', // emerald-50
    textColor: '#065f46', // emerald-900
    badgeBackground: 'rgba(16, 185, 129, 0.1)',
    badgeTextColor: 'rgba(16, 185, 129, 1)',
  },
};

export function getNodeTheme(kind: DiagramNodeKind): NodeTheme {
  return NODE_THEMES[kind] || NODE_THEMES.MODEL;
}

export function getBadgeClass(kind: DiagramNodeKind): string {
  return kind.toLowerCase().replace(/_/g, '-');
}

import React, { useCallback, useEffect, useState } from 'react'
import {
  ReactFlow,
  Node,
  Edge,
  useNodesState,
  useEdgesState,
  useReactFlow,
  ReactFlowProvider,
  Controls,
  ControlButton,
  Background,
  BackgroundVariant,
  MarkerType,
  Position,
  useNodesInitialized,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { QueryPlanDiagramData, DiagramNode as DiagramNodeData, DiagramLink } from './types'
import QueryPlanNode, { Link } from './nodes/QueryPlanNode'
import QueryPlanTypeNode from './nodes/QueryPlanTypeNode'
import QueryPlanServiceNode from './nodes/QueryPlanServiceNode'
import QueryPlanOperationNode from './nodes/QueryPlanOperationNode'
import { ArrowsPointingOutIcon, ArrowsPointingInIcon } from '@heroicons/react/24/outline'
import colors from 'tailwindcss/colors'
import { applyFixedPortElkLayout } from './elk-chart-layout'

const nodeTypes = {
  QueryPlanNode: QueryPlanNode,
  QueryPlanTypeNode: QueryPlanTypeNode,
  QueryPlanServiceNode: QueryPlanServiceNode,
  QueryPlanOperationNode: QueryPlanOperationNode,
}

interface QueryPlanVisualizationProps {
  queryPlanData: QueryPlanDiagramData | null;
  height?: number;
  title?: string; // Optional title for the visualization
}

function QueryPlanFlowInternal({ queryPlanData, height = 400, title }: QueryPlanVisualizationProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [isFullScreen, setIsFullScreen] = useState(false)
  const instance = useReactFlow()
  const nodesInitialized = useNodesInitialized()

  // Handle escape key to exit fullscreen
  useEffect(() => {
    const handleEsc = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsFullScreen(false)
      }
    }
    window.addEventListener('keydown', handleEsc)
    return () => {
      window.removeEventListener('keydown', handleEsc)
    }
  }, [])

  // Build nodes and edges when data changes
  useEffect(() => {
    if (!queryPlanData || !queryPlanData.nodes || queryPlanData.nodes.length === 0) {
      setNodes([])
      setEdges([])
      return
    }

    const buildResult = buildNodesAndEdges(queryPlanData)
    setNodes(buildResult.nodes)
    setEdges(buildResult.edges)
  }, [queryPlanData])

  // Perform layout after nodes are initialized (measured)
  useEffect(() => {
    if (nodesInitialized && nodes.length > 0) {
      performLayout()
    }
  }, [nodesInitialized])

  const performLayout = async () => {
    const layoutedNodes = await applyFixedPortElkLayout(instance.getNodes(), instance.getEdges(), 'RIGHT')
    setNodes(layoutedNodes)
  }

  // Rebuild edges when fullscreen changes (to update colors)
  useEffect(() => {
    if (queryPlanData && queryPlanData.nodes && queryPlanData.nodes.length > 0) {
      const buildResult = buildNodesAndEdges(queryPlanData)
      setEdges(buildResult.edges)
    }
  }, [isFullScreen, queryPlanData])

  // Handle fullscreen view changes
  useEffect(() => {
    // Wait for DOM to update before applying view changes
    setTimeout(() => {
      if (isFullScreen) {
        // Entering fullscreen - fit to view with padding
        instance.fitView({ padding: 0.2, duration: 300 })
      } else {
        // Exiting fullscreen - reset to default viewport
        instance.setViewport({ x: 0, y: 0, zoom: 1 }, { duration: 300 })
      }
    }, 50)
  }, [isFullScreen, instance])

  const buildNodesAndEdges = (data: QueryPlanDiagramData) => {
    // Helper function to determine node type based on kind
    const getNodeType = (kind: string): string => {
      switch (kind) {
        case 'OPERATION':
          return 'QueryPlanOperationNode'
        case 'SERVICE':
          return 'QueryPlanServiceNode'
        case 'SCALAR_TYPE':
        case 'MODEL':
        case 'REQUEST_MODEL':
        case 'RESPONSE_MODEL':
        case 'REQUEST_RESPONSE_MODEL':
          return 'QueryPlanTypeNode'
        default:
          return 'QueryPlanNode'
      }
    }

    // Helper function to convert DiagramLink to Link
    const convertLink = (link: DiagramLink): Link => ({
      sourceNodeId: link.sourceId,
      sourceHandleId: link.sourceHandleId,
      sourceNodeName: null as any, // Not needed for query plan
      targetNodeId: link.targetId,
      targetHandleId: link.targetHandleId,
      targetNodeName: null as any, // Not needed for query plan
      linkKind: 'entity',
    });

    // Build React Flow nodes (Elk will position them)
    const reactFlowNodes: Node[] = data.nodes.map((diagramNode) => ({
      id: diagramNode.id,
      type: getNodeType(diagramNode.kind),
      position: { x: 0, y: 0 }, // Elk will set the real position
      data: {
        node: diagramNode,
        inboundLinks: diagramNode.inboundHeaderLinks.map(convertLink),
        outboundLinks: diagramNode.outboundHeaderLinks.map(convertLink),
        memberLinks: Object.fromEntries(
          Object.entries(diagramNode.memberLinks).map(([handleId, links]) => [
            handleId,
            links.map(convertLink),
          ])
        ),
      },
    }))

    // Build React Flow edges
    const edgeColor = isFullScreen ? colors.slate['700'] : colors.slate['500']
    const reactFlowEdges: Edge[] = data.links.map((link, index) => ({
      id: `edge-${index}`,
      source: link.sourceId,
      target: link.targetId,
      sourceHandle: `${link.sourceHandleId}-right`,
      targetHandle: `${link.targetHandleId}-left`,
      type: 'smoothstep',
      markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 10,
        height: 10,
        color: edgeColor,
      },
      style: {
        strokeWidth: 2,
        stroke: edgeColor,
      },
    }))

    return { nodes: reactFlowNodes, edges: reactFlowEdges }
  }

  const wrapperStyle = isFullScreen
    ? {
      position: 'fixed' as const,
      top: '1rem',
      left: '1rem',
      width: 'calc(100vw - 2rem)',
      height: 'calc(100vh - 2rem)',
      zIndex: 9999,
      backgroundColor: '#f8fafc',
      borderRadius: '8px',
      boxShadow: '0 10px 40px rgba(0, 0, 0, 0.3)',
      color: 'black',
      display: 'flex',
      flexDirection: 'column' as const,
    }
    : {
      width: '100%',
      height: title ? `${height + 40}px` : `${height}px`, // Add space for title if present
      color: 'black',
      display: 'flex',
      flexDirection: 'column' as const,
    }

  const titleStyle: React.CSSProperties = {
    padding: '8px 16px',
    fontSize: '16px',
    fontWeight: 600,
    color: 'var(--vscode-foreground)',
  }

  const flowContainerStyle: React.CSSProperties = {
    flex: 1,
    minHeight: 0, // Important for flex child with overflow
  }

  return (
    <div style={wrapperStyle}>
      {title && <div style={titleStyle}>{title}</div>}
      <div style={flowContainerStyle} className="query-plan-flow">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          defaultViewport={{ x: 0, y: 0, zoom: 1 }}
        >
          <Controls showInteractive={false}>
            <ControlButton
              onClick={() => setIsFullScreen(!isFullScreen)}
              title={isFullScreen ? 'Exit fullscreen' : 'Enter fullscreen'}
            >
              {isFullScreen ? (
                <ArrowsPointingInIcon className="w-4 h-4 text-slate-700" />
              ) : (
                <ArrowsPointingOutIcon className="w-4 h-4 text-slate-700" />
              )}
            </ControlButton>
          </Controls>
          <Background color="#94a3b8" variant={BackgroundVariant.Dots} />
        </ReactFlow>
      </div>
    </div>
  )
}

export default function QueryPlanVisualization(props: QueryPlanVisualizationProps) {
  return (
    <ReactFlowProvider>
      <QueryPlanFlowInternal {...props} />
    </ReactFlowProvider>
  )
}

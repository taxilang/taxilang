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
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { QueryPlanDiagramData, DiagramNode as DiagramNodeData } from './types'
import QueryPlanNode from './nodes/QueryPlanNode'
import QueryPlanTypeNode from './nodes/QueryPlanTypeNode'
import QueryPlanServiceNode from './nodes/QueryPlanServiceNode'
import QueryPlanOperationNode from './nodes/QueryPlanOperationNode'
import { ArrowsPointingOutIcon, ArrowsPointingInIcon } from '@heroicons/react/24/outline'
import colors from 'tailwindcss/colors'

const nodeTypes = {
  QueryPlanNode: QueryPlanNode,
  QueryPlanTypeNode: QueryPlanTypeNode,
  QueryPlanServiceNode: QueryPlanServiceNode,
  QueryPlanOperationNode: QueryPlanOperationNode,
}

interface QueryPlanVisualizationProps {
  queryPlanData: QueryPlanDiagramData | null;
  height?: number;
}

function QueryPlanFlowInternal({ queryPlanData, height = 400 }: QueryPlanVisualizationProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [isFullScreen, setIsFullScreen] = useState(false)
  const { fitView } = useReactFlow()

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

  // Rebuild edges when fullscreen changes (to update colors)
  useEffect(() => {
    if (queryPlanData && queryPlanData.nodes && queryPlanData.nodes.length > 0) {
      const buildResult = buildNodesAndEdges(queryPlanData)
      setEdges(buildResult.edges)
    }
  }, [isFullScreen, queryPlanData])

  // Fit view when entering fullscreen
  useEffect(() => {
    if (isFullScreen) {
      // Wait for DOM to update before fitting view
      setTimeout(() => {
        fitView({ padding: 0.2, duration: 300 })
      }, 50)
    }
  }, [isFullScreen, fitView])

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

    // Simple layout algorithm: arrange nodes in columns based on their connections
    const nodeDepths = new Map<string, number>()

    // Calculate depth for each node (0 = source nodes)
    const calculateDepth = (nodeId: string, visited = new Set<string>()): number => {
      if (nodeDepths.has(nodeId)) {
        return nodeDepths.get(nodeId)!
      }
      if (visited.has(nodeId)) {
        return 0 // Circular dependency, treat as source
      }

      visited.add(nodeId)
      const incomingLinks = data.links.filter(link => link.targetId === nodeId)

      if (incomingLinks.length === 0) {
        nodeDepths.set(nodeId, 0)
        return 0
      }

      const maxSourceDepth = Math.max(
        ...incomingLinks.map(link => calculateDepth(link.sourceId, new Set(visited))),
      )
      const depth = maxSourceDepth + 1
      nodeDepths.set(nodeId, depth)
      return depth
    }

    // Calculate depths for all nodes
    data.nodes.forEach(node => calculateDepth(node.id))

    // Group nodes by depth
    const nodesByDepth = new Map<number, DiagramNodeData[]>()
    data.nodes.forEach(node => {
      const depth = nodeDepths.get(node.id) || 0
      if (!nodesByDepth.has(depth)) {
        nodesByDepth.set(depth, [])
      }
      nodesByDepth.get(depth)!.push(node)
    })

    // Layout parameters
    const horizontalSpacing = 300
    const verticalSpacing = 100
    const startX = 50
    const startY = 50

    // Build React Flow nodes with positions
    const reactFlowNodes: Node[] = []
    nodesByDepth.forEach((nodesAtDepth, depth) => {
      nodesAtDepth.forEach((diagramNode, index) => {
        reactFlowNodes.push({
          id: diagramNode.id,
          type: getNodeType(diagramNode.kind),
          position: {
            x: startX + depth * horizontalSpacing,
            y: startY + index * verticalSpacing,
          },
          data: {
            node: diagramNode,
          },
        })
      })
    })

    // Build React Flow edges
    const reactFlowEdges: Edge[] = data.links.map((link, index) => {
      const edgeColor = isFullScreen ? colors.slate['700'] : colors.slate['500']
      return {
        id: `edge-${index}`,
        source: link.sourceId,
        target: link.targetId,
        sourceHandle: `${link.sourceHandleId}-right`,
        targetHandle: `${link.targetHandleId}-left`,
        type: 'smoothstep',
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 15,
          height: 15,
          color: edgeColor,
        },
        style: {
          strokeWidth: 2,
          stroke: edgeColor,
        },
      }
    })

    return { nodes: reactFlowNodes, edges: reactFlowEdges }
  }

  const containerStyle = isFullScreen
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
    }
    : {
      width: '100%',
      height: `${height}px`,
      color: 'black'
    }

  return (
    <div style={containerStyle} className="query-plan-flow">
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
  )
}

export default function QueryPlanVisualization(props: QueryPlanVisualizationProps) {
  return (
    <ReactFlowProvider>
      <QueryPlanFlowInternal {...props} />
    </ReactFlowProvider>
  )
}

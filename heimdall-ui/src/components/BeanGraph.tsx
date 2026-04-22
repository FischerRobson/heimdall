import { useEffect, useMemo, useState, useRef, useCallback } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  useNodesState,
  useEdgesState,
  Position,
  type Node,
  type Edge,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

interface BeanNode {
  id: string
  fullClass: string
  scope: string
  types: string[]
}

interface BeanEdge {
  from: string
  to: string
}

interface BeanGraphData {
  nodes: BeanNode[]
  edges: BeanEdge[]
}

interface Props {
  data: BeanGraphData
}

const SCOPE_COLORS: Record<string, { border: string; bg: string; text: string }> = {
  ApplicationScoped: { border: '#00e5a0', bg: 'rgba(0,229,160,0.08)', text: '#00e5a0' },
  Singleton:         { border: '#3b82f6', bg: 'rgba(59,130,246,0.08)', text: '#3b82f6' },
  RequestScoped:     { border: '#f59e0b', bg: 'rgba(245,158,11,0.08)', text: '#f59e0b' },
  Dependent:         { border: '#a78bfa', bg: 'rgba(167,139,250,0.08)', text: '#a78bfa' },
  SessionScoped:     { border: '#f43f5e', bg: 'rgba(244,63,94,0.08)',  text: '#f43f5e' },
}

const DEFAULT_COLOR = { border: '#475569', bg: 'rgba(71,85,105,0.08)', text: '#475569' }

function getColor(scope: string) {
  return SCOPE_COLORS[scope] ?? DEFAULT_COLOR
}

function getConnected(id: string, edges: BeanEdge[]) {
  const children = edges.filter(e => e.from === id).map(e => e.to)
  const parents = edges.filter(e => e.to === id).map(e => e.from)
  return { children, parents, all: new Set([id, ...children, ...parents]) }
}

interface DetailPanel {
  id: string
  scope: string
  pkg: string
  deps: number
  usedBy: number
}

export function BeanGraph({ data }: Props) {
  const { nodes: beans, edges: beanEdges } = data

  const [focusedBean, setFocusedBean] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [hiddenScopes, setHiddenScopes] = useState<Set<string>>(new Set())
  const [detail, setDetail] = useState<DetailPanel | null>(null)

  const scopes = useMemo(() => [...new Set(beans.map(b => b.scope))], [beans])

  const toggleScope = useCallback((scope: string) => {
    setHiddenScopes(prev => {
      const next = new Set(prev)
      next.has(scope) ? next.delete(scope) : next.add(scope)
      return next
    })
  }, [])

  const visibleBeans = useMemo(() =>
    beans.filter(b => !hiddenScopes.has(b.scope)),
    [beans, hiddenScopes]
  )

  const visibleIds = useMemo(() =>
    new Set(visibleBeans.map(b => b.id)),
    [visibleBeans]
  )

  const buildNodes = useCallback((): Node[] => {
    const COLS = 3
    const H_GAP = 230
    const V_GAP = 110

    return visibleBeans.map((bean, i) => {
      const color = getColor(bean.scope)
      const connected = focusedBean ? getConnected(focusedBean, beanEdges) : null

      let opacity = 1
      let borderColor = color.border
      let bgColor = color.bg

      if (focusedBean) {
        const isFocused = bean.id === focusedBean
        const isConnected = connected!.all.has(bean.id)
        opacity = isFocused ? 1 : isConnected ? 0.85 : 0.12
        if (isFocused) bgColor = color.bg.replace('0.08', '0.18')
      }

      if (searchQuery) {
        const matches = bean.id.toLowerCase().includes(searchQuery.toLowerCase())
        opacity = matches ? 1 : 0.1
        if (matches) borderColor = '#f59e0b'
      }

      const isParent = connected?.parents.includes(bean.id)
      const isChild = connected?.children.includes(bean.id)
      const roleColor = isParent ? '#3b82f6' : isChild ? '#00e5a0' : color.text

      return {
        id: bean.id,
        position: {
          x: (i % COLS) * H_GAP + 40,
          y: Math.floor(i / COLS) * V_GAP + 40,
        },
        sourcePosition: Position.Right,
        targetPosition: Position.Left,
        data: {
          label: (
            <div style={{ textAlign: 'left', opacity }}>
              <div style={{
                fontSize: '12px',
                fontWeight: 500,
                color: '#e2e8f0',
                fontFamily: 'monospace',
                marginBottom: '3px'
              }}>
                {bean.id}
              </div>
              <div style={{
                fontSize: '9px',
                color: roleColor,
                fontFamily: 'monospace',
                letterSpacing: '1px',
                textTransform: 'uppercase'
              }}>
                {bean.scope}
              </div>
              <div style={{
                fontSize: '8px',
                color: '#475569',
                fontFamily: 'monospace',
                marginTop: '2px'
              }}>
                {bean.fullClass.split('.').slice(0, -1).join('.')}
              </div>
            </div>
          )
        },
        style: {
          background: bgColor,
          border: `1.5px solid ${borderColor}`,
          borderRadius: '8px',
          padding: '10px 14px',
          width: 200,
          cursor: 'pointer',
          transition: 'opacity 0.2s',
          opacity,
        }
      }
    })
  }, [visibleBeans, focusedBean, searchQuery, beanEdges])

  const buildEdges = useCallback((): Edge[] => {
    const connected = focusedBean ? getConnected(focusedBean, beanEdges) : null

    return beanEdges
      .filter(e => visibleIds.has(e.from) && visibleIds.has(e.to))
      .map((e, i) => {
        let opacity = 1
        let stroke = '#1e2433'
        let animated = false

        if (focusedBean) {
          const isConnected = connected!.all.has(e.from) && connected!.all.has(e.to)
          opacity = isConnected ? 1 : 0.05
          if (isConnected) {
            stroke = e.from === focusedBean
              ? '#00e5a0'
              : e.to === focusedBean
              ? '#3b82f6'
              : '#334155'
            animated = true
          }
        }

        if (searchQuery) {
          const matches =
            e.from.toLowerCase().includes(searchQuery.toLowerCase()) ||
            e.to.toLowerCase().includes(searchQuery.toLowerCase())
          opacity = matches ? 1 : 0.05
        }

        return {
          id: `${e.from}-${e.to}-${i}`,
          source: e.from,
          target: e.to,
          animated,
          style: { stroke, strokeWidth: 1.5, opacity },
          markerEnd: {
            type: 'arrowclosed' as any,
            color: stroke,
          }
        }
      })
  }, [beanEdges, visibleIds, focusedBean, searchQuery])

  const [nodes, setNodes, onNodesChange] = useNodesState(buildNodes())
  const [edges, setEdges, onEdgesChange] = useEdgesState(buildEdges())

  useEffect(() => {
    setNodes(buildNodes())
    setEdges(buildEdges())
  }, [buildNodes, buildEdges])

  const handleNodeClick = useCallback((_: any, node: Node) => {
    if (focusedBean === node.id) {
      setFocusedBean(null)
      setDetail(null)
    } else {
      setFocusedBean(node.id)
      const bean = beans.find(b => b.id === node.id)
      const conn = getConnected(node.id, beanEdges)
      if (bean) {
        setDetail({
          id: bean.id,
          scope: bean.scope,
          pkg: bean.fullClass.split('.').slice(0, -1).join('.'),
          deps: conn.children.length,
          usedBy: conn.parents.length,
        })
      }
    }
  }, [focusedBean, beans, beanEdges])

  const handlePaneClick = useCallback(() => {
    setFocusedBean(null)
    setDetail(null)
  }, [])

  const reset = useCallback(() => {
    setFocusedBean(null)
    setSearchQuery('')
    setDetail(null)
    setHiddenScopes(new Set())
  }, [])

  return (
    <div className="bg-gray-900 border border-gray-700 rounded-xl col-span-2" style={{ height: '520px' }}>

      <div className="flex items-center gap-3 px-5 py-3 border-b border-gray-700 flex-wrap">
        <h2 className="text-gray-400 font-mono text-xs tracking-widest uppercase">
          Bean Graph
        </h2>

        <input
          type="text"
          placeholder="search beans..."
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          className="bg-gray-950 border border-gray-700 rounded-md px-3 py-1.5 font-mono text-xs text-gray-200 outline-none focus:border-gray-500 placeholder-gray-600 w-44"
        />

        <div className="flex gap-2 flex-wrap">
          {scopes.map(scope => {
            const color = getColor(scope)
            const isHidden = hiddenScopes.has(scope)
            return (
              <button
                key={scope}
                onClick={() => toggleScope(scope)}
                className="border rounded px-2.5 py-1 font-mono text-xs tracking-widest uppercase transition-all"
                style={{
                  borderColor: color.border,
                  color: isHidden ? '#334155' : color.text,
                  background: isHidden ? 'transparent' : color.bg,
                  opacity: isHidden ? 0.4 : 1,
                }}
              >
                {scope.replace('Scoped', '')}
              </button>
            )
          })}
        </div>

        <button
          onClick={reset}
          className="ml-auto border border-gray-700 rounded-md px-3 py-1.5 font-mono text-xs text-gray-500 hover:border-gray-500 hover:text-gray-300 transition-all"
        >
          reset
        </button>
      </div>

      <div style={{ height: 'calc(100% - 52px)', position: 'relative' }}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={handleNodeClick}
          onPaneClick={handlePaneClick}
          fitView
          fitViewOptions={{ padding: 0.3 }}
          proOptions={{ hideAttribution: true }}
          style={{ background: 'transparent' }}
        >
          <Background color="#1e2433" gap={20} size={1} />
          <Controls
            style={{
              background: '#13161e',
              border: '1px solid #1e2433',
              borderRadius: '8px',
            }}
          />
        </ReactFlow>

        {!focusedBean && !searchQuery && (
          <div className="absolute bottom-3 left-1/2 -translate-x-1/2 font-mono text-xs text-gray-600 pointer-events-none">
            click a bean to focus · drag to explore
          </div>
        )}

        {detail && (
          <div className="absolute top-3 right-3 bg-gray-950 border border-gray-700 rounded-lg p-3 w-48">
            <div
              className="font-mono text-sm font-medium mb-3"
              style={{ color: getColor(detail.scope).text }}
            >
              {detail.id}
            </div>
            <div className="flex flex-col gap-1.5">
              {[
                { label: 'scope',     value: detail.scope },
                { label: 'package',   value: detail.pkg || '—' },
                { label: 'depends on', value: `${detail.deps} bean${detail.deps !== 1 ? 's' : ''}` },
                { label: 'used by',   value: `${detail.usedBy} bean${detail.usedBy !== 1 ? 's' : ''}` },
              ].map(row => (
                <div key={row.label} className="flex justify-between font-mono text-xs">
                  <span className="text-gray-500">{row.label}</span>
                  <span className="text-gray-300">{row.value}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
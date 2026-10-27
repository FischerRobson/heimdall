import { useState, useEffect, type ReactNode } from 'react'
import { HeimdallSpan, HeimdallTrace } from '../../types/traces'
import { CopyButton } from '../../components/CopyButton'
import { downloadJson, timestampedName } from '../../utils/download'

const METHOD_COLORS: Record<string, string> = {
  GET:    '#00e5a0',
  POST:   '#3b82f6',
  PUT:    '#f59e0b',
  DELETE: '#ef4444',
  PATCH:  '#a78bfa',
}

const LAYER_COLORS: Record<string, string> = {
  resource:   '#00e5a0',
  service:    '#3b82f6',
  repository: '#f59e0b',
  unknown:    '#64748b',
}

function getMethodColor(method: string) {
  return METHOD_COLORS[method] ?? '#64748b'
}

function getLayerColor(layer: string) {
  return LAYER_COLORS[layer] ?? '#64748b'
}

function KVRow({ label, value, highlight, muted }: { label: string; value: string; highlight?: boolean; muted?: boolean }) {
  return (
    <div className="flex justify-between items-start gap-3 py-1.5 border-b border-gray-800 last:border-0">
      <span className="font-mono text-xs text-gray-500 shrink-0">{label}</span>
      <span className={`font-mono text-xs text-right break-all ${highlight ? 'text-green-400' : muted ? 'text-gray-600 italic' : 'text-gray-300'}`}>
        {value}
      </span>
    </div>
  )
}

function SectionTitle({ title, count }: { title: string; count?: number }) {
  return (
    <div className="flex items-center justify-between mb-2">
      <span className="font-mono text-xs tracking-widest uppercase text-gray-600">{title}</span>
      {count !== undefined && (
        <span className="font-mono text-xs px-1.5 py-0.5 rounded bg-gray-800 text-gray-500">{count}</span>
      )}
    </div>
  )
}

function SpanRow({ span, depth, traceStart, totalDuration, isSelected, onSelect }: {
  span: HeimdallSpan
  depth: number
  traceStart: number
  totalDuration: number
  isSelected: boolean
  onSelect: () => void
}) {
  // span.startTime is an epoch timestamp — position it relative to the trace start
  const offset = Math.max(span.startTime - traceStart, 0)
  const width  = totalDuration > 0 ? Math.min(Math.max(span.duration / totalDuration * 100, 0.5), 100) : 0.5
  const left   = totalDuration > 0 ? Math.min(offset / totalDuration * 100, 100 - width) : 0
  const color = span.error ? '#ef4444' : getLayerColor(span.layer)
  const durColor = span.duration > totalDuration * 0.7 ? '#ef4444'
                 : span.duration > totalDuration * 0.4 ? '#f59e0b'
                 : '#64748b'

  return (
    <div
      className={`grid gap-2 items-center py-1 px-2 rounded cursor-pointer transition-colors
        ${isSelected ? 'bg-gray-800' : 'hover:bg-white/5'}`}
      style={{ gridTemplateColumns: '220px 1fr 64px' }}
      onClick={e => { e.stopPropagation(); onSelect() }}
    >
      <div className="flex items-center overflow-hidden">
        <div style={{ width: depth * 16, flexShrink: 0 }} />
        {depth > 0 && (
          <div className="w-3 h-3 shrink-0 relative mr-1">
            <div className="absolute left-0 top-1/2 w-3 h-px bg-gray-700" />
            <div className="absolute left-0 top-0 w-px h-1/2 bg-gray-700" />
          </div>
        )}
        <div className="font-mono text-xs truncate">
          <span className="text-gray-500">{span.className}.</span>
          <span className="text-gray-200">{span.methodName}()</span>
          {span.error && <span className="text-red-400 ml-1.5">● err</span>}
        </div>
      </div>

      <div className="relative h-4">
        <div
          className="absolute h-3 top-0.5 rounded-sm"
          style={{
            left: `${left}%`,
            width: `${width}%`,
            background: color,
            opacity: isSelected ? 1 : 0.6,
            minWidth: 3,
          }}
        />
      </div>

      <div className="font-mono text-xs text-right" style={{ color: durColor }}>
        {span.duration}ms
      </div>
    </div>
  )
}

function TraceDetail({ trace, onClose }: { trace: HeimdallTrace; onClose: () => void }) {
  const [activeTab, setActiveTab] = useState<'waterfall' | 'request' | 'response'>('waterfall')
  const [selectedSpan, setSelectedSpan] = useState<string | null>(null)
  const mc = getMethodColor(trace.httpMethod)

  const roots    = trace.spans.filter(s => !s.parentId)
  const children = (id: string) => trace.spans.filter(s => s.parentId === id)
  const errSpans = trace.spans.filter(s => s.error).length
  const params   = Object.entries(trace.queryParams ?? {})
  const cookies  = Object.entries(trace.cookies ?? {})
  const reqHeaders  = Object.entries(trace.requestHeaders ?? {})
  const resHeaders  = Object.entries(trace.responseHeaders ?? {})

  function renderSpanTree(span: HeimdallSpan, depth: number): ReactNode {
    const isSel = selectedSpan === span.id
    return (
      <div key={span.id}>
        <SpanRow
          span={span}
          depth={depth}
          traceStart={trace.startTime}
          totalDuration={trace.duration}
          isSelected={isSel}
          onSelect={() => setSelectedSpan(isSel ? null : span.id)}
        />
        {isSel && (
          <div className="mx-2 mb-2 bg-gray-950 border border-gray-800 rounded-lg p-3">
            <div className="grid grid-cols-3 gap-3 mb-3">
              <div>
                <div className="font-mono text-xs text-gray-600 uppercase tracking-widest mb-1">Layer</div>
                <div className="font-mono text-xs" style={{ color: getLayerColor(span.layer) }}>{span.layer}</div>
              </div>
              <div>
                <div className="font-mono text-xs text-gray-600 uppercase tracking-widest mb-1">Duration</div>
                <div className="font-mono text-xs text-gray-200">{span.duration}ms</div>
              </div>
              <div>
                <div className="font-mono text-xs text-gray-600 uppercase tracking-widest mb-1">Start</div>
                <div className="font-mono text-xs text-gray-200">+{span.startTime - trace.startTime}ms</div>
              </div>
            </div>
            {span.error && (
              <div className="bg-red-950 border border-red-900 rounded p-2 mb-2 font-mono text-xs text-red-400 flex items-start justify-between gap-2">
                <span className="break-all">{span.error}</span>
                <CopyButton text={`${span.className}.${span.methodName}(): ${span.error}`} />
              </div>
            )}
            {span.tags && Object.keys(span.tags).length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {Object.entries(span.tags).map(([k, v]) => (
                  <div key={k} className="font-mono text-xs bg-gray-900 border border-gray-800 rounded px-2 py-0.5 text-gray-500">
                    {k}: <span className="text-gray-300">{v}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
        {children(span.id).map(child => renderSpanTree(child, depth + 1))}
      </div>
    )
  }

  const tLabels = [0, 25, 50, 75, 100].map(p => (
    <span key={p} className="font-mono text-gray-700" style={{ fontSize: '8px' }}>
      {Math.round(trace.duration * p / 100)}ms
    </span>
  ))

  return (
    <div className="border-t border-gray-800 bg-gray-950">
      <div className="grid" style={{ gridTemplateColumns: '1fr 280px' }}>

        {/* left — tabs + content */}
        <div className="border-r border-gray-800">
          <div className="flex items-center gap-3 px-4 py-2.5 border-b border-gray-800">
            <div className="font-mono text-xs font-medium" style={{ color: mc }}>{trace.httpMethod}</div>
            <div className="font-mono text-xs text-gray-200 flex-1">{trace.path}</div>
            <div className={`font-mono text-xs px-2 py-0.5 rounded ${trace.status < 400 ? 'bg-green-950 text-green-400' : 'bg-red-950 text-red-400'}`}>
              {trace.status}
            </div>
            <div className="font-mono text-xs text-gray-500">{trace.duration}ms</div>
            <button
              onClick={() => downloadJson(`${timestampedName('heimdall-trace', new Date(trace.timestamp))}.json`, trace)}
              title="export this trace as JSON"
              className="font-mono text-xs text-gray-500 border border-gray-700 hover:border-gray-500 hover:text-gray-300 hover:bg-white/5 rounded px-2 py-0.5 ml-2 transition-colors"
            >
              json
            </button>
            <button
              onClick={onClose}
              className="font-mono text-xs text-gray-600 hover:text-gray-300 hover:bg-white/5 rounded px-1.5 py-0.5 ml-2 transition-colors"
            >
              ✕
            </button>
          </div>

          <div className="flex border-b border-gray-800">
            {(['waterfall', 'request', 'response'] as const).map(tab => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-4 py-2 font-mono text-xs tracking-widest uppercase transition-colors border-b-2 ${
                  activeTab === tab
                    ? 'text-green-400 border-green-400'
                    : 'text-gray-600 border-transparent hover:text-gray-400 hover:bg-white/5'
                }`}
              >
                {tab}
              </button>
            ))}
          </div>

          {activeTab === 'waterfall' && (
            <div className="p-3">
              <div
                className="grid gap-2 px-2 pb-2 mb-1 border-b border-gray-800"
                style={{ gridTemplateColumns: '220px 1fr 64px' }}
              >
                {['Span', 'Timeline', 'Duration'].map(h => (
                  <div key={h} className="font-mono text-gray-700 uppercase tracking-widest" style={{ fontSize: '9px', textAlign: h === 'Duration' ? 'right' : 'left' }}>{h}</div>
                ))}
              </div>
              {trace.spans.length === 0
                ? <div className="font-mono text-xs text-gray-700 text-center py-6">no spans recorded</div>
                : roots.map(r => renderSpanTree(r, 0))
              }
              <div className="flex justify-between px-2 mt-2 pt-2 border-t border-gray-800">{tLabels}</div>
              <div className="flex gap-4 px-2 mt-3">
                {Object.entries(LAYER_COLORS).map(([layer, color]) => (
                  <div key={layer} className="flex items-center gap-1.5">
                    <div className="w-2 h-2 rounded-sm" style={{ background: color, opacity: 0.8 }} />
                    <span className="font-mono text-gray-600" style={{ fontSize: '9px' }}>{layer}</span>
                  </div>
                ))}
                <div className="flex items-center gap-1.5">
                  <div className="w-2 h-2 rounded-sm bg-red-500" style={{ opacity: 0.8 }} />
                  <span className="font-mono text-gray-600" style={{ fontSize: '9px' }}>error</span>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'request' && (
            <div className="p-4 flex flex-col gap-4">
              <div>
                <SectionTitle title="Headers" count={reqHeaders.length} />
                {reqHeaders.map(([k, v]) => (
                  <KVRow key={k} label={k} value={k === 'authorization' ? v.substring(0, 20) + '...' : v} muted={k === 'authorization'} />
                ))}
              </div>
              {trace.requestBody && (
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <SectionTitle title="Body" />
                    <CopyButton text={trace.requestBody} />
                  </div>
                  <pre className="bg-gray-900 border border-gray-800 rounded p-3 font-mono text-xs text-gray-300 whitespace-pre-wrap break-all">
                    {trace.requestBody}
                  </pre>
                </div>
              )}
              {!trace.requestBody && (
                <div className="font-mono text-xs text-gray-700">no request body</div>
              )}
            </div>
          )}

          {activeTab === 'response' && (
            <div className="p-4 flex flex-col gap-4">
              <div>
                <SectionTitle title="Headers" count={resHeaders.length} />
                {resHeaders.length > 0
                  ? resHeaders.map(([k, v]) => <KVRow key={k} label={k} value={v} />)
                  : <div className="font-mono text-xs text-gray-700">no response headers captured</div>
                }
              </div>
              {trace.responseBody && (
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <SectionTitle title="Body" />
                    <CopyButton text={trace.responseBody} />
                  </div>
                  <pre className="bg-gray-900 border border-gray-800 rounded p-3 font-mono text-xs text-gray-300 whitespace-pre-wrap break-all">
                    {trace.responseBody}
                  </pre>
                </div>
              )}
              {!trace.responseBody && (
                <div className="font-mono text-xs text-gray-700">no response body</div>
              )}
            </div>
          )}
        </div>

        {/* right sidebar */}
        <div className="p-4 flex flex-col gap-5">
          <div>
            <SectionTitle title="Summary" />
            <KVRow label="method"   value={trace.httpMethod} highlight />
            <KVRow label="status"   value={String(trace.status)} highlight={trace.status < 400} />
            <KVRow label="duration" value={`${trace.duration}ms`} />
            <KVRow label="spans"    value={String(trace.spans.length)} />
            <KVRow label="errors"   value={String(errSpans)} />
            <KVRow label="time"     value={new Date(trace.timestamp).toLocaleTimeString()} />
          </div>

          <div>
            <SectionTitle title="Params" count={params.length} />
            {params.length > 0
              ? params.map(([k, v]) => <KVRow key={k} label={k} value={v} highlight />)
              : <div className="font-mono text-xs text-gray-700">none</div>
            }
          </div>

          <div>
            <SectionTitle title="Auth" />
            {trace.requestHeaders?.['authorization'] ? (
              <>
                <KVRow label="type"  value="Bearer JWT" highlight />
                <KVRow label="token" value={trace.requestHeaders['authorization'].substring(0, 20) + '...'} muted />
              </>
            ) : trace.requestHeaders?.['x-api-key'] ? (
              <>
                <KVRow label="type" value="API Key" highlight />
                <KVRow label="key"  value={trace.requestHeaders['x-api-key'].substring(0, 14) + '...'} muted />
              </>
            ) : (
              <div className="font-mono text-xs text-gray-700">no auth present</div>
            )}
          </div>

          <div>
            <SectionTitle title="Cookies" count={cookies.length} />
            {cookies.length > 0
              ? cookies.map(([k, v]) => (
                  <KVRow
                    key={k}
                    label={k}
                    value={k === 'session' || k === 'token' ? v.substring(0, 8) + '...' : v}
                    muted={k === 'session' || k === 'token'}
                  />
                ))
              : <div className="font-mono text-xs text-gray-700">none</div>
            }
          </div>
        </div>
      </div>
    </div>
  )
}

export function TracePage() {
  const [traces, setTraces]           = useState<HeimdallTrace[]>([])
  const [openTrace, setOpenTrace]     = useState<string | null>(null)
  const [activeFilter, setActiveFilter] = useState('all')
  const [search, setSearch]           = useState('')

  useEffect(() => {
    const fetch_traces = () => {
      fetch('/heimdall/api/traces')
        .then(res => res.json())
        .then(data => setTraces(Array.isArray(data) ? data : []))
        .catch(() => {})
    }
    fetch_traces()
    const interval = setInterval(fetch_traces, 3000)
    return () => clearInterval(interval)
  }, [])

  const filtered = traces.filter(t => {
    if (activeFilter === 'GET'    && t.httpMethod !== 'GET')    return false
    if (activeFilter === 'POST'   && t.httpMethod !== 'POST')   return false
    if (activeFilter === 'errors' && t.status < 400)            return false
    if (search && !t.path.toLowerCase().includes(search.toLowerCase())
               && !t.httpMethod.toLowerCase().includes(search.toLowerCase())) return false
    return true
  })

  const filters = ['all', 'GET', 'POST', 'errors']

  return (
    <div className="p-6 max-w-full">
      <div className="flex items-center justify-between mb-5">
        <h2 className="font-mono text-xs tracking-widest uppercase text-gray-500">Trace Viewer</h2>
        <div className="flex items-center gap-3">
          <button
            disabled={filtered.length === 0}
            onClick={() => downloadJson(`${timestampedName('heimdall-traces')}.json`, filtered)}
            title="export the traces currently shown (respects search and filters)"
            className="font-mono text-xs tracking-widest uppercase px-3 py-1.5 rounded border border-gray-700 text-gray-400 hover:border-gray-500 hover:text-gray-200 hover:bg-white/5 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            export json
          </button>
          <div className="flex items-center gap-2 font-mono text-xs text-green-400">
            <div className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" />
            {traces.length} traces
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <input
          type="text"
          placeholder="search path or method..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="bg-gray-900 border border-gray-700 rounded-md px-3 py-1.5 font-mono text-xs text-gray-200 outline-none focus:border-gray-500 placeholder-gray-700 w-52"
        />
        {filters.map(f => (
          <button
            key={f}
            onClick={() => setActiveFilter(f)}
            className={`font-mono text-xs tracking-widest uppercase px-3 py-1.5 rounded border transition-all ${
              activeFilter === f
                ? 'border-green-400 text-green-400 bg-green-400/5'
                : 'border-gray-700 text-gray-500 hover:border-gray-500 hover:text-gray-300 hover:bg-white/5'
            }`}
          >
            {f}
          </button>
        ))}
      </div>

      <div className="flex flex-col gap-1.5">
        {filtered.length === 0 && (
          <div className="flex items-center justify-center h-32 font-mono text-xs text-gray-700 tracking-widest uppercase">
            no traces yet
          </div>
        )}
        {filtered.map(trace => {
          const isOpen = openTrace === trace.id
          const mc     = getMethodColor(trace.httpMethod)

          return (
            <div
              key={trace.id}
              className="border rounded-lg overflow-hidden transition-colors"
              style={{
                borderColor: isOpen ? mc + '60' : '#1e2433',
                background: isOpen ? '#13161e' : '#0d0f14'
              }}
            >
              <div
                className="flex items-center gap-3 px-4 py-2.5 cursor-pointer hover:bg-white/5 transition-colors"
                onClick={() => setOpenTrace(isOpen ? null : trace.id)}
              >
                <div className="font-mono text-xs font-medium w-12 flex-shrink-0" style={{ color: mc }}>
                  {trace.httpMethod}
                </div>
                <div className="font-mono text-xs text-gray-200 flex-1">{trace.path}</div>
                <div className={`font-mono text-xs px-2 py-0.5 rounded flex-shrink-0 ${
                  trace.status < 400 ? 'bg-green-950 text-green-400' : 'bg-red-950 text-red-400'
                }`}>
                  {trace.status}
                </div>
                <div className="font-mono text-xs text-gray-500 shrink-0 w-14 text-right">
                  {trace.duration}ms
                </div>
                <div className="font-mono text-gray-700 shrink-0" style={{ fontSize: '9px' }}>
                  {trace.spans.length} spans
                </div>
                <div className="font-mono text-gray-600 shrink-0" style={{ fontSize: '10px' }}>
                  {new Date(trace.timestamp).toLocaleTimeString()}
                </div>
                <div
                  className="font-mono text-gray-600 shrink-0 transition-transform"
                  style={{ fontSize: '10px', transform: isOpen ? 'rotate(180deg)' : 'none' }}
                >
                  ▼
                </div>
              </div>

              {isOpen && (
                <TraceDetail
                  trace={trace}
                  onClose={() => setOpenTrace(null)}
                />
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
import { useState, useEffect } from 'react'

interface ConnectionInfo {
  name: string
  type: string
  technology: string
  status: string
  host: string | null
  stats: Record<string, unknown>
  checkedAt: string
}

const TYPE_ORDER = ['database', 'messaging', 'cache', 'unknown']

const TYPE_LABELS: Record<string, string> = {
  database:  'Database',
  messaging: 'Messaging',
  cache:     'Cache',
  unknown:   'Other',
}

const TECH_ICONS: Record<string, { icon: string; bg: string }> = {
  'HikariCP':        { icon: '🗄️', bg: 'rgba(59,130,246,0.1)'  },
  'Kafka Consumer':  { icon: '⚡', bg: 'rgba(245,158,11,0.1)'  },
  'Kafka Producer':  { icon: '📤', bg: 'rgba(245,158,11,0.1)'  },
  'RabbitMQ':        { icon: '📨', bg: 'rgba(239,68,68,0.1)'   },
  'Redis':           { icon: '🔴', bg: 'rgba(167,139,250,0.1)' },
  'Redisson':        { icon: '🔴', bg: 'rgba(167,139,250,0.1)' },
  'Lettuce':         { icon: '🔴', bg: 'rgba(167,139,250,0.1)' },
  'MongoDB':         { icon: '🍃', bg: 'rgba(0,229,160,0.1)'   },
}

const DEFAULT_TECH = { icon: '🔌', bg: 'rgba(100,116,139,0.1)' }

const STATUS_STYLES: Record<string, { pill: string; dot: string }> = {
  UP:       { pill: 'bg-green-950 text-green-400 border border-green-900',  dot: 'bg-green-400'  },
  DEGRADED: { pill: 'bg-amber-950 text-amber-400 border border-amber-900',  dot: 'bg-amber-400'  },
  DOWN:     { pill: 'bg-red-950   text-red-400   border border-red-900',    dot: 'bg-red-400'    },
  UNKNOWN:  { pill: 'bg-gray-900  text-gray-500  border border-gray-800',   dot: 'bg-gray-500'   },
}

function getStatusStyle(status: string) {
  return STATUS_STYLES[status.toUpperCase()] ?? STATUS_STYLES['UNKNOWN']
}

function getTechInfo(technology: string) {
  return TECH_ICONS[technology] ?? DEFAULT_TECH
}

function formatStatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'number') {
    if (value > 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
    if (value > 1_000)     return `${(value / 1_000).toFixed(1)}k`
    return String(Math.round(value * 100) / 100)
  }
  return String(value)
}

function getQuickStats(conn: ConnectionInfo): { label: string; value: string; warn?: boolean }[] {
  const s = conn.stats

  switch (conn.technology) {
    case 'HikariCP':
      return [
        { label: 'Active',   value: formatStatValue(s.activeConnections) },
        { label: 'Idle',     value: formatStatValue(s.idleConnections) },
        { label: 'Pending',  value: formatStatValue(s.pendingThreads), warn: Number(s.pendingThreads) > 0 },
        { label: 'Max',      value: formatStatValue(s.maxConnections) },
      ]
    case 'Kafka Consumer':
      return [
        { label: 'Lag',       value: formatStatValue(s.recordsLagMax), warn: Number(s.recordsLagMax) > 1000 },
        { label: 'Partitions',value: formatStatValue(s.assignedPartitions) },
        { label: 'Fetch/s',   value: formatStatValue(s.fetchRate) },
        { label: 'Commit/s',  value: formatStatValue(s.commitRate) },
      ]
    case 'Kafka Producer':
      return [
        { label: 'Send/s',   value: formatStatValue(s.recordSendRate) },
        { label: 'Errors/s', value: formatStatValue(s.recordErrorRate), warn: Number(s.recordErrorRate) > 0 },
        { label: 'Lat avg',  value: `${formatStatValue(s.latencyAvgMs)}ms` },
        { label: 'Lat max',  value: `${formatStatValue(s.latencyMaxMs)}ms` },
      ]
    case 'RabbitMQ':
      return [
        { label: 'Connections', value: formatStatValue(s.connections) },
        { label: 'Channels',    value: formatStatValue(s.channels) },
        { label: 'Published',   value: formatStatValue(s.publishedMessages) },
        { label: 'Failed',      value: formatStatValue(s.failedToPublish), warn: Number(s.failedToPublish) > 0 },
      ]
    case 'Redis':
    case 'Redisson':
    case 'Lettuce':
      return [
        { label: 'Used',  value: formatStatValue(s.usedConnections ?? s.activeCount) },
        { label: 'Free',  value: formatStatValue(s.freeConnections ?? s.idleCount) },
        { label: 'Max',   value: formatStatValue(s.maxConnections) },
        { label: 'Host',  value: String(s.host ?? conn.host ?? '—') },
      ]
    default:
      const entries = Object.entries(s).slice(0, 4)
      return entries.map(([k, v]) => ({ label: k, value: formatStatValue(v) }))
  }
}

function getUsageBar(conn: ConnectionInfo): { label: string; left: string; value: number; color: string } | null {
  const s = conn.stats

  if (conn.technology === 'HikariCP') {
    return {
      label: 'Pool usage',
      left:  `${formatStatValue(s.activeConnections)} / ${formatStatValue(s.maxConnections)} active`,
      value: Number(s.usagePercent ?? 0),
      color: '#3b82f6',
    }
  }

  if (conn.technology === 'Kafka Consumer') {
    const lag = Number(s.recordsLagMax ?? 0)
    const pct = Math.min(lag / 2000 * 100, 100)
    return {
      label: 'Consumer lag',
      left:  `${formatStatValue(s.recordsLagMax)} messages`,
      value: pct,
      color: pct > 50 ? '#ef4444' : pct > 25 ? '#f59e0b' : '#00e5a0',
    }
  }

  return null
}

function StatKVList({ stats }: { stats: Record<string, unknown> }) {
  return (
    <div className="mt-3">
      <div className="font-mono text-xs tracking-widest uppercase text-gray-700 mb-2">All stats</div>
      {Object.entries(stats).map(([k, v]) => (
        <div key={k} className="flex justify-between py-1 border-b border-gray-800 last:border-0">
          <span className="font-mono text-xs text-gray-500">{k}</span>
          <span className="font-mono text-xs text-gray-300">{formatStatValue(v)}</span>
        </div>
      ))}
    </div>
  )
}

function ConnectionCard({ conn }: { conn: ConnectionInfo }) {
  const [open, setOpen] = useState(false)
  const tech     = getTechInfo(conn.technology)
  const status   = getStatusStyle(conn.status)
  const quickStats = getQuickStats(conn)
  const usageBar = getUsageBar(conn)

  return (
    <div
      className="border rounded-xl overflow-hidden transition-colors"
      style={{ borderColor: open ? '#334155' : '#1e2433', background: '#13161e' }}
    >
      {/* top row */}
      <div
        className="flex items-center gap-3 px-4 py-3 cursor-pointer hover:bg-gray-900 transition-colors"
        onClick={() => setOpen(!open)}
      >
        <div
          className="w-9 h-9 rounded-lg flex items-center justify-center text-lg flex-shrink-0"
          style={{ background: tech.bg }}
        >
          {tech.icon}
        </div>

        <div className="flex-1 min-w-0">
          <div className="font-mono text-sm font-medium text-gray-100">{conn.name}</div>
          <div className="font-mono text-xs text-gray-500 mt-0.5">{conn.technology}</div>
          {conn.host && (
            <div className="font-mono text-xs text-gray-700 mt-0.5">{conn.host}</div>
          )}
        </div>

        <div className="flex flex-col items-end gap-1.5 flex-shrink-0">
          <div className={`flex items-center gap-1.5 font-mono text-xs px-2 py-0.5 rounded-full ${status.pill}`}>
            <div className={`w-1.5 h-1.5 rounded-full ${status.dot}`} />
            {conn.status}
          </div>
          <div className="font-mono text-xs text-gray-600 uppercase tracking-widest" style={{ fontSize: '9px' }}>
            {conn.type}
          </div>
        </div>

        <div
          className="font-mono text-gray-600 flex-shrink-0 transition-transform ml-1"
          style={{ fontSize: '10px', transform: open ? 'rotate(180deg)' : 'none' }}
        >
          ▼
        </div>
      </div>

      {/* usage bar */}
      {usageBar && (
        <div className="px-4 pb-2">
          <div className="flex justify-between font-mono mb-1" style={{ fontSize: '9px', color: '#64748b' }}>
            <span>{usageBar.label}</span>
            <span>{usageBar.left}</span>
          </div>
          <div className="w-full bg-gray-950 rounded-full h-1">
            <div
              className="h-1 rounded-full transition-all duration-500"
              style={{ width: `${Math.min(usageBar.value, 100)}%`, background: usageBar.color }}
            />
          </div>
        </div>
      )}

      {/* quick stats */}
      <div className="grid border-t border-gray-800" style={{ gridTemplateColumns: `repeat(${quickStats.length}, 1fr)` }}>
        {quickStats.map((stat, i) => (
          <div
            key={i}
            className="px-3 py-2 border-r border-gray-800 last:border-0"
          >
            <div className="font-mono uppercase tracking-widest text-gray-600 mb-1" style={{ fontSize: '8px' }}>
              {stat.label}
            </div>
            <div
              className="font-mono text-xs"
              style={{ color: stat.warn ? '#f59e0b' : '#94a3b8' }}
            >
              {stat.value}
            </div>
          </div>
        ))}
      </div>

      {/* expanded detail */}
      {open && (
        <div className="border-t border-gray-800 px-4 py-4">
          <div className="grid grid-cols-3 gap-3 mb-3">
            {quickStats.map((stat, i) => (
              <div key={i} className="bg-gray-950 rounded-lg p-3">
                <div className="font-mono uppercase tracking-widest text-gray-600 mb-1" style={{ fontSize: '8px' }}>
                  {stat.label}
                </div>
                <div
                  className="font-mono text-sm"
                  style={{ color: stat.warn ? '#f59e0b' : '#e2e8f0' }}
                >
                  {stat.value}
                </div>
              </div>
            ))}
            <div className="bg-gray-950 rounded-lg p-3">
              <div className="font-mono uppercase tracking-widest text-gray-600 mb-1" style={{ fontSize: '8px' }}>
                Checked at
              </div>
              <div className="font-mono text-xs text-gray-400">
                {new Date(conn.checkedAt).toLocaleTimeString()}
              </div>
            </div>
          </div>
          <StatKVList stats={conn.stats} />
        </div>
      )}
    </div>
  )
}

function TypeSection({ type, connections }: { type: string; connections: ConnectionInfo[] }) {
  if (connections.length === 0) return null
  return (
    <div className="mb-6">
      <div className="flex items-center gap-3 mb-3">
        <span className="font-mono text-xs tracking-widest uppercase text-gray-500">
          {TYPE_LABELS[type] ?? type}
        </span>
        <div className="flex-1 h-px bg-gray-800" />
        <span className="font-mono text-gray-700" style={{ fontSize: '9px' }}>
          {connections.length} connection{connections.length !== 1 ? 's' : ''}
        </span>
      </div>
      <div className="grid grid-cols-2 gap-3">
        {connections.map(conn => (
          <ConnectionCard key={`${conn.name}-${conn.technology}`} conn={conn} />
        ))}
      </div>
    </div>
  )
}

export function ConnectionsPage() {
  const [connections, setConnections] = useState<ConnectionInfo[]>([])
  const [loading, setLoading]         = useState(true)

  useEffect(() => {
    const fetchConnections = () => {
      fetch('/heimdall/api/connections')
        .then(res => res.json())
        .then(data => {
          setConnections(Array.isArray(data) ? data : [])
          setLoading(false)
        })
        .catch(() => setLoading(false))
    }

    fetchConnections()
    const interval = setInterval(fetchConnections, 5000)
    return () => clearInterval(interval)
  }, [])

  const total    = connections.length
  const healthy  = connections.filter(c => c.status === 'UP').length
  const degraded = connections.filter(c => c.status === 'DEGRADED').length
  const down     = connections.filter(c => c.status === 'DOWN').length

  const grouped = TYPE_ORDER.reduce((acc, type) => {
    acc[type] = connections.filter(c => (c.type ?? 'unknown') === type)
    return acc
  }, {} as Record<string, ConnectionInfo[]>)

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <p className="font-mono text-xs text-gray-700 tracking-widest uppercase animate-pulse">
        detecting connections...
      </p>
    </div>
  )

  if (connections.length === 0) return (
    <div className="flex items-center justify-center h-64">
      <p className="font-mono text-xs text-gray-700 tracking-widest uppercase">
        no connections detected
      </p>
    </div>
  )

  return (
    <div className="p-6 max-w-full">

      <div className="flex items-center justify-between mb-5">
        <h2 className="font-mono text-xs tracking-widest uppercase text-gray-500">Connections</h2>
        <div className="flex items-center gap-2 font-mono text-xs text-green-400">
          <div className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" />
          {total} detected
        </div>
      </div>

      {/* summary */}
      <div className="grid grid-cols-4 gap-3 mb-6">
        {[
          { label: 'Total',    value: total,    color: '#3b82f6', sub: 'connections' },
          { label: 'Healthy',  value: healthy,  color: '#00e5a0', sub: 'status UP' },
          { label: 'Degraded', value: degraded, color: '#f59e0b', sub: 'needs attention' },
          { label: 'Down',     value: down,     color: '#ef4444', sub: 'unreachable' },
        ].map(card => (
          <div key={card.label} className="bg-gray-900 border border-gray-800 rounded-xl p-4">
            <div className="font-mono uppercase tracking-widest text-gray-600 mb-2" style={{ fontSize: '9px' }}>
              {card.label}
            </div>
            <div className="font-mono text-2xl font-medium" style={{ color: card.color }}>
              {card.value}
            </div>
            <div className="font-mono text-gray-700 mt-1" style={{ fontSize: '9px' }}>
              {card.sub}
            </div>
          </div>
        ))}
      </div>

      {/* grouped by type */}
      {TYPE_ORDER.map(type => (
        <TypeSection
          key={type}
          type={type}
          connections={grouped[type] ?? []}
        />
      ))}

    </div>
  )
}
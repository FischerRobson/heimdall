import { useState, useEffect } from 'react'

interface DockerStats {
  cpuPercent: number
  memoryUsed: number
  memoryLimit: number
  memoryPercent: number
  networkIn: number
  networkOut: number
  blockRead: number
  blockWrite: number
}

interface DockerContainer {
  shortId: string
  name: string
  image: string
  status: string
  state: string
  role: string
  cluster: string
  stats: DockerStats
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatMemory(bytes: number): string {
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(0)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

const ROLE_STYLES: Record<string, { border: string; badge: string; text: string }> = {
  server:       { border: '#00e5a0', badge: 'rgba(0,229,160,0.08)',   text: '#00e5a0' },
  agent:        { border: '#3b82f6', badge: 'rgba(59,130,246,0.08)',  text: '#3b82f6' },
  loadbalancer: { border: '#f59e0b', badge: 'rgba(245,158,11,0.08)',  text: '#f59e0b' },
}

const DEFAULT_ROLE_STYLE = { border: '#475569', badge: 'rgba(71,85,105,0.08)', text: '#475569' }

function getRoleStyle(role: string) {
  return ROLE_STYLES[role.toLowerCase()] ?? DEFAULT_ROLE_STYLE
}

function ContainerCard({ container }: { container: DockerContainer }) {
  const role  = container.role?.toLowerCase() ?? 'unknown'
  const style = getRoleStyle(role)

  return (
    <div
      className="bg-gray-900 border border-gray-700 rounded-xl p-4 relative overflow-hidden"
      style={{ borderTop: `2px solid ${style.border}` }}
    >
      {/* header */}
      <div className="flex items-start justify-between mb-3">
        <div>
          <div className="font-mono text-sm font-medium text-gray-100">
            {container.name}
          </div>
          <div className="font-mono text-xs text-gray-600 mt-0.5 truncate max-w-48">
            {container.image}
          </div>
          <div className="font-mono text-xs text-gray-700 mt-0.5">
            {container.shortId}
          </div>
        </div>
        <span
          className="font-mono text-xs px-2 py-1 rounded tracking-widest uppercase border"
          style={{
            background: style.badge,
            color: style.text,
            borderColor: style.border + '40',
          }}
        >
          {role}
        </span>
      </div>

      {/* stat cards */}
      <div className="grid grid-cols-4 gap-2 mb-3">
        {[
          { label: 'CPU',     value: `${container.stats.cpuPercent}%`,          color: '#3b82f6' },
          { label: 'Memory',  value: `${container.stats.memoryPercent}%`,        color: '#00e5a0' },
          { label: 'Net In',  value: formatBytes(container.stats.networkIn),     color: undefined },
          { label: 'Net Out', value: formatBytes(container.stats.networkOut),    color: undefined },
        ].map(stat => (
          <div key={stat.label} className="bg-gray-950 rounded-md p-2">
            <div className="font-mono text-xs text-gray-500 uppercase tracking-wide mb-1" style={{ fontSize: '8px' }}>
              {stat.label}
            </div>
            <div className="font-mono text-xs" style={{ color: stat.color ?? '#e2e8f0' }}>
              {stat.value}
            </div>
          </div>
        ))}
      </div>

      {/* bars */}
      <div className="flex flex-col gap-2 mb-3">
        <div>
          <div className="flex justify-between font-mono mb-1" style={{ fontSize: '9px', color: '#64748b' }}>
            <span>CPU</span>
            <span>{container.stats.cpuPercent}%</span>
          </div>
          <div className="w-full bg-gray-950 rounded-full h-1">
            <div
              className="h-1 rounded-full transition-all duration-500"
              style={{
                width: `${Math.min(container.stats.cpuPercent, 100)}%`,
                background: '#3b82f6'
              }}
            />
          </div>
        </div>
        <div>
          <div className="flex justify-between font-mono mb-1" style={{ fontSize: '9px', color: '#64748b' }}>
            <span>Memory</span>
            <span>{formatMemory(container.stats.memoryUsed)} / {formatMemory(container.stats.memoryLimit)}</span>
          </div>
          <div className="w-full bg-gray-950 rounded-full h-1">
            <div
              className="h-1 rounded-full transition-all duration-500"
              style={{
                width: `${Math.min(container.stats.memoryPercent, 100)}%`,
                background: '#00e5a0'
              }}
            />
          </div>
        </div>
      </div>

      {/* footer */}
      <div
        className="flex items-center justify-between pt-2"
        style={{ borderTop: '1px solid #1e2433' }}
      >
        <div className="flex gap-3">
          <span className="font-mono" style={{ fontSize: '9px', color: '#64748b' }}>
            disk read <span style={{ color: '#94a3b8' }}>{formatBytes(container.stats.blockRead)}</span>
          </span>
          <span className="font-mono" style={{ fontSize: '9px', color: '#64748b' }}>
            disk write <span style={{ color: '#94a3b8' }}>{formatBytes(container.stats.blockWrite)}</span>
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-1.5 h-1.5 rounded-full bg-green-400" />
          <span className="font-mono text-green-400" style={{ fontSize: '9px' }}>
            {container.status}
          </span>
        </div>
      </div>
    </div>
  )
}

function ClusterSection({ cluster, containers }: { cluster: string; containers: DockerContainer[] }) {
  return (
    <div className="mb-6">
      <div className="flex items-center gap-3 mb-3">
        <span className="font-mono text-xs tracking-widest uppercase text-blue-400">
          {cluster}
        </span>
        <div className="flex-1 h-px bg-gray-800" />
        <span className="font-mono text-gray-600" style={{ fontSize: '9px' }}>
          {containers.length} container{containers.length !== 1 ? 's' : ''}
        </span>
      </div>
      <div className="grid grid-cols-2 gap-3">
        {containers.map(c => (
          <ContainerCard key={c.shortId} container={c} />
        ))}
      </div>
    </div>
  )
}

export default function DockerDashboard() {
  const [containers, setContainers] = useState<DockerContainer[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchContainers = () => {
      fetch('/heimdall/api/docker/containers')
        .then(res => res.json())
        .then(data => {
          setContainers(data)
          setLoading(false)
        })
        .catch(() => setLoading(false))
    }

    fetchContainers()
    const interval = setInterval(fetchContainers, 5000)
    return () => clearInterval(interval)
  }, [])

  const clusters = [...new Set(containers.map(c => c.cluster ?? 'unknown'))]

  const totalCpu = containers.reduce((acc, c) => acc + c.stats.cpuPercent, 0)
  const totalMemory = containers.reduce((acc, c) => acc + c.stats.memoryUsed, 0)
  const totalMemoryLimit = containers[0]?.stats.memoryLimit ?? 0

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <p className="font-mono text-xs text-gray-600 tracking-widest uppercase animate-pulse">
        connecting to docker...
      </p>
    </div>
  )

  if (containers.length === 0) return (
    <div className="flex items-center justify-center h-64">
      <p className="font-mono text-xs text-gray-600 tracking-widest uppercase">
        no running containers detected
      </p>
    </div>
  )

  return (
    <div className="p-6 max-w-6xl mx-auto">

      {/* page header */}
      <div className="flex items-center justify-between mb-6">
        <h2 className="font-mono text-xs tracking-widest uppercase text-gray-400">
          Docker
        </h2>
        <div className="flex items-center gap-2 font-mono text-xs text-blue-400">
          <div
            className="w-1.5 h-1.5 rounded-full bg-blue-400"
            style={{ boxShadow: '0 0 6px #3b82f6', animation: 'pulse 2s ease-in-out infinite' }}
          />
          {containers.length} containers running
        </div>
      </div>

      {/* summary row */}
      <div className="grid grid-cols-4 gap-3 mb-6">
        {[
          {
            label: 'Containers',
            value: containers.length.toString(),
            sub: 'all running',
            color: '#3b82f6'
          },
          {
            label: 'Total CPU',
            value: `${Math.round(totalCpu * 10) / 10}%`,
            sub: 'across all containers',
            color: '#00e5a0'
          },
          {
            label: 'Total Memory',
            value: formatMemory(totalMemory),
            sub: `of ${formatMemory(totalMemoryLimit)} available`,
            color: '#a78bfa'
          },
          {
            label: 'Clusters',
            value: clusters.length.toString(),
            sub: clusters.join(', '),
            color: '#f59e0b'
          },
        ].map(card => (
          <div
            key={card.label}
            className="bg-gray-900 border border-gray-700 rounded-xl p-4"
          >
            <div className="font-mono uppercase tracking-widest text-gray-500 mb-2" style={{ fontSize: '9px' }}>
              {card.label}
            </div>
            <div className="font-mono text-2xl font-medium" style={{ color: card.color }}>
              {card.value}
            </div>
            <div className="font-mono text-gray-600 mt-1 truncate" style={{ fontSize: '9px' }}>
              {card.sub}
            </div>
          </div>
        ))}
      </div>

      {/* clusters */}
      {clusters.map(cluster => (
        <ClusterSection
          key={cluster}
          cluster={cluster}
          containers={containers.filter(c => (c.cluster ?? 'unknown') === cluster)}
        />
      ))}

    </div>
  )
}
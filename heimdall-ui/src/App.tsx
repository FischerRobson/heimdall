import { useState, useEffect } from 'react'
import { Metrics } from './types/metrics'
import { BeansPage } from './pages/beans/BeansPage'
import { JvmInfo } from './components/JvmInfo'
import { BifrostLoader } from './components/BifrostLoader'
import { DockerPage } from './pages/docker/DockerPage'
import { DashboardPage } from './pages/dashboard/DashboardPage'
import { TracePage } from './pages/trace/TracePage'
import { ConnectionsPage } from './pages/connections/ConnectionsPage'
import { ThreadDumpPage } from './pages/threads/ThreadDumpPage'

export function App() {
  const [metrics, setMetrics] = useState<Metrics | null>(null)
  const [beanGraph, setBeanGraph] = useState<{ nodes: any[], edges: any[] } | null>(null)
  const [page, setPage] = useState<'dashboard' | 'docker' | 'beans' | 'trace' | 'conns' | 'threads'>('dashboard')
  const [hoveredPage, setHoveredPage] = useState<string | null>(null)
  const [connected, setConnected] = useState(true)

  useEffect(() => {
    let stopped = false
    let beansLoaded = false

    // polled so the header's uptime stays current and "live" reflects whether the
    // backend is actually answering (it also retries if the first load failed)
    const tick = () => {
      fetch('/heimdall/api/metrics')
        .then(res => {
          if (!res.ok) throw new Error(`HTTP ${res.status}`)
          return res.json()
        })
        .then(data => {
          if (stopped) return
          setMetrics(data)
          setConnected(true)
        })
        .catch(() => { if (!stopped) setConnected(false) })

      // the bean graph doesn't change at runtime — load it once
      if (!beansLoaded) {
        fetch('/heimdall/api/beans/graph')
          .then(res => {
            if (!res.ok) throw new Error(`HTTP ${res.status}`)
            return res.json()
          })
          .then(data => {
            if (stopped) return
            beansLoaded = true
            setBeanGraph(data)
          })
          .catch(err => console.error(err))
      }
    }

    tick()
    const interval = setInterval(tick, 3000)
    return () => {
      stopped = true
      clearInterval(interval)
    }
  }, [])

  if (!metrics) return <BifrostLoader />

  return (
    <div className="min-h-screen bg-gray-950 p-6">
      <div className="max-w-5xl mx-auto">
        <h1 className="text-white font-mono text-2xl tracking-widest mb-8 flex items-center gap-3">
          HEIMDALL{' '}
          {connected
            ? <span className="text-green-400 text-sm">● live</span>
            : <span className="text-red-400 text-sm">● offline</span>}
          <JvmInfo data={metrics.jvmInfo} />
        </h1>
        <div className="flex gap-2 mb-2">
          {(['dashboard', 'docker', 'beans', 'trace', 'conns', 'threads'] as const).map(p => (
            <button
              key={p}
              onClick={() => setPage(p)}
              onMouseEnter={() => setHoveredPage(p)}
              onMouseLeave={() => setHoveredPage(null)}
              className="font-mono text-xs tracking-widest uppercase px-3 py-1.5 rounded border transition-all"
              style={{
                borderColor: page === p ? '#00e5a0' : hoveredPage === p ? '#334155' : '#1e2433',
                color: page === p ? '#00e5a0' : hoveredPage === p ? '#94a3b8' : '#64748b',
                background: page === p ? 'rgba(0,229,160,0.06)' : hoveredPage === p ? 'rgba(255,255,255,0.05)' : 'transparent'
              }}
            >
              {p}
            </button>
          ))}
        </div>
        {page === 'dashboard' && <DashboardPage />}
        {page === 'docker' && <DockerPage />}
        {page === 'beans' && beanGraph && <BeansPage data={beanGraph} />}
        {page === 'trace' && <TracePage />}
        {page === 'conns' && <ConnectionsPage />}
        {page === 'threads' && <ThreadDumpPage />}
      </div>
    </div>
  )
}
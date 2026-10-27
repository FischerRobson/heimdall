import { useState, useEffect, lazy, Suspense } from 'react'
import { Metrics } from './types/metrics'
import { JvmInfo } from './components/JvmInfo'
import { BifrostLoader } from './components/BifrostLoader'
import { DashboardPage } from './pages/dashboard/DashboardPage'

// the dashboard is the landing page and stays in the main bundle; the other pages are
// split into their own chunks (BeansPage pulls in @xyflow/react) and load on first visit
const BeansPage       = lazy(() => import('./pages/beans/BeansPage').then(m => ({ default: m.BeansPage })))
const DockerPage      = lazy(() => import('./pages/docker/DockerPage').then(m => ({ default: m.DockerPage })))
const TracePage       = lazy(() => import('./pages/trace/TracePage').then(m => ({ default: m.TracePage })))
const ConnectionsPage = lazy(() => import('./pages/connections/ConnectionsPage').then(m => ({ default: m.ConnectionsPage })))
const ThreadDumpPage  = lazy(() => import('./pages/threads/ThreadDumpPage').then(m => ({ default: m.ThreadDumpPage })))

function PageLoading() {
  return (
    <div className="flex items-center justify-center h-64">
      <p className="font-mono text-xs text-gray-600 tracking-widest uppercase animate-pulse">loading…</p>
    </div>
  )
}

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
        <Suspense fallback={<PageLoading />}>
          {page === 'docker' && <DockerPage />}
          {page === 'beans' && beanGraph && <BeansPage data={beanGraph} />}
          {page === 'trace' && <TracePage />}
          {page === 'conns' && <ConnectionsPage />}
          {page === 'threads' && <ThreadDumpPage />}
        </Suspense>
      </div>
    </div>
  )
}
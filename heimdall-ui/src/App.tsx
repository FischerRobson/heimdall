import { useState, useEffect } from 'react'
import { Metrics } from './types/metrics'
import { MemoryInfo } from './components/MemoryInfo'
import { CpuInfo } from './components/CpuInfo'
import { ThreadInfo } from './components/ThreadInfo'
import { GarbageCollectorInfo } from './components/GarbageCollectorInfo'
import { MetricsChart, SnapshotEntry } from './components/MetricsChart'
import { BeanGraph } from './components/BeanGraph'
import { JvmInfo } from './components/JvmInfo'
import { BifrostLoader } from './components/BifrostLoader'
import DockerDashboard from './components/DockerDashboard'

export function App() {
  const [metrics, setMetrics] = useState<Metrics | null>(null)
  const [history, setHistory] = useState<SnapshotEntry[]>([])
  const [beanGraph, setBeanGraph] = useState<{ nodes: any[], edges: any[] } | null>(null)
  const [page, setPage] = useState<'dashboard' | 'docker'>('dashboard')

  useEffect(() => {
    const fetch_metrics = () => {
      fetch('/heimdall/api/metrics')
        .then(res => res.json())
        .then(data => setMetrics(data))
        .catch(err => console.error(err))

      fetch('/heimdall/api/metrics/history')
        .then(res => res.json())
        .then(data => setHistory(data))
        .catch(err => console.error(err))

      fetch('/heimdall/api/beans/graph')
        .then(res => res.json())
        .then(data => setBeanGraph(data))
        .catch(err => console.error(err))
    }
    fetch_metrics()
    const interval = setInterval(fetch_metrics, 3000)
    return () => clearInterval(interval)
  }, [])

  if (!metrics) return <BifrostLoader />

  return (
    <div className="min-h-screen bg-gray-950 p-6">
      <div className="max-w-5xl mx-auto">
        <h1 className="text-white font-mono text-2xl tracking-widest mb-8 flex items-center gap-3">
          HEIMDALL <span className="text-green-400 text-sm">● live</span>
          <JvmInfo data={metrics.jvmInfo} />
        </h1>
        <div className="flex gap-2 mb-2">
          {(['dashboard', 'docker'] as const).map(p => (
            <button
              key={p}
              onClick={() => setPage(p)}
              className="font-mono text-xs tracking-widest uppercase px-3 py-1.5 rounded border transition-all"
              style={{
                borderColor: page === p ? '#00e5a0' : '#1e2433',
                color: page === p ? '#00e5a0' : '#64748b',
                background: page === p ? 'rgba(0,229,160,0.06)' : 'transparent'
              }}
            >
              {p}
            </button>
          ))}
        </div>

        {page === 'dashboard' && (
          <div className="grid grid-cols-2 gap-4">
            <MetricsChart history={history} />
            <MemoryInfo data={metrics.memoryInfo} />
            <CpuInfo data={metrics.cpuInfo} />
            <ThreadInfo data={metrics.threadInfo} />
            <GarbageCollectorInfo data={metrics.garbageCollectorInfo} />
            {beanGraph && <BeanGraph data={beanGraph} />}
          </div>
        )}

        {page === 'docker' && <DockerDashboard />}
      </div>
    </div>
  )
}
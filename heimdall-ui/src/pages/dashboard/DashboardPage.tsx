import { useEffect, useState } from "react";
import { CpuInfo } from "../../components/CpuInfo";
import { GarbageCollectorInfo } from "../../components/GarbageCollectorInfo";
import { MemoryInfo } from "../../components/MemoryInfo";
import { MetricsChart, SnapshotEntry } from "../../components/MetricsChart";
import { ThreadInfo } from "../../components/ThreadInfo";
import { Metrics } from "../../types/metrics";
import { BifrostLoader } from "../../components/BifrostLoader";

const HISTORY_LIMIT = 60
const RECONNECT_DELAY_MS = 2000

export function DashboardPage() {
  const [metrics, setMetrics] = useState<Metrics | null>(null)
  const [history, setHistory] = useState<SnapshotEntry[]>([])

  useEffect(() => {
    let socket: WebSocket | null = null
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null
    let stopped = false

    const connect = () => {
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
      socket = new WebSocket(`${protocol}://${window.location.host}/heimdall/ws/metrics`)

      socket.onmessage = (event) => {
        const data = JSON.parse(event.data)

        if (Array.isArray(data)) {
          setHistory(data)
          if (data.length > 0) setMetrics(data[data.length - 1].metrics)
        } else {
          setHistory(prev => [...prev.slice(-(HISTORY_LIMIT - 1)), data])
          setMetrics(data.metrics)
        }
      }

      socket.onclose = () => {
        if (!stopped) reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS)
      }

      socket.onerror = () => socket?.close()
    }

    connect()

    return () => {
      stopped = true
      if (reconnectTimer) clearTimeout(reconnectTimer)
      socket?.close()
    }
  }, [])

  if (!metrics) return <BifrostLoader />

  return (
    <div className="grid grid-cols-2 gap-4">
      <MetricsChart history={history} />
      <MemoryInfo data={metrics.memoryInfo} />
      <CpuInfo data={metrics.cpuInfo} />
      <ThreadInfo data={metrics.threadInfo} />
      <GarbageCollectorInfo data={metrics.garbageCollectorInfo} />
    </div>
  )
}
import { useEffect, useState } from "react";
import { CpuInfo } from "../../components/CpuInfo";
import { GarbageCollectorInfo } from "../../components/GarbageCollectorInfo";
import { MemoryInfo } from "../../components/MemoryInfo";
import { MetricsChart, SnapshotEntry } from "../../components/MetricsChart";
import { ThreadInfo } from "../../components/ThreadInfo";
import { Metrics } from "../../types/metrics";
import { BifrostLoader } from "../../components/BifrostLoader";

export function DashboardPage() {
  const [metrics, setMetrics] = useState<Metrics | null>(null)
  const [history, setHistory] = useState<SnapshotEntry[]>([])

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
    }
    fetch_metrics()
    const interval = setInterval(fetch_metrics, 3000)
    return () => clearInterval(interval)
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
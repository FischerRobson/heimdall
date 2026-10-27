import { useState } from 'react'
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { Metrics } from '../types/metrics'
import { downloadCsv, downloadJson, timestampedName } from '../utils/download'

const CSV_HEADERS = [
  'timestamp', 'heap_used_mb', 'heap_committed_mb', 'heap_max_mb', 'heap_percent',
  'process_cpu_percent', 'system_cpu_percent',
  'threads', 'threads_blocked', 'threads_waiting', 'threads_timed_waiting', 'threads_peak',
  'gc_collections', 'gc_time_ms',
]

function exportCsv(history: SnapshotEntry[]) {
  const rows = history.map(({ timestamp, metrics: m }) => {
    const gcs = m.garbageCollectorInfo?.garbageCollectorList ?? []
    return [
      timestamp,
      m.memoryInfo.inUsage, m.memoryInfo.committed, m.memoryInfo.maxAllowed, m.memoryInfo.percentage,
      m.cpuInfo.processCpuLoad, m.cpuInfo.systemCpuLoad,
      m.threadInfo.threadCount, m.threadInfo.threadBlocked, m.threadInfo.threadWaiting,
      m.threadInfo.threadTimedWaiting, m.threadInfo.threadPeak,
      gcs.reduce((acc, gc) => acc + gc.collectionCount, 0),
      gcs.reduce((acc, gc) => acc + gc.collectionTime, 0),
    ]
  })
  downloadCsv(`${timestampedName('heimdall-metrics')}.csv`, CSV_HEADERS, rows)
}

interface Props {
  history: SnapshotEntry[]
}

export interface SnapshotEntry {
  timestamp: string
  metrics: Metrics
}

type MetricKey = 'memory' | 'cpu' | 'threads' | 'gc'

interface TabConfig {
  label: string
  color: string
  activeClass: string
  getValue: (m: Metrics) => number
  getUnit: () => string
  getSub: (m: Metrics) => string
  getDataKey: () => string
  getSubStats: (m: Metrics) => { label: string; value: string }[]
}

const tabs: Record<MetricKey, TabConfig> = {
  memory: {
    label: 'Heap Memory',
    color: '#00e5a0',
    activeClass: 'border-[#00e5a0] text-[#00e5a0] bg-[#00e5a0]/5',
    getValue: (m) => m.memoryInfo.inUsage,
    getUnit: () => 'MB',
    getSub: (m) => `${m.memoryInfo.percentage}% of max`,
    getDataKey: () => 'memory',
    getSubStats: (m) => [
      { label: 'IN USAGE', value: `${m.memoryInfo.inUsage} MB` },
      { label: 'COMMITTED', value: `${m.memoryInfo.committed} MB` },
      { label: 'MAX', value: `${m.memoryInfo.maxAllowed} MB` },
      { label: 'INITIAL', value: `${m.memoryInfo.initialMemory} MB` },
    ]
  },
  cpu: {
    label: 'CPU',
    color: '#3b82f6',
    activeClass: 'border-[#3b82f6] text-[#3b82f6] bg-[#3b82f6]/5',
    getValue: (m) => m.cpuInfo.systemCpuLoad,
    getUnit: () => '%',
    getSub: (m) => `${m.cpuInfo.availableProcessors} cores`,
    getDataKey: () => 'cpu',
    getSubStats: (m) => [
      { label: 'PROCESS', value: `${m.cpuInfo.processCpuLoad}%` },
      { label: 'SYSTEM', value: `${m.cpuInfo.systemCpuLoad}%` },
      { label: 'CORES', value: `${m.cpuInfo.availableProcessors}` },
      { label: 'TOTAL RAM', value: `${m.cpuInfo.totalMemorySize} MB` },
    ]
  },
  threads: {
    label: 'Threads',
    color: '#a78bfa',
    activeClass: 'border-[#a78bfa] text-[#a78bfa] bg-[#a78bfa]/5',
    getValue: (m) => m.threadInfo.threadCount,
    getUnit: () => 'threads',
    getSub: (m) => `peak ${m.threadInfo.threadPeak}`,
    getDataKey: () => 'threads',
    getSubStats: (m) => [
      { label: 'ACTIVE', value: `${m.threadInfo.threadCount}` },
      { label: 'BLOCKED', value: `${m.threadInfo.threadBlocked}` },
      { label: 'WAITING', value: `${m.threadInfo.threadWaiting}` },
      { label: 'TIMED WAIT', value: `${m.threadInfo.threadTimedWaiting}` },
    ]
  },
  gc: {
    label: 'GC',
    color: '#f59e0b',
    activeClass: 'border-[#f59e0b] text-[#f59e0b] bg-[#f59e0b]/5',
    getValue: (m) => m.garbageCollectorInfo.garbageCollectorList.reduce((acc, gc) => acc + gc.collectionCount, 0),
    getUnit: () => 'collections',
    getSub: (m) => `${m.garbageCollectorInfo.garbageCollectorList.reduce((acc, gc) => acc + gc.collectionTime, 0)}ms total`,
    getDataKey: () => 'gc',
    getSubStats: (m) => m.garbageCollectorInfo.garbageCollectorList.map(gc => ({
      label: gc.name.replace('G1 ', '').toUpperCase(),
      value: `${gc.collectionCount} · ${gc.collectionTime}ms`
    }))
  }
}

export function MetricsChart({ history }: Props) {
  const [selected, setSelected] = useState<MetricKey>('memory')

  const tab = tabs[selected]

  const chartData = history.map((snapshot) => ({
    time: new Date(snapshot.timestamp).toLocaleTimeString(),
    value: tab.getValue(snapshot.metrics)
  }))

  const latest = history[history.length - 1]?.metrics

  return (
    <div className="bg-gray-900 border border-gray-700 rounded-xl p-5 col-span-2">

      <div className="flex gap-2 flex-wrap mb-5">
        {(Object.keys(tabs) as MetricKey[]).map((key) => (
          <button
            key={key}
            onClick={() => setSelected(key)}
            className={`border rounded-md px-3 py-1.5 font-mono text-xs tracking-widest uppercase transition-all
              ${selected === key ? tabs[key].activeClass : 'border-gray-700 text-gray-500 hover:border-gray-500 hover:text-gray-300 hover:bg-white/5'}`}
          >
            {tabs[key].label}
          </button>
        ))}
        <div className="ml-auto flex gap-2">
          {(['csv', 'json'] as const).map(format => (
            <button
              key={format}
              disabled={history.length === 0}
              onClick={() => format === 'csv'
                ? exportCsv(history)
                : downloadJson(`${timestampedName('heimdall-metrics')}.json`, history)}
              title={`export the last ${history.length} snapshots as ${format.toUpperCase()}`}
              className="border border-gray-700 rounded-md px-2.5 py-1.5 font-mono text-xs tracking-widest uppercase text-gray-500 hover:border-gray-500 hover:text-gray-300 hover:bg-white/5 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {format}
            </button>
          ))}
        </div>
      </div>

      {latest && (
        <div className="flex items-baseline gap-3 mb-4">
          <span className="text-white font-mono text-4xl">{tab.getValue(latest)}</span>
          <span className="text-gray-500 font-mono text-sm">{tab.getUnit()}</span>
          <span className="font-mono text-xs ml-auto" style={{ color: tab.color }}>
            {tab.getSub(latest)}
          </span>
        </div>
      )}

      <ResponsiveContainer width="100%" height={120}>
        <AreaChart data={chartData} margin={{ top: 5, right: 0, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id={`grad-${selected}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={tab.color} stopOpacity={0.2} />
              <stop offset="100%" stopColor={tab.color} stopOpacity={0} />
            </linearGradient>
          </defs>
          <XAxis dataKey="time" hide />
          <YAxis hide domain={['auto', 'auto']} />
          <Tooltip
            contentStyle={{
              background: '#13161e',
              border: '1px solid #1e2433',
              borderRadius: '8px',
              fontFamily: 'monospace',
              fontSize: '11px',
              color: '#e2e8f0'
            }}
            itemStyle={{ color: tab.color }}
          />
          <Area
            type="monotone"
            dataKey="value"
            stroke={tab.color}
            strokeWidth={1.5}
            fill={`url(#grad-${selected})`}
            dot={false}
            activeDot={{ r: 4, fill: tab.color }}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>

      <div className="flex justify-between mt-1 mb-4">
        <span className="text-gray-600 font-mono text-xs">
          {history.length > 0 ? new Date(history[0].timestamp).toLocaleTimeString() : ''}
        </span>
        <span className="text-gray-600 font-mono text-xs">now</span>
      </div>

      {latest && (
        <div className="grid grid-cols-4 gap-2">
          {tab.getSubStats(latest).map((s) => (
            <div key={s.label} className="bg-gray-800 rounded-lg p-3">
              <div className="text-gray-500 font-mono text-xs mb-1">{s.label}</div>
              <div className="text-white font-mono text-sm">{s.value}</div>
            </div>
          ))}
        </div>
      )}

    </div>
  )
}
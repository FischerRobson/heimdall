import { useState } from 'react'
import { MemoryInfo as MemoryInfoType, GcResult } from '../types/metrics'

interface Props {
  data: MemoryInfoType
}

export function MemoryInfo({ data }: Props) {
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState<GcResult | null>(null)

  const runGc = () => {
    setRunning(true)
    setResult(null)
    fetch('/heimdall/api/metrics/gc', { method: 'POST' })
      .then(res => res.json())
      .then((data: GcResult) => setResult(data))
      .catch(() => {})
      .finally(() => setRunning(false))
  }

  return (
    <div className="bg-gray-900 border border-gray-700 rounded-xl p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-green-400 font-mono text-xs tracking-widest uppercase">
          Heap Memory
        </h2>
        <div className="flex items-center gap-2">
          {/* a plain link so the browser streams the .hprof straight to disk */}
          <a
            href="/heimdall/api/metrics/heapdump?live=true"
            download
            title="Downloads a .hprof heap dump (live objects). Pauses the JVM while it is written and can be as large as the heap."
            className="font-mono text-xs tracking-widest uppercase px-2.5 py-1 rounded border border-gray-700 text-gray-400 hover:border-amber-400 hover:text-amber-400 hover:bg-amber-400/5 transition-colors"
          >
            heap dump
          </a>
          <button
            onClick={runGc}
            disabled={running}
            className="font-mono text-xs tracking-widest uppercase px-2.5 py-1 rounded border border-gray-700 text-gray-400 hover:border-green-400 hover:text-green-400 hover:bg-green-400/5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {running ? 'running…' : 'run gc'}
          </button>
        </div>
      </div>

      {result && (
        <div className="mb-3 font-mono text-xs text-gray-500">
          freed {Math.max(result.freedMb, 0).toFixed(1)} MB in {result.durationMs}ms
        </div>
      )}

      <div className="grid grid-cols-2 gap-3 mb-4">
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">In Usage</p>
          <p className="text-white text-xl font-mono">{data.inUsage} <span className="text-gray-500 text-sm">MB</span></p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Max Allowed</p>
          <p className="text-white text-xl font-mono">{data.maxAllowed} <span className="text-gray-500 text-sm">MB</span></p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Committed</p>
          <p className="text-white text-xl font-mono">{data.committed} <span className="text-gray-500 text-sm">MB</span></p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Initial</p>
          <p className="text-white text-xl font-mono">{data.initialMemory} <span className="text-gray-500 text-sm">MB</span></p>
        </div>
      </div>

      <div>
        <div className="flex justify-between text-xs font-mono text-gray-400 mb-1">
          <span>Usage</span>
          <span>{data.percentage}%</span>
        </div>
        <div className="w-full bg-gray-800 rounded-full h-2">
          <div
            className="bg-green-400 h-2 rounded-full transition-all duration-500"
            style={{ width: `${data.percentage}%` }}
          />
        </div>
      </div>
    </div>
  )
}
import { MemoryInfo as MemoryInfoType } from '../types/metrics'

interface Props {
  data: MemoryInfoType
}

export function MemoryInfo({ data }: Props) {
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-xl p-5">
      <h2 className="text-green-400 font-mono text-xs tracking-widest uppercase mb-4">
        Heap Memory
      </h2>

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
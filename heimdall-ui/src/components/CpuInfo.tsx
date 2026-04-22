import { CpuInfo as CpuInfoType } from '../types/metrics'

interface Props {
  data: CpuInfoType
}

export function CpuInfo({ data }: Props) {
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-xl p-5">
      <h2 className="text-blue-400 font-mono text-xs tracking-widest uppercase mb-4">
        CPU
      </h2>

      <div className="grid grid-cols-2 gap-3 mb-4">
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Process Load</p>
          <p className="text-white text-xl font-mono">{data.processCpuLoad}<span className="text-gray-500 text-sm">%</span></p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">System Load</p>
          <p className="text-white text-xl font-mono">{data.systemCpuLoad}<span className="text-gray-500 text-sm">%</span></p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Processors</p>
          <p className="text-white text-xl font-mono">{data.availableProcessors}</p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Total RAM</p>
          <p className="text-white text-xl font-mono">{data.totalMemorySize}<span className="text-gray-500 text-sm">MB</span></p>
        </div>
      </div>

      <div>
        <div className="flex justify-between text-xs font-mono text-gray-400 mb-1">
          <span>System CPU</span>
          <span>{data.systemCpuLoad}%</span>
        </div>
        <div className="w-full bg-gray-800 rounded-full h-2">
          <div
            className="bg-blue-400 h-2 rounded-full transition-all duration-500"
            style={{ width: `${data.systemCpuLoad}%` }}
          />
        </div>
      </div>
    </div>
  )
}
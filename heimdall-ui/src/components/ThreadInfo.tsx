import { ThreadInfo as ThreadInfoType } from '../types/metrics'

interface Props {
  data: ThreadInfoType
}

export function ThreadInfo({ data }: Props) {
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-xl p-5">
      <h2 className="text-purple-400 font-mono text-xs tracking-widest uppercase mb-4">
        Threads
      </h2>

      <div className="grid grid-cols-3 gap-3">
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Active</p>
          <p className="text-white text-xl font-mono">{data.threadCount}</p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Peak</p>
          <p className="text-white text-xl font-mono">{data.threadPeak}</p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className={`text-xs font-mono mb-1 ${data.threadBlocked > 0 ? 'text-red-400' : 'text-gray-400'}`}>Blocked</p>
          <p className={`text-xl font-mono ${data.threadBlocked > 0 ? 'text-red-400' : 'text-white'}`}>{data.threadBlocked}</p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Waiting</p>
          <p className="text-white text-xl font-mono">{data.threadWaiting}</p>
        </div>
        <div className="bg-gray-800 rounded-lg p-3">
          <p className="text-gray-400 text-xs font-mono mb-1">Timed Waiting</p>
          <p className="text-white text-xl font-mono">{data.threadTimedWaiting}</p>
        </div>
      </div>
    </div>
  )
}
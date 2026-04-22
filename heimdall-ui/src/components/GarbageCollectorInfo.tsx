import { GarbageCollectorInfo as GarbageCollectorInfoType } from '../types/metrics'

interface Props {
  data: GarbageCollectorInfoType
}

export function GarbageCollectorInfo({ data }: Props) {
  const {garbageCollectorList} = data;
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-xl p-5">
      <h2 className="text-amber-400 font-mono text-xs tracking-widest uppercase mb-4">
        Garbage Collection
      </h2>

      <div className="flex flex-col gap-3">
        {garbageCollectorList.map((gc) => (
          <div key={gc.name} className="bg-gray-800 rounded-lg p-3 flex items-center justify-between">
            <div>
              <p className="text-white text-sm font-mono">{gc.name}</p>
              <p className="text-gray-400 text-xs font-mono mt-1">{gc.collectionCount} collections</p>
            </div>
            <div className="text-right">
              <p className="text-amber-400 text-lg font-mono">{gc.collectionTime}<span className="text-gray-500 text-xs ml-1">ms</span></p>
              <p className="text-gray-500 text-xs font-mono">total time</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
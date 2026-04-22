import { JvmInfo as JvmInfoType } from "../types/metrics"

interface Props {
  data: JvmInfoType
}

export function JvmInfo({ data }: Props) {
  return (
    <div className="flex items-center gap-4">
      <div className="flex items-center gap-1.5">
        <span className="text-gray-600 font-mono text-xs">uptime</span>
        <span className="text-gray-300 font-mono text-xs">{data.uptime}</span>
      </div>
      <div className="text-gray-700 font-mono text-xs">·</div>
      <div className="flex items-center gap-1.5">
        <span className="text-gray-600 font-mono text-xs">pid</span>
        <span className="text-gray-300 font-mono text-xs">{data.pid}</span>
      </div>
      <div className="text-gray-700 font-mono text-xs">·</div>
      <div className="flex items-center gap-1.5">
        <span className="text-gray-600 font-mono text-xs">java</span>
        <span className="text-gray-300 font-mono text-xs">{data.javaVersion}</span>
      </div>
      <div className="text-gray-700 font-mono text-xs">·</div>
      <div className="flex items-center gap-1.5">
        <span className="text-gray-600 font-mono text-xs">jvm</span>
        <span className="text-gray-300 font-mono text-xs">{data.jvmName}</span>
      </div>
    </div>
  )
}
export interface MemoryInfo {
  initialMemory: number
  inUsage: number
  committed: number
  maxAllowed: number
  percentage: number
}

export interface GarbageCollector {
  name: string
  collectionCount: number
  collectionTime: number
}

export interface GarbageCollectorInfo {
  garbageCollectorList: GarbageCollector[]
}

export interface ThreadInfo {
  threadCount: number
  threadBlocked: number
  threadWaiting: number
  threadTimedWaiting: number
  threadPeak: number
}

export interface CpuInfo {
  processCpuLoad: number
  systemCpuLoad: number
  availableProcessors: number
  totalMemorySize: number
}

export interface JvmInfo {
  uptime: string
  pid: number
  jvmName: string
  javaVersion: string
}

export interface Metrics {
  memoryInfo: MemoryInfo
  garbageCollectorInfo: GarbageCollectorInfo
  threadInfo: ThreadInfo
  cpuInfo: CpuInfo
  jvmInfo: JvmInfo
}
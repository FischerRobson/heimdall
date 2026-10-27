export interface ThreadDumpEntry {
  id: number
  name: string
  state: string
  daemon: boolean
  priority: number
  lockName: string | null
  lockOwnerName: string | null
  lockOwnerId: number
  inNative: boolean
  suspended: boolean
  deadlocked: boolean
  stackTrace: string[]
}

export interface ThreadDump {
  id: string
  timestamp: string
  threadCount: number
  deadlockedThreadCount: number
  threads: ThreadDumpEntry[]
}

// entry of /threads/dumps — a stored dump without stack traces
export interface ThreadDumpSummary {
  id: string
  timestamp: string
  threadCount: number
  deadlockedThreadCount: number
  stateCounts: Record<string, number>
}

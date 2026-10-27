import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ThreadDump, ThreadDumpEntry, ThreadDumpSummary } from '../../types/threads'
import { CopyButton } from '../../components/CopyButton'
import { downloadJson, downloadText, timestampedName } from '../../utils/download'

const STATE_COLORS: Record<string, string> = {
  RUNNABLE:      '#00e5a0',
  WAITING:       '#3b82f6',
  TIMED_WAITING: '#f59e0b',
  BLOCKED:       '#ef4444',
  NEW:           '#64748b',
  TERMINATED:    '#64748b',
}

function getStateColor(state: string) {
  return STATE_COLORS[state] ?? '#64748b'
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString()
}

function stackText(thread: ThreadDumpEntry) {
  return thread.stackTrace.map(frame => `  at ${frame}`).join('\n')
}

// jstack-like text, so the export can be opened by the usual thread dump analyzers
function toJstackText(dump: ThreadDump) {
  const lines = [
    `Heimdall thread dump ${dump.timestamp}`,
    `${dump.threadCount} threads, ${dump.deadlockedThreadCount} deadlocked`,
    '',
  ]
  for (const t of dump.threads) {
    lines.push(`"${t.name}" #${t.id}${t.daemon ? ' daemon' : ''} prio=${t.priority}${t.deadlocked ? ' DEADLOCKED' : ''}`)
    lines.push(`   java.lang.Thread.State: ${t.state}`)
    for (const frame of t.stackTrace) lines.push(`\tat ${frame}`)
    if (t.lockName) {
      const owner = t.lockOwnerName ? ` (owned by "${t.lockOwnerName}" #${t.lockOwnerId})` : ''
      lines.push(`\t- waiting on <${t.lockName}>${owner}`)
    }
    lines.push('')
  }
  return lines.join('\n')
}

interface DumpDiff {
  added: ThreadDumpEntry[]
  removed: ThreadDumpEntry[]
  changed: { thread: ThreadDumpEntry; from: string }[]
  stateDelta: { state: string; before: number; after: number }[]
}

// thread ids are never reused within a JVM's lifetime, so they identify a thread across dumps
function diffDumps(base: ThreadDump, current: ThreadDump): DumpDiff {
  const baseById = new Map(base.threads.map(t => [t.id, t]))
  const currentById = new Map(current.threads.map(t => [t.id, t]))

  const added = current.threads.filter(t => !baseById.has(t.id))
  const removed = base.threads.filter(t => !currentById.has(t.id))
  const changed = current.threads
    .filter(t => baseById.has(t.id) && baseById.get(t.id)!.state !== t.state)
    .map(t => ({ thread: t, from: baseById.get(t.id)!.state }))

  const count = (d: ThreadDump) => d.threads.reduce<Record<string, number>>((acc, t) => {
    acc[t.state] = (acc[t.state] ?? 0) + 1
    return acc
  }, {})
  const before = count(base)
  const after = count(current)
  const states = [...new Set([...Object.keys(before), ...Object.keys(after)])].sort()
  const stateDelta = states.map(state => ({ state, before: before[state] ?? 0, after: after[state] ?? 0 }))

  return { added, removed, changed, stateDelta }
}

function ThreadRow({ thread, isOpen, onToggle }: {
  thread: ThreadDumpEntry
  isOpen: boolean
  onToggle: () => void
}) {
  const color = thread.deadlocked ? '#ef4444' : getStateColor(thread.state)

  return (
    <div
      className="border rounded-lg overflow-hidden transition-colors"
      style={{
        borderColor: thread.deadlocked ? '#7f1d1d' : isOpen ? '#334155' : '#1e2433',
        background: '#13161e',
      }}
    >
      <div
        className="flex items-center gap-3 px-4 py-2.5 cursor-pointer hover:bg-white/5 transition-colors"
        onClick={onToggle}
      >
        <div className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: color }} />
        <div className="font-mono text-xs text-gray-200 flex-1 truncate">{thread.name}</div>
        {thread.deadlocked && (
          <div className="font-mono text-xs px-2 py-0.5 rounded bg-red-950 text-red-400 border border-red-900 flex-shrink-0">
            deadlock
          </div>
        )}
        <div
          className="font-mono text-xs px-2 py-0.5 rounded flex-shrink-0"
          style={{ color, background: color + '15' }}
        >
          {thread.state}
        </div>
        {thread.daemon && (
          <div className="font-mono text-gray-700 flex-shrink-0" style={{ fontSize: '9px' }}>daemon</div>
        )}
        <div className="font-mono text-gray-600 shrink-0 w-10 text-right" style={{ fontSize: '10px' }}>
          #{thread.id}
        </div>
        <div
          className="font-mono text-gray-600 shrink-0 transition-transform"
          style={{ fontSize: '10px', transform: isOpen ? 'rotate(180deg)' : 'none' }}
        >
          ▼
        </div>
      </div>

      {isOpen && (
        <div className="border-t border-gray-800 px-4 py-3 bg-gray-950">
          {(thread.lockName || thread.lockOwnerName) && (
            <div className="mb-3 font-mono text-xs text-amber-400 flex flex-col gap-0.5">
              {thread.lockName && <div>waiting on {thread.lockName}</div>}
              {thread.lockOwnerName && (
                <div>held by {thread.lockOwnerName} (#{thread.lockOwnerId})</div>
              )}
            </div>
          )}
          {thread.stackTrace.length === 0 ? (
            <div className="font-mono text-xs text-gray-700">no stack trace</div>
          ) : (
            <div className="relative">
              <div className="absolute top-0 right-0">
                <CopyButton text={`"${thread.name}" #${thread.id} ${thread.state}\n${stackText(thread)}`} />
              </div>
              <pre className="font-mono text-xs text-gray-400 whitespace-pre-wrap break-all pr-16">
                {stackText(thread)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function StatCard({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
      <div className="font-mono uppercase tracking-widest text-gray-600 mb-2" style={{ fontSize: '9px' }}>
        {label}
      </div>
      {children}
    </div>
  )
}

function DiffList({ title, color, items }: {
  title: string
  color: string
  items: { key: number; name: string; detail: string }[]
}) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 min-w-0">
      <div className="flex items-center justify-between mb-2">
        <span className="font-mono uppercase tracking-widest text-gray-600" style={{ fontSize: '9px' }}>{title}</span>
        <span className="font-mono text-xs" style={{ color }}>{items.length}</span>
      </div>
      <div className="flex flex-col gap-1 max-h-56 overflow-y-auto">
        {items.length === 0 && <div className="font-mono text-xs text-gray-700">none</div>}
        {items.map(item => (
          <div key={item.key} className="flex justify-between gap-2 font-mono text-xs">
            <span className="text-gray-300 truncate">{item.name}</span>
            <span className="text-gray-500 shrink-0">{item.detail}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function ComparePanel({ base, current }: { base: ThreadDump; current: ThreadDump }) {
  const diff = useMemo(() => diffDumps(base, current), [base, current])

  return (
    <div className="mb-6">
      <div className="font-mono text-xs text-gray-500 mb-3">
        comparing <span className="text-gray-300">{formatTime(base.timestamp)}</span>
        {' → '}
        <span className="text-gray-300">{formatTime(current.timestamp)}</span>
      </div>

      <div className="flex gap-2 flex-wrap mb-3">
        {diff.stateDelta.map(({ state, before, after }) => {
          const delta = after - before
          return (
            <div key={state} className="bg-gray-900 border border-gray-800 rounded-lg px-3 py-2 font-mono text-xs">
              <span style={{ color: getStateColor(state) }}>{state}</span>
              <span className="text-gray-400 ml-2">{before} → {after}</span>
              {delta !== 0 && (
                <span className="ml-1.5" style={{ color: delta > 0 ? '#f59e0b' : '#64748b' }}>
                  ({delta > 0 ? '+' : ''}{delta})
                </span>
              )}
            </div>
          )
        })}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <DiffList
          title="State changed"
          color="#f59e0b"
          items={diff.changed.map(c => ({ key: c.thread.id, name: c.thread.name, detail: `${c.from} → ${c.thread.state}` }))}
        />
        <DiffList
          title="New threads"
          color="#00e5a0"
          items={diff.added.map(t => ({ key: t.id, name: t.name, detail: t.state }))}
        />
        <DiffList
          title="Gone threads"
          color="#64748b"
          items={diff.removed.map(t => ({ key: t.id, name: t.name, detail: t.state }))}
        />
      </div>
    </div>
  )
}

export function ThreadDumpPage() {
  const [history, setHistory] = useState<ThreadDumpSummary[]>([])
  const [dump, setDump] = useState<ThreadDump | null>(null)
  const [compareBase, setCompareBase] = useState<ThreadDump | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [stateFilter, setStateFilter] = useState('all')
  const [openThread, setOpenThread] = useState<number | null>(null)

  const fetchJson = <T,>(url: string): Promise<T> =>
    fetch(url).then(res => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json() as Promise<T>
    })

  const loadHistory = () =>
    fetchJson<ThreadDumpSummary[]>('/heimdall/api/threads/dumps')
      .then(data => setHistory(Array.isArray(data) ? data : []))
      .catch(() => {})

  // show the latest stored dump when coming back to the page
  useEffect(() => {
    fetchJson<ThreadDumpSummary[]>('/heimdall/api/threads/dumps')
      .then(data => {
        const list = Array.isArray(data) ? data : []
        setHistory(list)
        if (list.length > 0) openDump(list[0].id)
      })
      .catch(() => {})
  }, [])

  const takeDump = () => {
    setLoading(true)
    setError(null)
    fetchJson<ThreadDump>('/heimdall/api/threads/dump')
      .then(data => {
        setDump(data)
        setOpenThread(null)
        loadHistory()
      })
      .catch(err => setError(`capture failed: ${err.message}`))
      .finally(() => setLoading(false))
  }

  const openDump = (id: string) => {
    setError(null)
    fetchJson<ThreadDump>(`/heimdall/api/threads/dumps/${id}`)
      .then(data => {
        setDump(data)
        setOpenThread(null)
        if (compareBase?.id === data.id) setCompareBase(null)
      })
      .catch(err => setError(`could not load dump: ${err.message}`))
  }

  const toggleCompare = (id: string) => {
    if (compareBase?.id === id) {
      setCompareBase(null)
      return
    }
    fetchJson<ThreadDump>(`/heimdall/api/threads/dumps/${id}`)
      .then(setCompareBase)
      .catch(err => setError(`could not load dump: ${err.message}`))
  }

  const states = ['all', 'RUNNABLE', 'WAITING', 'TIMED_WAITING', 'BLOCKED']

  const filtered = (dump?.threads ?? []).filter(t => {
    if (stateFilter !== 'all' && t.state !== stateFilter) return false
    if (search && !t.name.toLowerCase().includes(search.toLowerCase())) return false
    return true
  })

  const exportName = dump ? timestampedName('heimdall-threads', new Date(dump.timestamp)) : ''

  return (
    <div className="p-6 max-w-full">
      <div className="flex items-center justify-between mb-5 gap-2 flex-wrap">
        <h2 className="font-mono text-xs tracking-widest uppercase text-gray-500">Thread Dump</h2>
        <div className="flex items-center gap-2">
          {dump && (
            <>
              <button
                onClick={() => downloadJson(`${exportName}.json`, dump)}
                className="font-mono text-xs tracking-widest uppercase px-3 py-1.5 rounded border border-gray-700 text-gray-400 hover:border-gray-500 hover:text-gray-200 hover:bg-white/5 transition-colors"
              >
                export json
              </button>
              <button
                onClick={() => downloadText(`${exportName}.txt`, toJstackText(dump))}
                title="jstack-style text, readable by thread dump analyzers"
                className="font-mono text-xs tracking-widest uppercase px-3 py-1.5 rounded border border-gray-700 text-gray-400 hover:border-gray-500 hover:text-gray-200 hover:bg-white/5 transition-colors"
              >
                export txt
              </button>
            </>
          )}
          <button
            onClick={takeDump}
            disabled={loading}
            className="font-mono text-xs tracking-widest uppercase px-3 py-1.5 rounded border border-green-400 text-green-400 bg-green-400/5 hover:bg-green-400/10 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? 'capturing…' : 'capture dump'}
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-4 font-mono text-xs text-red-400 border border-red-900 bg-red-950/40 rounded-lg px-3 py-2">
          {error}
        </div>
      )}

      {history.length > 0 && (
        <div className="mb-6">
          <div className="font-mono uppercase tracking-widest text-gray-600 mb-2" style={{ fontSize: '9px' }}>
            History · click to open · "vs" to compare with the open dump
          </div>
          <div className="flex gap-2 flex-wrap">
            {history.map(h => {
              const isOpen = dump?.id === h.id
              const isBase = compareBase?.id === h.id
              return (
                <div
                  key={h.id}
                  className="flex items-center rounded border overflow-hidden font-mono text-xs"
                  style={{ borderColor: isOpen ? '#00e5a0' : isBase ? '#f59e0b' : '#1e2433' }}
                >
                  <button
                    onClick={() => openDump(h.id)}
                    className={`px-2.5 py-1 transition-colors hover:bg-white/5 ${isOpen ? 'text-green-400' : 'text-gray-400'}`}
                  >
                    {formatTime(h.timestamp)}
                    <span className="text-gray-600 ml-1.5">{h.threadCount}t</span>
                    {h.deadlockedThreadCount > 0 && <span className="text-red-400 ml-1.5">●</span>}
                  </button>
                  {!isOpen && dump && (
                    <button
                      onClick={() => toggleCompare(h.id)}
                      className={`px-2 py-1 border-l border-gray-800 transition-colors hover:bg-white/5 ${isBase ? 'text-amber-400' : 'text-gray-600'}`}
                      title="compare the open dump against this one"
                    >
                      vs
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}

      {!dump && !loading && (
        <div className="flex items-center justify-center h-64 font-mono text-xs text-gray-700 tracking-widest uppercase">
          no dump captured yet
        </div>
      )}

      {dump && (
        <>
          {compareBase && compareBase.id !== dump.id && (
            <ComparePanel
              // always diff older → newer, whichever order they were picked in
              base={new Date(compareBase.timestamp) <= new Date(dump.timestamp) ? compareBase : dump}
              current={new Date(compareBase.timestamp) <= new Date(dump.timestamp) ? dump : compareBase}
            />
          )}

          <div className="grid grid-cols-3 gap-3 mb-6">
            <StatCard label="Threads">
              <div className="font-mono text-2xl font-medium text-blue-400">{dump.threadCount}</div>
            </StatCard>
            <StatCard label="Deadlocked">
              <div
                className="font-mono text-2xl font-medium"
                style={{ color: dump.deadlockedThreadCount > 0 ? '#ef4444' : '#00e5a0' }}
              >
                {dump.deadlockedThreadCount}
              </div>
            </StatCard>
            <StatCard label="Captured at">
              <div className="font-mono text-sm text-gray-300">{formatTime(dump.timestamp)}</div>
            </StatCard>
          </div>

          <div className="flex items-center gap-2 mb-4 flex-wrap">
            <input
              type="text"
              placeholder="search thread name..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="bg-gray-900 border border-gray-700 rounded-md px-3 py-1.5 font-mono text-xs text-gray-200 outline-none focus:border-gray-500 placeholder-gray-700 w-52"
            />
            {states.map(s => (
              <button
                key={s}
                onClick={() => setStateFilter(s)}
                className={`font-mono text-xs tracking-widest uppercase px-3 py-1.5 rounded border transition-all ${
                  stateFilter === s
                    ? 'border-green-400 text-green-400 bg-green-400/5'
                    : 'border-gray-700 text-gray-500 hover:border-gray-500 hover:text-gray-300 hover:bg-white/5'
                }`}
              >
                {s}
              </button>
            ))}
          </div>

          <div className="flex flex-col gap-1.5">
            {filtered.length === 0 && (
              <div className="flex items-center justify-center h-32 font-mono text-xs text-gray-700 tracking-widest uppercase">
                no threads match
              </div>
            )}
            {filtered.map(thread => (
              <ThreadRow
                key={thread.id}
                thread={thread}
                isOpen={openThread === thread.id}
                onToggle={() => setOpenThread(openThread === thread.id ? null : thread.id)}
              />
            ))}
          </div>
        </>
      )}
    </div>
  )
}

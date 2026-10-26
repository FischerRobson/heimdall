import { useState } from 'react'
import { ThreadDump, ThreadDumpEntry } from '../../types/threads'

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
            <pre className="font-mono text-xs text-gray-400 whitespace-pre-wrap break-all">
              {thread.stackTrace.map(frame => `  at ${frame}`).join('\n')}
            </pre>
          )}
        </div>
      )}
    </div>
  )
}

export function ThreadDumpPage() {
  const [dump, setDump] = useState<ThreadDump | null>(null)
  const [loading, setLoading] = useState(false)
  const [search, setSearch] = useState('')
  const [stateFilter, setStateFilter] = useState('all')
  const [openThread, setOpenThread] = useState<number | null>(null)

  const takeDump = () => {
    setLoading(true)
    fetch('/heimdall/api/threads/dump')
      .then(res => res.json())
      .then((data: ThreadDump) => {
        setDump(data)
        setOpenThread(null)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }

  const states = ['all', 'RUNNABLE', 'WAITING', 'TIMED_WAITING', 'BLOCKED']

  const filtered = (dump?.threads ?? []).filter(t => {
    if (stateFilter !== 'all' && t.state !== stateFilter) return false
    if (search && !t.name.toLowerCase().includes(search.toLowerCase())) return false
    return true
  })

  return (
    <div className="p-6 max-w-full">
      <div className="flex items-center justify-between mb-5">
        <h2 className="font-mono text-xs tracking-widest uppercase text-gray-500">Thread Dump</h2>
        <button
          onClick={takeDump}
          disabled={loading}
          className="font-mono text-xs tracking-widest uppercase px-3 py-1.5 rounded border border-green-400 text-green-400 bg-green-400/5 hover:bg-green-400/10 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? 'capturing…' : 'capture dump'}
        </button>
      </div>

      {!dump && !loading && (
        <div className="flex items-center justify-center h-64 font-mono text-xs text-gray-700 tracking-widest uppercase">
          no dump captured yet
        </div>
      )}

      {dump && (
        <>
          <div className="grid grid-cols-3 gap-3 mb-6">
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
              <div className="font-mono uppercase tracking-widest text-gray-600 mb-2" style={{ fontSize: '9px' }}>
                Threads
              </div>
              <div className="font-mono text-2xl font-medium text-blue-400">{dump.threadCount}</div>
            </div>
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
              <div className="font-mono uppercase tracking-widest text-gray-600 mb-2" style={{ fontSize: '9px' }}>
                Deadlocked
              </div>
              <div
                className="font-mono text-2xl font-medium"
                style={{ color: dump.deadlockedThreadCount > 0 ? '#ef4444' : '#00e5a0' }}
              >
                {dump.deadlockedThreadCount}
              </div>
            </div>
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
              <div className="font-mono uppercase tracking-widest text-gray-600 mb-2" style={{ fontSize: '9px' }}>
                Captured at
              </div>
              <div className="font-mono text-sm text-gray-300">
                {new Date(dump.timestamp).toLocaleTimeString()}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2 mb-4">
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

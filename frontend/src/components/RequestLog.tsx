import { useState, useSyncExternalStore } from 'react'
import { clearLog, getLog, subscribeLog } from '../api/client'
import type { LogEntry } from '../api/client'

function statusClass(status: number | null): string {
  if (status === null) return 'log-bad'
  if (status >= 500) return 'log-bad'
  if (status >= 400) return 'log-warn'
  return 'log-ok'
}

function Json({ title, value }: { title: string; value: unknown }) {
  if (value === undefined) return null
  return (
    <div>
      <div className="log-title">{title}</div>
      <pre>{typeof value === 'string' ? value : JSON.stringify(value, null, 2)}</pre>
    </div>
  )
}

function Entry({ entry }: { entry: LogEntry }) {
  const [open, setOpen] = useState(false)
  return (
    <li className="log-entry">
      <button type="button" className="log-row" onClick={() => setOpen((value) => !value)}>
        <span className="muted">{entry.time}</span>
        <strong>{entry.method}</strong>
        <span className="log-path">{entry.path}</span>
        <span className={`log-status ${statusClass(entry.status)}`}>{entry.status ?? 'ERR'}</span>
        <span className="muted">{entry.ms}ms</span>
      </button>
      {open ? (
        <div className="log-detail">
          <Json title="Request headers" value={entry.requestHeaders} />
          <Json title="Request body" value={entry.requestBody} />
          <Json title="Response" value={entry.responseBody} />
        </div>
      ) : null}
    </li>
  )
}

/** Mọi request/response đi qua api/client đều được ghi lại để dễ đối chiếu với backend. */
export default function RequestLog() {
  const entries = useSyncExternalStore(subscribeLog, getLog)
  return (
    <aside className="log-panel">
      <header className="card-header">
        <h2>Nhật ký request</h2>
        <button type="button" onClick={clearLog} disabled={entries.length === 0}>
          Xóa
        </button>
      </header>
      {entries.length === 0 ? (
        <p className="muted pad">Chưa có request nào. Thao tác ở bên trái để xem request/response thật.</p>
      ) : (
        <ul className="log-list">
          {entries.map((entry) => (
            <Entry key={entry.id} entry={entry} />
          ))}
        </ul>
      )}
    </aside>
  )
}

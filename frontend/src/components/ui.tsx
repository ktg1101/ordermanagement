import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import type { ApiError } from '../api/client'

// ---- Định dạng ------------------------------------------------------------------------------

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('vi-VN')
}

export function formatMoney(value: number, currency = 'VND'): string {
  if (currency === 'VND') return `${new Intl.NumberFormat('vi-VN').format(value)} ₫`
  return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value)} ${currency}`
}

/** UUID v4; dùng crypto.randomUUID khi có (chỉ có trong secure context như localhost). */
export function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = (Math.random() * 16) | 0
    return (char === 'x' ? random : (random & 0x3) | 0x8).toString(16)
  })
}

// ---- Bố cục ---------------------------------------------------------------------------------

export function Card({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      <header className="card-header">
        <h2>{title}</h2>
        {actions ? <div className="card-actions">{actions}</div> : null}
      </header>
      <div className="card-body">{children}</div>
    </section>
  )
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint ? <small>{hint}</small> : null}
    </label>
  )
}

// ---- Trạng thái -----------------------------------------------------------------------------

const BADGE_CLASS: Record<string, string> = {
  ACTIVE: 'badge-ok',
  CONFIRMED: 'badge-ok',
  COMPLETED: 'badge-ok',
  SUCCEEDED: 'badge-ok',
  PENDING: 'badge-warn',
  PROCESSING: 'badge-warn',
  DISABLED: 'badge-bad',
  CANCELLED: 'badge-bad',
  FAILED: 'badge-bad',
  REFUNDED: 'badge-info',
}

export function StatusBadge({ value }: { value: string }) {
  return <span className={`badge ${BADGE_CLASS[value] ?? 'badge-info'}`}>{value}</span>
}

export function ErrorBox({ error }: { error: ApiError | null }) {
  if (!error) return null
  const problem = error.problem
  return (
    <div className="error-box" role="alert">
      <div>
        <strong>{error.code}</strong> <span className="muted">HTTP {error.status || '—'}</span>
      </div>
      <div>{error.message}</div>
      {problem?.errors?.length ? (
        <ul>
          {problem.errors.map((item, index) => (
            <li key={`${item.field}-${index}`}>
              <code>{item.field}</code>: {item.message}
            </li>
          ))}
        </ul>
      ) : null}
      {problem?.shortages?.length ? (
        <ul>
          {problem.shortages.map((item) => (
            <li key={item.productId}>
              Thiếu hàng <code>{item.productId}</code> (yêu cầu {item.requestedQuantity})
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number
  totalPages: number
  totalElements: number
  onPage: (page: number) => void
}) {
  return (
    <div className="pagination">
      <span className="muted">{totalElements} bản ghi</span>
      <button type="button" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        ‹ Trước
      </button>
      <span>
        Trang {totalPages === 0 ? 0 : page + 1}/{totalPages}
      </span>
      <button type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Sau ›
      </button>
    </div>
  )
}

// ---- Thông báo ------------------------------------------------------------------------------

interface Toast {
  id: number
  text: string
  kind: 'ok' | 'error'
}

interface ToastApi {
  notify: (text: string, kind?: Toast['kind']) => void
}

const ToastContext = createContext<ToastApi | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const counter = useRef(0)

  const notify = useCallback((text: string, kind: Toast['kind'] = 'ok') => {
    counter.current += 1
    const id = counter.current
    setToasts((current) => [...current, { id, text, kind }])
    window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 4500)
  }, [])

  const value = useMemo(() => ({ notify }), [notify])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.kind}`}>
            {toast.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast phải được dùng bên trong ToastProvider')
  return context
}

/** Hiển thị 8 ký tự đầu của UUID; bấm để copy đầy đủ. */
export function ShortId({ id }: { id: string }) {
  const toast = useToast()
  const copy = () => {
    navigator.clipboard?.writeText(id).then(
      () => toast.notify('Đã copy ID'),
      () => toast.notify('Không copy được ID', 'error'),
    )
  }
  return (
    <button type="button" className="link mono" title={`${id} (bấm để copy)`} onClick={copy}>
      {id.slice(0, 8)}
    </button>
  )
}

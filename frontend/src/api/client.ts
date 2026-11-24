import type { ProblemDetail } from './types'

/** Lỗi trả về từ backend (hoặc lỗi mạng). `code` là mã máy đọc được, ví dụ INSUFFICIENT_STOCK. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly problem: ProblemDetail | null

  constructor(status: number, code: string, message: string, problem: ProblemDetail | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.problem = problem
  }
}

export function asApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  return new ApiError(0, 'UNEXPECTED_ERROR', error instanceof Error ? error.message : String(error), null)
}

// ---- Nhật ký request (hiển thị ở panel bên phải) -------------------------------------------

export interface LogEntry {
  id: number
  time: string
  method: string
  path: string
  status: number | null
  ms: number
  requestHeaders?: Record<string, string>
  requestBody?: unknown
  responseBody?: unknown
}

const MAX_LOG_ENTRIES = 200
let nextLogId = 1
let entries: LogEntry[] = []
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((listener) => listener())
}

export function subscribeLog(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Trả về cùng một mảng cho tới khi có thay đổi (yêu cầu của useSyncExternalStore). */
export function getLog(): LogEntry[] {
  return entries
}

export function clearLog() {
  entries = []
  emit()
}

function pushLog(entry: Omit<LogEntry, 'id' | 'time'>) {
  const full: LogEntry = { ...entry, id: nextLogId++, time: new Date().toLocaleTimeString('vi-VN') }
  entries = [full, ...entries].slice(0, MAX_LOG_ENTRIES)
  emit()
}

// ---- Gọi API --------------------------------------------------------------------------------

export interface RequestOptions {
  body?: unknown
  query?: Record<string, string | number | boolean | null | undefined>
  headers?: Record<string, string>
  /** true = không ghi vào nhật ký (dùng cho request nền như health check). */
  silent?: boolean
}

export interface ApiResponse<T> {
  status: number
  data: T
}

function buildPath(path: string, query: RequestOptions['query']): string {
  if (!query) return path
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  }
  const queryString = params.toString()
  return queryString ? `${path}?${queryString}` : path
}

function toApiError(status: number, parsed: unknown): ApiError {
  if (parsed !== null && typeof parsed === 'object') {
    const problem = parsed as ProblemDetail
    return new ApiError(status, problem.code ?? `HTTP_${status}`, problem.detail ?? problem.title ?? `HTTP ${status}`, problem)
  }
  if (status >= 500) {
    // Backend thật luôn trả JSON; 5xx không có JSON thường là proxy không nối được backend.
    return new ApiError(status, 'BACKEND_UNREACHABLE', 'Backend không phản hồi. Backend đã chạy chưa (cổng 8080)?', null)
  }
  const text = typeof parsed === 'string' && parsed ? parsed : `HTTP ${status}`
  return new ApiError(status, `HTTP_${status}`, text, null)
}

/** Gọi API, trả về cả mã HTTP (cần để phân biệt 201 và 200). Ném ApiError khi lỗi. */
export async function call<T>(method: string, path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const url = buildPath(path, options.query)
  const headers: Record<string, string> = { ...options.headers }
  let body: string | undefined
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  const started = performance.now()
  let response: Response
  try {
    response = await fetch(url, { method, headers, body })
  } catch {
    if (!options.silent) {
      pushLog({ method, path: url, status: null, ms: Math.round(performance.now() - started), requestHeaders: headers, requestBody: options.body })
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'Không gọi được máy chủ. Kiểm tra dev server và backend.', null)
  }

  const text = await response.text()
  let parsed: unknown = undefined
  if (text) {
    try {
      parsed = JSON.parse(text)
    } catch {
      parsed = text
    }
  }

  if (!options.silent) {
    pushLog({
      method,
      path: url,
      status: response.status,
      ms: Math.round(performance.now() - started),
      requestHeaders: Object.keys(headers).length > 0 ? headers : undefined,
      requestBody: options.body,
      responseBody: parsed,
    })
  }

  if (!response.ok) throw toApiError(response.status, parsed)
  return { status: response.status, data: parsed as T }
}

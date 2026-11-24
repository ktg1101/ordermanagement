import { call } from './client'
import type { ApiResponse, RequestOptions } from './client'
import type {
  InventoryView,
  OrderStatus,
  OrderView,
  PageResponse,
  PaymentOutcome,
  PaymentView,
  ProductView,
  UserStatus,
  UserView,
} from './types'

const BASE = '/api/v1'

export interface PageParams {
  page?: number
  size?: number
}

const get = <T>(path: string, query?: RequestOptions['query'], silent = false) =>
  call<T>('GET', path, { query, silent }).then((response) => response.data)

const post = <T>(path: string, body?: unknown) => call<T>('POST', path, { body }).then((response) => response.data)

const put = <T>(path: string, body: unknown) => call<T>('PUT', path, { body }).then((response) => response.data)

const del = (path: string) => call<void>('DELETE', path).then(() => undefined)

// ---- User -----------------------------------------------------------------------------------

export const usersApi = {
  list: (params: PageParams & { q?: string; status?: UserStatus | '' }) =>
    get<PageResponse<UserView>>(`${BASE}/users`, { ...params }),
  get: (id: string) => get<UserView>(`${BASE}/users/${id}`),
  create: (body: { email: string; password: string; fullName: string }) => post<UserView>(`${BASE}/users`, body),
  updateName: (id: string, fullName: string) => put<UserView>(`${BASE}/users/${id}`, { fullName }),
  disable: (id: string) => del(`${BASE}/users/${id}`),
  enable: (id: string) => post<UserView>(`${BASE}/users/${id}/enable`),
}

// ---- Product & Inventory --------------------------------------------------------------------

export const productsApi = {
  list: (params: PageParams & { q?: string; active?: boolean | '' }) =>
    get<PageResponse<ProductView>>(`${BASE}/products`, { ...params }),
  get: (id: string) => get<ProductView>(`${BASE}/products/${id}`),
  create: (body: { sku: string; name: string; description?: string; price: number }) =>
    post<ProductView>(`${BASE}/products`, body),
  update: (id: string, body: { name: string; description?: string; price: number; active: boolean }) =>
    put<ProductView>(`${BASE}/products/${id}`, body),
  deactivate: (id: string) => del(`${BASE}/products/${id}`),
}

export const inventoryApi = {
  get: (productId: string, silent = false) => get<InventoryView>(`${BASE}/inventory/${productId}`, undefined, silent),
  adjust: (productId: string, delta: number) =>
    post<InventoryView>(`${BASE}/inventory/${productId}/adjustments`, { delta }),
}

// ---- Order ----------------------------------------------------------------------------------

export const ordersApi = {
  list: (params: PageParams & { userId?: string; status?: OrderStatus | '' }) =>
    get<PageResponse<OrderView>>(`${BASE}/orders`, { ...params }),
  get: (id: string) => get<OrderView>(`${BASE}/orders/${id}`),
  create: (body: { userId: string; items: { productId: string; quantity: number }[] }) =>
    post<OrderView>(`${BASE}/orders`, body),
  cancel: (id: string) => post<OrderView>(`${BASE}/orders/${id}/cancel`),
  complete: (id: string) => post<OrderView>(`${BASE}/orders/${id}/complete`),
}

// ---- Payment --------------------------------------------------------------------------------

export const paymentsApi = {
  /** Trả cả mã HTTP: 201 = tạo mới, 200 = gửi lại cùng Idempotency-Key. */
  create: (orderId: string, provider: string, idempotencyKey: string): Promise<ApiResponse<PaymentView>> =>
    call<PaymentView>('POST', `${BASE}/orders/${orderId}/payments`, {
      body: { provider },
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  listByOrder: (orderId: string) => get<PaymentView[]>(`${BASE}/orders/${orderId}/payments`),
  get: (id: string) => get<PaymentView>(`${BASE}/payments/${id}`),
  submit: (id: string) => post<PaymentView>(`${BASE}/payments/${id}/submit`),
  callback: (id: string, body: { outcome: PaymentOutcome; providerReference?: string; failureCode?: string }) =>
    post<PaymentView>(`${BASE}/payments/${id}/callback`, body),
  cancel: (id: string) => post<PaymentView>(`${BASE}/payments/${id}/cancel`),
}

// ---- Health ---------------------------------------------------------------------------------

export const healthApi = {
  check: () => call<{ status: string }>('GET', '/actuator/health', { silent: true }).then((response) => response.data),
}

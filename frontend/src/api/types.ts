// Kiểu dữ liệu khớp với các view/request của backend (module application + api).

export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface UserView {
  id: string
  email: string
  fullName: string
  status: UserStatus
  roles: string[]
  createdAt: string
  updatedAt: string
}

export interface ProductView {
  id: string
  sku: string
  name: string
  description: string | null
  price: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface InventoryView {
  productId: string
  availableQuantity: number
  reservedQuantity: number
  updatedAt: string | null
}

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED'

export interface OrderItemView {
  productId: string
  sku: string
  name: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface OrderView {
  id: string
  orderNumber: string
  userId: string
  status: OrderStatus
  totalAmount: number
  currency: string
  items: OrderItemView[]
  createdAt: string
  updatedAt: string
}

export type PaymentStatus = 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'REFUNDED'
export type PaymentOutcome = 'SUCCEEDED' | 'FAILED'

export interface PaymentView {
  id: string
  orderId: string
  attemptNumber: number
  status: PaymentStatus
  amount: number
  currency: string
  provider: string
  providerReference: string | null
  failureCode: string | null
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface FieldViolation {
  field: string
  message: string
}

export interface StockShortage {
  productId: string
  requestedQuantity: number
}

/** Dạng lỗi application/problem+json của backend (RFC 9457) kèm `code` máy đọc được. */
export interface ProblemDetail {
  status?: number
  title?: string
  detail?: string
  code?: string
  errors?: FieldViolation[]
  shortages?: StockShortage[]
}

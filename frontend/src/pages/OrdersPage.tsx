import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ordersApi, paymentsApi, productsApi, usersApi } from '../api/endpoints'
import type { OrderStatus, OrderView, PageResponse, PaymentView, ProductView, UserView } from '../api/types'
import { Card, ErrorBox, Field, Pagination, ShortId, StatusBadge, formatDate, formatMoney, newId, useToast } from '../components/ui'
import { useAction } from '../hooks/useAction'

const PAGE_SIZE = 10
const ORDER_STATUSES: OrderStatus[] = ['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']

interface Line {
  key: number
  productId: string
  quantity: string
}

export default function OrdersPage() {
  const toast = useToast()

  // Dữ liệu cho các ô chọn (chỉ user đang hoạt động, sản phẩm đang bán).
  const [users, setUsers] = useState<UserView[]>([])
  const [products, setProducts] = useState<ProductView[]>([])

  const [userId, setUserId] = useState('')
  const [lines, setLines] = useState<Line[]>([{ key: 1, productId: '', quantity: '1' }])
  const [nextKey, setNextKey] = useState(2)

  const [filters, setFilters] = useState<{ userId: string; status: OrderStatus | '' }>({ userId: '', status: '' })
  const [draft, setDraft] = useState(filters)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<OrderView> | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { run: runLookups, busy: lookupsBusy, error: lookupsError } = useAction()
  const { run: runList, busy: listBusy, error: listError } = useAction()
  const { run: runCreate, busy: createBusy, error: createError } = useAction()

  const loadLookups = useCallback(async () => {
    const result = await runLookups(() =>
      Promise.all([usersApi.list({ status: 'ACTIVE', size: 100 }), productsApi.list({ active: true, size: 100 })]),
    )
    if (!result) return
    const [userPage, productPage] = result
    setUsers(userPage.items)
    setProducts(productPage.items)
    setUserId((current) => current || userPage.items[0]?.id || '')
  }, [runLookups])

  const loadOrders = useCallback(async () => {
    const result = await runList(() => ordersApi.list({ ...filters, page, size: PAGE_SIZE }))
    if (result) setData(result)
  }, [runList, filters, page])

  useEffect(() => {
    void loadLookups()
  }, [loadLookups])

  useEffect(() => {
    void loadOrders()
  }, [loadOrders])

  const userLabel = (id: string) => users.find((user) => user.id === id)?.email ?? id.slice(0, 8)

  const updateLine = (key: number, patch: Partial<Line>) =>
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...patch } : line)))

  const addLine = () => {
    setLines((current) => [...current, { key: nextKey, productId: '', quantity: '1' }])
    setNextKey((value) => value + 1)
  }

  const removeLine = (key: number) => setLines((current) => current.filter((line) => line.key !== key))

  const onCreate = async (event: FormEvent) => {
    event.preventDefault()
    const items = lines.map((line) => ({ productId: line.productId, quantity: Number(line.quantity) }))
    const created = await runCreate(() => ordersApi.create({ userId, items }))
    if (created) {
      toast.notify(`Đã tạo đơn ${created.orderNumber} (${created.status})`)
      setSelectedId(created.id)
      if (page === 0) await loadOrders()
      else setPage(0)
    }
  }

  const onSearch = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    setFilters(draft)
  }

  return (
    <>
      <Card
        title="Tạo đơn hàng"
        actions={
          <button type="button" onClick={() => void loadLookups()} disabled={lookupsBusy}>
            Tải lại user/sản phẩm
          </button>
        }
      >
        <form onSubmit={onCreate}>
          <div className="form-grid">
            <Field label="Khách hàng (user ACTIVE)">
              <select value={userId} onChange={(e) => setUserId(e.target.value)}>
                <option value="">— chọn user —</option>
                {users.map((user) => (
                  <option key={user.id} value={user.id}>
                    {user.email}
                  </option>
                ))}
              </select>
            </Field>
          </div>

          <h3>Các dòng hàng</h3>
          {lines.map((line) => (
            <div key={line.key} className="inline" style={{ marginBottom: 6 }}>
              <select
                style={{ width: 360 }}
                value={line.productId}
                onChange={(e) => updateLine(line.key, { productId: e.target.value })}
              >
                <option value="">— chọn sản phẩm —</option>
                {products.map((product) => (
                  <option key={product.id} value={product.id}>
                    {product.sku} · {product.name} · {formatMoney(product.price)}
                  </option>
                ))}
              </select>
              <input
                type="number"
                className="narrow"
                min="1"
                value={line.quantity}
                onChange={(e) => updateLine(line.key, { quantity: e.target.value })}
              />
              <button type="button" className="small" onClick={() => removeLine(line.key)} disabled={lines.length === 1}>
                Xóa dòng
              </button>
            </div>
          ))}

          <div className="actions-row">
            <button type="button" onClick={addLine}>
              + Thêm dòng
            </button>
            <button type="submit" className="primary" disabled={createBusy}>
              Tạo đơn
            </button>
            <span className="muted">Số lượng vượt tồn kho → 409 INSUFFICIENT_STOCK kèm danh sách thiếu hàng.</span>
          </div>
          <ErrorBox error={createError} />
          <ErrorBox error={lookupsError} />
        </form>
      </Card>

      <Card
        title="Danh sách đơn hàng"
        actions={
          <button type="button" onClick={() => void loadOrders()} disabled={listBusy}>
            Tải lại
          </button>
        }
      >
        <form className="toolbar" onSubmit={onSearch}>
          <Field label="User">
            <select value={draft.userId} onChange={(e) => setDraft({ ...draft, userId: e.target.value })}>
              <option value="">Tất cả</option>
              {users.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.email}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Trạng thái">
            <select value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value as OrderStatus | '' })}>
              <option value="">Tất cả</option>
              {ORDER_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </Field>
          <button type="submit">Lọc</button>
        </form>

        <ErrorBox error={listError} />

        <table>
          <thead>
            <tr>
              <th>Mã đơn</th>
              <th>ID</th>
              <th>User</th>
              <th>Trạng thái</th>
              <th className="num">Tổng tiền</th>
              <th>Tạo lúc</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data?.items.map((order) => (
              <tr key={order.id} className={order.id === selectedId ? 'selected' : undefined}>
                <td>{order.orderNumber}</td>
                <td>
                  <ShortId id={order.id} />
                </td>
                <td>{userLabel(order.userId)}</td>
                <td>
                  <StatusBadge value={order.status} />
                </td>
                <td className="num">{formatMoney(order.totalAmount, order.currency)}</td>
                <td>{formatDate(order.createdAt)}</td>
                <td>
                  <button type="button" className="small" onClick={() => setSelectedId(order.id)}>
                    Chi tiết
                  </button>
                </td>
              </tr>
            ))}
            {data && data.items.length === 0 ? (
              <tr>
                <td colSpan={7} className="muted">
                  Không có đơn hàng nào.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
        {data ? (
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        ) : null}
      </Card>

      {selectedId ? (
        <OrderDetail key={selectedId} orderId={selectedId} onChanged={() => void loadOrders()} onClose={() => setSelectedId(null)} />
      ) : null}
    </>
  )
}

// ---- Chi tiết đơn + thanh toán --------------------------------------------------------------

function OrderDetail({ orderId, onChanged, onClose }: { orderId: string; onChanged: () => void; onClose: () => void }) {
  const toast = useToast()

  const [order, setOrder] = useState<OrderView | null>(null)
  const [payments, setPayments] = useState<PaymentView[]>([])

  const [provider, setProvider] = useState('MOCK')
  const [idempotencyKey, setIdempotencyKey] = useState(() => newId())
  const [lastKey, setLastKey] = useState<string | null>(null)
  const [providerReference, setProviderReference] = useState(() => `ref-${newId().slice(0, 8)}`)
  const [failureCode, setFailureCode] = useState('CARD_DECLINED')

  const { run: runLoad, busy: loadBusy, error: loadError } = useAction()
  const { run: runOrder, error: orderError } = useAction()
  const { run: runPayment, error: paymentError } = useAction()

  const load = useCallback(async () => {
    const result = await runLoad(() => Promise.all([ordersApi.get(orderId), paymentsApi.listByOrder(orderId)]))
    if (result) {
      setOrder(result[0])
      setPayments(result[1])
    }
  }, [runLoad, orderId])

  useEffect(() => {
    void load()
  }, [load])

  const afterChange = async (message: string) => {
    toast.notify(message)
    await load()
    onChanged()
  }

  const orderAction = async (kind: 'cancel' | 'complete') => {
    const result = await runOrder(() => (kind === 'cancel' ? ordersApi.cancel(orderId) : ordersApi.complete(orderId)))
    if (result) await afterChange(kind === 'cancel' ? `Đã hủy đơn ${result.orderNumber}` : `Đã hoàn tất đơn ${result.orderNumber}`)
  }

  const createPayment = async (key: string) => {
    const result = await runPayment(() => paymentsApi.create(orderId, provider, key))
    if (result) {
      setLastKey(key)
      setIdempotencyKey(newId())
      await afterChange(
        result.status === 201
          ? `201: tạo payment #${result.data.attemptNumber}`
          : `200: replay Idempotency-Key, trả lại payment #${result.data.attemptNumber} cũ`,
      )
    }
  }

  const paymentAction = async (label: string, action: () => Promise<PaymentView>) => {
    const result = await runPayment(action)
    if (result) await afterChange(`${label}: payment #${result.attemptNumber} → ${result.status}`)
  }

  return (
    <Card
      title={order ? `Đơn ${order.orderNumber}` : 'Chi tiết đơn'}
      actions={
        <>
          <button type="button" onClick={() => void load()} disabled={loadBusy}>
            Tải lại
          </button>
          <button type="button" onClick={onClose}>
            Đóng
          </button>
        </>
      }
    >
      <ErrorBox error={loadError} />
      {order ? (
        <>
          <dl className="detail-grid">
            <dt>ID</dt>
            <dd>
              <code>{order.id}</code>
            </dd>
            <dt>User</dt>
            <dd>
              <code>{order.userId}</code>
            </dd>
            <dt>Trạng thái</dt>
            <dd>
              <StatusBadge value={order.status} />
            </dd>
            <dt>Tổng tiền</dt>
            <dd>{formatMoney(order.totalAmount, order.currency)}</dd>
            <dt>Tạo / cập nhật</dt>
            <dd>
              {formatDate(order.createdAt)} / {formatDate(order.updatedAt)}
            </dd>
          </dl>

          <table>
            <thead>
              <tr>
                <th>SKU</th>
                <th>Tên</th>
                <th className="num">Đơn giá (snapshot)</th>
                <th className="num">SL</th>
                <th className="num">Thành tiền</th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((item) => (
                <tr key={item.productId}>
                  <td>{item.sku}</td>
                  <td>{item.name}</td>
                  <td className="num">{formatMoney(item.unitPrice, order.currency)}</td>
                  <td className="num">{item.quantity}</td>
                  <td className="num">{formatMoney(item.lineTotal, order.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="actions-row">
            <button type="button" className="danger" onClick={() => void orderAction('cancel')}>
              Hủy đơn
            </button>
            <button type="button" onClick={() => void orderAction('complete')}>
              Hoàn tất đơn
            </button>
            <span className="muted">Hủy đơn đã thanh toán → payment SUCCEEDED chuyển REFUNDED; hoàn tất chỉ khi CONFIRMED.</span>
          </div>
          <ErrorBox error={orderError} />

          <h3>Thanh toán</h3>
          <div className="form-grid">
            <Field label="Provider">
              <input value={provider} onChange={(e) => setProvider(e.target.value)} />
            </Field>
            <Field label="Idempotency-Key" hint="Cùng key + cùng provider → 200 (replay); khác đơn/provider → 409">
              <input value={idempotencyKey} onChange={(e) => setIdempotencyKey(e.target.value)} />
            </Field>
          </div>
          <div className="actions-row">
            <button type="button" className="primary" onClick={() => void createPayment(idempotencyKey)}>
              Tạo payment
            </button>
            <button type="button" onClick={() => setIdempotencyKey(newId())}>
              Key mới
            </button>
            <button type="button" disabled={!lastKey} onClick={() => lastKey && void createPayment(lastKey)}>
              Gửi lại key vừa dùng (replay)
            </button>
          </div>

          <div className="form-grid" style={{ marginTop: 10 }}>
            <Field label="providerReference (callback SUCCEEDED)">
              <input value={providerReference} onChange={(e) => setProviderReference(e.target.value)} />
            </Field>
            <Field label="failureCode (callback FAILED)">
              <input value={failureCode} onChange={(e) => setFailureCode(e.target.value)} />
            </Field>
          </div>
          <ErrorBox error={paymentError} />

          <table style={{ marginTop: 10 }}>
            <thead>
              <tr>
                <th>#</th>
                <th>ID</th>
                <th>Trạng thái</th>
                <th className="num">Số tiền</th>
                <th>Provider</th>
                <th>Tham chiếu / lỗi</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((payment) => (
                <tr key={payment.id}>
                  <td>{payment.attemptNumber}</td>
                  <td>
                    <ShortId id={payment.id} />
                  </td>
                  <td>
                    <StatusBadge value={payment.status} />
                  </td>
                  <td className="num">{formatMoney(payment.amount, payment.currency)}</td>
                  <td>{payment.provider}</td>
                  <td>{payment.providerReference ?? payment.failureCode ?? '—'}</td>
                  <td>
                    <span className="inline">
                      <button
                        type="button"
                        className="small"
                        onClick={() => void paymentAction('Submit', () => paymentsApi.submit(payment.id))}
                      >
                        Submit
                      </button>
                      <button
                        type="button"
                        className="small"
                        onClick={() =>
                          void paymentAction('Callback SUCCEEDED', () =>
                            paymentsApi.callback(payment.id, { outcome: 'SUCCEEDED', providerReference }),
                          )
                        }
                      >
                        Callback ✓
                      </button>
                      <button
                        type="button"
                        className="small"
                        onClick={() =>
                          void paymentAction('Callback FAILED', () =>
                            paymentsApi.callback(payment.id, { outcome: 'FAILED', failureCode }),
                          )
                        }
                      >
                        Callback ✗
                      </button>
                      <button
                        type="button"
                        className="small danger"
                        onClick={() => void paymentAction('Cancel', () => paymentsApi.cancel(payment.id))}
                      >
                        Hủy
                      </button>
                    </span>
                  </td>
                </tr>
              ))}
              {payments.length === 0 ? (
                <tr>
                  <td colSpan={7} className="muted">
                    Chưa có payment nào.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </>
      ) : null}
    </Card>
  )
}

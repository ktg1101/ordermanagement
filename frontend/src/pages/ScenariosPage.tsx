import { useState } from 'react'
import { ApiError, asApiError } from '../api/client'
import { inventoryApi, ordersApi, paymentsApi, productsApi, usersApi } from '../api/endpoints'
import type { OrderView, ProductView, UserView } from '../api/types'
import { Card, newId } from '../components/ui'

// ---- Bộ dựng kịch bản -----------------------------------------------------------------------

interface StepLog {
  ok: boolean
  text: string
}

class StepFailed extends Error {}

/** Công cụ kiểm tra dùng trong kịch bản: mỗi lời gọi ghi một bước ✓, hoặc ném StepFailed (✗). */
interface Steps {
  ok: (text: string) => void
  eq: <V extends string | number | boolean>(actual: V, expected: V, text: string) => void
  check: (condition: boolean, text: string) => void
  /** Mong đợi request thất bại đúng mã HTTP và `code`. */
  fails: (request: Promise<unknown>, status: number, code: string, text: string) => Promise<ApiError>
}

function makeSteps(push: (step: StepLog) => void): Steps {
  return {
    ok: (text) => push({ ok: true, text }),
    eq: (actual, expected, text) => {
      if (actual !== expected) throw new StepFailed(`${text} — mong đợi ${String(expected)}, nhận ${String(actual)}`)
      push({ ok: true, text: `${text} (= ${String(expected)})` })
    },
    check: (condition, text) => {
      if (!condition) throw new StepFailed(text)
      push({ ok: true, text })
    },
    fails: async (request, status, code, text) => {
      try {
        await request
      } catch (caught) {
        const error = asApiError(caught)
        if (error.status !== status || error.code !== code) {
          throw new StepFailed(`${text} — mong đợi ${status} ${code}, nhận ${error.status} ${error.code}: ${error.message}`)
        }
        push({ ok: true, text: `${text} → ${status} ${code}` })
        return error
      }
      throw new StepFailed(`${text} — mong đợi lỗi ${status} ${code} nhưng request lại thành công`)
    },
  }
}

const uid = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`

// ---- Dữ liệu mẫu (tiền tố fe- / FE- để dễ nhận ra và lọc) -------------------------------------

const mkUser = (tag: string): Promise<UserView> =>
  usersApi.create({ email: `fe-${tag}-${uid()}@test.invalid`, password: 'Passw0rd!x', fullName: `FE ${tag}` })

async function mkProduct(tag: string, price: number, stock: number): Promise<ProductView> {
  const product = await productsApi.create({ sku: `FE-${tag}-${uid()}`.toUpperCase(), name: `FE ${tag}`, price })
  if (stock > 0) await inventoryApi.adjust(product.id, stock)
  return product
}

const mkOrder = (user: UserView, product: ProductView, quantity: number): Promise<OrderView> =>
  ordersApi.create({ userId: user.id, items: [{ productId: product.id, quantity }] })

async function stockOf(product: ProductView): Promise<string> {
  const inventory = await inventoryApi.get(product.id)
  return `${inventory.availableQuantity}/${inventory.reservedQuantity}`
}

// ---- Các kịch bản ---------------------------------------------------------------------------

interface Scenario {
  id: string
  title: string
  description: string
  run: (t: Steps) => Promise<void>
}

const SCENARIOS: Scenario[] = [
  {
    id: 'happy',
    title: '1. Luồng đặt hàng thành công',
    description: 'Tạo đơn → giữ hàng → payment → callback thành công → đơn CONFIRMED → hoàn tất → xuất kho.',
    run: async (t) => {
      const user = await mkUser('happy')
      t.ok(`Tạo user ${user.email}`)
      const product = await mkProduct('HAPPY', 100000, 10)
      t.ok(`Tạo sản phẩm ${product.sku} giá 100000, nhập kho 10`)

      const order = await mkOrder(user, product, 2)
      t.eq(order.status, 'PENDING', 'Đơn mới tạo')
      t.eq(Number(order.totalAmount), 200000, 'Tổng tiền 2 × 100000')
      t.eq(await stockOf(product), '8/2', 'Tồn kho available/reserved sau khi giữ hàng')

      const created = await paymentsApi.create(order.id, 'MOCK', newId())
      t.eq(created.status, 201, 'Tạo payment trả HTTP')
      t.eq(created.data.status, 'PENDING', 'Payment mới')
      t.eq(created.data.attemptNumber, 1, 'Lần thanh toán thứ')
      t.eq(Number(created.data.amount), 200000, 'Số tiền payment khớp tổng đơn')

      t.eq((await paymentsApi.submit(created.data.id)).status, 'PROCESSING', 'Submit payment')
      const paid = await paymentsApi.callback(created.data.id, { outcome: 'SUCCEEDED', providerReference: 'fe-ref-1' })
      t.eq(paid.status, 'SUCCEEDED', 'Callback thành công')
      t.eq((await ordersApi.get(order.id)).status, 'CONFIRMED', 'Đơn tự chuyển trạng thái')

      t.eq((await ordersApi.complete(order.id)).status, 'COMPLETED', 'Hoàn tất đơn')
      t.eq(await stockOf(product), '8/0', 'Hàng giữ được xuất kho (reserved về 0)')
    },
  },
  {
    id: 'insufficient',
    title: '2. Không đủ tồn kho',
    description: 'Đặt vượt tồn kho phải bị từ chối, liệt kê hàng thiếu, và không làm thay đổi kho.',
    run: async (t) => {
      const user = await mkUser('stock')
      const product = await mkProduct('STOCK', 50000, 3)
      t.ok(`Tạo sản phẩm ${product.sku}, tồn kho 3`)

      const error = await t.fails(mkOrder(user, product, 5), 409, 'INSUFFICIENT_STOCK', 'Đặt 5 khi chỉ có 3')
      const shortages = error.problem?.shortages ?? []
      t.check(
        shortages.length === 1 && shortages[0].productId === product.id && shortages[0].requestedQuantity === 5,
        'Phản hồi liệt kê đúng sản phẩm thiếu và số lượng yêu cầu',
      )
      t.eq(await stockOf(product), '3/0', 'Kho không đổi sau khi bị từ chối (rollback)')
    },
  },
  {
    id: 'cancel-refund',
    title: '3. Hủy đơn đã thanh toán → hoàn tiền & trả hàng',
    description: 'Payment SUCCEEDED chuyển REFUNDED, hàng giữ được trả về kho trong cùng transaction.',
    run: async (t) => {
      const user = await mkUser('refund')
      const product = await mkProduct('REFUND', 70000, 5)
      const order = await mkOrder(user, product, 3)
      t.eq(await stockOf(product), '2/3', 'Sau khi đặt 3')

      const payment = (await paymentsApi.create(order.id, 'MOCK', newId())).data
      await paymentsApi.submit(payment.id)
      await paymentsApi.callback(payment.id, { outcome: 'SUCCEEDED', providerReference: 'fe-ref-2' })
      t.eq((await ordersApi.get(order.id)).status, 'CONFIRMED', 'Đơn đã thanh toán')

      t.eq((await ordersApi.cancel(order.id)).status, 'CANCELLED', 'Hủy đơn')
      const payments = await paymentsApi.listByOrder(order.id)
      t.eq(payments[0].status, 'REFUNDED', 'Payment được hoàn tiền')
      t.eq(await stockOf(product), '5/0', 'Hàng được trả về kho')

      await t.fails(ordersApi.cancel(order.id), 409, 'ORDER_INVALID_STATE', 'Hủy lại đơn đã hủy')
      await t.fails(ordersApi.complete(order.id), 409, 'ORDER_INVALID_STATE', 'Hoàn tất đơn đã hủy')
    },
  },
  {
    id: 'cancel-unpaid',
    title: '4. Hủy đơn chưa thanh toán',
    description: 'Payment đang hoạt động bị hủy cùng đơn; hàng giữ được trả lại.',
    run: async (t) => {
      const user = await mkUser('cancel')
      const product = await mkProduct('CANCEL', 20000, 4)
      const order = await mkOrder(user, product, 4)
      t.eq(await stockOf(product), '0/4', 'Giữ hết hàng')

      const payment = (await paymentsApi.create(order.id, 'MOCK', newId())).data
      await paymentsApi.submit(payment.id)

      t.eq((await ordersApi.cancel(order.id)).status, 'CANCELLED', 'Hủy đơn khi payment đang PROCESSING')
      t.eq((await paymentsApi.get(payment.id)).status, 'CANCELLED', 'Payment bị hủy theo (qua OrderCancelledEvent)')
      t.eq(await stockOf(product), '4/0', 'Hàng được trả về kho')
      await t.fails(paymentsApi.create(order.id, 'MOCK', newId()), 409, 'ORDER_NOT_PAYABLE', 'Tạo payment cho đơn đã hủy')
    },
  },
  {
    id: 'retry',
    title: '5. Thanh toán thất bại rồi thử lại',
    description: 'Payment FAILED không hủy đơn; hàng vẫn giữ; payment mới (lần 2) thành công.',
    run: async (t) => {
      const user = await mkUser('retry')
      const product = await mkProduct('RETRY', 30000, 6)
      const order = await mkOrder(user, product, 2)

      const first = (await paymentsApi.create(order.id, 'MOCK', newId())).data
      await paymentsApi.submit(first.id)
      const failed = await paymentsApi.callback(first.id, { outcome: 'FAILED', failureCode: 'CARD_DECLINED' })
      t.eq(failed.status, 'FAILED', 'Callback thất bại')
      t.eq(failed.failureCode ?? '', 'CARD_DECLINED', 'Lưu mã lỗi')
      t.eq((await ordersApi.get(order.id)).status, 'PENDING', 'Đơn vẫn PENDING')
      t.eq(await stockOf(product), '4/2', 'Hàng vẫn đang được giữ')

      const second = await paymentsApi.create(order.id, 'MOCK', newId())
      t.eq(second.status, 201, 'Tạo payment mới')
      t.eq(second.data.attemptNumber, 2, 'Là lần thanh toán thứ')
      await t.fails(paymentsApi.create(order.id, 'MOCK', newId()), 409, 'PAYMENT_IN_PROGRESS', 'Tạo thêm payment khi payment 2 đang mở')

      await paymentsApi.submit(second.data.id)
      const paid = await paymentsApi.callback(second.data.id, { outcome: 'SUCCEEDED', providerReference: 'fe-ref-3' })
      t.eq(paid.status, 'SUCCEEDED', 'Lần 2 thành công')
      t.eq((await ordersApi.get(order.id)).status, 'CONFIRMED', 'Đơn CONFIRMED')

      const again = await paymentsApi.callback(second.data.id, { outcome: 'SUCCEEDED', providerReference: 'fe-ref-3' })
      t.eq(again.status, 'SUCCEEDED', 'Gửi lại callback (idempotent) không lỗi')
    },
  },
  {
    id: 'idempotency',
    title: '6. Idempotency-Key khi tạo payment',
    description: 'Cùng key → 200 và trả lại payment cũ; dùng key cho request khác → 409.',
    run: async (t) => {
      const user = await mkUser('idem')
      const product = await mkProduct('IDEM', 10000, 10)
      const order = await mkOrder(user, product, 1)
      const other = await mkOrder(user, product, 1)

      const key = newId()
      const first = await paymentsApi.create(order.id, 'MOCK', key)
      t.eq(first.status, 201, 'Lần đầu')
      const replay = await paymentsApi.create(order.id, 'MOCK', key)
      t.eq(replay.status, 200, 'Gửi lại cùng key')
      t.eq(replay.data.id, first.data.id, 'Trả về đúng payment ban đầu')
      t.eq((await paymentsApi.listByOrder(order.id)).length, 1, 'Chỉ có 1 payment được tạo')

      await t.fails(paymentsApi.create(order.id, 'OTHER', key), 409, 'IDEMPOTENCY_KEY_REUSED', 'Cùng key nhưng khác provider')
      await t.fails(paymentsApi.create(other.id, 'MOCK', key), 409, 'IDEMPOTENCY_KEY_REUSED', 'Cùng key nhưng cho đơn khác')
    },
  },
  {
    id: 'concurrency',
    title: '7. Đặt hàng đồng thời (chống bán vượt tồn kho)',
    description: '12 request cùng lúc cho sản phẩm tồn kho 5: đúng 5 thành công, 7 bị INSUFFICIENT_STOCK.',
    run: async (t) => {
      const user = await mkUser('race')
      const product = await mkProduct('RACE', 1000, 5)
      t.ok(`Tạo sản phẩm ${product.sku}, tồn kho 5`)

      const results = await Promise.allSettled(Array.from({ length: 12 }, () => mkOrder(user, product, 1)))
      const created = results.flatMap((result) => (result.status === 'fulfilled' ? [result.value] : []))
      const rejected = results.flatMap((result) => (result.status === 'rejected' ? [asApiError(result.reason)] : []))

      t.eq(created.length, 5, 'Số đơn tạo được')
      t.eq(rejected.filter((error) => error.status === 409 && error.code === 'INSUFFICIENT_STOCK').length, 7, 'Số request bị INSUFFICIENT_STOCK')
      t.eq(await stockOf(product), '0/5', 'Kho: available 0, reserved 5 (không âm, không vượt)')

      for (const order of created) await ordersApi.cancel(order.id)
      t.eq(await stockOf(product), '5/0', 'Hủy 5 đơn → kho trở về 5')
    },
  },
  {
    id: 'validation',
    title: '8. Validation, 404 và tham số sai',
    description: 'Lỗi 400/404/409 trả dạng problem+json với mã `code` ổn định.',
    run: async (t) => {
      const bad = await t.fails(
        usersApi.create({ email: 'not-an-email', password: 'short', fullName: '' }),
        400,
        'VALIDATION_FAILED',
        'Tạo user dữ liệu sai',
      )
      const fields = (bad.problem?.errors ?? []).map((item) => item.field)
      t.check(['email', 'password', 'fullName'].every((field) => fields.includes(field)), `Liệt kê lỗi từng trường (${fields.join(', ')})`)

      const user = await mkUser('dup')
      await t.fails(
        usersApi.create({ email: user.email, password: 'Passw0rd!x', fullName: 'Trùng' }),
        409,
        'EMAIL_ALREADY_EXISTS',
        'Tạo user trùng email',
      )

      const product = await mkProduct('DUP', 1000, 0)
      await t.fails(
        productsApi.create({ sku: product.sku.toLowerCase(), name: 'Trùng SKU', price: 1 }),
        409,
        'SKU_ALREADY_EXISTS',
        'Tạo sản phẩm trùng SKU (không phân biệt hoa/thường)',
      )
      await t.fails(productsApi.create({ sku: 'FE-NEG', name: 'Giá âm', price: -1 }), 400, 'VALIDATION_FAILED', 'Giá âm')

      const missing = '00000000-0000-4000-8000-000000000000'
      await t.fails(ordersApi.get(missing), 404, 'ORDER_NOT_FOUND', 'Đơn không tồn tại')
      await t.fails(usersApi.get(missing), 404, 'USER_NOT_FOUND', 'User không tồn tại')
      await t.fails(paymentsApi.get(missing), 404, 'PAYMENT_NOT_FOUND', 'Payment không tồn tại')
      await t.fails(usersApi.get('not-a-uuid'), 400, 'INVALID_PARAMETER', 'ID không phải UUID')
      await t.fails(
        ordersApi.create({ userId: user.id, items: [] }),
        400,
        'VALIDATION_FAILED',
        'Đơn không có dòng hàng',
      )
    },
  },
  {
    id: 'rules',
    title: '9. Quy tắc nghiệp vụ (422 / 409)',
    description: 'User bị khóa, sản phẩm ngừng bán, kho âm, chuyển trạng thái sai, callback thiếu dữ liệu.',
    run: async (t) => {
      const user = await mkUser('rules')
      const product = await mkProduct('RULES', 15000, 5)

      await usersApi.disable(user.id)
      await t.fails(mkOrder(user, product, 1), 422, 'USER_DISABLED', 'User bị khóa đặt hàng')
      await usersApi.enable(user.id)
      t.ok('Mở khóa user')

      await t.fails(inventoryApi.adjust(product.id, -100), 422, 'STOCK_CANNOT_BE_NEGATIVE', 'Điều chỉnh kho xuống âm')
      t.eq(await stockOf(product), '5/0', 'Kho không đổi')

      const order = await mkOrder(user, product, 1)
      await t.fails(ordersApi.complete(order.id), 409, 'ORDER_INVALID_STATE', 'Hoàn tất đơn chưa thanh toán')

      const payment = (await paymentsApi.create(order.id, 'MOCK', newId())).data
      await t.fails(
        paymentsApi.callback(payment.id, { outcome: 'SUCCEEDED' }),
        400,
        'PROVIDER_REFERENCE_REQUIRED',
        'Callback SUCCEEDED thiếu providerReference',
      )
      await t.fails(
        paymentsApi.callback(payment.id, { outcome: 'FAILED' }),
        400,
        'FAILURE_CODE_REQUIRED',
        'Callback FAILED thiếu failureCode',
      )
      await t.fails(
        paymentsApi.callback(payment.id, { outcome: 'SUCCEEDED', providerReference: 'x' }),
        409,
        'PAYMENT_INVALID_STATE',
        'Callback khi payment chưa ở PROCESSING',
      )

      await productsApi.deactivate(product.id)
      await t.fails(mkOrder(user, product, 1), 422, 'PRODUCT_NOT_AVAILABLE', 'Đặt sản phẩm đã ngừng bán')
    },
  },
]

// ---- Giao diện ------------------------------------------------------------------------------

type RunStatus = 'running' | 'passed' | 'failed'

interface ScenarioResult {
  status: RunStatus
  steps: StepLog[]
  ms: number
}

const RESULT_BADGE: Record<RunStatus, { label: string; className: string }> = {
  running: { label: 'ĐANG CHẠY', className: 'badge-warn' },
  passed: { label: 'ĐẠT', className: 'badge-ok' },
  failed: { label: 'LỖI', className: 'badge-bad' },
}

function describeFailure(error: unknown): string {
  if (error instanceof StepFailed) return error.message
  const api = asApiError(error)
  return `${api.code} (HTTP ${api.status || '—'}): ${api.message}`
}

export default function ScenariosPage() {
  const [results, setResults] = useState<Record<string, ScenarioResult>>({})
  const [running, setRunning] = useState(false)

  const runScenario = async (scenario: Scenario): Promise<boolean> => {
    const started = performance.now()
    setResults((current) => ({ ...current, [scenario.id]: { status: 'running', steps: [], ms: 0 } }))

    const push = (step: StepLog) =>
      setResults((current) => {
        const previous = current[scenario.id] ?? { status: 'running' as const, steps: [], ms: 0 }
        return { ...current, [scenario.id]: { ...previous, steps: [...previous.steps, step] } }
      })

    let status: RunStatus = 'passed'
    try {
      await scenario.run(makeSteps(push))
    } catch (caught) {
      status = 'failed'
      push({ ok: false, text: describeFailure(caught) })
    }

    const ms = Math.round(performance.now() - started)
    setResults((current) => ({ ...current, [scenario.id]: { ...current[scenario.id], status, ms } }))
    return status === 'passed'
  }

  const runOne = async (scenario: Scenario) => {
    setRunning(true)
    try {
      await runScenario(scenario)
    } finally {
      setRunning(false)
    }
  }

  const runAll = async () => {
    setRunning(true)
    try {
      for (const scenario of SCENARIOS) await runScenario(scenario)
    } finally {
      setRunning(false)
    }
  }

  const finished = Object.values(results).filter((result) => result.status !== 'running')
  const passed = finished.filter((result) => result.status === 'passed').length
  const failed = finished.filter((result) => result.status === 'failed').length

  return (
    <Card
      title="Kịch bản test nhanh"
      actions={
        <button type="button" className="primary" onClick={() => void runAll()} disabled={running}>
          Chạy tất cả
        </button>
      }
    >
      <p className="muted" style={{ marginTop: 0 }}>
        Mỗi kịch bản tự tạo dữ liệu riêng (user <code>fe-…@test.invalid</code>, sản phẩm <code>FE-…</code>), gọi API thật rồi
        kiểm tra kết quả từng bước. Dữ liệu tạo ra không bị xóa — xem lại ở các tab khác hoặc ở panel nhật ký request bên phải.
      </p>
      {finished.length > 0 ? (
        <p>
          Kết quả: <strong className="step-ok">{passed} đạt</strong>
          {failed > 0 ? (
            <>
              {' · '}
              <strong className="step-bad">{failed} lỗi</strong>
            </>
          ) : null}
        </p>
      ) : null}

      {SCENARIOS.map((scenario) => {
        const result = results[scenario.id]
        const badge = result ? RESULT_BADGE[result.status] : null
        return (
          <div key={scenario.id} className="scenario">
            <div className="scenario-head">
              <div>
                <h3>{scenario.title}</h3>
                <span className="muted">{scenario.description}</span>
              </div>
              <span className="inline">
                {badge ? <span className={`badge ${badge.className}`}>{badge.label}</span> : null}
                {result && result.status !== 'running' ? <span className="muted">{result.ms} ms</span> : null}
                <button type="button" onClick={() => void runOne(scenario)} disabled={running}>
                  Chạy
                </button>
              </span>
            </div>
            {result ? (
              <ul className="steps">
                {result.steps.map((step, index) => (
                  <li key={index} className={step.ok ? 'step-ok' : 'step-bad'}>
                    <span>{step.ok ? '✓' : '✗'}</span>
                    <span>{step.text}</span>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        )
      })}
    </Card>
  )
}

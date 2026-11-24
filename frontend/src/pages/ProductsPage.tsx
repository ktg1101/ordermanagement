import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { inventoryApi, productsApi } from '../api/endpoints'
import type { InventoryView, PageResponse, ProductView } from '../api/types'
import { Card, ErrorBox, Field, Pagination, ShortId, StatusBadge, formatMoney, useToast } from '../components/ui'
import { useAction } from '../hooks/useAction'

const PAGE_SIZE = 10

interface EditState {
  id: string
  name: string
  description: string
  price: string
  active: boolean
}

export default function ProductsPage() {
  const toast = useToast()

  const [filters, setFilters] = useState<{ q: string; active: '' | 'true' | 'false' }>({ q: '', active: '' })
  const [draft, setDraft] = useState(filters)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<ProductView> | null>(null)
  const [stock, setStock] = useState<Record<string, InventoryView | null>>({})
  const [delta, setDelta] = useState<Record<string, string>>({})

  const [sku, setSku] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [price, setPrice] = useState('50000')

  const [edit, setEdit] = useState<EditState | null>(null)

  const { run: runList, busy: listBusy, error: listError } = useAction()
  const { run: runCreate, busy: createBusy, error: createError } = useAction()
  const { run: runRow, error: rowError } = useAction()

  const load = useCallback(async () => {
    const result = await runList(() =>
      productsApi.list({
        q: filters.q,
        active: filters.active === '' ? '' : filters.active === 'true',
        page,
        size: PAGE_SIZE,
      }),
    )
    if (!result) return
    setData(result)
    // Tồn kho của từng dòng; lỗi từng dòng được bỏ qua (hiển thị "—").
    const entries = await Promise.all(
      result.items.map((product) =>
        inventoryApi
          .get(product.id, true)
          .then((view) => [product.id, view] as const)
          .catch(() => [product.id, null] as const),
      ),
    )
    setStock(Object.fromEntries(entries))
  }, [runList, filters, page])

  useEffect(() => {
    void load()
  }, [load])

  const onCreate = async (event: FormEvent) => {
    event.preventDefault()
    const created = await runCreate(() => productsApi.create({ sku, name, description, price: Number(price) }))
    if (created) {
      toast.notify(`Đã tạo sản phẩm ${created.sku}`)
      setSku('')
      setName('')
      setDescription('')
      if (page === 0) await load()
      else setPage(0)
    }
  }

  const onSearch = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    setFilters(draft)
  }

  const saveEdit = async () => {
    if (!edit) return
    const updated = await runRow(() =>
      productsApi.update(edit.id, {
        name: edit.name,
        description: edit.description,
        price: Number(edit.price),
        active: edit.active,
      }),
    )
    if (updated) {
      toast.notify('Đã cập nhật sản phẩm')
      setEdit(null)
      await load()
    }
  }

  const deactivate = async (product: ProductView) => {
    const done = await runRow(async () => {
      await productsApi.deactivate(product.id)
      return true
    })
    if (done) {
      toast.notify(`Đã ngừng bán ${product.sku}`)
      await load()
    }
  }

  const adjust = async (product: ProductView) => {
    const value = Number(delta[product.id] ?? '10')
    const updated = await runRow(() => inventoryApi.adjust(product.id, value))
    if (updated) {
      toast.notify(`Kho ${product.sku}: available ${updated.availableQuantity}, reserved ${updated.reservedQuantity}`)
      setStock((current) => ({ ...current, [product.id]: updated }))
    }
  }

  return (
    <>
      <Card title="Tạo sản phẩm">
        <form onSubmit={onCreate}>
          <div className="form-grid">
            <Field label="SKU" hint="Chữ/số, . _ - ; tự chuyển thành chữ hoa">
              <input value={sku} onChange={(e) => setSku(e.target.value)} placeholder="SKU-001" />
            </Field>
            <Field label="Tên">
              <input value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field label="Mô tả">
              <input value={description} onChange={(e) => setDescription(e.target.value)} />
            </Field>
            <Field label="Giá (VND)" hint="Tối đa 2 chữ số thập phân">
              <input type="number" min="0" step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} />
            </Field>
          </div>
          <div className="actions-row">
            <button type="submit" className="primary" disabled={createBusy}>
              Tạo sản phẩm
            </button>
          </div>
          <ErrorBox error={createError} />
        </form>
      </Card>

      <Card
        title="Sản phẩm & tồn kho"
        actions={
          <button type="button" onClick={() => void load()} disabled={listBusy}>
            Tải lại
          </button>
        }
      >
        <form className="toolbar" onSubmit={onSearch}>
          <Field label="Tìm (tên / SKU)">
            <input value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
          </Field>
          <Field label="Đang bán">
            <select value={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.value as '' | 'true' | 'false' })}>
              <option value="">Tất cả</option>
              <option value="true">Đang bán</option>
              <option value="false">Ngừng bán</option>
            </select>
          </Field>
          <button type="submit">Tìm</button>
        </form>

        <ErrorBox error={listError} />
        <ErrorBox error={rowError} />

        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>SKU</th>
              <th>Tên</th>
              <th className="num">Giá</th>
              <th>Trạng thái</th>
              <th className="num">Available</th>
              <th className="num">Reserved</th>
              <th>Điều chỉnh kho (±)</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data?.items.map((product) =>
              edit?.id === product.id ? (
                <tr key={product.id} className="selected">
                  <td>
                    <ShortId id={product.id} />
                  </td>
                  <td>{product.sku}</td>
                  <td>
                    <input value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} />
                  </td>
                  <td>
                    <input
                      type="number"
                      step="0.01"
                      className="narrow"
                      value={edit.price}
                      onChange={(e) => setEdit({ ...edit, price: e.target.value })}
                    />
                  </td>
                  <td>
                    <label className="inline">
                      <input
                        type="checkbox"
                        checked={edit.active}
                        onChange={(e) => setEdit({ ...edit, active: e.target.checked })}
                      />
                      active
                    </label>
                  </td>
                  <td colSpan={3}>
                    <input
                      value={edit.description}
                      placeholder="Mô tả"
                      onChange={(e) => setEdit({ ...edit, description: e.target.value })}
                    />
                  </td>
                  <td>
                    <span className="inline">
                      <button type="button" className="small primary" onClick={() => void saveEdit()}>
                        Lưu
                      </button>
                      <button type="button" className="small" onClick={() => setEdit(null)}>
                        Hủy
                      </button>
                    </span>
                  </td>
                </tr>
              ) : (
                <tr key={product.id}>
                  <td>
                    <ShortId id={product.id} />
                  </td>
                  <td>{product.sku}</td>
                  <td>{product.name}</td>
                  <td className="num">{formatMoney(product.price)}</td>
                  <td>
                    <StatusBadge value={product.active ? 'ACTIVE' : 'DISABLED'} />
                  </td>
                  <td className="num">{stock[product.id]?.availableQuantity ?? '—'}</td>
                  <td className="num">{stock[product.id]?.reservedQuantity ?? '—'}</td>
                  <td>
                    <span className="inline">
                      <input
                        type="number"
                        className="narrow"
                        value={delta[product.id] ?? '10'}
                        onChange={(e) => setDelta({ ...delta, [product.id]: e.target.value })}
                      />
                      <button type="button" className="small" onClick={() => void adjust(product)}>
                        Áp dụng
                      </button>
                    </span>
                  </td>
                  <td>
                    <span className="inline">
                      <button
                        type="button"
                        className="small"
                        onClick={() =>
                          setEdit({
                            id: product.id,
                            name: product.name,
                            description: product.description ?? '',
                            price: String(product.price),
                            active: product.active,
                          })
                        }
                      >
                        Sửa
                      </button>
                      <button
                        type="button"
                        className="small danger"
                        disabled={!product.active}
                        onClick={() => void deactivate(product)}
                      >
                        Ngừng bán
                      </button>
                    </span>
                  </td>
                </tr>
              ),
            )}
            {data && data.items.length === 0 ? (
              <tr>
                <td colSpan={9} className="muted">
                  Không có sản phẩm nào.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
        {data ? (
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        ) : null}
      </Card>
    </>
  )
}

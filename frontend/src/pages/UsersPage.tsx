import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { usersApi } from '../api/endpoints'
import type { PageResponse, UserStatus, UserView } from '../api/types'
import { Card, ErrorBox, Field, Pagination, ShortId, StatusBadge, formatDate, useToast } from '../components/ui'
import { useAction } from '../hooks/useAction'

const PAGE_SIZE = 10

export default function UsersPage() {
  const toast = useToast()

  // Bộ lọc đang áp dụng (chỉ đổi khi bấm "Tìm") và bản nháp trong ô nhập.
  const [filters, setFilters] = useState<{ q: string; status: UserStatus | '' }>({ q: '', status: '' })
  const [draft, setDraft] = useState(filters)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<UserView> | null>(null)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('Passw0rd!x')
  const [fullName, setFullName] = useState('')

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')

  const { run: runList, busy: listBusy, error: listError } = useAction()
  const { run: runCreate, busy: createBusy, error: createError } = useAction()
  const { run: runRow, error: rowError } = useAction()

  const load = useCallback(async () => {
    const result = await runList(() => usersApi.list({ q: filters.q, status: filters.status, page, size: PAGE_SIZE }))
    if (result) setData(result)
  }, [runList, filters, page])

  useEffect(() => {
    void load()
  }, [load])

  const onCreate = async (event: FormEvent) => {
    event.preventDefault()
    const created = await runCreate(() => usersApi.create({ email, password, fullName }))
    if (created) {
      toast.notify(`Đã tạo user ${created.email}`)
      setEmail('')
      setFullName('')
      if (page === 0) await load()
      else setPage(0)
    }
  }

  const onSearch = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    setFilters(draft)
  }

  const saveName = async (user: UserView) => {
    const updated = await runRow(() => usersApi.updateName(user.id, editName))
    if (updated) {
      toast.notify('Đã cập nhật họ tên')
      setEditingId(null)
      await load()
    }
  }

  const toggleStatus = async (user: UserView) => {
    const done = await runRow(async () => {
      if (user.status === 'ACTIVE') await usersApi.disable(user.id)
      else await usersApi.enable(user.id)
      return true
    })
    if (done) {
      toast.notify(user.status === 'ACTIVE' ? 'Đã khóa user' : 'Đã mở khóa user')
      await load()
    }
  }

  return (
    <>
      <Card title="Tạo user">
        <form onSubmit={onCreate}>
          <div className="form-grid">
            <Field label="Email">
              <input type="text" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="khoa@example.com" />
            </Field>
            <Field label="Mật khẩu" hint="8–72 ký tự">
              <input type="text" value={password} onChange={(e) => setPassword(e.target.value)} />
            </Field>
            <Field label="Họ tên">
              <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} />
            </Field>
          </div>
          <div className="actions-row">
            <button type="submit" className="primary" disabled={createBusy}>
              Tạo user
            </button>
            <span className="muted">Để trống ô để thử lỗi validation (400 VALIDATION_FAILED).</span>
          </div>
          <ErrorBox error={createError} />
        </form>
      </Card>

      <Card
        title="Danh sách user"
        actions={
          <button type="button" onClick={() => void load()} disabled={listBusy}>
            Tải lại
          </button>
        }
      >
        <form className="toolbar" onSubmit={onSearch}>
          <Field label="Tìm (email / họ tên)">
            <input type="text" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
          </Field>
          <Field label="Trạng thái">
            <select value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value as UserStatus | '' })}>
              <option value="">Tất cả</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="DISABLED">DISABLED</option>
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
              <th>Email</th>
              <th>Họ tên</th>
              <th>Trạng thái</th>
              <th>Role</th>
              <th>Tạo lúc</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data?.items.map((user) => (
              <tr key={user.id}>
                <td>
                  <ShortId id={user.id} />
                </td>
                <td>{user.email}</td>
                <td>
                  {editingId === user.id ? (
                    <span className="inline">
                      <input value={editName} onChange={(e) => setEditName(e.target.value)} />
                      <button type="button" className="small primary" onClick={() => void saveName(user)}>
                        Lưu
                      </button>
                      <button type="button" className="small" onClick={() => setEditingId(null)}>
                        Hủy
                      </button>
                    </span>
                  ) : (
                    user.fullName
                  )}
                </td>
                <td>
                  <StatusBadge value={user.status} />
                </td>
                <td>{user.roles.join(', ')}</td>
                <td>{formatDate(user.createdAt)}</td>
                <td>
                  <span className="inline">
                    <button
                      type="button"
                      className="small"
                      onClick={() => {
                        setEditingId(user.id)
                        setEditName(user.fullName)
                      }}
                    >
                      Sửa tên
                    </button>
                    <button
                      type="button"
                      className={user.status === 'ACTIVE' ? 'small danger' : 'small'}
                      onClick={() => void toggleStatus(user)}
                    >
                      {user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}
                    </button>
                  </span>
                </td>
              </tr>
            ))}
            {data && data.items.length === 0 ? (
              <tr>
                <td colSpan={7} className="muted">
                  Không có user nào.
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

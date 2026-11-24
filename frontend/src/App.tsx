import { useEffect, useState } from 'react'
import { healthApi } from './api/endpoints'
import RequestLog from './components/RequestLog'
import { ToastProvider } from './components/ui'
import OrdersPage from './pages/OrdersPage'
import ProductsPage from './pages/ProductsPage'
import ScenariosPage from './pages/ScenariosPage'
import UsersPage from './pages/UsersPage'

type TabId = 'scenarios' | 'users' | 'products' | 'orders'

const TABS: { id: TabId; label: string }[] = [
  { id: 'scenarios', label: 'Kịch bản test nhanh' },
  { id: 'users', label: 'Users' },
  { id: 'products', label: 'Sản phẩm & Kho' },
  { id: 'orders', label: 'Đơn hàng & Thanh toán' },
]

type Health = 'checking' | 'up' | 'down'

function useBackendHealth(): Health {
  const [health, setHealth] = useState<Health>('checking')
  useEffect(() => {
    let cancelled = false
    const check = async () => {
      try {
        const result = await healthApi.check()
        if (!cancelled) setHealth(result.status === 'UP' ? 'up' : 'down')
      } catch {
        if (!cancelled) setHealth('down')
      }
    }
    void check()
    const timer = window.setInterval(() => void check(), 10_000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [])
  return health
}

const HEALTH_LABEL: Record<Health, string> = {
  checking: 'Đang kiểm tra backend…',
  up: 'Backend: UP',
  down: 'Backend: không kết nối được',
}

export default function App() {
  const [tab, setTab] = useState<TabId>('scenarios')
  const health = useBackendHealth()

  return (
    <ToastProvider>
      <div className="app">
        <header className="topbar">
          <div>
            <h1>Order Management – Test Console</h1>
            <p className="muted">Gọi trực tiếp REST API /api/v1 (qua proxy tới backend)</p>
          </div>
          <span className={`health health-${health}`}>{HEALTH_LABEL[health]}</span>
        </header>

        <nav className="tabs" aria-label="Chức năng">
          {TABS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={item.id === tab ? 'tab tab-active' : 'tab'}
              onClick={() => setTab(item.id)}
            >
              {item.label}
            </button>
          ))}
        </nav>

        <div className="layout">
          <main className="main">
            {tab === 'scenarios' && <ScenariosPage />}
            {tab === 'users' && <UsersPage />}
            {tab === 'products' && <ProductsPage />}
            {tab === 'orders' && <OrdersPage />}
          </main>
          <RequestLog />
        </div>
      </div>
    </ToastProvider>
  )
}

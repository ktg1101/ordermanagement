import { createRoot } from 'react-dom/client'
import App from './App'
import './styles.css'

// Không bọc StrictMode: nó chạy effect hai lần ở dev và làm nhật ký request bị trùng lặp.
createRoot(document.getElementById('root')!).render(<App />)

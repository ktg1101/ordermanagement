import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// Trình duyệt chỉ gọi cùng origin (http://localhost:5173); dev server chuyển tiếp /api và /actuator
// sang backend nên không cần cấu hình CORS ở Spring Boot.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_API_TARGET || 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target, changeOrigin: true },
        '/actuator': { target, changeOrigin: true },
      },
    },
    preview: {
      port: 5173,
      proxy: {
        '/api': { target, changeOrigin: true },
        '/actuator': { target, changeOrigin: true },
      },
    },
  }
})

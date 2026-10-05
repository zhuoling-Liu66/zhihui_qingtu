import path from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 后端端口是 8080；这里原来错写成 8082，会导致所有 /api 请求
      // 打到不存在的服务上（表现为登录/列表全部网络错误）。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 上传的封面等静态资源同样由后端 8080 提供。
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

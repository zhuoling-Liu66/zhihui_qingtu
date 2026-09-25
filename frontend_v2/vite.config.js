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
      // ⚠️ 端口必须和后端 application.properties 里的 server.port 一致（当前是 8080）。
      // 这里原本写的是 8082，与后端对不上——表现为所有接口 ECONNREFUSED，
      // 排查时容易误以为是"后端没启动"或"跨域问题"，其实只是代理打错了端口。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 封面图走 <img src="/uploads/...">，所以 /uploads 也必须代理，
      // 否则图片全裂（后端的 TokenInterceptor 已放行 /uploads/**，不需要带 token）。
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

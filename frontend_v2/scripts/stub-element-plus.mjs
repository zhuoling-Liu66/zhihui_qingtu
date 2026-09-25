// 拦截 element-plus：只测请求拦截器，不需要 UI 库
export const ElMessage = {
  error: (m) => console.log(`[ElMessage.error] ${m}`),
  success: (m) => console.log(`[ElMessage.success] ${m}`),
  warning: (m) => console.log(`[ElMessage.warning] ${m}`),
}

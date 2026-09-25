/**
 * Node 侧加载 Vite 源码的解析 hook
 * ================================
 *
 * 作用：让 Node 能直接 `import` `frontend_v2/src` 下的**真实源码**，
 * 从而可以在没有浏览器的情况下验证 `api/http.js`、`composables/useSession.js`、
 * `api/platform.js` 这些"纯逻辑"文件的真实行为 —— 而不是靠读代码"看起来对"。
 *
 * 要解决两个差距：
 *   1. `@/xxx` 是 Vite 的别名（见 `vite.config.js`），Node 不认识；
 *      而且源码里的导入**不带扩展名**（`import http from './http'`），Node 原生 ESM 也不认。
 *   2. `element-plus` 是浏览器 UI 库，在 Node 里加载它既没必要也跑不起来，用 stub 顶掉。
 *
 * 用法（配合 `--import`）：
 *
 *     cd frontend_v2
 *     node --import ./scripts/node-vite-alias-hook.mjs scripts/verify-token-injection.mjs
 *
 * 注意：这只用于**开发期验证**，不参与打包，`vite build` 完全不受影响。
 */
import { registerHooks } from 'node:module'
import { pathToFileURL } from 'node:url'
import fs from 'node:fs'
import path from 'node:path'

const PROJECT_ROOT = process.cwd()
const SRC_ROOT = path.join(PROJECT_ROOT, 'src')
const STUB = pathToFileURL(path.join(PROJECT_ROOT, 'scripts/stub-element-plus.mjs')).href

/** `@/xxx` 指向的文件通常不带扩展名，这里按 Vite 的解析顺序补全 */
function resolveWithExtension(basePath) {
  const candidates = [basePath, `${basePath}.js`, `${basePath}.mjs`, path.join(basePath, 'index.js')]
  for (const candidate of candidates) {
    if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) {
      return candidate
    }
  }
  return basePath
}

registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === 'element-plus') {
      return { url: STUB, shortCircuit: true }
    }
    if (specifier.startsWith('@/')) {
      const target = resolveWithExtension(path.join(SRC_ROOT, specifier.slice(2)))
      return { url: pathToFileURL(target).href, shortCircuit: true }
    }
    // 相对导入的扩展名省略（platform.js 里写的是 './http'）也要补全。
    // Vite 会自动处理，Node 的原生 ESM 不会。
    if (specifier.startsWith('./') || specifier.startsWith('../')) {
      const parentDir = path.dirname(new URL(context.parentURL).pathname.replace(/^\/([A-Za-z]:)/, '$1'))
      const target = resolveWithExtension(path.join(parentDir, specifier))
      return { url: pathToFileURL(target).href, shortCircuit: true }
    }
    return nextResolve(specifier, context)
  },
})

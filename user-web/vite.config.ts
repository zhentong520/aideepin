import path from 'path'
import type { PluginOption } from 'vite'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

// 按包名拆分 vendor chunk（T2-03）
function manualChunks(id: string) {
  if (!id.includes('node_modules'))
    return
  if (id.includes('vue-flow'))
    return 'vendor-flow'
  // 精确按 node_modules 段匹配，避免 vue-demi / vueuc 等子串误命中
  if (/[\\/]node_modules[\\/](vue|vue-router|pinia|vue-i18n|@vue|@vueuse|@intlify)[\\/]/.test(id))
    return 'vendor-vue'
  if (/[\\/]node_modules[\\/](naive-ui|vueuc|@css-render|css-render|seemly|vdirs|treemate|vooks|@vicons|date-fns)[\\/]/.test(id))
    return 'vendor-naive'
  if (/[\\/]node_modules[\\/]highlight\.js[\\/]/.test(id))
    return 'vendor-hljs'
  // ⚠️ dompurify 不能放进 vendor-md：sanitize.ts 在入口静态链上，会把 markdown-it-katex→katex 拖进首屏
  if (id.includes('markdown-it') || id.includes('link-attributes'))
    return 'vendor-md'
  if (/[\\/]node_modules[\\/]katex[\\/]/.test(id))
    return 'vendor-katex'
  if (/[\\/]node_modules[\\/]cytoscape/.test(id))
    return 'vendor-cyto'
  if (id.includes('json-editor') || id.includes('jsoneditor'))
    return 'vendor-jsoneditor'
  if (/[\\/]node_modules[\\/]axios[\\/]/.test(id))
    return 'vendor-http'
  return 'vendor'
}

// T9-11：PWA 缓存策略——构建产物静态资源由 workbox precache 接管（cache-first，文件名带 hash，安全）；
// /api 一律 network-only——不缓存 API 响应，避免数据陈旧。开关：VITE_GLOB_APP_PWA（生产构建生效，开发环境始终关闭）
function setupPlugins(env: ImportMetaEnv): PluginOption[] {
  return [
    vue(),
    env.VITE_GLOB_APP_PWA === 'true' && VitePWA({
      injectRegister: 'auto',
      registerType: 'autoUpdate',
      manifest: {
        name: 'AISelf',
        short_name: 'AISelf',
        start_url: '.',
        display: 'standalone',
        theme_color: '#2d8cf0',
        background_color: '#ffffff',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2}'],
        navigateFallback: 'index.html',
        // 导航回退只服务前端路由；API 请求绝不能落到 index.html，也不做任何缓存
        navigateFallbackDenylist: [/^\/api\//],
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
      },
    }),
  ]
}

export default defineConfig((env) => {
  const viteEnv = loadEnv(env.mode, process.cwd()) as unknown as ImportMetaEnv

  return {
    resolve: {
      alias: {
        '@': path.resolve(process.cwd(), 'src'),
        // 共享包 SFC 供应商模块（S2-07 步骤④）：共享包内不裸导入 vue/naive-ui，
        // 统一经本别名拿到宿主实现（与 admin 端同名别名对齐）
        '#wf-shared-vendor': path.resolve(process.cwd(), 'src/wf-shared-vendor.ts'),
      },
    },
    plugins: setupPlugins(viteEnv),
    server: {
      host: '0.0.0.0',
      port: 1002,
      open: false,
      proxy: {
        '/api': {
          target: viteEnv.VITE_APP_API_BASE_URL,
          changeOrigin: true, // 允许跨域
          // 坑（T5-12 修）：原 rewrite 用 path.replace('/api/', '/')，
          // ① 未锚定开头——路径中后段出现 "/api/" 会被误剥（如 /api/proxy/api/x → /proxy/x 丢失首段语义前的正确性尚可，但 /xapi/y 之类含 "/api/" 子串的路径会错剥）；
          // ② /api 精确路径（无尾斜杠）不被剥前缀，原样打到后端 404。
          // 后端 context-path=/，这里统一剥掉开头的 /api 段。
          rewrite: path => path.replace(/^\/api(?=\/|$)/, ''),
        },
      },
    },
    build: {
      reportCompressedSize: false,
      sourcemap: false,
      chunkSizeWarningLimit: 700,
      commonjsOptions: {
        ignoreTryCatch: false,
      },
      rollupOptions: {
        output: {
          manualChunks,
        },
      },
    },
  }
})

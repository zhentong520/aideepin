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

function setupPlugins(env: ImportMetaEnv): PluginOption[] {
  return [
    vue(),
    env.VITE_GLOB_APP_PWA === 'true' && VitePWA({
      injectRegister: 'auto',
      manifest: {
        name: 'chatGPT',
        short_name: 'chatGPT',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
        ],
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

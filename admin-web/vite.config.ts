import type { UserConfig, ConfigEnv } from 'vite';
import { loadEnv } from 'vite';
import { resolve } from 'path';
import { wrapperEnv } from './build/utils';
import { createVitePlugins } from './build/vite/plugin';
import { createProxy } from './build/vite/proxy';
import { OUTPUT_DIR } from './build/constant';
import pkg from './package.json';
import { format } from 'date-fns';
const { dependencies, devDependencies, name, version } = pkg;

const __APP_INFO__ = {
  pkg: { dependencies, devDependencies, name, version },
  lastBuildTime: format(new Date(), 'yyyy-MM-dd HH:mm:ss'),
};

function pathResolve(dir: string) {
  return resolve(process.cwd(), '.', dir);
}

// 按包名拆分 vendor chunk（T2-03）
function manualChunks(id: string) {
  if (!id.includes('node_modules')) return;
  if (id.includes('vue-flow')) return 'vendor-flow';
  // 精确按 node_modules 段匹配，避免 vue-demi / vueuc 等子串误命中
  if (/[\\/]node_modules[\\/](vue|vue-router|pinia|vue-i18n|@vue|@vueuse|@intlify|vue-types)[\\/]/.test(id)) return 'vendor-vue';
  if (/[\\/]node_modules[\\/](naive-ui|vueuc|@css-render|css-render|seemly|vdirs|treemate|vooks|@vicons|date-fns|@iconify)[\\/]/.test(id)) return 'vendor-naive';
  if (/[\\/]node_modules[\\/]echarts[\\/]/.test(id)) return 'vendor-echarts';
  if (/[\\/]node_modules[\\/]highlight\.js[\\/]/.test(id)) return 'vendor-hljs';
  // ⚠️ dompurify 不能放进 vendor-md：sanitize.ts 在入口静态链上，会把 markdown-it-katex→katex 拖进首屏
  if (id.includes('markdown-it') || id.includes('link-attributes')) return 'vendor-md';
  if (/[\\/]node_modules[\\/]katex[\\/]/.test(id)) return 'vendor-katex';
  if (id.includes('json-editor') || id.includes('jsoneditor')) return 'vendor-jsoneditor';
  if (id.includes('vuedraggable') || id.includes('sortablejs')) return 'vendor-drag';
  if (/[\\/]node_modules[\\/]axios[\\/]/.test(id)) return 'vendor-http';
  return 'vendor';
}

export default ({ command, mode }: ConfigEnv): UserConfig => {
  const root = process.cwd();
  const env = loadEnv(mode, root);
  const viteEnv = wrapperEnv(env);
  const { VITE_PUBLIC_PATH, VITE_PORT } = viteEnv;
  const isBuild = command === 'build';
  return {
    base: VITE_PUBLIC_PATH,
    esbuild: {},
    resolve: {
      alias: [
        {
          find: /\/#\//,
          replacement: pathResolve('types') + '/',
        },
        {
          find: '@',
          replacement: pathResolve('src') + '/',
        },
      ],
      dedupe: ['vue'],
    },
    plugins: createVitePlugins(viteEnv, isBuild),
    define: {
      __APP_INFO__: JSON.stringify(__APP_INFO__),
    },
    server: {
      host: true,
      port: VITE_PORT,
      // 开发代理：从 .env.development 的 VITE_PROXY 读取（createProxy 会校验并跳过无效条目），
      // 生产构建不加载 .env.development，VITE_PROXY 为空数组即无代理，由 nginx/网关负责转发。
      proxy: createProxy(viteEnv.VITE_PROXY || []),
      hmr: {
        overlay: true,
      },
      watch: {
        usePolling: true,
        interval: 1000,
      },
    },
    optimizeDeps: {
      include: [],
      exclude: ['vue-demi'],
    },
    build: {
      target: 'es2015',
      cssTarget: 'chrome80',
      outDir: OUTPUT_DIR,
      reportCompressedSize: false,
      chunkSizeWarningLimit: 700,
      rollupOptions: {
        output: {
          manualChunks,
        },
      },
    },
  };
};

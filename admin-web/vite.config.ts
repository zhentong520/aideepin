import type { UserConfig, ConfigEnv } from 'vite';
import { loadEnv } from 'vite';
import { resolve } from 'path';
import { wrapperEnv } from './build/utils';
import { createVitePlugins } from './build/vite/plugin';
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

export default ({ command, mode }: ConfigEnv): UserConfig => {
  const root = process.cwd();
  const env = loadEnv(mode, root);
  const viteEnv = wrapperEnv(env);
  const { VITE_PUBLIC_PATH, VITE_PORT, VITE_GLOB_PROD_MOCK } = viteEnv;
  const prodMock = VITE_GLOB_PROD_MOCK;
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
    plugins: createVitePlugins(viteEnv, isBuild, prodMock),
    define: {
      __APP_INFO__: JSON.stringify(__APP_INFO__),
    },
    server: {
      host: true,
      port: VITE_PORT,
      // 开发代理：vite3 对 VITE_PROXY 环境变量的 JSON 解析在某些重启场景下会失效
      // （被当成字符串逐字符遍历，导致 /api 代理根本不注册）。这里直接硬编码到本地后端，
      // 避免依赖易碎的 env 解析。生产构建由 nginx/网关负责转发，不受此影响。
      proxy: {
        '/api': {
          target: 'http://localhost:9999',
          changeOrigin: true,
          ws: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
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
      chunkSizeWarningLimit: 2000,
    },
  };
};

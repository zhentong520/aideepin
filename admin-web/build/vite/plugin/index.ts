import type { Plugin, PluginOption } from 'vite';
import Components from 'unplugin-vue-components/vite';
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers';

import vue from '@vitejs/plugin-vue';
import vueJsx from '@vitejs/plugin-vue-jsx';

import { VitePWA } from 'vite-plugin-pwa';

import { configHtmlPlugin } from './html';
import { configMockPlugin } from './mock';
import { configCompressPlugin } from './compress';

// T9-11：PWA 插件（仅生产构建注入；开发环境不生成 SW，避免干扰调试）
// 缓存策略：构建产物静态资源由 workbox precache 接管（cache-first，文件名带 hash，安全）；
// /api 一律 network-only——不缓存 API 响应，避免数据陈旧。
function configPwaPlugin(viteEnv: ViteEnv) {
  return VitePWA({
    injectRegister: 'auto',
    registerType: 'autoUpdate',
    manifest: {
      name: viteEnv.VITE_GLOB_APP_TITLE,
      short_name: viteEnv.VITE_GLOB_APP_SHORT_NAME,
      description: viteEnv.VITE_GLOB_APP_TITLE,
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
  });
}

export function createVitePlugins(viteEnv: ViteEnv, isBuild: boolean, prodMock: boolean) {
  const { VITE_USE_MOCK, VITE_BUILD_COMPRESS, VITE_BUILD_COMPRESS_DELETE_ORIGIN_FILE } = viteEnv;

  const vitePlugins: (Plugin | Plugin[] | PluginOption[])[] = [
    // have to
    vue(),
    // have to
    vueJsx(),

    // 按需引入NaiveUi且自动创建组件声明
    Components({
      dts: true,
      resolvers: [NaiveUiResolver()],
    }),
  ];

  // vite-plugin-html
  vitePlugins.push(configHtmlPlugin(viteEnv, isBuild));

  // vite-plugin-mock
  VITE_USE_MOCK && vitePlugins.push(configMockPlugin(isBuild, prodMock));

  // T9-11：PWA 开关（VITE_GLOB_APP_PWA），仅生产构建生效
  if (isBuild && viteEnv.VITE_GLOB_APP_PWA) {
    vitePlugins.push(configPwaPlugin(viteEnv));
  }

  if (isBuild) {
    // rollup-plugin-gzip
    vitePlugins.push(
      configCompressPlugin(VITE_BUILD_COMPRESS, VITE_BUILD_COMPRESS_DELETE_ORIGIN_FILE)
    );
  }

  return vitePlugins;
}

import { createApp } from 'vue'
import masonry from 'vue-next-masonry'
import App from './App.vue'
import { setupI18n } from './locales'
import { setupAssets, setupScrollbarStyle } from './plugins'
import { setupStore } from './store'
import { setupRouter } from './router'
import { setupLocalIcons } from './utils/icons-local'

async function bootstrap() {
  const app = createApp(App)
  // 图标本地注册：消除 Iconify 运行时外网请求（unisvg 等不可达会报错）
  setupLocalIcons()
  setupAssets()

  setupScrollbarStyle()

  setupStore(app)

  setupI18n(app)

  masonry.install(app, null)

  await setupRouter(app)

  app.mount('#app')
}

bootstrap()

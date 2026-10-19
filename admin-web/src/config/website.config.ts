import logoImage from '@/assets/images/logo.png'
import loginImage from '@/assets/images/account-logo.png'

export const websiteConfig = Object.freeze({
  title: 'AISelf Admin',
  logo: logoImage,
  loginImage: loginImage,
  loginDesc: 'AISelf Admin',
  // 「去官网」链接：由环境变量 VITE_GLOB_SITE_URL 控制（见 .env / .env.development / .env.production）
  // 开发环境默认指向本项目 user-web（localhost:1002），生产环境默认同源根路径 /
  siteUrl: import.meta.env.VITE_GLOB_SITE_URL || '/',
})

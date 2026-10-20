/**
 * T4-01：design token 单一来源消费
 * 颜色走 CSS 变量（var(--adi-*)），暗色模式由 html.dark 下的变量值自动切换（T4-02/T4-04）
 * @type {import('tailwindcss').Config}
 */
const { brandScale, radius, space, shadow, zIndex, breakpoints } = require('../packages/aideepin-tokens/src/index.ts')

module.exports = {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{vue,ts,tsx}'],
  important: true,
  theme: {
    extend: {
      colors: {
        brand: brandScale,
        primary: 'var(--adi-primary)',
        success: 'var(--adi-success)',
        warning: 'var(--adi-warning)',
        danger: 'var(--adi-danger)',
        info: 'var(--adi-info)',
        'danger-text': 'var(--adi-danger-text)',
        border: 'var(--adi-border)',
        muted: 'var(--adi-text-muted)',
        'app-bg': 'var(--adi-bg)',
        'app-bg-subtle': 'var(--adi-bg-subtle)',
        'app-text': 'var(--adi-text)',
      },
      borderRadius: radius,
      spacing: Object.fromEntries(space.map(px => [`s${px}`, `${px}px`])),
      boxShadow: shadow,
      zIndex,
      screens: { ...breakpoints },
    },
  },
};

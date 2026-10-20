import type { GlobalThemeOverrides } from 'naive-ui'
import { computed, watch } from 'vue'
import { darkTheme, useOsTheme } from 'naive-ui'
import { tokens, brandScale } from 'aideepin-tokens'
import { useAppStore } from '@/store'

export function useTheme() {
  const appStore = useAppStore()

  const OsTheme = useOsTheme()

  const isDark = computed(() => {
    if (appStore.theme === 'auto')
      return OsTheme.value === 'dark'
    else
      return appStore.theme === 'dark'
  })

  const theme = computed(() => {
    return isDark.value ? darkTheme : undefined
  })

  // T4-01：themeOverrides 接 design token，isDark 时切 tokens.dark / tokens.light
  const themeOverrides = computed<GlobalThemeOverrides>(() => {
    const t = isDark.value ? tokens.dark : tokens.light
    return {
      common: {
        primaryColor: t.primary,
        primaryColorHover: brandScale[400],
        primaryColorPressed: brandScale[600],
        primaryColorSuppl: t.primary,
        successColor: t.success,
        warningColor: t.warning,
        errorColor: t.danger,
        infoColor: t.info,
        borderColor: t.border,
        textColorBase: t.text,
        bodyColor: t.bg,
        borderRadius: '8px',
      },
    }
  })

  watch(
    () => isDark.value,
    (dark) => {
      if (dark)
        document.documentElement.classList.add('dark')
      else
        document.documentElement.classList.remove('dark')
    },
    { immediate: true },
  )

  return { theme, themeOverrides }
}

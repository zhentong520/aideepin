<template>
  <NConfigProvider
    :locale="naiveLocalePair.locale"
    :theme="getDarkTheme"
    :theme-overrides="getThemeOverrides"
    :date-locale="naiveLocalePair.dateLocale"
  >
    <AppProvider>
      <RouterView />
    </AppProvider>
  </NConfigProvider>
</template>

<script lang="ts" setup>
  import { computed, watch } from 'vue'
  import { darkTheme } from 'naive-ui'
  import { tokens, brandScale } from 'aideepin-tokens'
  import { AppProvider } from '@/components/Application'
  import { useDesignSettingStore } from '@/store/modules/designSetting'
  import { useLocale } from '@/hooks/useLocale'

  const { naiveLocalePair } = useLocale()

  const designStore = useDesignSettingStore()

  // T4-01/T4-02：语义色按 isDark 切 tokens.dark / tokens.light；
  // 修复 hover/pressed 同值（原 lighten(appTheme,6) 两处相同，悬停与按下无视觉差）
  const getThemeOverrides = computed(() => {
    const appTheme = designStore.appTheme
    const t = designStore.darkTheme ? tokens.dark : tokens.light
    return {
      common: {
        primaryColor: appTheme,
        primaryColorHover: brandScale[400],
        primaryColorPressed: brandScale[600],
        primaryColorSuppl: appTheme,
        successColor: t.success,
        warningColor: t.warning,
        errorColor: t.danger,
        infoColor: t.info,
        borderColor: t.border,
        textColorBase: t.text,
        bodyColor: t.bg,
        borderRadius: '8px',
      },
      LoadingBar: {
        colorLoading: appTheme,
      },
    }
  })

  const getDarkTheme = computed(() => (designStore.darkTheme ? darkTheme : undefined))

  // T4-02 根因修复：admin 此前从未把 'dark' class 注入 documentElement，
  // 导致 tailwind darkMode:'class' 的 dark: 变体（23 处）全部失效
  watch(
    () => designStore.darkTheme,
    (dark) => {
      if (dark) document.documentElement.classList.add('dark')
      else document.documentElement.classList.remove('dark')
    },
    { immediate: true },
  )
</script>

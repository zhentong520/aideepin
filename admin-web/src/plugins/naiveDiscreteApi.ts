import * as NaiveUI from 'naive-ui'
import { computed } from 'vue'
import { tokens, brandScale } from 'aideepin-tokens'
import { useDesignSetting } from '@/store/modules/designSetting'

/**
 * 挂载 Naive-ui 脱离上下文的 API
 * 如果你想在 setup 外使用 useDialog、useMessage、useNotification、useLoadingBar，可以通过 createDiscreteApi 来构建对应的 API。
 * https://www.naiveui.com/zh-CN/dark/components/discrete
 */

export function setupNaiveDiscreteApi() {
  const designStore = useDesignSetting()

  // T4-01/T4-02：与 App.vue 保持一致——语义色按 isDark 切 tokens；
  // 修复 hover/pressed 同值 + 补齐缺失的 primaryColorSuppl
  const configProviderPropsRef = computed(() => {
    const t = designStore.darkTheme ? tokens.dark : tokens.light
    return {
      theme: designStore.darkTheme ? NaiveUI.darkTheme : undefined,
      themeOverrides: {
        common: {
          primaryColor: designStore.appTheme,
          primaryColorHover: brandScale[400],
          primaryColorPressed: brandScale[600],
          primaryColorSuppl: designStore.appTheme,
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
          colorLoading: designStore.appTheme,
        },
      },
    }
  })
  const { message, dialog, notification, loadingBar } = NaiveUI.createDiscreteApi(
    ['message', 'dialog', 'notification', 'loadingBar'],
    {
      configProviderProps: configProviderPropsRef,
    }
  )

  window['$message'] = message
  window['$dialog'] = dialog
  window['$notification'] = notification
  window['$loading'] = loadingBar
}

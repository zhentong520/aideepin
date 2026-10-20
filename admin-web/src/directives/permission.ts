import { ObjectDirective } from 'vue'
import { usePermission } from '@/hooks/web/usePermission'

export const permission: ObjectDirective = {
  mounted(el: HTMLButtonElement, binding: { value?: { action?: string[]; effect?: string } }) {
    if (binding.value == undefined) return
    const { action, effect } = binding.value as { action?: string[]; effect?: string }
    const { hasPermission } = usePermission()
    if (!hasPermission(action || [])) {
      if (effect == 'disabled') {
        el.disabled = true
        ;(el.style as unknown as Record<string, string>)['disabled'] = 'disabled'
        el.classList.add('n-button--disabled')
      } else {
        el.remove()
      }
    }
  },
}

<script lang="tsx">
  import { defineComponent, onBeforeMount } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import UxState from '@/components/UxState.vue'

  export default defineComponent({
    name: 'Redirect',
    setup() {
      const route = useRoute()
      const router = useRouter()
      onBeforeMount(() => {
        const { params, query } = route
        const { path } = params
        router.replace({
          path: '/' + (Array.isArray(path) ? path.join('/') : path),
          query,
        })
      })
      // T4-03：跳转占位页统一为骨架屏，消灭「白屏 + 无提示」
      return () => <UxState state="loading" skeletonRows={3} />
    },
  })
</script>

<script setup lang='ts'>
import AppSider from '@/components/common/AppSider.vue'
import List from './List.vue'

/**
 * 侧边栏现在只是「场景列表」的容器。
 *
 * 2026-10-05：场景与智能体都由管理员在管理后台配置，对所有用户共享。
 * 用户端因此**移除了「新建场景」入口**（原来那个弹窗等于让终端用户替管理员做配置），
 * 这里也不再挂 CreateConv —— 用户端只读场景列表。
 */
</script>

<template>
  <AppSider>
    <List />
  </AppSider>
</template>

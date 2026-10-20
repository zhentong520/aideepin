<template>
  <div class="h-full p-4 overflow-auto">
    <NTabs v-model:value="activeTab" type="line" @update:value="onTabChange">
      <NTabPane name="market" :tab="t('skill.market')">
        <NInput v-model:value="keyword" :placeholder="t('skill.searchPlaceholder')" clearable style="max-width: 320px" @keyup.enter="loadMarket">
          <template #prefix>
            <NIcon><Search12Regular /></NIcon>
          </template>
        </NInput>
        <div class="flex flex-col gap-3 mt-3">
          <NCard v-for="skill in marketList" :key="skill.uuid" size="small">
            <div class="flex items-start justify-between gap-3">
              <div class="flex-1 cursor-pointer" @click="showDetail(skill)">
                <div class="flex items-center gap-2">
                  <NTag type="info" size="small">{{ skill.name }}</NTag>
                  <span class="font-bold">{{ skill.title }}</span>
                  <NTag v-if="skill.hasScripts" type="success" size="small">scripts</NTag>
                  <NTag v-if="installedMap[skill.id]" size="small" type="primary">{{ t('skill.installed') }}</NTag>
                </div>
                <div class="text-sm text-gray-500 mt-1">{{ skill.description }}</div>
              </div>
              <NButton
                size="small"
                :type="installedMap[skill.id] ? 'default' : 'primary'"
                @click="toggleInstall(skill)"
              >
                {{ installedMap[skill.id] ? t('skill.installed') : t('skill.install') }}
              </NButton>
            </div>
          </NCard>
          <UxState v-if="!loading && marketList.length === 0" state="empty" :text="t('skill.empty')" />
        </div>
      </NTabPane>

      <NTabPane name="mine" :tab="t('skill.mine')">
        <div class="flex flex-col gap-3">
          <NCard v-for="item in myList" :key="item.uuid" size="small">
            <div class="flex items-start justify-between gap-3">
              <div class="flex-1 cursor-pointer" @click="showDetail(item)">
                <div class="flex items-center gap-2">
                  <NTag type="info" size="small">{{ item.name }}</NTag>
                  <span class="font-bold">{{ item.title }}</span>
                  <NTag v-if="item.hasScripts" type="success" size="small">scripts</NTag>
                </div>
                <div class="text-sm text-gray-500 mt-1">{{ item.description }}</div>
              </div>
              <NSwitch :value="item.isEnable" @update:value="(v: boolean) => toggleEnable(item, v)" />
            </div>
          </NCard>
          <UxState v-if="!loading && myList.length === 0" state="empty" :text="t('skill.emptyMine')" />
        </div>
      </NTabPane>
    </NTabs>

    <!-- 坑（2026-10-03 修）：技能详情内容长（SKILL.md 全文等），原不限高 → 底部不可见 -->
    <NModal
      v-model:show="detailVisible" preset="card" :title="detailSkill ? detailSkill.title : ''" style="width: 760px"
      content-style="max-height: 80vh; overflow-y: auto;"
    >
      <div class="markdown-body" v-html="detailHtml" />
    </NModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NCard, NIcon, NInput, NModal, NTag, NTabs, NTabPane, NSwitch, useLoadingBar, useMessage } from 'naive-ui'
import { UxState } from '@/components/common'
import { Search12Regular } from '@vicons/fluent'
import MarkdownIt from 'markdown-it'
import { t } from '@/locales'
import { safeHtml } from '@/utils/sanitize'
import api from '@/api'

const mdi = new MarkdownIt({ html: false, linkify: true })
const ms = useMessage()
const loadingBar = useLoadingBar()

const activeTab = ref('market')
const keyword = ref('')
const loading = ref(false)
const marketList = ref<any[]>([])
const myRawList = ref<any[]>([])
const detailVisible = ref(false)
const detailSkill = ref<any>(null)

const installedMap = computed(() => {
  const map: Record<number, boolean> = {}
  for (const item of myRawList.value) {
    map[item.skillId] = true
  }
  return map
})

const myList = computed(() => {
  return myRawList.value
    .map((row: any) => {
      const skill = marketList.value.find((s: any) => s.id === row.skillId)
      return skill ? { ...skill, isEnable: row.isEnable } : null
    })
    .filter(Boolean)
})

const detailHtml = computed(() => (detailSkill.value ? safeHtml(mdi.render(detailSkill.value.content || '')) : ''))

async function loadMarket() {
  loading.value = true
  loadingBar.start()
  try {
    const res = await api.skillPublicSearch(keyword.value || '', 1, 100)
    marketList.value = (res.data && res.data.records) || []
  } finally {
    loading.value = false
    loadingBar.finish()
  }
}

async function loadMine() {
  const res = await api.userSkillList()
  myRawList.value = (res.data as any[]) || []
}

function onTabChange(tab: string) {
  if (tab === 'mine')
    loadMine()
}

async function toggleInstall(skill: any) {
  await api.userSkillSaveOrUpdate(skill.id, true)
  ms.success(t('common.operationSuccess'))
  await loadMine()
}

async function toggleEnable(item: any, enable: boolean) {
  await api.userSkillSaveOrUpdate(item.id, enable)
  item.isEnable = enable
}

function showDetail(skill: any) {
  detailSkill.value = skill
  detailVisible.value = true
}

onMounted(() => {
  loadMarket()
  loadMine()
})
</script>

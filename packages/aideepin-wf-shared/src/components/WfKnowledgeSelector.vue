<script setup lang="ts">
// S2-07#4 下沉：单源实现，依赖统一经 #wf-shared-vendor（禁止裸导入/宿主@别名）
import { Cloud32Regular, LockClosed32Regular, NIcon, NSelect, api, computed, h, nextTick, ref, t, useAuthStore, watch } from '#wf-shared-vendor'
import type { SelectGroupOption, SelectInst, SelectOption, VNodeChild } from '#wf-shared-vendor'
import { debounce } from '../utils/debounce'

interface KbOption {
  value: string
  label: string
  isPublic: boolean
  categoryId: number
}
interface Props {
  knowledgeBaseUuid: string
}
interface Emit {
  (e: 'selected', knowledge_base_uuid: string, knowledge_base_name: string): void
}
const props = withDefaults(defineProps<Props>(), {
  knowledgeBaseUuid: '',
})
const emit = defineEmits<Emit>()
const selectInstRef = ref<SelectInst | null>(null)
const authStore = useAuthStore()
const selectedKnowledgeUuid = ref<string>(props.knowledgeBaseUuid)
const currentPage = ref<number>(1)
const pageSize = 10
// 扁平 KB 列表（我的 + 公共去重），按分类动态分组渲染
const flatKbs = ref<KbOption[]>([])
// 分类 id -> 名称 映射（空串表示接口不可用，退化为单组）
const categoryNames = ref<Record<number, string>>({})

const options = computed<Array<SelectOption | SelectGroupOption>>(() => {
  if (Object.keys(categoryNames.value).length === 0) {
    // 无分类数据时退化为单组，保持可用
    return [{
      type: 'group',
      label: t('common.mine'),
      key: 'g_fallback',
      children: flatKbs.value.map(kb => ({ label: kb.label, value: kb.value, isPublic: kb.isPublic })),
    }]
  }
  const sorted = [...flatKbs.value].sort((a, b) => {
    // 未分类(0)排最后，其余按分类 id 升序
    if ((a.categoryId > 0) !== (b.categoryId > 0))
      return a.categoryId > 0 ? -1 : 1
    return a.categoryId - b.categoryId
  })
  const groups: Array<SelectGroupOption> = []
  let lastCat = -1
  for (const kb of sorted) {
    if (kb.categoryId !== lastCat) {
      lastCat = kb.categoryId
      const label = kb.categoryId > 0
        ? (categoryNames.value[kb.categoryId] || `#${kb.categoryId}`)
        : t('columns.uncategorized')
      groups.push({
        type: 'group',
        label,
        key: `g_cat_${kb.categoryId}`,
        children: [],
      })
    }
    groups[groups.length - 1].children!.push({ label: kb.label, value: kb.value, isPublic: kb.isPublic })
  }
  return groups
})

function renderLabel(option: SelectOption): VNodeChild {
  if (option.type === 'group')
    return option.label as string
  return [
    h('div', { class: 'flex items-center' }, {
      default: () => [
        h(
          NIcon,
          {
            style: {
              verticalAlign: '-0.15em',
              marginRight: '4px',
            },
          },
          {
            default: () => h(option.isPublic ? Cloud32Regular : LockClosed32Regular),
          },
        ),
        h(
          'div',
          {
            class: 'ml-1.5',
          },
          { default: () => option.label as string },
        ),
      ],
    }),
  ]
}

function handleSelect(knowledgeBaseUuid: string) {
  const hit = flatKbs.value.find(kb => kb.value === knowledgeBaseUuid)
  emit('selected', knowledgeBaseUuid, hit?.label || '')
}

const handleSearch = debounce(search, 300)
async function search(query: string) {
  try {
    await searchKbs(query)
  } catch (e) {
    console.error(e)
  }
}

function upsert(list: KbOption[], item: KbOption) {
  const idx = list.findIndex(kb => kb.value === item.value)
  if (idx === -1)
    list.push(item)
}

async function searchKbs(query: string) {
  const { data } = await api.knowledgeBaseSearchMine<KnowledgeBase.InfoListResp>(query, currentPage.value, pageSize)
  const next: KbOption[] = []
  data.records.forEach((item) => {
    next.push({
      value: item.uuid,
      label: item.title,
      isPublic: item.isPublic,
      categoryId: Number(item.categoryId || 0),
    })
  })
  const { data: data2 } = await api.knowledgeBaseSearchPublic<KnowledgeBase.InfoListResp>(query, currentPage.value, pageSize)
  if (data2) {
    // 自己创建且公开的知识库会同时出现在 mine 与 public 两个接口里（2026-10-30 用户反馈下拉重复），
    // 以 mine 结果为准，public 侧按 uuid 去重
    const mineUuids = new Set(next.map(kb => kb.value))
    data2.records.forEach((item) => {
      if (mineUuids.has(item.uuid))
        return
      next.push({
        value: item.uuid,
        label: item.title,
        isPublic: item.isPublic,
        categoryId: Number(item.categoryId || 0),
      })
    })
  }
  flatKbs.value = next
}

async function loadCategories() {
  try {
    const resp = await api.knowledgeBaseCategoryList<{ id: number; name: string }[]>()
    const map: Record<number, string> = {}
    for (const c of resp.data || []) map[Number(c.id)] = c.name
    categoryNames.value = map
  } catch (e) {
    console.error('load kb categories failed', e)
  }
}

/**
 * 1. 初始化选中项不存在，指定下拉列表的第一个为选中项
 * 2. 初始化选中项存在，检查选中项是否在下拉列表中，没有则请求一次该选中的知识库详情，追回到下拉列表中
 */
async function checkAndGetSelected() {
  if (!selectedKnowledgeUuid.value) {
    if (flatKbs.value.length) {
      selectedKnowledgeUuid.value = flatKbs.value[0].value
      handleSelect(selectedKnowledgeUuid.value)
    }
    return
  }
  if (!flatKbs.value.some(kb => kb.value === selectedKnowledgeUuid.value)) {
    const resp = await api.knowledgeBaseInfo<KnowledgeBase.Info>(selectedKnowledgeUuid.value)
    upsert(flatKbs.value, {
      value: resp.data.uuid,
      label: resp.data.title,
      isPublic: resp.data.isPublic,
      categoryId: Number(resp.data.categoryId || 0),
    })
  }
}

watch(
  () => authStore.token,
  () => {
    if (authStore.token) {
      nextTick(async () => {
        await loadCategories()
        await search('')
        await checkAndGetSelected()
      })
    }
  },
  { immediate: true },
)
</script>

<template>
  <NSelect
    ref="selectInstRef" v-model:value="selectedKnowledgeUuid" filterable :placeholder="t('workflow.searchKnowledgeBase')" :options="options"
    clearable remote :render-label="renderLabel" @update:value="handleSelect" @search="handleSearch"
  />
</template>

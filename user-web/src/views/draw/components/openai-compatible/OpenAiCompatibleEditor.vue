<script setup lang='ts'>
// 二开新增（2026-10-29）：OpenAI 协议兼容图片平台的通用绘图编辑器。
// 此前 index.vue footer 只为 openai/dashscope/siliconflow 三个显式平台提供了编辑组件，
// 火山方舟（volcengine）等 OpenAI 协议兼容平台的图片模型注册进 /model/imageModels 后，
// 选中时三个 v-show 分支都不命中，整个输入区被隐藏，绘画页无法输入提示词。
// 说明：quality 不在前端选择，后端 OpenAiImageService 默认补 "medium"（Ark seedream 实测可用）。
import { ref } from 'vue'
import { NCol, NRadio, NRadioGroup, NRow, NSpace, useMessage } from 'naive-ui'
import SearchInput from '@/views/draw/components/SearchInput.vue'
import { checkProcess } from '@/views/draw/helper'
import { useAppStore, useDrawStore } from '@/store'
import api from '@/api'
import { t } from '@/locales'
import { emptyDraw } from '@/utils/functions'

interface Emit {
  (e: 'submitted'): void
}
const emit = defineEmits<Emit>()
const appStore = useAppStore()
const drawStore = useDrawStore()
const ms = useMessage()
const selectedImageSize = ref<string>('1024x1024')
// Seedream 系列支持的尺寸档位（1K/2K 常用比例）
const imageSizes = ['1024x1024', '864x1152', '1152x864', '1280x720', '720x1280', '2048x2048']

async function handleSubmit(prompt: string) {
  console.log(`OpenAiCompatibleEditor submit:${prompt}`)
  try {
    const modelName = appStore.selectedImageModel.modelName
    const resp = await api.imageGenerate<CreateImageResult>({ interactingMethod: 1, modelName, prompt, size: selectedImageSize.value, number: 1 })
    const uuid = resp.data.uuid
    drawStore.setLoadingUuid(uuid)

    const draw = emptyDraw()
    draw.uuid = uuid
    draw.prompt = prompt
    draw.aiModelName = modelName
    drawStore.pushOne(draw)

    emit('submitted')

    setTimeout(() => {
      checkProcess(uuid)
    }, 5000)
  } catch (error: any) {
    const e = error as { message: string }
    ms.error(e.message)
  }
}
</script>

<template>
  <div>
    <NRow class="pt-4 pb-4">
      <NCol :span="2" class="min-w-fit">
        {{ t('common.imageSize') }}
      </NCol>
      <NCol :span="12">
        <NRadioGroup v-model:value="selectedImageSize" name="radiogroup">
          <NSpace>
            <NRadio v-for="imageSize in imageSizes" :key="imageSize" :value="imageSize">
              {{ imageSize }}
            </NRadio>
          </NSpace>
        </NRadioGroup>
      </NCol>
    </NRow>
    <SearchInput @submit="handleSubmit" />
  </div>
</template>

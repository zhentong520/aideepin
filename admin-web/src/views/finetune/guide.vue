<template>
  <div>
    <n-card :bordered="false" :title="t('finetune.guide.title')">
      <n-alert type="info" :show-icon="false" style="margin-bottom: 14px">{{ t('finetune.guide.intro') }}</n-alert>

      <!-- 全流程 -->
      <div class="sec">{{ t('finetune.guide.secFlow') }}</div>
      <n-grid :cols="24" :x-gap="12">
        <n-grid-item v-for="(step, i) in flow" :key="i" :span="6">
          <n-card size="small" embedded style="height: 100%">
            <n-space align="center" style="margin-bottom: 6px">
              <n-tag type="info" size="small" round>{{ i + 1 }}</n-tag>
              <b>{{ t(`finetune.guide.flow.${step}.t`) }}</b>
            </n-space>
            <div class="dim" v-html="safeHtml(t(`finetune.guide.flow.${step}.d`))"></div>
          </n-card>
        </n-grid-item>
      </n-grid>

      <!-- 数据准备 -->
      <div class="sec">{{ t('finetune.guide.secData') }}</div>
      <n-tabs type="segment" size="small">
        <n-tab-pane name="alpaca" tab="Alpaca (instruction/input/output)">
          <pre class="code">{{ alpacaSample }}</pre>
        </n-tab-pane>
        <n-tab-pane name="sharegpt" tab="Sharegpt (conversations)">
          <pre class="code">{{ sharegptSample }}</pre>
        </n-tab-pane>
      </n-tabs>
      <n-alert type="warning" :show-icon="false" size="small" style="margin: var(--adi-space-s12) 0">{{ t('finetune.guide.dataRule') }}</n-alert>
      <n-space vertical size="small">
        <div class="dim" v-html="safeHtml(t('finetune.guide.dataTip1'))"></div>
        <div class="dim" v-html="safeHtml(t('finetune.guide.dataTip2'))"></div>
        <div class="dim" v-html="safeHtml(t('finetune.guide.dataTip3'))"></div>
      </n-space>

      <!-- 数据人工检查清单 -->
      <div class="sec">{{ t('finetune.guide.secCheck') }}</div>
      <n-space vertical size="small">
        <div v-for="(c, i) in checks" :key="i" class="dim" v-html="safeHtml(t(`finetune.guide.${c}`))"></div>
      </n-space>

      <!-- 选型 -->
      <div class="sec">{{ t('finetune.guide.secModel') }}</div>
      <n-data-table :columns="modelColumns" :data="modelRows" size="small" :bordered="false" :pagination="false" />
      <div class="dim" style="margin-top: 8px">{{ t('finetune.guide.modelNote') }}</div>

      <!-- 参数速查 -->
      <div class="sec">{{ t('finetune.guide.secParam') }}</div>
      <n-data-table :columns="paramColumns" :data="paramRows" size="small" :bordered="false" :pagination="false" />

      <!-- Worker 部署 -->
      <div class="sec">{{ t('finetune.guide.secWorker') }}</div>
      <n-space align="center" style="margin-bottom: 6px">
        <n-tag size="small">{{ t('finetune.guide.workerKeyHint') }}</n-tag>
        <n-button size="tiny" @click="goArtifact">{{ t('finetune.guide.goArtifact') }}</n-button>
      </n-space>
      <div v-for="(cmd, i) in workerCmds" :key="i" class="cmd">
        <pre class="code" style="margin: 0">{{ cmd }}</pre>
        <n-button size="tiny" quaternary @click="copy(cmd)">{{ t('finetune.copy') }}</n-button>
      </div>
      <div class="dim" style="margin-top: 8px" v-html="safeHtml(t('finetune.guide.workerNote'))"></div>

      <!-- 训练后使用 -->
      <div class="sec">{{ t('finetune.guide.secDeploy') }}</div>
      <div class="dim" v-html="safeHtml(t('finetune.guide.deployText'))"></div>
      <div class="cmd">
        <pre class="code" style="margin: 0">{{ ollamaCmd }}</pre>
        <n-button size="tiny" quaternary @click="copy(ollamaCmd)">{{ t('finetune.copy') }}</n-button>
      </div>

      <!-- FAQ -->
      <div class="sec">{{ t('finetune.guide.secFaq') }}</div>
      <n-space vertical size="small">
        <div v-for="(q, i) in faq" :key="i" class="faq">
          <b>{{ t(`finetune.guide.faq.${q}.q`) }}</b>
          <div class="dim" v-html="safeHtml(t(`finetune.guide.faq.${q}.a`))"></div>
        </div>
      </n-space>
    </n-card>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h } from 'vue';
  import { useRouter } from 'vue-router';
  import { useI18n } from 'vue-i18n';
  import { safeHtml } from '@/utils/sanitize';
  import { NTag, useMessage } from 'naive-ui';

  const { t } = useI18n();
  const message = useMessage();
  const router = useRouter();

  const flow = ['data', 'upload', 'job', 'worker', 'watch', 'use'];

  const checks = ['check1', 'check2', 'check3', 'check4', 'check5'];

  const alpacaSample = `{"instruction": "介绍一下公司的退货政策", "input": "", "output": "自签收之日起 7 天内可无理由退货……"}
{"instruction": "把下面这句话翻译成英文", "input": "今天天气不错", "output": "The weather is nice today."}`;

  const sharegptSample = `{"conversations": [{"from": "human", "value": "你好，我想咨询退款"}, {"from": "gpt", "value": "您好，请问订单号是多少？"}, {"from": "human", "value": "A12345"}, {"from": "gpt", "value": "已为您查询到订单，正在处理退款。"}]}`;

  const modelRows = computed(() => [
    { size: '0.5B ~ 1.5B', model: 'Qwen2.5-0.5B/1.5B', lora: '≤ 6 GB', qlora: '≤ 4 GB', gpu: 'RTX 3060 8G', scene: t('finetune.guide.scene.verify') },
    { size: '3B ~ 4B', model: 'Qwen2.5-3B / Qwen3-4B', lora: '8 ~ 12 GB', qlora: '5 ~ 6 GB', gpu: 'RTX 3060 12G / 4060Ti', scene: t('finetune.guide.scene.small') },
    { size: '7B ~ 9B', model: 'Qwen2.5-7B ★ / GLM-4-9B', lora: '18 ~ 22 GB', qlora: '8 ~ 10 GB', gpu: 'RTX 3090/4090 24G', scene: t('finetune.guide.scene.main') },
    { size: '13B ~ 14B', model: 'Qwen2.5-14B', lora: '32 GB+', qlora: '12 ~ 14 GB', gpu: 'V100 32G / A6000 48G', scene: t('finetune.guide.scene.pro') },
    { size: '32B', model: 'Qwen2.5-32B', lora: '—', qlora: '24 ~ 28 GB', gpu: 'A100 40G', scene: t('finetune.guide.scene.pro') },
    { size: '72B', model: 'Qwen2.5-72B', lora: '—', qlora: '48 GB+', gpu: 'A100 80G ×1~2', scene: t('finetune.guide.scene.pro') },
  ]);

  const modelColumns = computed(() => [
    { title: t('finetune.guide.col.size'), key: 'size', width: 110 },
    { title: t('finetune.guide.col.model'), key: 'model', width: 220 },
    { title: 'LoRA (bf16)', key: 'lora', width: 130 },
    { title: 'QLoRA (4bit)', key: 'qlora', width: 130 },
    { title: t('finetune.guide.col.gpu'), key: 'gpu', width: 190 },
    {
      title: t('finetune.guide.col.scene'),
      key: 'scene',
      render: (row: any) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.scene }),
    },
  ]);

  const paramRows = computed(() => [
    { name: t('finetune.p.learningRate'), v: '1e-4 ~ 2e-4', k: t('finetune.guide.key.lr') },
    { name: t('finetune.p.loraRank'), v: '8 / 16 / 32', k: t('finetune.guide.key.rank') },
    { name: t('finetune.p.loraAlpha'), v: '2 × rank', k: t('finetune.guide.key.alpha') },
    { name: t('finetune.p.batchSize') + ' × ' + t('finetune.p.gradientAccumulation'), v: '有效批 16 ~ 32', k: t('finetune.guide.key.batch') },
    { name: t('finetune.p.maxSeqLen'), v: '2048 / 3072', k: t('finetune.guide.key.cutoff') },
    { name: t('finetune.p.numEpochs'), v: '3 ~ 5', k: t('finetune.guide.key.epoch') },
    { name: t('finetune.p.valRatio'), v: '0.05 ~ 0.1', k: t('finetune.guide.key.val') },
    { name: t('finetune.p.earlyStoppingPatience'), v: '2 ~ 3', k: t('finetune.guide.key.early') },
    { name: t('finetune.p.packing'), v: t('finetune.guide.on'), k: t('finetune.guide.key.packing') },
    { name: t('finetune.p.gradientCheckpointing'), v: t('finetune.guide.on'), k: t('finetune.guide.key.gc') },
  ]);

  const paramColumns = computed(() => [
    { title: t('finetune.guide.col.param'), key: 'name', width: 240 },
    { title: t('finetune.guide.col.value'), key: 'v', width: 160 },
    { title: t('finetune.guide.col.why'), key: 'k' },
  ]);

  const workerCmds = [
    `export ADI_SERVER_URL=http://<AISelf后端>:9999
export ADI_API_KEY=ext-xxxxxxxx
export ADI_WORKER_ID=gpu-node-01

pip install -r requirements.txt
pip install "llamafactory[torch,metrics]"
python worker.py --mode real`,
    `# 国内拉模型加速（可选）
export HF_ENDPOINT=https://hf-mirror.com
export USE_MODELSCOPE=1

# GGUF 量化需要 llama.cpp（可选）
git clone --depth 1 https://github.com/ggml-org/llama.cpp /opt/llama.cpp
cd /opt/llama.cpp && cmake -B build && cmake --build build -j`,
  ];

  const ollamaCmd = `# 在 GPU 机器上把导出的合并模型/GGUF 注册为 Ollama 模型
ollama create <tag> -f ./Modelfile
# 然后在管理端「模型产物」页点「注册为模型」填同名 tag，
# 训练成品即出现在对话页与知识库问答的模型列表中`;

  const faq = ['lowSample', 'lossNaN', 'huge', 'stall', 'deploy', 'quality'];

  async function copy(text: string) {
    await navigator.clipboard.writeText(text);
    message.success(t('finetune.copyKeyDone'));
  }

  function goArtifact() {
    router.push('/finetune/artifact');
  }
</script>

<style lang="less" scoped>
  .sec {
    font-weight: 600;
    font-size: 14px;
    margin: 20px 0 var(--adi-space-s12);
    padding-left: 8px;
    border-left: 3px solid #2080f0;
  }
  .dim {
    font-size: 12.5px;
    opacity: 0.78;
    line-height: 1.8;
  }
  .code {
    background: rgba(128, 128, 128, 0.1);
    border-radius: 4px;
    padding: var(--adi-space-s12);
    font-size: 12px;
    overflow: auto;
    white-space: pre-wrap;
  }
  .cmd {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin-bottom: 8px;
  }
  .faq {
    border-bottom: 1px dashed rgba(128, 128, 128, 0.28);
    padding-bottom: 8px;
  }
</style>

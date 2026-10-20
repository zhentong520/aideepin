<template>
  <div>
    <n-card :bordered="false">
      <n-space justify="space-between" align="center" style="margin-bottom: 12px">
        <n-space>
          <n-select v-model:value="searchStatus" :options="statusOptions" :placeholder="t('finetune.status')" style="width: 160px" clearable />
          <n-input v-model:value="searchName" :placeholder="t('finetune.namePlaceholder')" style="width: 200px" clearable @keyup.enter="load" />
          <n-button type="primary" @click="load">{{ t('common.search') }}</n-button>
        </n-space>
        <n-space>
          <n-button quaternary type="info" @click="goGuide">{{ t('finetune.guideEntry') }}</n-button>
          <n-button type="info" @click="openCreate">{{ t('finetune.createJob') }}</n-button>
        </n-space>
      </n-space>
      <n-alert type="default" :show-icon="false" style="margin-bottom: 12px">
        {{ t('finetune.jobHint') }}
      </n-alert>
      <n-data-table :columns="columns" :data="list" :loading="loading" :pagination="pagination" remote @update:page="onPage" />
    </n-card>

    <!-- ================= 创建训练任务 ================= -->
    <!-- 坑（2026-10-03 修）：创建训练任务表单很高（原仅 mask-closable=false，未限高），
         视口矮时保存按钮不可见；此处补内容限高滚动。任务详情为只读，同样限高避免底部看不到。 -->
    <n-modal
      v-model:show="createVisible" preset="card" :title="t('finetune.createJob')" style="width: 1180px"
      :mask-closable="false" content-style="max-height: 80vh; overflow-y: auto;"
    >
      <n-grid :cols="24" :x-gap="16">
        <!-- 左：参数表单 -->
        <n-grid-item :span="15">
          <div style="max-height: 62vh; overflow: auto; padding-right: 8px">
            <!-- 基础 -->
            <div class="sec">{{ t('finetune.grpBasic') }}</div>
            <n-form label-placement="left" label-width="130" size="small" :show-feedback="false">
              <n-grid :cols="24" :x-gap="12">
                <n-grid-item :span="12">
                  <n-form-item :label="t('finetune.name')">
                    <n-input v-model:value="form.name" :placeholder="t('finetune.jobNamePlaceholder')" />
                  </n-form-item>
                </n-grid-item>
                <n-grid-item :span="12">
                  <n-form-item :label="t('finetune.dataset')">
                    <n-select v-model:value="form.datasetUuid" :options="datasetOptions" filterable :placeholder="t('finetune.datasetPlaceholder')" />
                  </n-form-item>
                </n-grid-item>
                <n-grid-item :span="12">
                  <n-form-item>
                    <template #label>
                      <span class="lbl">{{ t('finetune.baseModel') }}</span>
                      <n-tooltip trigger="hover" :style="{ maxWidth: '360px' }">
                        <template #trigger><n-icon :component="HelpCircleOutline" size="14" class="hint" /></template>
                        <div v-html="safeHtml(t('finetune.h.baseModelName'))"></div>
                      </n-tooltip>
                    </template>
                    <n-select v-model:value="form.baseModelName" :options="modelOptions" filterable tag :placeholder="t('finetune.baseModelPlaceholder')" />
                  </n-form-item>
                </n-grid-item>
                <n-grid-item :span="12">
                  <n-form-item>
                    <template #label>
                      <span class="lbl">{{ t('finetune.method') }}</span>
                      <n-tooltip trigger="hover" :style="{ maxWidth: '360px' }">
                        <template #trigger><n-icon :component="HelpCircleOutline" size="14" class="hint" /></template>
                        <div v-html="safeHtml(t('finetune.h.method'))"></div>
                      </n-tooltip>
                    </template>
                    <n-select v-model:value="form.method" :options="methodOptions" @update:value="applyMethodDefault" />
                  </n-form-item>
                </n-grid-item>
                <n-grid-item :span="24">
                  <n-form-item>
                    <template #label>
                      <span class="lbl">{{ t('finetune.presetLabel') }}</span>
                      <n-tooltip trigger="hover" :style="{ maxWidth: '400px' }">
                        <template #trigger><n-icon :component="HelpCircleOutline" size="14" class="hint" /></template>
                        <div v-html="safeHtml(t('finetune.h.preset'))"></div>
                      </n-tooltip>
                    </template>
                    <n-space align="center" style="width: 100%">
                      <n-select v-model:value="presetKey" :options="presetOptions" style="width: 260px" :placeholder="t('finetune.presetPlaceholder')" @update:value="applyPreset" />
                      <n-button size="tiny" quaternary @click="resetDefaults">{{ t('finetune.resetDefaults') }}</n-button>
                    </n-space>
                  </n-form-item>
                  <n-alert v-if="presetDesc" type="info" :show-icon="false" size="small" style="margin-bottom: var(--adi-space-s12)">{{ presetDesc }}</n-alert>
                </n-grid-item>
                <n-grid-item v-if="datasetStat" :span="24">
                  <n-alert :type="datasetStat.quality === 'poor' ? 'error' : datasetStat.quality === 'fair' ? 'warning' : 'success'" :show-icon="false" size="small" style="margin-bottom: var(--adi-space-s12)">
                    {{ t('finetune.datasetStat', {
                      n: datasetStat.unique_samples ?? '-',
                      dup: Math.round((datasetStat.dup_ratio || 0) * 100),
                      p95: datasetStat.len_p95_chars ?? '-',
                      suggest: datasetStat.suggest_cutoff_len ?? '-',
                    }) }}
                    <n-button v-if="datasetStat.suggest_cutoff_len && datasetStat.suggest_cutoff_len !== form.maxSeqLen" size="tiny" quaternary type="primary" @click="form.maxSeqLen = datasetStat.suggest_cutoff_len">
                      {{ t('finetune.applySuggest') }}
                    </n-button>
                  </n-alert>
                </n-grid-item>
              </n-grid>
            </n-form>

            <!-- 其余参数分组 -->
            <template v-for="grp in visibleGroups" :key="grp.key">
              <div class="sec">{{ t(`finetune.grp.${grp.key}`) }}</div>
              <n-form label-placement="left" label-width="130" size="small" :show-feedback="false">
                <n-grid :cols="24" :x-gap="12">
                  <n-grid-item v-for="f in grp.fields" :key="f.key" :span="12">
                    <n-form-item>
                      <template #label>
                        <span class="lbl">{{ t(`finetune.p.${f.key}`) }}</span>
                        <n-tooltip trigger="hover" :style="{ maxWidth: '380px' }">
                          <template #trigger><n-icon :component="HelpCircleOutline" size="14" class="hint" /></template>
                          <div v-html="safeHtml(t(`finetune.h.${f.key}`))"></div>
                        </n-tooltip>
                      </template>
                      <n-input-number
                        v-if="f.type === 'number'"
                        v-model:value="form[f.key]"
                        size="small"
                        :min="limit(f.key, 0)"
                        :max="limit(f.key, 1)"
                        :step="limit(f.key, 2)"
                        style="width: 100%"
                      />
                      <n-switch v-else-if="f.type === 'switch'" v-model:value="form[f.key]" size="small" />
                      <n-select v-else-if="f.type === 'select'" v-model:value="form[f.key]" size="small" :options="optionList(f)" style="width: 100%" />
                      <n-input v-else v-model:value="form[f.key]" size="small" :placeholder="t('finetune.templatePlaceholder')" />
                    </n-form-item>
                  </n-grid-item>
                </n-grid>
              </n-form>
            </template>
          </div>
        </n-grid-item>

        <!-- 右：预演 -->
        <n-grid-item :span="9">
          <div class="panel">
            <div class="panel-title">
              {{ t('finetune.previewTitleJob') }}
              <n-spin v-if="previewLoading" :size="14" style="margin-left: 6px" />
            </div>
            <n-descriptions :column="2" size="small" bordered>
              <n-descriptions-item :label="t('finetune.trainSamples')">{{ preview.trainSamples ?? '-' }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.evalSamples')">{{ preview.evalSamples ?? '-' }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.effBatch')">{{ preview.effectiveBatchSize ?? '-' }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.totalSteps')">{{ preview.totalSteps ?? '-' }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.loggingSteps')">{{ preview.loggingSteps ?? '-' }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.saveSteps')">{{ preview.saveSteps || t('finetune.off') }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.evalSteps')">{{ preview.evalSteps || t('finetune.off') }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.earlyStopShort')">{{ form.earlyStoppingPatience ? form.earlyStoppingPatience : t('finetune.off') }}</n-descriptions-item>
            </n-descriptions>

            <div class="panel-title" style="margin-top: 12px">{{ t('finetune.vramTitle') }}</div>
            <template v-if="preview.vram && preview.vram.totalGb">
              <n-tag :type="vramTagType" size="small" round style="margin-bottom: 6px">
                {{ t('finetune.vramTotal', { gb: preview.vram.totalGb }) }}
              </n-tag>
              <div class="dim">
                {{ t('finetune.vramBreakdown', { w: preview.vram.weightGb, a: preview.vram.activationGb, o: preview.vram.overheadGb }) }}
              </div>
              <div v-if="form.method !== 'qlora' && preview.vram.qloraTotalGb" class="dim">
                {{ t('finetune.vramQlora', { gb: preview.vram.qloraTotalGb }) }}
              </div>
              <n-space size="small" style="margin: 8px 0">
                <n-tag v-for="tier in preview.vram.tiers" :key="tier.gpu" size="small" :type="tier.ok ? 'success' : 'default'" :bordered="!tier.ok">
                  {{ tier.gpu }}{{ tier.ok ? ' ✓' : '' }}
                </n-tag>
              </n-space>
              <div class="dim">{{ t('finetune.vramDisclaimer') }}</div>
            </template>
            <n-alert v-else type="warning" :show-icon="false" size="small">{{ t('finetune.vramUnknown') }}</n-alert>

            <div class="panel-title" style="margin-top: 12px">{{ t('finetune.diagTitle') }}</div>
            <UxState v-if="!previewWarnings.length" state="empty" :text="t('finetune.diagClean')" />
            <n-alert
              v-for="(w, i) in previewWarnings"
              :key="i"
              :type="w.level === 'error' ? 'error' : w.level === 'warn' ? 'warning' : 'info'"
              :show-icon="false"
              size="small"
              style="margin-bottom: 6px"
            >
              {{ t(`finetune.warn.${w.code}`, w.args || {}) }}
            </n-alert>
          </div>
        </n-grid-item>
      </n-grid>
      <template #footer>
        <n-space justify="end">
          <n-button @click="createVisible = false">{{ t('common.cancel') }}</n-button>
          <n-button type="primary" :loading="creating" @click="doCreate">{{ t('finetune.createAndQueue') }}</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- ================= 任务详情 ================= -->
    <!-- 坑（2026-10-03 修）：任务详情含多个 tab 与长文本，原弹窗不限高 → 底部内容被推出视口 -->
    <n-modal
      v-model:show="detailVisible" preset="card" :title="detailRow.name || detailRow.uuid" style="width: 920px"
      content-style="max-height: 80vh; overflow-y: auto;"
    >
      <n-tabs type="line" animated>
        <n-tab-pane name="overview" :tab="t('finetune.tabOverview')">
          <n-descriptions :column="2" bordered size="small" style="margin-bottom: 12px">
            <n-descriptions-item :label="t('finetune.status')">
              <n-tag :type="statusTagType(detailRow.status)" size="small">{{ detailRow.status }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="Base Model">{{ detailRow.baseModelName }}</n-descriptions-item>
            <n-descriptions-item :label="t('finetune.method')">{{ detailRow.method }}</n-descriptions-item>
            <n-descriptions-item :label="t('finetune.presetLabel')">{{ presetLabel(detailRow.preset) }}</n-descriptions-item>
            <n-descriptions-item :label="t('finetune.worker')">{{ detailRow.workerId || '-' }}</n-descriptions-item>
            <n-descriptions-item :label="t('finetune.createTime')">{{ detailRow.createTime || '-' }}</n-descriptions-item>
          </n-descriptions>
          <n-progress type="line" :percentage="Number(detailRow.progress || 0)" indicator-placement="inside" style="margin-bottom: 12px" />
          <n-space style="margin-bottom: 12px">
            <n-tag size="small">epoch {{ detailRow.currentEpoch }}</n-tag>
            <n-tag size="small">train_loss {{ detailRow.trainLoss }}</n-tag>
            <n-tag size="small">eval_loss {{ detailRow.evalLoss }}</n-tag>
          </n-space>
          <n-alert v-if="detailRow.failReason" type="error" :show-icon="false" size="small">{{ detailRow.failReason }}</n-alert>
        </n-tab-pane>

        <n-tab-pane name="report" :tab="t('finetune.tabReport')">
          <UxState v-if="!hasMetrics" state="empty" :text="t('finetune.reportEmpty')" />
          <template v-else>
            <n-alert v-if="metrics.eval_loss_increased" type="warning" :show-icon="false" style="margin-bottom: var(--adi-space-s12)">
              {{ t('finetune.reportOverfit') }}
            </n-alert>
            <n-alert v-else-if="metrics.best_step && metrics.total_steps && metrics.best_step < metrics.total_steps" type="info" :show-icon="false" style="margin-bottom: var(--adi-space-s12)">
              {{ t('finetune.reportEarlyStop', { step: metrics.best_step, total: metrics.total_steps }) }}
            </n-alert>
            <n-descriptions :column="2" bordered size="small">
              <n-descriptions-item :label="t('finetune.r.trainLoss')">{{ fmt(metrics.train_loss) }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.firstLoss')">{{ fmt(metrics.first_train_loss) }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.evalLoss')">{{ fmt(metrics.eval_loss) }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.bestEvalLoss')">{{ fmt(metrics.best_eval_loss) }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.bestStep')">{{ metrics.best_step ?? '-' }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.totalSteps')">{{ metrics.total_steps ?? '-' }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.epochsDone')">{{ fmt(metrics.epochs_done) }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.runtime')">{{ runtimeText }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.params')">{{ paramsText }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.bestCkpt')">{{ metrics.best_checkpoint || '-' }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.gpu')">{{ metrics.gpu || '-' }}</n-descriptions-item>
              <n-descriptions-item :label="t('finetune.r.preset')">{{ presetLabel(metrics.preset) }}</n-descriptions-item>
            </n-descriptions>
          </template>
        </n-tab-pane>

        <n-tab-pane name="params" :tab="t('finetune.tabParams')">
          <n-descriptions :column="2" bordered size="small">
            <n-descriptions-item v-for="(v, k) in (detailRow.hyperParams || {})" :key="k" :label="paramLabel(String(k))">
              {{ v === '' ? '-' : v }}
            </n-descriptions-item>
          </n-descriptions>
        </n-tab-pane>

        <n-tab-pane name="log" :tab="t('finetune.tabLog')">
          <n-space style="margin-bottom: 8px">
            <n-button size="small" type="primary" :loading="logLoading" @click="refreshDetail">{{ t('finetune.refreshLog') }}</n-button>
          </n-space>
          <pre class="max-h-[360px] overflow-auto p-s12 text-xs" style="background: var(--adi-code-bg); color: var(--adi-code-text);">{{ logText || '...' }}</pre>
        </n-tab-pane>
      </n-tabs>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onUnmounted, reactive, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { useI18n } from 'vue-i18n';
  import { safeHtml } from '@/utils/sanitize';
  import { NButton, NSpace, NTag, useDialog, useMessage } from 'naive-ui';
  import { HelpCircleOutline } from '@vicons/ionicons5';
  import { datasetSearch, finetuneMeta, jobCreate, jobDel, jobDetail, jobLog, jobPreview, jobSearch, jobStop } from '@/api/finetune';

  const { t } = useI18n();
  const dialog = useDialog();
  const message = useMessage();
  const router = useRouter();

  const loading = ref(false);
  const list = ref<any[]>([]);
  const searchName = ref('');
  const searchStatus = ref<string | null>(null);
  const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 });

  const statusOptions = ['pending', 'running', 'success', 'failed', 'stopped'].map((s) => ({ label: s, value: s }));

  // ---------------- 元数据与表单 ----------------
  const meta = ref<any>({ baseModels: [], presets: [], defaults: {}, limits: {} });
  const createVisible = ref(false);
  const creating = ref(false);
  const datasetOptions = ref<any[]>([]);
  const datasetList = ref<any[]>([]);
  const presetKey = ref<string | null>(null);

  /** 所选数据集的质量体检结果（由上传时统计），用于提示截断长度建议 */
  const datasetStat = computed(() => {
    const d = datasetList.value.find((x: any) => x.uuid === form.datasetUuid);
    return d && d.tokenStat && d.tokenStat.suggest_cutoff_len ? d.tokenStat : null;
  });

  /** snake_case -> camelCase：后端超参用的是 LLaMA-Factory 风格键名 */
  function toCamel(k: string) {
    return k.replace(/_([a-z0-9])/g, (_m, c) => c.toUpperCase());
  }

  const form = reactive<any>({ name: '', datasetUuid: null, baseModelName: 'Qwen2.5-7B-Instruct', method: 'lora' });

  /**
   * 参数分组与控件类型。说明文案统一走 i18n（finetune.p.xxx 标签 / finetune.h.xxx 帮助），
   * 因此这里只描述「结构」，加参数时改这一处 + 补两条文案即可。
   */
  const groups: any[] = [
    {
      key: 'lora',
      fields: [
        { key: 'loraRank', type: 'number' },
        { key: 'loraAlpha', type: 'number' },
        { key: 'loraDropout', type: 'number' },
        { key: 'targetModules', type: 'select' },
        { key: 'useRslora', type: 'switch' },
        { key: 'useDora', type: 'switch' },
      ],
    },
    {
      key: 'optim',
      fields: [
        { key: 'learningRate', type: 'number' },
        { key: 'numEpochs', type: 'number' },
        { key: 'batchSize', type: 'number' },
        { key: 'gradientAccumulation', type: 'number' },
        { key: 'lrSchedulerType', type: 'select' },
        { key: 'optim', type: 'select' },
        { key: 'warmupRatio', type: 'number' },
        { key: 'weightDecay', type: 'number' },
        { key: 'maxGradNorm', type: 'number' },
      ],
    },
    {
      key: 'data',
      fields: [
        { key: 'maxSeqLen', type: 'number' },
        { key: 'valRatio', type: 'number' },
        { key: 'packing', type: 'switch' },
        { key: 'neftuneNoiseAlpha', type: 'number' },
      ],
    },
    {
      key: 'runtime',
      fields: [
        { key: 'gradientCheckpointing', type: 'switch' },
        { key: 'flashAttn', type: 'select' },
        { key: 'seed', type: 'number' },
        { key: 'earlyStoppingPatience', type: 'number' },
        { key: 'loggingSteps', type: 'number' },
        { key: 'saveSteps', type: 'number' },
        { key: 'evalSteps', type: 'number' },
      ],
    },
    {
      key: 'quant',
      showWhen: () => form.method === 'qlora',
      fields: [
        { key: 'quantBit', type: 'select' },
        { key: 'quantMethod', type: 'select' },
      ],
    },
    {
      key: 'export',
      fields: [
        { key: 'exportMerged', type: 'switch' },
        { key: 'exportGguf', type: 'switch' },
        { key: 'ggufQuant', type: 'select' },
        { key: 'template', type: 'text' },
      ],
    },
  ];

  /** 分组按条件显示（如 QLoRA 专属参数仅在 method=qlora 时出现） */
  const visibleGroups = computed(() => groups.filter((g) => !g.showWhen || g.showWhen()));

  /** 后端给的是 hyper_params 的 snake_case 键，表单用 camelCase，这里做一次双向映射 */
  const snakeKeyOf: Record<string, string> = {};
  function buildKeyMap() {
    groups.forEach((g) => g.fields.forEach((f: any) => (snakeKeyOf[f.key] = f.key.replace(/[A-Z]/g, (c: string) => '_' + c.toLowerCase()))));
    snakeKeyOf.maxSeqLen = 'cutoff_len';
    snakeKeyOf.baseModelName = 'base_model_name';
  }

  const limit = (key: string, idx: number) => {
    const snake = snakeKeyOf[key] || key;
    const l = meta.value.limits && meta.value.limits[snake];
    return l ? l[idx] : undefined;
  };

  const methodOptions = computed(() => ['lora', 'qlora'].map((v) => ({ label: t(`finetune.opt.method.${v}`), value: v })));
  const modelOptions = computed(() =>
    (meta.value.baseModels || []).map((m: any) => ({
      label: `${m.name} · ${m.paramsB}B${m.recommended ? ' ★' : ''}${m.noteKey ? ' · ' + t(`finetune.modelNote.${m.noteKey}`) : ''}`,
      value: m.name,
    }))
  );
  const presetOptions = computed(() => (meta.value.presets || []).map((p: any) => ({ label: t(`finetune.preset.${p.key}.name`), value: p.key })));
  const presetDesc = computed(() => (presetKey.value ? t(`finetune.preset.${presetKey.value}.desc`) : ''));

  function optionList(f: any) {
    const map: Record<string, string[]> = {
      targetModules: ['all', 'attn', 'allLinear'],
      lrSchedulerType: ['cosine', 'linear', 'cosine_with_restarts', 'polynomial', 'constant', 'constant_with_warmup'],
      optim: ['adamw_torch', 'paged_adamw_8bit', 'adamw_hf', 'adafactor'],
      flashAttn: ['auto', 'fa2', 'sdpa', 'disabled'],
      quantBit: ['4', '8'],
      quantMethod: ['bitsandbytes', 'hqq'],
      ggufQuant: ['q4_k_m', 'q5_k_m', 'q8_0', 'f16'],
    };
    const values = map[f.key] || [];
    if (f.key === 'targetModules') {
      // 值本身含逗号，无法当 i18n key 用，这里显式给出映射
      const real: Record<string, string> = {
        all: 'all',
        attn: 'q_proj,k_proj,v_proj,o_proj',
        allLinear: 'q_proj,k_proj,v_proj,o_proj,gate_proj,up_proj,down_proj',
      };
      return values.map((v) => ({ label: t(`finetune.opt.targetModules.${v}`), value: real[v] }));
    }
    if (f.key === 'quantBit') {
      return values.map((v) => ({ label: t(`finetune.opt.quantBit.${v}`), value: Number(v) }));
    }
    return values.map((v) => ({ label: t(`finetune.opt.${f.key}.${v}`), value: v }));
  }

  /** 把后端默认超参灌进表单 */
  function fillForm(params: any) {
    Object.keys(snakeKeyOf).forEach((camel) => {
      const snake = snakeKeyOf[camel];
      if (Object.prototype.hasOwnProperty.call(params, snake)) {
        form[camel] = params[snake];
      } else if (Object.prototype.hasOwnProperty.call(params, camel)) {
        form[camel] = params[camel];
      }
    });
  }

  function applyPreset(key: string | null) {
    const p = (meta.value.presets || []).find((x: any) => x.key === key);
    if (!p) return;
    fillForm(p.params);
    if (p.method) form.method = p.method;
    message.success(t('finetune.presetApplied'));
    runPreview();
  }

  function resetDefaults() {
    presetKey.value = null;
    fillForm(meta.value.defaults || {});
    form.method = 'lora';
    runPreview();
  }

  function applyMethodDefault() {
    runPreview();
  }

  // ---------------- 预演 ----------------
  const preview = ref<any>({});
  const previewLoading = ref(false);
  const previewWarnings = computed(() => preview.value.warnings || []);
  const vramTagType = computed(() => {
    const lv = preview.value.vram && preview.value.vram.level;
    return lv === 'danger' ? 'error' : lv === 'warn' ? 'warning' : 'success';
  });

  let previewTimer: any = null;
  function schedulePreview() {
    if (!createVisible.value) return;
    if (previewTimer) clearTimeout(previewTimer);
    previewTimer = setTimeout(runPreview, 350);
  }

  async function runPreview() {
    if (!createVisible.value) return;
    previewLoading.value = true;
    try {
      const res = await jobPreview({ ...form });
      preview.value = (res && res.data) || {};
    } finally {
      previewLoading.value = false;
    }
  }

  watch(form, schedulePreview, { deep: true });

  // ---------------- 列表 ----------------
  function statusTagType(status: string): 'success' | 'error' | 'info' | 'warning' | 'default' {
    const map: Record<string, 'success' | 'error' | 'info' | 'warning' | 'default'> = { success: 'success', failed: 'error', running: 'info', stopped: 'warning', pending: 'default' };
    return map[status] || 'default';
  }

  const columns = [
    { title: 'ID', key: 'id', width: 70 },
    { title: t('finetune.name'), key: 'name', ellipsis: { tooltip: true } },
    { title: t('finetune.baseModel'), key: 'baseModelName', width: 180, ellipsis: { tooltip: true } },
    {
      title: t('finetune.method'),
      key: 'method',
      width: 120,
      render: (row: any) => h(NTag, { size: 'small' }, { default: () => row.method + (row.preset ? ` · ${row.preset}` : '') }),
    },
    {
      title: t('finetune.status'),
      key: 'status',
      width: 100,
      render: (row: any) => h(NTag, { size: 'small', type: statusTagType(row.status) }, { default: () => row.status }),
    },
    {
      title: t('finetune.progress'),
      width: 150,
      render: (row: any) => `${Number(row.progress || 0).toFixed(1)}% (epoch ${row.currentEpoch})`,
    },
    { title: 'train_loss', key: 'trainLoss', width: 100 },
    { title: 'eval_loss', key: 'evalLoss', width: 100 },
    {
      title: t('finetune.actions'),
      key: 'actions',
      width: 250,
      render: (row: any) =>
        h(NSpace, {}, {
          default: () => [
            h(NButton, { size: 'small', onClick: () => openDetail(row) }, { default: () => t('finetune.detail') }),
            h(
              NButton,
              {
                size: 'small',
                type: 'warning',
                disabled: !['pending', 'running'].includes(row.status),
                onClick: () => doStop(row),
              },
              { default: () => t('finetune.stop') }
            ),
            h(NButton, { size: 'small', type: 'error', onClick: () => doDel(row) }, { default: () => t('finetune.delete') }),
          ],
        }),
    },
  ];

  async function load() {
    loading.value = true;
    try {
      const res = await jobSearch(
        { current: pagination.page, size: pagination.pageSize },
        searchStatus.value || undefined,
        searchName.value || undefined
      );
      list.value = (res.data && res.data.records) || [];
      pagination.itemCount = Number((res.data && res.data.total) || 0);
    } finally {
      loading.value = false;
    }
  }

  function onPage(page: number) {
    pagination.page = page;
    load();
  }

  function goGuide() {
    router.push('/finetune/guide');
  }

  async function openCreate() {
    createVisible.value = true;
    if (!meta.value.baseModels.length) {
      const res = await finetuneMeta();
      meta.value = (res && res.data) || {};
      buildKeyMap();
      fillForm(meta.value.defaults || {});
    }
    const res = await datasetSearch({ current: 1, size: 100 });
    datasetList.value = (res.data && res.data.records) || [];
    datasetOptions.value = datasetList.value.map((d: any) => ({
      label: `${d.name} (${d.sampleCount} samples${d.tokenStat && d.tokenStat.dup_ratio ? `, ${Math.round(d.tokenStat.dup_ratio * 100)}% dup` : ''})`,
      value: d.uuid,
    }));
    runPreview();
  }

  async function doCreate() {
    if (!form.name) {
      message.error(t('finetune.namePlaceholder'));
      return;
    }
    if (!form.datasetUuid) {
      message.error(t('finetune.datasetPlaceholder'));
      return;
    }
    creating.value = true;
    try {
      await jobCreate({ ...form, preset: presetKey.value || '' });
      message.success(t('common.operationSuccess'));
      createVisible.value = false;
      await load();
    } finally {
      creating.value = false;
    }
  }

  // ---------------- 详情 ----------------
  const detailVisible = ref(false);
  const detailRow = ref<any>({});
  const logText = ref('');
  const logLoading = ref(false);
  let timer: any = null;

  const metrics = computed(() => detailRow.value.metrics || {});
  const hasMetrics = computed(() => Object.keys(metrics.value).length > 0);
  const runtimeText = computed(() => {
    const s = Number(metrics.value.train_runtime || 0);
    if (!s) return '-';
    return s >= 3600 ? `${(s / 3600).toFixed(2)} h` : `${Math.round(s / 60)} min`;
  });
  const paramsText = computed(() => {
    const p = Number(metrics.value.trainable_params || 0);
    const all = Number(metrics.value.total_params || 0);
    if (!p) return '-';
    const pct = all ? ((p / all) * 100).toFixed(3) + '%' : '';
    return `${p.toLocaleString()} / ${all.toLocaleString()} (${pct})`;
  });

  function fmt(v: any) {
    return v === null || v === undefined || v === '' ? '-' : v;
  }

  function presetLabel(key: string) {
    return key ? t(`finetune.preset.${key}.name`) : '-';
  }

  /** 参数快照的标签：能对上 i18n 的用中文名，对不上的（未来新增的键）原样显示 */
  function paramLabel(k: string) {
    if (k === 'preset') return t('finetune.presetLabel');
    const camel = toCamel(k);
    return Object.values(snakeKeyOf).includes(k) ? t(`finetune.p.${camel}`) : k;
  }

  async function openDetail(row: any) {
    detailRow.value = row;
    detailVisible.value = true;
    await refreshDetail();
    if (timer) clearInterval(timer);
    timer = setInterval(refreshDetail, 5000);
  }

  async function refreshDetail() {
    if (!detailRow.value.uuid) return;
    logLoading.value = true;
    try {
      const job = await jobDetail(detailRow.value.uuid);
      detailRow.value = (job && job.data) || {};
      const log = await jobLog(detailRow.value.uuid, 200);
      logText.value = ((log.data && log.data.lines) || []).join('\n');
    } finally {
      logLoading.value = false;
    }
  }

  function doStop(row: any) {
    dialog.warning({
      title: t('common.tip'),
      content: t('finetune.stopConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await jobStop(row.uuid);
        message.success(t('common.operationSuccess'));
        await load();
      },
    });
  }

  function doDel(row: any) {
    dialog.warning({
      title: t('common.tip'),
      content: t('finetune.delConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await jobDel(row.id);
        message.success(t('common.operationSuccess'));
        await load();
      },
    });
  }

  buildKeyMap();
  load();

  onUnmounted(() => {
    if (timer) clearInterval(timer);
    if (previewTimer) clearTimeout(previewTimer);
  });
</script>

<style lang="less" scoped>
  .sec {
    font-weight: 600;
    font-size: 13px;
    margin: 6px 0 8px;
    padding-left: 8px;
    border-left: 3px solid #2080f0;
  }
  .lbl {
    display: inline-block;
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
    vertical-align: bottom;
  }
  .hint {
    margin-left: 3px;
    cursor: help;
    opacity: 0.5;
    vertical-align: middle;
  }
  .panel {
    background: rgba(128, 128, 128, 0.06);
    border-radius: 6px;
    padding: var(--adi-space-s12);
  }
  .panel-title {
    font-weight: 600;
    font-size: 13px;
    margin-bottom: 8px;
  }
  .dim {
    font-size: 12px;
    opacity: 0.72;
    line-height: 1.6;
  }
</style>

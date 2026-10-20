import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import type { MessageApi } from 'naive-ui'
import type { Composer } from 'vue-i18n'

/** 参数控件类型 */
interface ParamField {
  key: string
  type: 'number' | 'switch' | 'select' | 'text'
}

/** 参数分组（showWhen 控制条件显示，如 QLoRA 专属参数） */
interface ParamGroup {
  key: string
  fields: ParamField[]
  showWhen?: () => boolean
}

/** 训练表单：键集合由 groups 描述，后端超参风格宽泛，用索引签名兜底 */
interface FinetuneForm {
  name: string
  datasetUuid: string | null
  baseModelName: string
  method: string
  [key: string]: unknown
}

/** 元数据（baseModels/presets/defaults/limits）由 /finetune/meta 下发，宽泛结构按索引签名兜底 */
/* eslint-disable @typescript-eslint/no-explicit-any */
interface FinetuneMeta {
  baseModels: Array<{ name: string; paramsB: number; recommended?: boolean; noteKey?: string }>
  presets: Array<{ key: string; method?: string; params: Record<string, any> }>
  defaults: Record<string, any>
  limits: Record<string, number[]>
  [key: string]: any
}

/**
 * 创建训练任务的表单与预演：元数据加载、参数分组渲染、预设套用、
 * 显存/诊断预演（350ms 防抖）。openCreate 触发 loadMeta。
 */
export function useFinetuneForm(ctx: { t: Composer['t']; message: MessageApi; jobPreview: (form: any) => Promise<any> }) {
  const { t, message } = ctx

  const meta = ref<FinetuneMeta>({ baseModels: [], presets: [], defaults: {}, limits: {} })
  const createVisible = ref(false)
  const creating = ref(false)
  const datasetOptions = ref<Array<{ label: string; value: string }>>([])
  const datasetList = ref<any[]>([])
  const presetKey = ref<string | null>(null)

  /** 所选数据集的质量体检结果（由上传时统计），用于提示截断长度建议 */
  const datasetStat = computed(() => {
    const d = datasetList.value.find((x: any) => x.uuid === form.datasetUuid);
    return d && d.tokenStat && d.tokenStat.suggest_cutoff_len ? d.tokenStat : null;
  });

  /** snake_case -> camelCase：后端超参用的是 LLaMA-Factory 风格键名 */
  function toCamel(k: string) {
    return k.replace(/_([a-z0-9])/g, (_m, c) => c.toUpperCase());
  }

  const form = reactive<FinetuneForm>({ name: '', datasetUuid: null, baseModelName: 'Qwen2.5-7B-Instruct', method: 'lora' });

  /**
   * 参数分组与控件类型。说明文案统一走 i18n（finetune.p.xxx 标签 / finetune.h.xxx 帮助），
   * 因此这里只描述「结构」，加参数时改这一处 + 补两条文案即可。
   */
  const groups: ParamGroup[] = [
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
    groups.forEach((g) => g.fields.forEach((f) => (snakeKeyOf[f.key] = f.key.replace(/[A-Z]/g, (c: string) => '_' + c.toLowerCase()))));
    snakeKeyOf.maxSeqLen = 'cutoff_len';
    snakeKeyOf.baseModelName = 'base_model_name';
  }

  const limit = (key: string, idx: number): number | undefined => {
    const snake = snakeKeyOf[key] || key;
    const l = meta.value.limits && meta.value.limits[snake];
    return l ? l[idx] : undefined;
  };

  const methodOptions = computed(() => ['lora', 'qlora'].map((v) => ({ label: t(`finetune.opt.method.${v}`), value: v })));
  const modelOptions = computed(() =>
    (meta.value.baseModels || []).map((m) => ({
      label: `${m.name} · ${m.paramsB}B${m.recommended ? ' ★' : ''}${m.noteKey ? ' · ' + t(`finetune.modelNote.${m.noteKey}`) : ''}`,
      value: m.name,
    }))
  );
  const presetOptions = computed(() => (meta.value.presets || []).map((p) => ({ label: t(`finetune.preset.${p.key}.name`), value: p.key })));
  const presetDesc = computed(() => (presetKey.value ? t(`finetune.preset.${presetKey.value}.desc`) : ''));

  function optionList(f: ParamField) {
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
  function fillForm(params: Record<string, any>) {
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
    const p = (meta.value.presets || []).find((x) => x.key === key);
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
  const previewWarnings = computed<any[]>(() => preview.value.warnings || []);
  const vramTagType = computed<'success' | 'warning' | 'error'>(() => {
    const lv = preview.value.vram && preview.value.vram.level;
    return lv === 'danger' ? 'error' : lv === 'warn' ? 'warning' : 'success';
  });

  let previewTimer: ReturnType<typeof setTimeout> | null = null;
  function schedulePreview() {
    if (!createVisible.value) return;
    if (previewTimer) clearTimeout(previewTimer);
    previewTimer = setTimeout(runPreview, 350);
  }

  async function runPreview() {
    if (!createVisible.value) return;
    previewLoading.value = true;
    try {
      const res = await ctx.jobPreview({ ...form });
      preview.value = (res && res.data) || {};
    } finally {
      previewLoading.value = false;
    }
  }

  watch(form, schedulePreview, { deep: true });

  onUnmounted(() => {
    if (previewTimer) clearTimeout(previewTimer);
  });

  return {
    meta,
    createVisible,
    creating,
    datasetOptions,
    datasetList,
    presetKey,
    datasetStat,
    toCamel,
    form,
    groups,
    visibleGroups,
    snakeKeyOf,
    buildKeyMap,
    limit,
    methodOptions,
    modelOptions,
    presetOptions,
    presetDesc,
    optionList,
    fillForm,
    applyPreset,
    resetDefaults,
    applyMethodDefault,
    preview,
    previewLoading,
    previewWarnings,
    vramTagType,
    runPreview,
  };
}
/* eslint-enable @typescript-eslint/no-explicit-any */

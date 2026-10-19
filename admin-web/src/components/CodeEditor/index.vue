<template>
  <div class="code-editor">
    <!-- 高亮层：纯展示。文本与 textarea 严格一致，仅多了 <span class="tok-*"> 着色 -->
    <pre ref="shadowRef" class="ce-shadow" aria-hidden="true"><code v-html="html"></code></pre>
    <textarea
      ref="inputRef"
      class="ce-input"
      :value="modelValue"
      :readonly="readonly"
      spellcheck="false"
      wrap="off"
      @input="onInput"
      @scroll="syncScroll"
      @keydown.tab.prevent="insertTab"
    />
  </div>
</template>

<script lang="ts" setup>
  import { computed, ref } from 'vue';
  import { safeHtml } from '@/utils/sanitize';

  const props = withDefaults(
    defineProps<{
      modelValue: string;
      /** 语言的判定依据，通常传文件扩展名（含点）或语言名 */
      language?: string;
      readonly?: boolean;
    }>(),
    { language: '', readonly: false }
  );
  const emit = defineEmits(['update:modelValue']);

  const shadowRef = ref<HTMLElement | null>(null);
  const inputRef = ref<HTMLTextAreaElement | null>(null);

  /**
   * 超过这个体量就放弃着色，直接按纯文本渲染。
   * 着色是每次按键同步跑的，几百 KB 的文本会让输入明显卡顿，而这类文件本来也不该在抽屉里编。
   */
  const MAX_HIGHLIGHT_CHARS = 60000;

  type Rule = { re: string; cls: string; onlyAtStart?: boolean };

  const JS_KW =
    'const|let|var|function|return|if|else|for|while|do|switch|case|default|break|continue|new|class|extends|super|import|export|from|as|try|catch|finally|throw|typeof|instanceof|in|of|delete|void|await|async|yield|this|null|undefined|true|false';
  const PY_KW =
    'def|class|return|if|elif|else|for|while|import|from|as|with|try|except|finally|raise|yield|lambda|pass|break|continue|and|or|not|in|is|None|True|False|global|nonlocal|assert|del|async|await|self';
  const SH_KW = 'if|then|else|elif|fi|for|while|do|done|case|esac|function|return|export|local|echo|exit|set|read|shift|source';

  // 每条规则内部一律用非捕获组 (?:...)：回调靠"哪个外层捕获组非 undefined"来判定命中规则，
  // 内层一旦有捕获组，下标就会整体错位、张冠李戴。
  const DQ = '"(?:\\\\.|[^"\\\\\\n])*"';
  const SQ = "'(?:\\\\.|[^'\\\\\\n])*'";

  const RULES: Record<string, Rule[]> = {
    md: [
      // frontmatter：只认文件开头的 ---，否则正文里的水平分割线会把后面一大段吞掉（靠 onlyAtStart 兜底）
      { re: '^---\\n[\\s\\S]*?\\n---', cls: 'meta', onlyAtStart: true },
      { re: '```[\\s\\S]*?```', cls: 'code' },
      { re: '^#{1,6}[^\\n]*', cls: 'head' },
      { re: '`[^`\\n]+`', cls: 'code' },
      { re: '\\*\\*[^*\\n]+\\*\\*', cls: 'bold' },
      { re: '\\[[^\\]\\n]*\\]\\([^)\\n]*\\)', cls: 'link' },
      { re: '^\\s*(?:[-*+]|\\d+\\.)\\s', cls: 'bullet' },
      { re: '>[^\\n]*', cls: 'quote' },
    ],
    json: [
      { re: DQ + '(?=\\s*:)', cls: 'key' },
      { re: DQ, cls: 'str' },
      { re: '-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b', cls: 'num' },
      { re: '\\b(?:true|false|null)\\b', cls: 'kw' },
    ],
    py: [
      { re: '#[^\\n]*', cls: 'com' },
      { re: '"""[\\s\\S]*?"""', cls: 'str' },
      { re: "'''[\\s\\S]*?'''", cls: 'str' },
      { re: DQ, cls: 'str' },
      { re: SQ, cls: 'str' },
      { re: '^\\s*@[\\w.]+', cls: 'fn' },
      { re: '\\b(?:' + PY_KW + ')\\b', cls: 'kw' },
      { re: '\\b[A-Za-z_]\\w*(?=\\s*\\()', cls: 'fn' },
      { re: '\\b\\d+(?:\\.\\d+)?\\b', cls: 'num' },
    ],
    js: [
      { re: '//[^\\n]*|/\\*[\\s\\S]*?\\*/', cls: 'com' },
      { re: '`(?:\\\\.|[^`\\\\])*`', cls: 'str' },
      { re: DQ, cls: 'str' },
      { re: SQ, cls: 'str' },
      { re: '\\b(?:' + JS_KW + ')\\b', cls: 'kw' },
      { re: '\\b[A-Za-z_$][\\w$]*(?=\\s*\\()', cls: 'fn' },
      { re: '\\b[A-Za-z_$][\\w$]*(?=\\s*:)', cls: 'key' },
      { re: '\\b\\d+(?:\\.\\d+)?\\b', cls: 'num' },
    ],
    yaml: [
      { re: '#[^\\n]*', cls: 'com' },
      { re: '^[ \\t-]*[A-Za-z0-9_.$\\-]+(?=\\s*:)', cls: 'key' },
      { re: DQ, cls: 'str' },
      { re: "'(?:''|[^'\\n])*'", cls: 'str' },
      { re: '\\b(?:true|false|null|yes|no|on|off)\\b', cls: 'kw' },
      { re: '\\b\\d+(?:\\.\\d+)?\\b', cls: 'num' },
    ],
    sh: [
      { re: '#[^\\n]*', cls: 'com' },
      { re: DQ, cls: 'str' },
      { re: "'[^'\\n]*'", cls: 'str' },
      { re: '\\$\\{[^}]*\\}|\\$[A-Za-z_]\\w*', cls: 'var' },
      { re: '\\b(?:' + SH_KW + ')\\b', cls: 'kw' },
    ],
    text: [],
  };

  const EXT_LANG: Record<string, string> = {
    '.md': 'md',
    '.markdown': 'md',
    '.json': 'json',
    '.jsonl': 'json',
    '.py': 'py',
    '.js': 'js',
    '.mjs': 'js',
    '.cjs': 'js',
    '.ts': 'js',
    '.vue': 'js',
    '.html': 'js',
    '.htm': 'js',
    '.css': 'js',
    '.scss': 'js',
    '.less': 'js',
    '.yaml': 'yaml',
    '.yml': 'yaml',
    '.toml': 'yaml',
    '.ini': 'yaml',
    '.cfg': 'yaml',
    '.conf': 'yaml',
    '.properties': 'yaml',
    '.sh': 'sh',
    '.bash': 'sh',
    '.bat': 'sh',
    '.cmd': 'sh',
    '.ps1': 'sh',
  };

  function langOf(path: string): string {
    const lower = String(path || '').toLowerCase();
    const dot = lower.lastIndexOf('.');
    if (dot < 0) {
      return 'text';
    }
    return EXT_LANG[lower.slice(dot)] || 'text';
  }

  const lang = computed(() => (props.language ? langOf(props.language) : 'text'));

  /** 只转义会破坏标签结构的三个字符；引号必须原样保留，否则字符串规则再也匹配不到 */
  function escapeHtml(src: string): string {
    return src.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  const masterCache = new Map<string, { re: RegExp; rules: Rule[] }>();

  function masterOf(name: string) {
    const cached = masterCache.get(name);
    if (cached) {
      return cached;
    }
    const rules = RULES[name] || [];
    const built = { re: new RegExp(rules.map((r) => '(' + r.re + ')').join('|'), 'gm'), rules };
    masterCache.set(name, built);
    return built;
  }

  /**
   * 单趟着色：先把整段转义，再用一条「优先级排序的联合正则」扫一遍。
   * 不能对已插入的 span 二次 replace——那样会把标签本身也当成待着色的文本再包一层。
   */
  function highlight(src: string, name: string): string {
    const esc = escapeHtml(src);
    const { re, rules } = masterOf(name);
    if (!rules.length) {
      return esc;
    }
    return esc.replace(re, (match: string, ...args: any[]) => {
      const groups = args.slice(0, rules.length);
      const offset = args[args.length - 2] as number;
      const idx = groups.findIndex((g) => g !== undefined);
      if (idx < 0) {
        return match;
      }
      const rule = rules[idx];
      if (rule.onlyAtStart && offset !== 0) {
        return match;
      }
      return `<span class="tok-${rule.cls}">${match}</span>`;
    });
  }

  const html = computed(() => {
    const text = props.modelValue || '';
    if (text.length > MAX_HIGHLIGHT_CHARS) {
      return safeHtml(escapeHtml(text));
    }
    return safeHtml(highlight(text, lang.value));
  });

  function onInput(e: Event) {
    emit('update:modelValue', (e.target as HTMLTextAreaElement).value);
  }

  /**
   * 滚动同步：滚动条在 textarea 上，高亮层只跟着偏移。
   * 两层用同一套 font/padding/white-space:pre，行盒完全重合，才能保证光标与着色对得上。
   */
  function syncScroll() {
    const ta = inputRef.value;
    const shadow = shadowRef.value;
    if (!ta || !shadow) {
      return;
    }
    shadow.scrollTop = ta.scrollTop;
    shadow.scrollLeft = ta.scrollLeft;
  }

  /** Tab 插入两个空格（不跳焦点），够用且不会把光标弹走 */
  function insertTab(e: KeyboardEvent) {
    const ta = e.target as HTMLTextAreaElement;
    const start = ta.selectionStart;
    const end = ta.selectionEnd;
    const next = ta.value.slice(0, start) + '  ' + ta.value.slice(end);
    emit('update:modelValue', next);
    requestAnimationFrame(() => {
      ta.selectionStart = ta.selectionEnd = start + 2;
    });
  }
</script>

<style lang="less" scoped>
  .code-editor {
    position: relative;
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
    font-family: Consolas, Monaco, 'Courier New', monospace;
    font-size: 12px;
    line-height: 1.65;
    background: #f7f8fa;
    border: 1px solid #e5e6eb;
    border-radius: 4px;

    .ce-shadow,
    .ce-input {
      position: absolute;
      top: 0;
      left: 0;
      box-sizing: border-box;
      width: 100%;
      height: 100%;
      padding: 10px 12px;
      margin: 0;
      border: 0;
      font: inherit;
      line-height: inherit;
      white-space: pre;
      tab-size: 4;
    }

    .ce-shadow {
      overflow: hidden;
      color: #333;
      pointer-events: none;

      code {
        font: inherit;
      }
    }

    .ce-input {
      overflow: auto;
      color: transparent;
      background: transparent;
      caret-color: #333;
      outline: none;
      resize: none;

      &::selection {
        background: #cfe3ff;
      }
    }

    :deep(.tok-meta) {
      color: #8250df;
    }

    :deep(.tok-head) {
      font-weight: 600;
      color: #0550ae;
    }

    :deep(.tok-bold) {
      font-weight: 600;
      color: #1f2328;
    }

    :deep(.tok-code) {
      color: #0a3069;
      background: #eef1f4;
    }

    :deep(.tok-link) {
      color: #0969da;
    }

    :deep(.tok-bullet) {
      color: #cf222e;
    }

    :deep(.tok-quote) {
      color: #6e7781;
    }

    :deep(.tok-str) {
      color: #0a3069;
    }

    :deep(.tok-key) {
      color: #0550ae;
    }

    :deep(.tok-num) {
      color: #0550ae;
    }

    :deep(.tok-kw) {
      color: #cf222e;
    }

    :deep(.tok-fn) {
      color: #8250df;
    }

    :deep(.tok-var) {
      color: #953800;
    }

    :deep(.tok-com) {
      font-style: italic;
      color: #6e7781;
    }
  }
</style>

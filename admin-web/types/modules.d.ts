/// <reference types="vite/client" />
declare module '*.vue' {
  import { DefineComponent } from 'vue';
  const Component: DefineComponent<{}, {}, any>;
  export default Component;
}

declare module 'virtual:*' {
  const result: any;
  export default result;
}

// T5-14: noImplicitAny 收紧后补的无类型依赖声明
declare module 'element-resize-detector' {
  const erd: (options?: Record<string, unknown>) => {
    listenTo: (el: Element, cb: (el: Element) => void) => void
    listenToOptions?: (el: Element, cb: (el: Element) => void, opts?: Record<string, unknown>) => void
    uninstall: (el: Element) => void
    removeListener: (el: Element, cb?: (el: Element) => void) => void
  }
  export default erd
}

declare module 'mockjs' {
  const Mock: {
    mock: (template: any) => any
    setup: (settings?: Record<string, unknown>) => void
    Random: Record<string, any>
    [key: string]: any
  }
  export default Mock
  export const Random: Record<string, any>
}

declare module 'markdown-it-link-attributes' {
  import type MarkdownIt from 'markdown-it'
  const mila: MarkdownIt.PluginSimple
  export default mila
}

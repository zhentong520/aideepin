// 这些花括号是**给用户看的字面文本**（工作流占位符语法），不是 i18n 的插值占位符。
// vue-i18n 会把文案里的 {xxx} 当插值处理：不处理的话，开发模式每次渲染都报
// "Message compilation error"，生产模式（__DEV__ = false）更是直接抛 SyntaxError，
// 导致整个属性面板渲染失败。
//
// 约定（原本分散在 ReferComment / ReferTooltip 里，2026-09-22 收敛到这里）：
// 文案里写 ASCII 插值名，真实文本由本表给出，调用方用 t(key, braceLiteral) 传入 ——
// 插值值是原样拼接的，不会再被解析。这样文案本身保持可读，也避免在 locale 文件里
// 塞 {'{'} 这类难读的转义序列。
export const braceLiteral = {
  /** 普通占位符写法 */
  input: '{input}',
  /** 自定义变量名示例 */
  var_name1: '{var_name1}',
  /** 变量示例用的两个名字 */
  x: '{x}',
  y: '{y}',
  /** 上游参数占位符，如 {order_id} */
  name: '{name}',
  /** 例子中的具体参数名 */
  city: '{city}',
  /** Database 节点保留兼容的旧式双花括号写法 */
  legacyInput: '{{input}}',
  /** 一个完整的 JSON 入参示例 */
  jsonExample: '{"city": "{input}"}',
}

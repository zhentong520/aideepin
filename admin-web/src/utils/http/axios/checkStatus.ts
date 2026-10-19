export function checkStatus(status: number, msg: string): void {
  const $message = window['$message']
  switch (status) {
    case 400:
      $message.error(msg)
      break
    // 401: 未登录/会话过期
    // FE-01：具体处置（清 token + 跳登录 + 防重复）在 axios/index.ts 的 401 分支统一做，
    // 这里只负责非误导性的提示——「没有权限」会让会话过期的用户误以为权限配置错了。
    case 401:
      $message.error('登录状态已过期，请重新登录')
      break
    case 403:
      $message.error('用户得到授权，但是访问是被禁止的。!')
      break
    // 404请求不存在
    case 404:
      $message.error('网络请求错误，未找到该资源!')
      break
    case 405:
      $message.error('网络请求错误，请求方法未允许!')
      break
    case 408:
      $message.error('网络请求超时')
      break
    case 500:
      $message.error('服务器错误,请联系管理员!')
      break
    case 501:
      $message.error('网络未实现')
      break
    case 502:
      $message.error('网络错误')
      break
    case 503:
      $message.error('服务不可用，服务器暂时过载或维护!')
      break
    case 504:
      $message.error('网络超时')
      break
    case 505:
      $message.error('http版本不支持该请求!')
      break
    default:
      $message.error(msg)
  }
}

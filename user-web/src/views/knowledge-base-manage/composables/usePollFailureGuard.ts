/**
 * 轮询失败护栏（自 DocumentDetail 拆出，T5-10）：
 * 连续静默失败（文档被删、token 过期、断网）达到上限即停轮询并提示一次，
 * 防止轮询器在永久失败的请求上空转。
 */
export const MAX_POLL_FAILURES = 5

export function usePollFailureGuard(onCapped: () => void) {
  let pollFailureCount = 0
  let pollFailureReported = false

  function resetPollFailures() {
    pollFailureCount = 0
    pollFailureReported = false
  }

  function recordPollFailure() {
    pollFailureCount++
    if (pollFailureCount >= MAX_POLL_FAILURES && !pollFailureReported) {
      pollFailureReported = true
      onCapped()
    }
  }

  function canPoll() {
    return pollFailureCount < MAX_POLL_FAILURES
  }

  return { resetPollFailures, recordPollFailure, canPoll }
}

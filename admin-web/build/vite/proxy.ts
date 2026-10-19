/**
 * Used to parse the .env.development proxy configuration
 */
import type { ProxyOptions } from 'vite';

type ProxyItem = [string, string];

type ProxyList = ProxyItem[];

type ProxyTargetList = Record<string, ProxyOptions & { rewrite: (path: string) => string }>;

const httpsRE = /^https:\/\//;

/**
 * Generate proxy
 * @param list
 */
export function createProxy(list: ProxyList = []) {
  const ret: ProxyTargetList = {};
  for (const [prefix, target] of list) {
    if (!prefix || !target) {
      // 跳过缺 target 的条目：否则 http-proxy 会抛 "Must provide a proper URL as target"，
      // 而 vite 3.x 的 proxy error 处理器对无 res 的 error 会再崩一次（见 vite dep-*.js）。
      console.warn(`[createProxy] 跳过无效代理条目: prefix=${prefix}, target=${target}`);
      continue;
    }
    const isHttps = httpsRE.test(target);

    // https://github.com/http-party/node-http-proxy#options
    ret[prefix] = {
      target: target,
      changeOrigin: true,
      ws: true,
      rewrite: (path) => path.replace(new RegExp(`^${prefix}`), ''),
      // https is require secure=false
      ...(isHttps ? { secure: false } : {}),
    };
  }
  return ret;
}

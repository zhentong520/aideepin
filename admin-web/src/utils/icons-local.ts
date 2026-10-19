/**
 * 图标本地化注册：把项目实际用到的全部 Iconify 图标在启动时注册进本地注册表。
 * 背景：@iconify/vue 默认运行时从 api.iconify.design / api.simplesvg.com / api.unisvg.com
 * 并行拉取图标数据，国内网络下 unisvg 等不可达会在控制台产生 ERR_CONNECTION_TIMED_OUT，
 * 且图标可能加载失败。本地注册后运行时不再发起任何外网请求。
 *
 * 维护方式：新增图标后，到 https://api.iconify.design/<prefix>.json?icons=<name>
 * 下载对应 JSON 放入 src/assets/icons/，并在下方 import + 注册。
 */
import { addCollection } from '@iconify/vue'

import carbon from '@/assets/icons/carbon.json'
import covid from '@/assets/icons/covid.json'
import ep from '@/assets/icons/ep.json'
import fluentMdl2 from '@/assets/icons/fluent-mdl2.json'
import healthicons from '@/assets/icons/healthicons.json'
import mage from '@/assets/icons/mage.json'
import mingcute from '@/assets/icons/mingcute.json'
import oui from '@/assets/icons/oui.json'
import ri from '@/assets/icons/ri.json'
import si from '@/assets/icons/si.json'
import solar from '@/assets/icons/solar.json'
import tabler from '@/assets/icons/tabler.json'
import typcn from '@/assets/icons/typcn.json'

const packs = [
  carbon, covid, ep, fluentMdl2, healthicons, mage, mingcute,
  oui, ri, si, solar, tabler, typcn,
]

export function setupLocalIcons() {
  for (const data of packs)
    addCollection(data as never)
}

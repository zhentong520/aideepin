/**
 * S2-07（ADR-005）步骤①：共享包节点描述数据完整性守卫。
 * descriptor 单源下沉后，wfNodeDescriptorData 是两端共用的定义源头——
 * 新增节点漏字段 / type 重复 / 图标缺失 等问题在这里第一时间暴露。
 */
import { describe, expect, it } from 'vitest'
import {
  getWfNodeDescriptorData,
  getWfNodePreset,
  wfNodeDescriptorData,
  wfNodePresets,
} from 'aideepin-wf-shared'

describe('wf-shared node descriptor data', () => {
  it('covers the 25 node types aligned with backend WfNodeFactory cases', () => {
    const types = wfNodeDescriptorData.map(d => d.type)
    expect(types).toHaveLength(25)
    expect(new Set(types).size).toBe(25)
    expect(types).toEqual(expect.arrayContaining([
      'Start', 'End', 'Answer', 'Classifier', 'Switcher', 'Template',
      'KeywordExtractor', 'DocumentExtractor', 'FaqExtractor', 'KnowledgeRetrieval',
      'Google', 'OpenAiImage', 'Tongyiwanx', 'HumanFeedback', 'MailSend', 'HttpRequest',
      'Agent', 'Database', 'Mcp', 'Skill', 'Orchestrator', 'Loop', 'Parallel', 'Code', 'SubWorkflow',
    ]))
  })

  it('every descriptor has non-empty icon / iconClass / title / configSchema / doc', () => {
    for (const d of wfNodeDescriptorData) {
      expect(d.icon, `${d.type}.icon`).not.toBe('')
      expect(d.iconClass, `${d.type}.iconClass`).not.toBe('')
      expect(d.componentTitle.zh, `${d.type}.componentTitle.zh`).not.toBe('')
      expect(d.componentTitle.en, `${d.type}.componentTitle.en`).not.toBe('')
      expect(Array.isArray(d.configSchema), `${d.type}.configSchema`).toBe(true)
      expect(d.doc.zh.purpose, `${d.type}.doc.zh.purpose`).not.toBe('')
      expect(d.doc.en.purpose, `${d.type}.doc.en.purpose`).not.toBe('')
      expect(typeof d.hasInput).toBe('boolean')
      expect(typeof d.hasOutput).toBe('boolean')
    }
  })

  it('configSchema fields reference valid types and localized labels', () => {
    const validFieldTypes = new Set([
      'input', 'textarea', 'number', 'switch', 'select',
      'modelSelector', 'imageModelSelector', 'kbSelector', 'agentSelector',
      'skillSelector', 'datasourceSelector', 'mcpSelector',
      'kvList', 'userInputs', 'categoryList', 'conditionCases',
    ])
    for (const d of wfNodeDescriptorData) {
      for (const f of d.configSchema) {
        expect(validFieldTypes.has(f.type), `${d.type}.${f.name}.type=${f.type}`).toBe(true)
        expect(f.label.zh, `${d.type}.${f.name}.label.zh`).not.toBe('')
        expect(f.label.en, `${d.type}.${f.name}.label.en`).not.toBe('')
      }
    }
  })

  it('lookup helpers resolve case-insensitively and return undefined on miss', () => {
    expect(getWfNodeDescriptorData('answer')?.type).toBe('Answer')
    expect(getWfNodeDescriptorData('KNOWLEDGERETRIEVAL')?.type).toBe('KnowledgeRetrieval')
    expect(getWfNodeDescriptorData('')).toBeUndefined()
    expect(getWfNodeDescriptorData('NoSuchType')).toBeUndefined()
  })

  it('presets reference existing node types and valid edge indexes', () => {
    expect(wfNodePresets.length).toBeGreaterThanOrEqual(3)
    for (const preset of wfNodePresets) {
      for (const node of preset.nodes)
        expect(getWfNodeDescriptorData(node.type), `${preset.key}:${node.type}`).toBeDefined()
      for (const [s, t] of preset.edges) {
        expect(s, `${preset.key} edge src`).toBeLessThan(preset.nodes.length)
        expect(t, `${preset.key} edge dst`).toBeLessThan(preset.nodes.length)
      }
      expect(getWfNodePreset(preset.key)).toBe(preset)
    }
    expect(getWfNodePreset('no-such-key')).toBeUndefined()
  })
})

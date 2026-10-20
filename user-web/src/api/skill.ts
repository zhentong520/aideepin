import { get, post } from '@/utils/request'

// Agent Skills
function skillPublicSearch<T>(keyword: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/skill/public/search?keyword=${keyword}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function skillPublicDetail<T>(uuid: string) {
  return get<T>({
    url: `/skill/public/${uuid}`,
  })
}

function userSkillList<T>() {
  return get<T>({
    url: '/user/skill/list',
  })
}

function userSkillSaveOrUpdate<T>(skillId: number, isEnable: boolean) {
  return post<T>({
    url: `/user/skill/saveOrUpdate?skillId=${skillId}&isEnable=${isEnable}`,
  })
}

// 技能目录内的文件清单（供 Skill 节点选择要执行的脚本）
function skillFiles<T>(uuid: string) {
  return get<T>({
    url: `/skill/${uuid}/files`,
  })
}

export default {
  skillPublicSearch,
  skillPublicDetail,
  userSkillList,
  userSkillSaveOrUpdate,
  skillFiles,
}

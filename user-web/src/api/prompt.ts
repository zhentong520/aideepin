import { get, post } from '@/utils/request'

function searchPrompts<T>(currentPage: number, pageSize: number, keyword?: string) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  return get<T>({
    url: `/prompt/my/search?${search}currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function promptAutocomplete<T>(keyword: string) {
  const search = keyword === undefined ? '' : `keyword=${keyword}`
  return get<T>({
    url: `/prompt/my/autocomplete?${search}`,
  })
}

function promptsSave<T>(prompts: { act: string; prompt: string }[]) {
  return post<T>({
    url: '/prompt/save',
    data: { prompts },
  })
}

function promptDel<T>(id: number) {
  return post<T>({
    url: `/prompt/del/${id}`,
  })
}

function promptEdit<T>(id: number, title: string, remark: string) {
  return post<T>({
    url: `/prompt/edit/${id}`,
    data: { title, remark },
  })
}

export default {
  searchPrompts,
  promptAutocomplete,
  promptsSave,
  promptDel,
  promptEdit,
}

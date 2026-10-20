import { http } from '@/utils/http/axios'

function searchPresetCharacters(data: any, params: { current: number; size: number }) {
  return http.request({
    url: `/admin/character-preset/search?currentPage=${params.current}&pageSize=${params.size}`,
    method: 'post',
    data,
  })
}

function addPresetCharacter(data: any) {
  return http.request({
    url: '/admin/character-preset/addOne',
    method: 'post',
    data,
  })
}

function editPresetCharacter(uuid: string, data: any) {
  return http.request({
    url: `/admin/character-preset/edit/${uuid}`,
    method: 'post',
    data,
  })
}

function deletePresetCharacter(uuid: string) {
  return http.request({
    url: `/admin/character-preset/del/${uuid}`,
    method: 'POST',
  })
}

function searchCharacters(data: any, params: { current: number; size: number }) {
  return http.request({
    url: `/admin/character/search?currentPage=${params.current}&pageSize=${params.size}`,
    method: 'post',
    data,
  })
}

function editCharacter(uuid: string, data: any) {
  return http.request({
    url: `/admin/character/edit/${uuid}`,
    method: 'post',
    data,
  })
}

function deleteCharacter(uuid: string) {
  return http.request({
    url: `/admin/character/del/${uuid}`,
    method: 'POST',
  })
}

export default {
  searchPresetCharacters,
  addPresetCharacter,
  editPresetCharacter,
  deletePresetCharacter,
  searchCharacters,
  editCharacter,
  deleteCharacter,
}

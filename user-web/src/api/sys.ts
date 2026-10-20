import { get, getRawAxios, post } from '@/utils/request'

function getSysConfig<T>() {
  return get<T>({
    url: '/sys/config/public/info',
  })
}

function fetchUserConfig<T>() {
  return get<T>({
    url: '/user/config',
  })
}

function loadSearchEngines<T>() {
  return get<T>({
    url: '/auth/search-engine/list',
  })
}

function loadLLMs<T>() {
  return get<T>({
    url: '/model/llms',
  })
}

function loadImageModels<T>() {
  return get<T>({
    url: '/model/imageModels',
  })
}

function fileUpload<T>(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<T>({
    url: '/file/upload',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

function fileDel<T>(uuid: string) {
  return post<T>({
    url: `/file/del/${uuid}`,
  })
}

function loadFileContent(fileUrl: string) {
  return getRawAxios().get(fileUrl, {
    responseType: 'text',
  })
}

// External API Key management (resource-level)
function extApiKeyGenerate<T>(type: string, uuid: string) {
  return post<T>({ url: `/external-api-key/${type}/${uuid}` })
}
function extApiKeyInfo<T>(type: string, uuid: string) {
  return get<T>({ url: `/external-api-key/${type}/${uuid}` })
}
function extApiKeyReveal<T>(type: string, uuid: string) {
  return post<T>({ url: `/external-api-key/${type}/${uuid}/reveal` })
}

// External API Key management (user-level: draw, mcp)
function extApiKeyUserGenerate<T>(type: string) {
  return post<T>({ url: `/external-api-key/user/${type}` })
}
function extApiKeyUserInfo<T>(type: string) {
  return get<T>({ url: `/external-api-key/user/${type}` })
}
function extApiKeyUserReveal<T>(type: string) {
  return post<T>({ url: `/external-api-key/user/${type}/reveal` })
}

export default {
  getSysConfig,
  fetchUserConfig,
  loadSearchEngines,
  loadLLMs,
  loadImageModels,
  fileUpload,
  fileDel,
  loadFileContent,
  extApiKeyGenerate,
  extApiKeyInfo,
  extApiKeyReveal,
  extApiKeyUserGenerate,
  extApiKeyUserInfo,
  extApiKeyUserReveal,
}

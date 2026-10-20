import { get, post } from '@/utils/request'

function fetchDraw<T>(uuid: string) {
  return get<T>({
    url: `/draw/detail/${uuid}`,
  })
}

function fetchNewerPublicDraw<T>(uuid: string) {
  return get<T>({
    url: `/draw/detail/newer-public/${uuid}`,
  })
}

function fetchOlderPublicDraw<T>(uuid: string) {
  return get<T>({
    url: `/draw/detail/older-public/${uuid}`,
  })
}

function fetchNewerStarredDraw<T>(uuid: string) {
  return get<T>({
    url: `/draw/detail/newer-starred/${uuid}`,
  })
}

function fetchOlderStarredDraw<T>(uuid: string) {
  return get<T>({
    url: `/draw/detail/older-starred/${uuid}`,
  })
}

function fetchNewerMineDraw<T>(uuid: string) {
  return get<T>({
    url: `/draw/detail/newer-mine/${uuid}`,
  })
}

function fetchOlderMineDraw<T>(uuid: string) {
  return get<T>({
    url: `/draw/detail/older-mine/${uuid}`,
  })
}

function fetchDraws<T>(maxId: number, pageSize: number) {
  return get<T>({
    url: `/draw/list?maxId=${maxId}&pageSize=${pageSize}`,
  })
}

function fetchStarDraws<T>(maxId: number, pageSize: number) {
  return get<T>({
    url: `/draw/star/mine?maxId=${maxId}&pageSize=${pageSize}`,
  })
}

function fetchPublicDraws<T>(maxId: number, pageSize: number) {
  return get<T>({
    url: `/draw/public/list?maxId=${maxId}&pageSize=${pageSize}`,
  })
}

function fetchDrawComments<T>(uuid: string, curentPage: number, pageSize: number) {
  return get<T>({
    url: `/draw/comment/list/${uuid}?curentPage=${curentPage}&pageSize=${pageSize}`,
  })
}

function drawStarOrUnStar<T>(uuid: string) {
  return post<T>({
    url: `/draw/star/toggle/${uuid}`,
  })
}

function drawCommentAdd<T>(uuid: string, comment: string) {
  return post<T>({
    url: '/draw/comment/add',
    data: { drawUuid: uuid, comment },
  })
}

function imageGenerate<T>(params: ImageGenerationParams) {
  return post<T>({
    url: '/draw/generation',
    data: params,
  })
}

/**
 * 删除作图任务
 * @param uuid 作图任务uuid
 */
function drawDel<T>(uuid: string) {
  return post<T>({
    url: `/draw/del/${uuid}`,
  })
}

function drawSetPublic<T>(uuid: string, isPublic: boolean) {
  return post<T>({
    url: `/draw/set-public/${uuid}?isPublic=${isPublic}`,
  })
}

/**
 * 删除作图任务中的一张图片
 * @param uuid 作图任务uuid
 * @param fileUuid 图片文件uuid
 */
function drawFileDel<T>(uuid: string, fileUuid: string) {
  return post<T>({
    url: `/draw/file/del/${fileUuid}?uuid=${uuid}`,
  })
}

function drawPublicImage<T>(uuid: string, imageUuid: string) {
  return post<T>({
    url: `/draw/public/image/${uuid}/${imageUuid}`,
  })
}

function drawThumbnailImage<T>(uuid: string, imageUuid: string) {
  return post<T>({
    url: `/draw/public/thumnbnail/${uuid}/${imageUuid}`,
  })
}

export default {
  fetchDraw,
  fetchNewerPublicDraw,
  fetchOlderPublicDraw,
  fetchNewerStarredDraw,
  fetchOlderStarredDraw,
  fetchNewerMineDraw,
  fetchOlderMineDraw,
  fetchDraws,
  fetchStarDraws,
  fetchPublicDraws,
  fetchDrawComments,
  drawStarOrUnStar,
  drawCommentAdd,
  imageGenerate,
  drawDel,
  drawSetPublic,
  drawFileDel,
  drawPublicImage,
  drawThumbnailImage,
}

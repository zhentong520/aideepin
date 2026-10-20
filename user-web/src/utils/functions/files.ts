export function calcImageUrls(draw: Chat.Draw) {
  // draw.imageUrls = draw.imageUuids.map((item) => {
  //   if (draw.isPublic)
  //     return `/draw/public/image/${draw.uuid}/${item}`
  //   else
  //     return `/image/${item}`
  // })
  for (let i = 0; i < draw.imageUrls.length; i++) {
    const url = draw.imageUrls[i]
    if (!url.includes('http')) {
      if (draw.isPublic)
        draw.imageUrls[i] = `/api/draw/public/image/${draw.uuid}/${changeFileUrlToUuid(url)}`
      else
        draw.imageUrls[i] = `/api${url}`
    }
  }
}

export function getRealFileUrl(fileUrl: string) {
  if (!fileUrl.includes('http') && !fileUrl.includes('/api'))
    return `/api${fileUrl}`
  else
    return fileUrl
}

export function changeFileUrlToUuid(fileUrl: string) {
  if (!fileUrl.includes('/'))
    return fileUrl
  return fileUrl.substring(fileUrl.lastIndexOf('/') + 1)
}

export function fillCompleteUrl(fileUrl: string) {
  if (!fileUrl.includes('/') || fileUrl.indexOf('/api') === 0)
    return fileUrl
  return `/api${fileUrl}`
}

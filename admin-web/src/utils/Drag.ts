//获取相关CSS属性
const getCss = function (o: HTMLElement, key: string): string {
  const ie = (o as unknown as { currentStyle?: Record<string, string> }).currentStyle
  if (ie) return ie[key]
  return ((document.defaultView?.getComputedStyle(o, null) as unknown as Record<string, string>) || {})[key]
}

const params = {
  left: 0 as number | string,
  top: 0 as number | string,
  currentX: 0,
  currentY: 0,
  flag: false,
}

const startDrag = function (bar: HTMLElement, target: HTMLElement, callback?: (...args: any[]) => void) {
  const screenWidth = document.body.clientWidth // body当前宽度
  const screenHeight = document.documentElement.clientHeight // 可见区域高度

  const dragDomW = target.offsetWidth // 对话框宽度
  const dragDomH = target.offsetHeight // 对话框高度

  const minDomLeft = target.offsetLeft
  const minDomTop = target.offsetTop

  const maxDragDomLeft = screenWidth - minDomLeft - dragDomW
  const maxDragDomTop = screenHeight - minDomTop - dragDomH

  if (getCss(target, 'left') !== 'auto') {
    params.left = getCss(target, 'left')
  }
  if (getCss(target, 'top') !== 'auto') {
    params.top = getCss(target, 'top')
  }

  //o是移动对象
  bar.onmousedown = function (event: MouseEvent) {
    params.flag = true
    if (!event) {
      event = window.event as MouseEvent
      //防止IE文字选中
      bar.onselectstart = function () {
        return false
      }
    }
    const e = event
    params.currentX = e.clientX
    params.currentY = e.clientY
  }
  document.onmouseup = function () {
    params.flag = false
    if (getCss(target, 'left') !== 'auto') {
      params.left = getCss(target, 'left')
    }
    if (getCss(target, 'top') !== 'auto') {
      params.top = getCss(target, 'top')
    }
  }
  document.onmousemove = function (event: MouseEvent) {
    const e: any = event ? event : window.event
    if (params.flag) {
      const nowX = e.clientX,
        nowY = e.clientY
      const disX = nowX - params.currentX,
        disY = nowY - params.currentY

      let left = parseInt(params.left) + disX
      let top = parseInt(params.top) + disY

      // 拖出屏幕边缘
      if (-left > minDomLeft) {
        left = -minDomLeft
      } else if (left > maxDragDomLeft) {
        left = maxDragDomLeft
      }

      if (-top > minDomTop) {
        top = -minDomTop
      } else if (top > maxDragDomTop) {
        top = maxDragDomTop
      }

      target.style.left = left + 'px'
      target.style.top = top + 'px'

      if (typeof callback == 'function') {
        callback((parseInt(params.left) || 0) + disX, (parseInt(params.top) || 0) + disY)
      }

      if (event.preventDefault) {
        event.preventDefault()
      }
      return false
    }
  }
}

export default startDrag

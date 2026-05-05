/**
 * pixi-live2d-display yêu cầu window.PIXI phải tồn tại khi module load.
 * File này phải được import TRƯỚC pixi-live2d-display.
 */
import * as PIXI from 'pixi.js'
;(window as any).PIXI = PIXI

/**
 * Monkey-patch: pixi.js v7 đổi API event system, dùng isInteractive() thay vì
 * property interactive. pixi-live2d-display@0.4 chưa cập nhật theo nên bị crash.
 * Patch này thêm isInteractive() vào bất kỳ DisplayObject nào còn thiếu.
 */
function patchPixiEventSystem() {
  const EventBoundary = (PIXI as any).EventBoundary
  if (!EventBoundary) return

  const proto = EventBoundary.prototype

  const addIsInteractive = (target: any) => {
    if (target && typeof target.isInteractive !== 'function') {
      target.isInteractive = () =>
        !!(target.interactive ||
           target.eventMode === 'static' ||
           target.eventMode === 'dynamic')
    }
  }

  const wrap = (fn: Function) =>
    function (this: any, currentTarget: any, ...rest: any[]) {
      addIsInteractive(currentTarget)
      return fn.call(this, currentTarget, ...rest)
    }

  if (proto.hitTestMoveRecursive) proto.hitTestMoveRecursive = wrap(proto.hitTestMoveRecursive)
  if (proto.hitTestRecursive)     proto.hitTestRecursive     = wrap(proto.hitTestRecursive)
}

patchPixiEventSystem()

export { PIXI }

// ============================================
// 01 · globalThis
// ============================================
// 这个文件刻意【不标】requires-transform：Rhino 里 globalThis 是 undefined，
// 但运行器会像线上 mixin 一样先装好内建补丁，所以「原文」也能跑。
// 它验证的是「补丁装上了、而且通过完整脚本链路能用」，不是「必须靠转换」。

__check('globalThis.Math 可用', globalThis.Math.max(1, 2), 2)
__check('globalThis 就是全局对象', globalThis.Object === Object, 'true')
__check('通过 globalThis 取 JSON', typeof globalThis.JSON.stringify, 'function')

const key = 'value'
globalThis[key] = 42
__check('能往 globalThis 上挂东西', globalThis.value, 42)

__check('globalThis 上的函数能调用', globalThis.Array.isArray([]), 'true')

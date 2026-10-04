// ============================================
// 13-07 · 可选调用（f?.()）与可选下标（a?.[0]）→ 显式判断
// ============================================
// requires-transform
//
// sweep 逐文件结果（同一个 `?.` 家族，支持的形态并不一样）：
//   a?.b    取属性 → PASS          （转换器能处理，可以直接写）
//   f?.()   可选调用 → UNSUPPORTED_SYNTAX
//   a?.[0]  可选下标 → UNSUPPORTED_SYNTAX
// 后两种线上会以语法错误拦下 —— 是响亮失败，不会静默算错。
//
// Rhino 能跑的等价写法：
//   f?.()   → typeof f === 'function' ? f() : undefined
//   a?.[0]  → a && a[0]        （a 为 null/undefined 时结果就是 a 本身，与 ?. 一致）

const table = { list: [10, 20] }
const blank = { list: null }

function maybeCall(fn) {
    return typeof fn === 'function' ? fn() : undefined
}

const first = table.list && table.list[0]
const none = blank.list && blank.list[0]
const called = maybeCall(() => 'hi')
const notCalled = maybeCall(null)

__check('&& 取下标', first, 10)
__check('&& 短路保持 null 语义', none, null)
__check('显式判断后调用', called, 'hi')
__check('不是函数就不调用', notCalled, undefined)
__check('展开仍由转换器负责', [...table.list].join(','), '10,20')

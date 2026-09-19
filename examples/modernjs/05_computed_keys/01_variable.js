// ============================================
// 01 · 计算属性名（变量）
// ============================================
// requires-transform
//
// KubeJS 里用变量拼属性名很常见，比如 { [itemId]: count }。
// Rhino 直接报 "invalid property id"。

const k = 'a'
const o = { [k]: 1 }

__check('变量当属性名', o.a, 1)
__check('键名确实来自变量', Object.keys(o).join(','), 'a')

const dynamic = 'b'
const both = { [dynamic]: 2, plain: 3 }

__check('计算属性名与普通成员共存（计算）', both.b, 2)
__check('计算属性名与普通成员共存（普通）', both.plain, 3)

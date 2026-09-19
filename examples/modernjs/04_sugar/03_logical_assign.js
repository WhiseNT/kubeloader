// ============================================
// 03 · 逻辑赋值 ||= / &&= / ??=
// ============================================
// requires-transform
//
// 三种语义要分清：
//   a ||= b   —— a 为假值时赋值（0 / '' / false / null / undefined 都算假）
//   a &&= b   —— a 为真值时赋值
//   a ??= b   —— 只有 null / undefined 才赋值（0 / '' / false 保留）
//
// 特别注意 ??= 不能写成 ||=，否则 0 和 '' 会被误替换（静默算错）。

let orNull = null
orNull ||= 5

let orTruthy = 1
orTruthy ||= 5

let orZero = 0
orZero ||= 5

let andNull = null
andNull &&= 5

let andTruthy = 1
andTruthy &&= 5

let coalesceNull = null
coalesceNull ??= 5

let coalesceZero = 0
coalesceZero ??= 5

let coalesceEmpty = ''
coalesceEmpty ??= 'filled'

let coalesceUndefined
coalesceUndefined ??= 'set'

const obj = {}
obj.count ||= 1
obj.count ||= 2

__check('||= 左侧 null → 赋值', orNull, 5)
__check('||= 左侧真值 → 保留', orTruthy, 1)
__check('||= 左侧 0 → 赋值（0 是假值）', orZero, 5)
__check('&&= 左侧 null → 保留', andNull, 'null')
__check('&&= 左侧真值 → 赋值', andTruthy, 5)
__check('??= 左侧 null → 赋值', coalesceNull, 5)
__check('??= 左侧 0 → 保留（关键区别）', coalesceZero, 0)
__check("??= 左侧空串 → 保留（关键区别）", coalesceEmpty, '')
__check('??= 左侧 undefined → 赋值', coalesceUndefined, 'set')
__check('成员左值：第一次赋值', obj.count, 1)

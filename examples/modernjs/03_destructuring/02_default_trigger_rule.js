// ============================================
// 02 · 默认值的触发条件：只有 undefined 才替换
// ============================================
// requires-transform
//
// 与 ES 规范一致：null 是「有值」，不触发默认值。
// 这个细节最容易写成 `|| 默认值` 而悄悄算错（null / 0 / '' / false 都会被误替换）。

const withValue = { a: 5 }
const withUndefined = { a: undefined }
const withNull = { a: null }
const withZero = { a: 0 }
const withEmpty = { a: '' }
const withFalse = { a: false }

const { a = 1 } = withValue
__check('有值时用真值', a, 5)

const { a: fromUndefined = 1 } = withUndefined
__check('值为 undefined → 用默认值', fromUndefined, 1)

const { a: fromNull = 1 } = withNull
__check('值为 null → 不替换', fromNull, 'null')

const { a: fromZero = 1 } = withZero
__check('值为 0 → 不替换', fromZero, 0)

const { a: fromEmpty = 'x' } = withEmpty
__check("值为空串 → 不替换", fromEmpty, '')

const { a: fromFalse = true } = withFalse
__check('值为 false → 不替换', fromFalse, 'false')

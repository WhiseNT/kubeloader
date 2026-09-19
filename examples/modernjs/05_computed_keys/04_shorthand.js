// ============================================
// 04 · 简写属性与计算属性名混用
// ============================================
// requires-transform
//
// { a, [k]: 2 } —— 简写属性（值就是同名变量）和计算属性名放在同一个对象里，
// 转换器得分别处理这两种成员的写法。

const a = 1
const k = 'b'

const o = { a, [k]: 2 }

__check('简写属性', o.a, 1)
__check('计算属性名', o.b, 2)

const name = 'x'
const value = 5
const pair = { name, value, ['extra']: value + 1 }

__check('多个简写属性', pair.name + '/' + pair.value, 'x/5')
__check('简写与计算混用', pair.extra, 6)

const shorthandObject = { inner: { a, [k]: 2 }.a }
__check('嵌套对象里的简写与计算', shorthandObject.inner, 1)

// ============================================
// 01 · 对象解构默认值（var / let / const）
// ============================================
// requires-transform
//
// Rhino 不认这种写法，而且报错极其误导：{ a = 1 } 报的是
// "missing ( before function parameters"，看报错根本猜不到跟解构有关。
// 所以转换器要把它摊平：a = 对象["a"] === undefined ? 1 : 对象["a"]。

const empty = {}
const one = { a: 5 }

const { a = 1 } = empty
__check('const 声明：缺省命中默认值', a, 1)

var { b = 2 } = one
__check('var 声明：没有该键则用默认值', b, 2)

let { a: renamed = 9 } = one
__check('改名：取到有值的键', renamed, 5)

let { missing: renamedDefault = 9 } = one
__check('改名：键不存在则用默认值', renamedDefault, 9)

const { c = 3, d = 4 } = empty
__check('一个模式里多个默认值（第一个）', c, 3)
__check('一个模式里多个默认值（第二个）', d, 4)

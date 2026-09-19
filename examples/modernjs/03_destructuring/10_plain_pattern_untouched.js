// ============================================
// 10 · 没有默认值的解构：照常工作，且不该被改写
// ============================================
// 注意：这个文件【没有】requires-transform —— 因为原文本来就能跑。
//
// 这些写法 Rhino 原生就支持（{a} / [x, y] / {a: b} / for-of 里的模式）。
// 转换器对它们应当**原样放过**：无谓改写只会平白引入行为差异，
// 比如把 `const {a} = ...` 变成临时变量就改变了作用域与求值时机。
// 所以这个文件的作用是「钉住不改写」这件事。
//
// 注意：不带声明的解构赋值 `({a} = o)` Rhino 是**不支持**的（报 invalid object
// initializer），那属于另一类限制，见 07_reject/。

const src = { a: 1, b: 2 }

const { a, b } = src
__check('普通对象解构', a + b, 3)

const arr = [10, 20]
const [first, second] = arr
__check('普通数组解构', first + second, 30)

const { a: renamed } = src
__check('普通改名解构', renamed, 1)

const { missing } = src
__check('取不存在的键是 undefined', String(missing), 'undefined')

function take({ x }) {
    return String(x)
}
__check('形参里没有默认值的模式', take({ x: 5 }), '5')

let total = 0
for (const { v } of [{ v: 1 }, { v: 2 }]) {
    total += v
}
__check('for-of 里没有默认值的模式', total, 3)

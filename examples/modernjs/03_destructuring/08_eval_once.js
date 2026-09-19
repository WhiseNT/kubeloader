// ============================================
// 08 · 右侧表达式只能求值一次
// ============================================
// requires-transform
//
// 摊平后是 `目标 = 取值 === undefined ? 默认值 : 取值`，天真的写法会「取值两次」。
// 当右侧是函数调用时，那意味着副作用跑两遍——这是最典型的静默错值。

let calls = 0

function sourceObject() {
    calls += 1
    return {}
}

const { a = 1 } = sourceObject()
__check('对象模式：右侧只求值一次', calls, 1)
__check('对象模式：默认值仍然生效', a, 1)

let hits = 0

function sourceArray() {
    hits += 1
    return []
}

const [z = 5] = sourceArray()
__check('数组模式：右侧只求值一次', hits, 1)
__check('数组模式：默认值生效', z, 5)

const obj = {}
const { q = 7 } = obj
__check('右侧是标识符时同样正确', q, 7)

function label({ name = 'x' }, second = 'y') {
    return name + '/' + second
}

__check('形参里的模式只取一次值（形参本身不由调用方重复求值）', label({}), 'x/y')

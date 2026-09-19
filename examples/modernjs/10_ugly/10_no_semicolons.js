// ============================================
// 丑写法 10 · 全篇不写分号（依赖 ASI）
// ============================================
// requires-transform
//
// KubeJS 脚本里不写分号很常见。这里所有语句都不带分号，
// 只在「行首是 [ 或 ( 」这种真有 ASI 风险的地方用前置分号自保。

class NoSemi {
    m() { return 1 }
    static of() { return new NoSemi() }
}

const a = NoSemi.of()
const b = a.m()
const arr = [...[1, 2], 3]
const { v = 9 } = {}
const k = 'k'
const o = { [k + 1]: 5 }
let n = null
n ||= 7
function f({ p = 4 }) { return p }

// 前置分号保护：行首是 [ 时不加分号会被上一行「吃」掉
const t = 1
;[1, 2].forEach(function () {})

__check('无分号：静态工厂 + 方法', b, 1)
__check('无分号：展开', arr.join(','), '1,2,3')
__check('无分号：解构默认值', v, 9)
__check('无分号：计算属性名', o.k1, 5)
__check('无分号：逻辑赋值', n, 7)
__check('无分号：形参解构默认值', f({}), 4)
__check('无分号：前置分号保护的数组方法', t, 1)

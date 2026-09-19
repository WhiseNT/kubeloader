// ============================================
// 丑写法 09 · IIFE / 逗号运算符 / 标签
// ============================================
// requires-transform
//
// 老式模块写法：整个模块包在 IIFE 里，里面放 class、解构、return 对象字面量。
// 这类写法对「按花括号配对找边界」的实现压力很大。

var Mod = (function () {
    class Inner {
        m() { return 7 }
    }
    var { base = 1 } = {}
    return {
        run: function () {
            return new Inner().m() + base
        }
    }
})()

__check('IIFE 里的 class + 解构默认值', Mod.run(), 8)

var comma = (1, 2, 3)
__check('逗号运算符取最后一个', comma, 3)

var bang = 0
!function () { bang = 1 }()
__check('!function IIFE', bang, 1)

var wrapped = (function () {
    return 5
})()
__check('括号包裹的 IIFE', wrapped, 5)

__check('void 0 就是 undefined', String(void 0), 'undefined')

var s = 0
outer: for (var i = 0; i < 3; i++) {
    for (var j = 0; j < 3; j++) {
        if (j === 1) continue outer
        s += 1
    }
}
__check('带标签的 continue', s, 3)

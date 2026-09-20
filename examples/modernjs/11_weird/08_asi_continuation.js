// ============================================
// 怪写法 08 · ASI 与跨行表达式
// ============================================
// requires-transform
//
// 契约：语句在哪结束由 ASI 规则决定，转换器不能自己「按行」切。
// 下面既有继续到下一行的表达式，也有靠前置分号自保的语句，
// 还有紧跟在语句后面的 class。

const a = 1
const b = a
    + 1
const c = b + (function () {
    return 1
})()
const d = 4

;[1, 2].forEach(function () {})

class AfterStatement {
    m() {
        return 5
    }
}

const e = new AfterStatement().m()
const f = a
    + b
    + c
    + d

const g = [
    1,
    2
].length

__check('表达式跨行继续', b, 2)
__check('跨行函数表达式参与运算', c, 3)
__check('前置分号保护的数组方法', d, 4)
__check('紧跟语句的 class', e, 5)
__check('多行加法', f, 10)
__check('跨行数组字面量', g, 2)

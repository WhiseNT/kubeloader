// ============================================
// 02 · 计算属性名（表达式）
// ============================================
// requires-transform
//
// 方括号里可以是任意表达式，不只是变量名。

const i = 2
const byConcat = { ['x' + i]: 9 }

__check('字符串拼接当属性名', byConcat.x2, 9)

const byMath = { [1 + 1]: 'two' }
__check('算术表达式当属性名', byMath[2], 'two')

function keyOf(n) {
    return 'k' + n
}

const byCall = { [keyOf(7)]: 'seven' }
__check('函数返回值当属性名', byCall.k7, 'seven')

const nested = { [['a'].join('')]: 'joined' }
__check('数组方法结果当属性名', nested.a, 'joined')

// ============================================
// 07 · 展开字符串 / 展开函数返回值
// ============================================
// requires-transform
//
// 展开的对象不一定是数组字面量：字符串按字符拆、函数返回值也行，
// 判断「这是什么」要在掩码后的文本上做，别被字符串字面量骗到。

__check('字符串按字符展开', [...'abc'].join(','), 'a,b,c')
__check('展开结果仍是数组', Array.isArray([...'ab']), 'true')

function pair() {
    return [1, 2]
}

__check('展开函数返回值', Math.max(...pair()), 2)

function pick() {
    return ['k']
}

const obj = { [pick()[0]]: 1 }
__check('计算属性名里调函数（对照）', obj.k, 1)

const chars = [0, ...'ab']
__check('展开字符串且前后都有元素', chars.join(','), '0,a,b')

const nestedCall = [1, ...[2, 3].concat([4])]
__check('展开一个表达式结果', nestedCall.join(','), '1,2,3,4')

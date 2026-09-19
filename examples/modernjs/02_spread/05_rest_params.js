// ============================================
// 05 · 剩余参数 ...rest
// ============================================
// requires-transform
//
// 注意：箭头函数的参数表不会被改写（Rhino 侧不支持），所以这里只用 function 声明。

function joinAll(sep, ...parts) {
    return parts.join(sep)
}

function countRest(first, ...rest) {
    return first + '|' + rest.length
}

function onlyRest(...all) {
    return all.length
}

function sumRest(...nums) {
    let total = 0
    for (let i = 0; i < nums.length; i++) {
        total += nums[i]
    }
    return total
}

__check('剩余参数拼接', joinAll('-', 'a', 'b', 'c'), 'a-b-c')
__check('只剩一个前导参数时，rest 为空数组', joinAll('-'), '')
__check('rest 是真数组（有 length）', countRest('x', 1, 2, 3), 'x|3')
__check('只有 rest 参数', onlyRest(1, 2, 3, 4), 4)
__check('rest 可以被遍历求和', sumRest(1, 2, 3, 4), 10)
__check('rest 为空时可遍历', sumRest(), 0)

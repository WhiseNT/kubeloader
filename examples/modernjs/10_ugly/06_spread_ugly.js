// ============================================
// 丑写法 06 · 展开的空白地狱
// ============================================
// requires-transform
//
// 契约：写 ... 时前后怎么加空格都要对。
// 特别注意「调用括号前留了个空格」（f (...args)）和「点两侧有空格」的成员调用
// —— 后者取不到完整接收者，按「宁可响亮失败也不丢 this」处理，见 07_reject/09。

function collect(a, b, c) {
    return a + '|' + b + '|' + c
}

const args = [1, 2, 3]
const two = [4, 5]

__check('括号内两侧留空格', collect( ...args ), '1|2|3')
__check('调用括号前留空格', collect (...args), '1|2|3')
__check('完全不留空格', collect(...args), '1|2|3')
__check('参数间多空格', collect( ...[1] , ...[2] , ...[3] ), '1|2|3')

const arr = [ ...[1, 2] , ...[3] ]
__check('数组字面量里留空格', arr.join(','), '1,2,3')

const one = { x: 1 }
const other = { y: 2 }
const merged = { ...one , ...other }
__check('对象字面量里留空格', merged.x + merged.y, 3)

const obj = {
    base: 10,
    add(a, b) {
        return this.base + a + b
    }
}
__check('成员调用括号前留空格', obj.add (...two), 19)

class Adder {
    constructor(base) {
        this.base = base
    }

    add(a) {
        return this.base + a
    }
}
const adder = new Adder(100)
__check('实例方法调用括号前留空格', adder.add (...[1]), 101)

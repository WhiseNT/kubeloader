// ============================================
// 05 · 数组模式里的空位（hole）
// ============================================
// requires-transform
//
// [ , b = 2 ] 这种写法里，第一个逗号前是空位，但它**要占一个下标**。
// 数错下标就会取到错误位置的元素，而且不报错——属于静默算错。

const data = ['a', 'b', 'c', 'd']

const [, second = 'x'] = data
__check('空位占下标：取到第 2 个', second, 'b')

const [, , third = 'x'] = data
__check('两个空位：取到第 3 个', third, 'c')

const [first, , thirdToo = 'x'] = data
__check('中间空位：前后都要取对', first + '/' + thirdToo, 'a/c')

const [, , , , fifth = 'x'] = data
__check('空位超过长度 → 默认值', fifth, 'x')

const holes = [, , , ,]
__check('尾部空位仍是数组字面量', holes.length, 4)

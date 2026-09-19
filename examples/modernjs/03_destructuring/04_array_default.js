// ============================================
// 04 · 数组解构默认值
// ============================================
// requires-transform
//
// 数组模式按位置取值：越界和 undefined 都要落到默认值。

const empty = []
const two = [7, 8]

const [x = 1] = empty
__check('空数组 → 默认值', x, 1)

const [y = 1] = two
__check('有元素时不替换', y, 7)

const [, second = 9] = two
__check('跳过一位后取值', second, 8)

const [, , third = 9] = two
__check('越界位置 → 默认值', third, 9)

const mixed = [10, undefined, 30]
const [a, b = 2, c = 3, d = 4] = mixed
__check('数组：有值', a, 10)
__check('数组：undefined → 默认值', b, 2)
__check('数组：有值不替换（第二位之后）', c, 30)
__check('数组：越界 → 默认值（末位）', d, 4)

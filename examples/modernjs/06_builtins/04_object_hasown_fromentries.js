// ============================================
// 04 · Object.hasOwn / Object.fromEntries
// ============================================
// 不标 requires-transform（补丁由运行器先装，同线上 mixin）。
//
// hasOwn 取代了 obj.hasOwnProperty —— 后者会被「属性名就叫 hasOwnProperty」的对象破坏。

const obj = { a: 1 }

__check('hasOwn：自己的属性', Object.hasOwn(obj, 'a'), 'true')
__check('hasOwn：不存在的属性', Object.hasOwn(obj, 'b'), 'false')
__check('hasOwn：继承来的属性算 false', Object.hasOwn(obj, 'toString'), 'false')
__check('hasOwn：数字键', Object.hasOwn({ 0: 'x' }, 0), 'true')

const pairs = [['a', 1], ['b', 2]]
const fromPairs = Object.fromEntries(pairs)

__check('fromEntries：数组对', fromPairs.a + fromPairs.b, 3)
__check('fromEntries：键数量', Object.keys(fromPairs).length, 2)

const fromMap = Object.fromEntries(new Map([['k', 'v']]))
__check('fromEntries：Map', fromMap.k, 'v')

const empty = Object.fromEntries([])
__check('fromEntries：空数组', Object.keys(empty).length, 0)

const chained = Object.fromEntries([['n', 3]])
__check('fromEntries 结果可继续使用', chained.n * 2, 6)

// ============================================
// 06 · 对象字面量里的展开 {...a}
// ============================================
// requires-transform
//
// 合并配置项：{ ...默认值, ...用户传入 }。书写顺序决定覆盖关系。

const base = { a: 1, b: 2 }

const merged = { ...base, c: 3 }

__check('对象展开 3 个键', merged.a + '/' + merged.b + '/' + merged.c, '1/2/3')

const override = { ...base, a: 9 }
__check('写在后面的覆盖前面的', override.a, 9)
__check('被覆盖时其它键保留', override.b, 2)

const twoSpreads = { ...{ x: 1 }, ...{ y: 2 } }
__check('一个对象里多次展开', twoSpreads.x + twoSpreads.y, 3)

const empty = { ...{} }
__check('展开空对象', Object.keys(empty).length, 0)

const nothing = { ...base, ...{} }
__check('展开后仍是普通对象', nothing.b, 2)

const layered = { ...base, ...{ b: 20 } }
__check('后展开的对象覆盖前一个', layered.b, 20)

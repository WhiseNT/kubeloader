// ============================================
// 03 · 书写顺序决定覆盖关系
// ============================================
// requires-transform
//
// 对象字面量是「按书写顺序赋值」的：后面的同名键覆盖前面的。
// 转换时如果把顺序弄乱（比如先放普通成员再放计算成员），结果就会变。

const same = { a: 1, ['a']: 2 }
__check('同名时后面的覆盖前面的', same.a, 2)

const reverse = { ['a']: 2, a: 1 }
__check('顺序反过来的情况也要对', reverse.a, 1)

const k = 'b'
const mixed = { a: 1, [k]: 2, c: 3 }
__check('普通成员在计算成员之前', mixed.a, 1)
__check('计算成员本身', mixed.b, 2)
__check('普通成员在计算成员之后', mixed.c, 3)

const overwrite = { [k]: 'first', [k]: 'second' }
__check('两个相同的计算键', overwrite.b, 'second')
